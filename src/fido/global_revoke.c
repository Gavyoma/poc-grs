#define MBEDTLS_ALLOW_PRIVATE_ACCESS

#include "global_revoke.h"
#include "global_revoke_display.h"

#include <crypto_utils.h>
#include "ctap2_cbor.h"
#include "hid/ctap_hid.h"
#include "files.h"
#include "apdu.h"
#include "mbedtls/sha256.h"
#include "random.h"
#include "pico_keys.h"

#include <random.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "crypto_utils.h"
#include "mbedtls/ecdsa.h"
#include "mbedtls/sha256.h"
#include "mbedtls/bignum.h"
#include "mbedtls/ecp.h"

#include <stdint.h>
#include "mbedtls/rsa.h"
#include "mbedtls/entropy.h"
#include "mbedtls/ctr_drbg.h"
#include <string.h>
#include <stddef.h>
#include "cbor.h"
#include "mbedtls/pk.h"
#include <stdio.h>
#include "file.h"
#include "mbedtls/base64.h"

// ============================================================================
// CONFIGURATION (Change this to 1024 or 2048 as needed)
// ============================================================================
#define FIDO_RSA_BITS 1024
#define FIDO_RSA_BYTES (FIDO_RSA_BITS / 8)
#define KEM_AES_SIZE 32
// ============================================================================
// Calculate max raw size of your custom struct (N + D + P + Q + E)
// For 1024-bit: 128 + 128 + 64 + 64 + 4 = 388 bytes
#define RAW_STRUCT_BYTES ((FIDO_RSA_BYTES * 3) + 4)
// Calculate the exact Base64 string size for your struct.
// Base64 inflates data by 4/3. The "+ 2" handles C integer division rounding.
// We add +15 as a safe padding margin (null terminators, struct alignment).
#define MAX_BASE64_KEY_SIZE ((((RAW_STRUCT_BYTES) + 2) / 3) * 4 + 15)


#define RN_SIZE 32 // RN Size
#define AES_PAYLOAD_BUFFER_SIZE (RN_SIZE + 16) // The final buffer MUST be the Message Size + 16 bytes for the Nonce

// A clean C structure to hold the raw RSA math parameters
typedef struct {
    uint8_t N[FIDO_RSA_BYTES];       // Modulus
    uint8_t P[FIDO_RSA_BYTES / 2];   // Prime 1
    uint8_t Q[FIDO_RSA_BYTES / 2];   // Prime 2
    uint8_t D[FIDO_RSA_BYTES];       // Private Exponent
    uint8_t E[4];                    // Public Exponent (Usually 65537)
} raw_rsa_key_t;

// ============================================================================
// 0. HARDWARE RNG BYPASS
// ============================================================================
#if defined(MCU_IS_ESP32)
    #include "esp_random.h"
    static int direct_hardware_rng(void *data, unsigned char *output, size_t len) {
        (void)data;
        esp_fill_random(output, len);
        return 0;
    }
#else
#include "hardware/structs/rosc.h"
static int direct_hardware_rng(void *data, unsigned char *output, size_t len) {
        (void)data;
        for (size_t i = 0; i < len; i++) {
            uint8_t byte = 0;
            for (int b = 0; b < 8; b++) {
                byte = (byte << 1) | (rosc_hw->randombit & 1);
            }
            output[i] = byte;
        }
        return 0;
    }
#endif

// ============================================================================
// BARE-METAL POSIX OVERRIDE
// Mbed TLS and Newlib are desperately looking for a Linux OS to provide
// random numbers. This intercepts their OS request and feeds them
// Raspberry Pi Pico hardware noise instead!
// ============================================================================

int _getentropy(void *buffer, size_t length) {
    uint8_t *buf = (uint8_t *)buffer;

    for (size_t i = 0; i < length; i++) {
        uint8_t byte = 0;
        for (int b = 0; b < 8; b++) {
            // Read 1 random bit from the physical Pico ROSC jitter
            byte = (byte << 1) | (rosc_hw->randombit & 1);
        }
        buf[i] = byte;
    }

    return 0;
}

// Some versions of Newlib look for the non-underscore version,
// so we safely alias it here just in case.
int getentropy(void *buffer, size_t length) {
    return _getentropy(buffer, length);
}
///////////////////////////////////////////////
///////////////////////////////////////////////
// ============================================================================
// READ PUBLIC KEY AND CONVERT TO BASE64
// ============================================================================
// ============================================================================
// STANDALONE BASE64 ENCODER (Bypasses Mbed TLS Linker Errors)
// ============================================================================
static const char b64_chars[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

void encode_base64_standalone(const uint8_t *in, size_t in_len, uint8_t *out, size_t *out_len) {
    size_t i = 0, j = 0;
    while (i < in_len) {
        uint32_t a = i < in_len ? in[i++] : 0;
        uint32_t b = i < in_len ? in[i++] : 0;
        uint32_t c = i < in_len ? in[i++] : 0;
        uint32_t triple = (a << 16) + (b << 8) + c;

        out[j++] = b64_chars[(triple >> 18) & 0x3F];
        out[j++] = b64_chars[(triple >> 12) & 0x3F];
        out[j++] = b64_chars[(triple >> 6) & 0x3F];
        out[j++] = b64_chars[triple & 0x3F];
    }

    // Add standard Base64 padding '='
    if (in_len % 3 == 1) { out[j - 1] = '='; out[j - 2] = '='; }
    else if (in_len % 3 == 2) { out[j - 1] = '='; }

    out[j] = '\0'; // Safely null-terminate the string
    if (out_len) *out_len = j;
}
int get_fido_pubkey_base64(unsigned char *out_b64_buffer, size_t buffer_size, size_t *written_len) {

    file_t *ef_pub = search_file(EF_GLOBALREVOKE_PUB);

    if (ef_pub == NULL || !file_has_data(ef_pub)) {
        printf("❌ Error: EF_FIDO_RSA_PUB not found or is empty!\n");
        return -1;
    }

    uint8_t *raw_data = file_get_data(ef_pub);
    uint16_t raw_size = file_get_size(ef_pub);

    // Buffer safety check: Base64 is mathematically ~1.33x larger than binary
    size_t required_size = ((raw_size + 2) / 3) * 4 + 1;
    if (buffer_size < required_size) {
        // Error: Base64 buffer is too small! Needs required_size bytes.
        return -2;
    }

    encode_base64_standalone(raw_data, raw_size, out_b64_buffer, written_len);

    return 0;
}
///////////////////////////////////////////////

///////////////////////////////////////////////
/**
 * Fills a 32-byte array with random data directly from the Pico's ROSC hardware.
 * Simple hardware TRNG — no MbedTLS, no extra libraries.
 *
 * WARNING: This is **not** cryptographically secure.
 * Use only for non-security purposes (LED blinking, simple games, etc.).
 * For FIDO2/crypto use the MbedTLS version instead.
 */
void generate_simple_random_32(uint8_t *output) {
    // Loop through all 32 bytes we need to fill
    for (int i = 0; i < 32; i++) {
        uint8_t random_byte = 0;

        // Build the byte 1 bit at a time (8 bits total)
        for (int bit = 0; bit < 8; bit++) {
            // Read exactly 1 bit of chaotic physical noise from the hardware
            random_byte = (random_byte << 1) | (rosc_hw->randombit & 1);
        }

        output[i] = random_byte;
    }
}
/////////////////////////////////////////////////////////
////////////////////////////////////////////
/// V
/**
 * Mathematically binds the uncompressed Public Key and a Random Challenge into a single 32-byte SHA-256 hash.
 */
int generate_combined_hash(mbedtls_ecdsa_context *ekey, const uint8_t *random_challenge, uint8_t *output_hash) {

    // 1. Get the curve size (for P-256, plen is 32 bytes)
    const mbedtls_ecp_curve_info *cinfo = mbedtls_ecp_curve_info_from_grp_id(ekey->grp.id);
    if (cinfo == NULL) {
        return CTAP1_ERR_OTHER;
    }
    size_t plen = cinfo->bit_size / 8;

    // The uncompressed key length is exactly 65 bytes (1 byte for 0x04 + 32 for X + 32 for Y)
    size_t raw_len = 1 + (plen * 2);

    // 2. Allocate pointers for the raw key and the hash
    uint8_t *raw_pub_key = (uint8_t *)calloc(1, raw_len);

    // Set the uncompressed format indicator
    raw_pub_key[0] = 0x04;

    // Extract X and Y mathematically straight into the buffer
    mbedtls_mpi_write_binary(&ekey->Q.X, raw_pub_key + 1, plen);
    mbedtls_mpi_write_binary(&ekey->Q.Y, raw_pub_key + 1 + plen, plen);

    // ==========================================
    // 3. THE STREAMING HASH ENGINE
    // ==========================================
    mbedtls_sha256_context sha_ctx;
    mbedtls_sha256_init(&sha_ctx);

    // Start the SHA-256 engine (0 means SHA-256, 1 would mean SHA-224)
    mbedtls_sha256_starts(&sha_ctx, 0);

    // Feed the 65-byte Public Key into the engine
    mbedtls_sha256_update(&sha_ctx, raw_pub_key, raw_len);

    // Feed the 32-byte Random Challenge into the engine right after it
    mbedtls_sha256_update(&sha_ctx, random_challenge, RN_SIZE);

    // Finish the math and write the final 32 bytes to our output array
    mbedtls_sha256_finish(&sha_ctx, output_hash);

    // Clean up the hash engine from RAM
    mbedtls_sha256_free(&sha_ctx);

    return 0;
}

////////////////////////////////////////////////////
////////////////////////////////////////////////////
////////////////////////////////////////////////////
// ============================================================================
// PUBLIC KEY DER CONVERTER
// ============================================================================
int convert_rsa_pubkey_to_der(mbedtls_pk_context *pk,
                              unsigned char *der_buf,
                              size_t buf_size,
                              unsigned char **out_der_start,
                              size_t *out_der_len) {

    int der_len = mbedtls_pk_write_pubkey_der(pk, der_buf, buf_size);
    if (der_len < 0) return der_len;

    // CRITICAL: Mbed TLS writes backwards!
    *out_der_start = der_buf + buf_size - der_len;
    *out_der_len = (size_t)der_len;

    return 0;
}

// ============================================================================
// RSA KEYPAIR LOAD OR GENERATE
// ============================================================================
int generate_rsa_keypair(mbedtls_pk_context *pk) {
    mbedtls_pk_init(pk);
    mbedtls_pk_setup(pk, mbedtls_pk_info_from_type(MBEDTLS_PK_RSA));
    mbedtls_rsa_context *rsa = mbedtls_pk_rsa(*pk);

    file_t *ef_priv = search_file(EF_GLOBALREVOKE);
    file_t *ef_pub  = search_file(EF_GLOBALREVOKE_PUB);

    if (ef_priv == NULL || ef_pub == NULL) {
        // Error: FIDO2 filesystem entries missing
        mbedtls_pk_free(pk);
        return -1;
    }

    // ------------------------------------------------------------------------
    // READ RAW KEY (If it exists)
    // ------------------------------------------------------------------------
    if (file_has_data(ef_priv) && file_has_data(ef_pub)) {

        if (file_get_size(ef_priv) == sizeof(raw_rsa_key_t)) {
            raw_rsa_key_t *raw_key = (raw_rsa_key_t *)file_get_data(ef_priv);

            int import_ret = mbedtls_rsa_import_raw(rsa,
                raw_key->N, sizeof(raw_key->N),
                raw_key->P, sizeof(raw_key->P),
                raw_key->Q, sizeof(raw_key->Q),
                raw_key->D, sizeof(raw_key->D),
                raw_key->E, sizeof(raw_key->E)
            );

            if (import_ret == 0) {
                mbedtls_rsa_complete(rsa);
                // Successfully restored RSA Keypair
                return 0;
            }
        }
    }

    // ------------------------------------------------------------------------
    // GENERATE NEW KEYPAIR
    // ------------------------------------------------------------------------
    // Generating RSA Key. This may take a moment
    int ret = mbedtls_rsa_gen_key(rsa, direct_hardware_rng, NULL, FIDO_RSA_BITS, 65537);

    if (ret != 0) {
        // Failed to generate RSA Keypair
        mbedtls_pk_free(pk);
        return ret;
    }

    // ------------------------------------------------------------------------
    // SAVE PRIVATE KEY TO FILE
    // ------------------------------------------------------------------------
    raw_rsa_key_t raw_key = {0};

    ret = mbedtls_rsa_export_raw(rsa,
        raw_key.N, sizeof(raw_key.N),
        raw_key.P, sizeof(raw_key.P),
        raw_key.Q, sizeof(raw_key.Q),
        raw_key.D, sizeof(raw_key.D),
        raw_key.E, sizeof(raw_key.E)
    );

    if (ret == 0) {
        int flash_ret = file_put_data(ef_priv, (uint8_t *)&raw_key, sizeof(raw_rsa_key_t));
        if (flash_ret == CCID_OK) {
            // Private key saved to a file
        }
    }

    // ------------------------------------------------------------------------
    // CONVERT AND SAVE PUBLIC DER TO FILE
    // ------------------------------------------------------------------------
    // An RSA 1024/2048 public key DER format is max ~300 bytes. 400 is extremely safe.
    unsigned char der_buffer[400] = {0};
    unsigned char *der_start_ptr = NULL;
    size_t der_length = 0;

    if (convert_rsa_pubkey_to_der(pk, der_buffer, sizeof(der_buffer), &der_start_ptr, &der_length) == 0) {

        int pub_flash_ret = file_put_data(ef_pub, der_start_ptr, (uint16_t)der_length);

        if (pub_flash_ret == CCID_OK) {
            // Public DER Key saved to file
        } else {
            // Failed to save Public DER to file
            return -1;
        }
    } else {
        // Failed to convert Public Key to DER format!
        return -1;
    }

    return 0;
}
//////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////
// ============================================================================
// Generates a random 32-byte AES secret and encrypts it.
// ============================================================================
// Reverse RSA KEM: ENCAPSULATE USING PRIVATE KEY
// ============================================================================
// Generate the Random Seed (Z) which is exact size of FIDO_RSA_BYTES so no need of padding with zeros.
// ============================================================================
int custom_kem_encapsulate_private(mbedtls_rsa_context *rsa,
                                   uint8_t *out_shared_secret,
                                   size_t secret_len,
                                   uint8_t *out_ciphertext) {

    // Get dynamic size of loaded key (e.g., 128 bytes for RSA-1024)
    size_t rsa_len = mbedtls_rsa_get_len(rsa);

    // Safety checks: Ensure buffers are large enough
    if (secret_len > 32 || rsa_len > FIDO_RSA_BYTES) {
        // [Custom KEM] Key size mismatch
        return -1;
    }

    // ========================================================================
    // The array size of seed_Z is exactly FIDO_RSA_BYTES
    // (e.g., 128 bytes for RSA-1024, or 256 bytes for RSA-2048).
    // It must perfectly match the length of the RSA key being used.
    // ========================================================================
    uint8_t seed_Z[FIDO_RSA_BYTES] = {0};

    // ------------------------------------------------------------------------
    // STEP A: Generate Random Seed (Z)
    // ------------------------------------------------------------------------
    int ret = direct_hardware_rng(NULL, seed_Z, rsa_len);
    if (ret != 0) return ret;

    // ========================================================================
    // Fix the sign bit: Forces the random number to be mathematically smaller
    // than the RSA Modulus (Matches the Java BigInteger logic exactly).
    // ========================================================================
    seed_Z[0] = 0x00;

    // ------------------------------------------------------------------------
    // STEP B: Derive Symmetric Key (The SHA-256)
    // ------------------------------------------------------------------------
    // The array size of hash_output is exactly 32 bytes,
    // which is the fixed output size of a standard SHA-256 cryptographic hash.
    // ------------------------------------------------------------------------
    uint8_t hash_output[32] = {0};

    // Hash the seed_Z down to exactly 32 bytes
    mbedtls_sha256(seed_Z, rsa_len, hash_output, 0);

    // Output the AES key for encryption
    memcpy(out_shared_secret, hash_output, secret_len);

    // ------------------------------------------------------------------------
    // STEP C: "Encrypt" seed using raw Private Key (No Padding)
    // ------------------------------------------------------------------------
    ret = mbedtls_rsa_private(rsa,
                              direct_hardware_rng,
                              NULL,
                              seed_Z,
                              out_ciphertext);

    // If RSA math fails, immediately destroy the AES key
    if (ret != 0) {
        memset(out_shared_secret, 0, secret_len);
    }

    // ------------------------------------------------------------------------
    // STEP D: Secure Cleanup
    // ------------------------------------------------------------------------
    // Wipe the plaintext seed and the hash from RAM immediately
    memset(seed_Z, 0, sizeof(seed_Z));
    memset(hash_output, 0, sizeof(hash_output));

    return ret;
}

// ============================================================================
// GENERATE KEM PAYLOAD
// ============================================================================
int generate_kem_payload(uint8_t *out_aes_key, uint8_t *out_ciphertext) {

    // [KEM Generator] Starting RSA-KEM Encapsulation
    // 1. Initialize the raw RSA engine
    mbedtls_rsa_context rsa;
    mbedtls_rsa_init(&rsa);

    // 2. Load the Private Key from file
    file_t *ef_priv = search_file(EF_GLOBALREVOKE);
    if (ef_priv == NULL || !file_has_data(ef_priv)) {
        // [KEM Generator] Key not found in file
        mbedtls_rsa_free(&rsa);
        return -1;
    }

    raw_rsa_key_t *raw_key = (raw_rsa_key_t *)file_get_data(ef_priv);

    // 3. Load ALL math variables (N, P, Q, D, E) into the RSA engine
    int import_ret = mbedtls_rsa_import_raw(&rsa,
        raw_key->N, sizeof(raw_key->N),
        raw_key->P, sizeof(raw_key->P), // <-- Private Prime 1
        raw_key->Q, sizeof(raw_key->Q), // <-- Private Prime 2
        raw_key->D, sizeof(raw_key->D), // <-- Private Exponent
        raw_key->E, sizeof(raw_key->E)
    );

    if (import_ret != 0) {
        // [KEM Generator] Failed to load private key into RSA engine
        mbedtls_rsa_free(&rsa);
        return -1;
    }
    mbedtls_rsa_complete(&rsa);

    // 4. Call the core RSA-KEM Encapsulation function
    int kem_ret = custom_kem_encapsulate_private(&rsa, out_aes_key, KEM_AES_SIZE, out_ciphertext);

    // Clean up the engine immediately to save Pico RAM
    mbedtls_rsa_free(&rsa);

    if (kem_ret != 0) {
        // [KEM Generator] Encapsulation failed
        return -1;
    }

    // [KEM Generator] Success! RSA-KEM AES Key derived and encrypted
    return 0;
}

///////////////////////////////////////////////////////////
// ============================================================================
// AES-256 CTR: AUTO-GENERATE NONCE & ENCRYPT
// ============================================================================
// The output_data buffer MUST be at least (data_len + 16) bytes long!
// Format: [ 16-byte Nonce ] + [ Encrypted Message ]
int apply_aes_ctr_with_nonce(const uint8_t *key,
                             const uint8_t *input_data,
                             size_t data_len,
                             uint8_t *output_data) {

    // 1. Generate a brand new, random 16-byte Nonce
    uint8_t local_nonce[16] = {0};
    int ret = direct_hardware_rng(NULL, local_nonce, 16);
    if (ret != 0) {
        // [AES] Failed to generate random Nonce!
        return ret;
    }

    // 2. Staple the pristine Nonce to the very front of the output buffer
    memcpy(output_data, local_nonce, 16);

    // 3. Initialize the AES engine
    mbedtls_aes_context aes;
    mbedtls_aes_init(&aes);
    ret = mbedtls_aes_setkey_enc(&aes, key, 256);

    if (ret != 0) {
        // [AES] Failed to set encryption key
        mbedtls_aes_free(&aes);
        return ret;
    }

    // 4. Set up CTR mode state trackers
    size_t nc_off = 0;
    // The size is 16 bytes, because AES requires a 128-bit block buffer.
    uint8_t stream_block[16] = {0};

    // 5. Encrypt the data!
    // CRITICAL: We use "output_data + 16" so the AES engine writes the
    // encrypted bytes exactly AFTER the Nonce we just copied in!
    ret = mbedtls_aes_crypt_ctr(&aes,
                                data_len,
                                &nc_off,
                                local_nonce, // Mbed TLS will safely modify this local copy
                                stream_block,
                                input_data,
                                output_data + 16);

    if (ret != 0) {
        // [AES] Cryptography failed
        return -1;
    }

    // Clean up
    mbedtls_aes_free(&aes);

    return ret;
}
//////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////

CborError encode_grs_extension(CborEncoder *mapEncoder, mbedtls_ecdsa_context *ekey) {
    CborEncoder nestedMapEncoder;
    CborError error = CborNoError;


    //////////////////////////////////////////
    ////////// generate revocation nonce RN + V
    // RN = revocation nonce // TODO: check 32 byte or bigger needed?
    uint8_t RN[RN_SIZE] = {0}; // Declare an array to hold the 32 bytes (initialized to 0)
    // Hash(CredPubKey+RN) = v = vCredPkRN
    uint8_t vCredPkRN[32] = {0};
    // Generate the 32-byte RN
    generate_simple_random_32(RN);
    // Generate the combined hash Hash(CredPubKey+RN) = v = vCredPkRN
    if (generate_combined_hash(ekey, RN, vCredPkRN) != 0) {
        return CTAP1_ERR_OTHER;
    }
    //////////////////////////////////////////

    // Encode the top-level extension
    CBOR_CHECK(cbor_encode_text_stringz(mapEncoder, "globalRevoke"));
    CBOR_CHECK(cbor_encoder_create_map(mapEncoder, &nestedMapEncoder, 6));

    //////////////////////////////////////////
    /////// Generate PK revoc and SK revoc
    //////////////////////////////////////////
    mbedtls_pk_context keypair;
    if (generate_rsa_keypair(&keypair) != 0) {
        return CTAP1_ERR_OTHER;
    }

    //////////////////////////////////////////
    /// Gnerate Global revocation key and convert it to base64 for QRCode.
    // Base64 strings are mathematically ~33% larger than raw binary data.
    // For RSA 1024 A 400-byte raw RSA key needs at least a 550-byte buffer.
    // We use 550 to be perfectly safe.
    //////////////////////////////////////////
    // unsigned char pk_r_base64_string[550] = {0};
    unsigned char pk_r_base64_string[MAX_BASE64_KEY_SIZE] = {0};
    size_t text_length = 0;
    get_fido_pubkey_base64(pk_r_base64_string, sizeof(pk_r_base64_string), &text_length);
    // draw QR Code
    draw_qrcode((const char*)pk_r_base64_string);
    // 5. Securely wipe the Private Key (and Public Key) from memory
    mbedtls_pk_free(&keypair);
    //////////////////////////////////////////


    //////////////////////////////////////////
    // V
    //////////////////////////////////////////
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "v"));
    CBOR_CHECK(cbor_encode_byte_string(&nestedMapEncoder, vCredPkRN, 32));
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "vAlg"));
    CBOR_CHECK(cbor_encode_int(&nestedMapEncoder, -16));
    //////////////////////////////////////////

    //////////////////////////////////////////
    /// Generate Reverse RSA KEM, AES CTR for W and C
    ///////////////////////////////////////////////////////
    /// W
    //////////////////////////////////////////
    uint8_t shared_aes_key[KEM_AES_SIZE] = {0};
    uint8_t ciphertext[FIDO_RSA_BYTES] = {0};
    int status = generate_kem_payload(shared_aes_key, ciphertext);
    if (status != 0) {
        return -1; // Abort if generation failed
    }

    // Allocate a buffer for W = Encrypted RN (Revocation Nonce)
    uint8_t w_encrypted[AES_PAYLOAD_BUFFER_SIZE] = {0};
    int aes_status = apply_aes_ctr_with_nonce(shared_aes_key,
                                          RN,
                                          RN_SIZE,
                                          w_encrypted);
    if (aes_status != 0) {
        memset(shared_aes_key, 0, sizeof(shared_aes_key));
        return -1;
    }
    // Encode W
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "w"));
    size_t w_encrypted_size = AES_PAYLOAD_BUFFER_SIZE;
    CBOR_CHECK(cbor_encode_byte_string(&nestedMapEncoder, w_encrypted, w_encrypted_size));
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "wAlg"));
    CBOR_CHECK(cbor_encode_int(&nestedMapEncoder, -65532));

    // Wipe the local copy of the AES key from the stack
    memset(shared_aes_key, 0, sizeof(shared_aes_key));

    // Encode C = Ciphertext
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "c"));
    CBOR_CHECK(cbor_encode_byte_string(&nestedMapEncoder, ciphertext, FIDO_RSA_BYTES));
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "cAlg"));
    CBOR_CHECK(cbor_encode_int(&nestedMapEncoder, -275));

    CBOR_CHECK(cbor_encoder_close_container(mapEncoder, &nestedMapEncoder));

err:
    return error;
}
