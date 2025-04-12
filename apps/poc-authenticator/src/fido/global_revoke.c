/*
 * Copyright (c) 2024-2025, N. "Gavi" Pistolwala
 * All rights reserved.
 *
 * This source code is licensed under the same terms as the rest of the
 * original project, found in the LICENSE file in the root directory.
 */

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
// HARDWARE TRUE RANDOM NUMBER GENERATOR (TRNG) ABSTRACTION LAYER
// ============================================================================
/**
 * @brief Hardware-agnostic entropy harvesting callback for cryptographic operations.
 *
 * This function conforms to standard cryptographic RNG callback signatures
 * (e.g., mbedTLS `f_rng`). It bypasses algorithmic pseudo-random number generators
 * (PRNGs) by directly extracting physical, non-deterministic environmental noise
 * from the underlying silicon.
 *
 * @param data   Opaque pointer to a cryptographic state context. Intentionally
 *               ignored `(void)data` as this function leverages stateless hardware entropy.
 * @param output Pointer to the memory buffer where the stochastic bytes are written.
 * @param len    The exact number of cryptographic random bytes requested.
 *
 * @return 0 on successful entropy extraction (standard cryptographic success code).
 */
#if defined(MCU_IS_ESP32)
    #include "esp_random.h"
    static int direct_hardware_rng(void *data, unsigned char *output, size_t len) {
        (void)data;
        esp_fill_random(output, len);
        return 0;
    }
#else
    #include "hardware/structs/rosc.h"

    /*
     * @brief RP2040/RP2350 TRNG Implementation
     * Harvests entropy from the inherent phase and thermal jitter of the uncalibrated
     * on-chip Ring Oscillator (ROSC). Because the hardware registers only yield
     * single-bit entropy, bytes are constructed mathematically via an LSB accumulator.
     */
    static int direct_hardware_rng(void *data, unsigned char *output, size_t len) {
            (void)data; // Suppress compiler warning for unused context pointer
            for (size_t i = 0; i < len; i++) {
                uint8_t byte = 0;
                // Sequentially harvest 8 independent stochastic bits to construct a full byte
                for (int b = 0; b < 8; b++) {
                    // Shift the accumulator left and inject the microscopic hardware fluctuation
                    // directly into the Least Significant Bit (LSB) slot via a bitwise OR mask.
                    byte = (byte << 1) | (rosc_hw->randombit & 1);
                }
                output[i] = byte;
            }
            return 0;
        }
#endif

// ============================================================================
// CRYPTOGRAPHY CONFIGURATION: RSA KEY SIZE
// ============================================================================
// Defines the bit-length of the RSA modulus.
//
// SECURITY & HARDWARE WARNING:
// - 2048 (Production): Industry minimum standard. Highly recommended
// - 1024 (Testing)   : Cryptographically weak (deprecated by NIST). Use ONLY
//                      for local testing.
// ============================================================================
#define RSA_KEY_BITS 1024

/** @brief Derives the strict byte-boundary required for modulus buffers */
#define RSA_KEY_BYTES (RSA_KEY_BITS / 8)

/** @brief Specifies a 256-bit (32-byte) symmetric target for Key Encapsulation (KEM) */
#define KEM_AES_SIZE 32

/**
 * @brief Computes the uncompressed memory footprint of the internal RSA key structure.
 *
 * Calculates the exact cumulative byte size of the essential RSA parameters:
 * Modulus (N) + Private Exponent (D) + Primes (P, Q) + Public Exponent (E).
 *
 * Mathematical allocation per component:
 * - N : 1.0 * RSA_KEY_BYTES
 * - D : 1.0 * RSA_KEY_BYTES
 * - P : 0.5 * RSA_KEY_BYTES (CRT prime)
 * - Q : 0.5 * RSA_KEY_BYTES (CRT prime)
 * - E : 4 bytes (Standard 65537, fixed 32-bit integer)
 *
 * The cumulative equation algorithmically reduces to: (3 * RSA_KEY_BYTES) + 4.
 * For example 1024-bit: 128 + 128 + 64 + 64 + 4 = 388 bytes
 */
#define RAW_STRUCT_BYTES ((RSA_KEY_BYTES * 3) + 4)

/**
 * @brief Derives the maximum Radix-64 (Base64) allocation boundary.
 *
 * The Radix-64 encoding scheme inflates binary payloads by a strict 4:3 ratio.
 *
 * Formula mechanics:
 * - `+ 2` : Implements a mathematical ceiling function over standard integer division
 *           to guarantee fractional byte thresholds round up to the next block.
 * - `+ 15`: Provides a deterministic safety margin to accommodate compiler-specific
 *           structure alignment constraints, potential newline delimiters, and the
 *           strictly required C-string null-terminator ('\0').
 */
#define MAX_BASE64_KEY_SIZE ((((RAW_STRUCT_BYTES) + 2) / 3) * 4 + 15)

/** @brief Revocation nonce a 256-bit (32-byte) */
#define RN_SIZE 32

/**
 * @brief Defines the absolute memory boundary for the AES-CTR transmission payload.
 *
 * Mathematically allocates continuous memory for the algebraic payload structure:
 * Output Capacity = length(Plaintext) + length(128-bit/16-bytes Nonce).
 *
 * @note This calculation is strictly bound to stream cipher modes (CTR) that do
 *       not require block padding. It is invalid for block modes (e.g., CBC) or
 *       authenticated modes (e.g., GCM) which require different structural overhead.
 */
#define AES_CTR_PAYLOAD_BUFFER_SIZE (RN_SIZE + 16)

/**
 * @brief Memory-mapped algebraic components of an RSA cryptographic keypair.
 *
 * This structure isolates the foundational multi-precision integers required for
 * RSA modular exponentiation. It contains highly sensitive secret key material
 * (P, Q, D) multiplexed with public parameters (N, E).
 *
 * @note Endianness: Cryptographic arrays in this structure are strictly expected
 *       to be formatted in Big-Endian (network byte order) representation.
 *
 * @warning MEMORY SECURITY: Instances of this structure contain raw, unencrypted
 *          prime factors. This memory must be cryptographically zeroized (scrubbed)
 *          immediately after processing to mitigate cold-boot and DMA extraction attacks.
 */
typedef struct {
    /**
     * @brief Public Modulus (N)
     * Mathematical product of the secret primes (N = P * Q). Its byte-length
     * strictly dictates the cryptographic strength of the trapdoor permutation.
     */
    uint8_t N[RSA_KEY_BYTES];

    /**
     * @brief First Secret Prime Factor (P)
     * Foundation of the trapdoor. Mathematically constrained to exactly half
     * the modulus bit-length to prevent elliptic curve factorization attacks.
     */
    uint8_t P[RSA_KEY_BYTES / 2];

    /**
     * @brief Second Secret Prime Factor (Q)
     * Co-prime foundation. Also mathematically constrained to half the modulus bit-length.
     */
    uint8_t Q[RSA_KEY_BYTES / 2];

    /**
     * @brief Private Exponent (D)
     * The modular multiplicative inverse of E modulo Carmichael's totient function of N.
     * Utilized as the primary exponent during decryption and digital signature generation.
     */
    uint8_t D[RSA_KEY_BYTES];

    /**
     * @brief Public Exponent (E)
     * Utilized for encryption and signature verification. Standardized to a 32-bit (4-byte)
     * allocation to perfectly accommodate the widely adopted Fermat prime F4 (65537 / 0x010001).
     */
    uint8_t E[4];

} raw_rsa_key_t;


/**
 * @brief System call override providing hardware-backed cryptographic entropy.
 *
 * In bare-metal environments, cryptographic libraries (e.g., mbedTLS) and standard C
 * libraries (Newlib) lack an underlying OS to service POSIX random requests (e.g., `/dev/urandom`).
 * This function intercepts the `_getentropy` syscall stub and routes it to the
 * microcontroller's True Random Number Generator (TRNG).
 *
 * Entropy is harvested directly from the inherent thermal and phase jitter of the
 * RP2040/RP2350 Ring Oscillator (ROSC).
 *
 * @param buffer Pointer to the memory block to be populated with random bytes.
 * @param length The number of cryptographic random bytes requested.
 * @return 0 on successful entropy generation.
 */
int _getentropy(void *buffer, size_t length) {
    uint8_t *buf = (uint8_t *)buffer;

    // Harvest 8 individual bits of physical ROSC phase jitter to construct a byte
    for (size_t i = 0; i < length; i++) {
        uint8_t byte = 0;
        for (int b = 0; b < 8; b++) {
            // Read the hardware random bit register and shift it into the LSB (Least Significant Bit) accumulator
            byte = (byte << 1) | (rosc_hw->randombit & 1);
        }
        buf[i] = byte;
    }

    return 0;
}

// Syscall Alias (For older Newlib versions)
int getentropy(void *buffer, size_t length) {
    return _getentropy(buffer, length);
}
///////////////////////////////////////////////
///////////////////////////////////////////////
// ============================================================================
// Read RSA Public Key and Convert to BASE64
// Base64 Encoder
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
        printf("Error: EF_FIDO_RSA_PUB not found or is empty!\n");
        return -1;
    }

    uint8_t *raw_data = file_get_data(ef_pub);
    uint16_t raw_size = file_get_size(ef_pub);

    // Buffer safety check: Base64 is mathematically ~1.33x larger than binary
    size_t required_size = ((raw_size + 2) / 3) * 4 + 1;
    if (buffer_size < required_size) {
        // Error: Base64 buffer is too small Needs required_size bytes.
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
 * WARNING: This is not cryptographically secure.
 * For PROD code use the MbedTLS version instead.
 */
void generate_simple_random_32(uint8_t *output) {
    // Loop through all 32 bytes to fill
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


/**
 * @brief Computes a composite 256-bit (32-byte) SHA-256 digest, mathematically binding Credential Public Key and a RN.
 * V = Hash(pkCred, RN)
 */
int calculate_v(mbedtls_ecdsa_context *pk_cred, const uint8_t *rn, uint8_t *output_hash) {

    // Get the curve size (for P-256, plen is 32 bytes)
    const mbedtls_ecp_curve_info *cinfo = mbedtls_ecp_curve_info_from_grp_id(pk_cred->grp.id);
    if (cinfo == NULL) {
        return CTAP1_ERR_OTHER;
    }
    size_t plen = cinfo->bit_size / 8;

    // uncompressed public key length is exactly 65 bytes (1 byte for 0x04 + 32 for X + 32 for Y)
    size_t raw_len = 1 + (plen * 2);

    // Allocate pointers for the raw key and the hash
    uint8_t *raw_pub_key = (uint8_t *)calloc(1, raw_len);

    // Set the uncompressed format indicator
    raw_pub_key[0] = 0x04;

    // Extract X and Y into the buffer
    mbedtls_mpi_write_binary(&pk_cred->Q.X, raw_pub_key + 1, plen);
    mbedtls_mpi_write_binary(&pk_cred->Q.Y, raw_pub_key + 1 + plen, plen);

    mbedtls_sha256_context sha_ctx;
    mbedtls_sha256_init(&sha_ctx);

    // SHA-256 (0 means SHA-256, 1 mean SHA-224)
    mbedtls_sha256_starts(&sha_ctx, 0);

    // Feed the 65-byte Public Key
    mbedtls_sha256_update(&sha_ctx, raw_pub_key, raw_len);

    // Feed the 32-byte RN
    mbedtls_sha256_update(&sha_ctx, rn, RN_SIZE);

    // Finish the math and write the final 32 bytes to output array
    mbedtls_sha256_finish(&sha_ctx, output_hash);

    // Clean up memory
    mbedtls_sha256_free(&sha_ctx);

    return 0;
}

/**
 * @brief Serializes an asymmetric public key to canonical ASN.1 DER and resolves retrograde memory alignment.
 *
 * Transcodes the in-memory RSA public key parameters into a strict Distinguished Encoding
 * Rules (DER) byte sequence (X.509 SubjectPublicKeyInfo format).
 *
 * @warning Retrograde Buffer Population: mbedTLS implements DER serialization using a
 *          bottom-up parsing algorithm. It populates the buffer from the highest memory
 *          address down to the lowest. This function mathematically derives the exact
 *          starting pointer of the valid payload using offset arithmetic.
 *
 * @param pk            Pointer to the initialized cryptographic context containing the RSA public key.
 * @param der_buf       Pre-allocated static memory block for the serialization output.
 * @param buf_size      Total byte capacity of the pre-allocated `der_buf`.
 * @param out_der_start Double-pointer mutated to reference the exact memory address where
 *                      the valid DER sequence begins (somewhere in the middle of `der_buf`).
 * @param out_der_len   Pointer mutated to hold the exact byte-length of the serialized DER sequence.
 *
 * @return 0 on successful serialization and pointer alignment, or a negative mbedTLS error code on failure.
 */
int convert_rsa_pubkey_to_der(mbedtls_pk_context *pk,
                              unsigned char *der_buf,
                              size_t buf_size,
                              unsigned char **out_der_start,
                              size_t *out_der_len) {

    // Execute the ASN.1 DER serialization
    // Returns the exact length of the serialized data, or a negative error code.
    int der_len = mbedtls_pk_write_pubkey_der(pk, der_buf, buf_size);
    if (der_len < 0) return der_len; // Cryptographic serialization failure

    // Resolve the Retrograde Memory Offset
    // Because the mbedTLS ASN.1 compiler writes back-to-front, the valid payload
    // resides at the absolute end of the buffer. We compute the exact starting
    // address via strictly typed pointer arithmetic.
    *out_der_start = der_buf + buf_size - der_len;
    *out_der_len = (size_t)der_len;

    return 0;
}

// RSA Keypair Load from file or Generate new
int generate_rsa_keypair(mbedtls_pk_context *pk) {
    mbedtls_pk_init(pk);
    mbedtls_pk_setup(pk, mbedtls_pk_info_from_type(MBEDTLS_PK_RSA));
    mbedtls_rsa_context *rsa = mbedtls_pk_rsa(*pk);

    file_t *ef_priv = search_file(EF_GLOBALREVOKE);
    file_t *ef_pub  = search_file(EF_GLOBALREVOKE_PUB);

    if (ef_priv == NULL || ef_pub == NULL) {
        // Error: PICO filesystem entries missing
        mbedtls_pk_free(pk);
        return -1;
    }

    // Read Raw RSA Key from file
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
                return 0;
            }
        }
    }

    // Generate RSA Keypair
    int ret = mbedtls_rsa_gen_key(rsa, direct_hardware_rng, NULL, RSA_KEY_BITS, 65537);

    if (ret != 0) {
        // Failed to generate RSA Keypair
        mbedtls_pk_free(pk);
        return ret;
    }

    // Save Private Key To File
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
        }else {
            // Failed to save Public DER to file
            return -1;
        }
    }

    // ------------------------------------------------------------------------
    // Covert and save public key in DER format to pico internal storage
    // ------------------------------------------------------------------------
    // RSA public key DER format is max ~300 bytes. using 400 bytes to be safe.
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
// ============================================================================
// Generates a random 32-byte AES secret and encrypts it.
// Reverse RSA KEM: ENCAPSULATE USING PRIVATE KEY
// Generate the Random Seed (Z) which is exact size of RSA_KEY_BYTES so no need of padding with zeros.
// =========================================================================
int custom_kem_encapsulate(mbedtls_rsa_context *rsa,
                                   uint8_t *out_shared_secret,
                                   size_t secret_len,
                                   uint8_t *out_ciphertext) {

    // Get dynamic size of loaded key
    size_t rsa_len = mbedtls_rsa_get_len(rsa);

    // Safety checks: Ensure buffers are large enough
    if (secret_len > 32 || rsa_len > RSA_KEY_BYTES) {
        // [Custom KEM] Key size mismatch
        return -1;
    }

    // The array size of seed_Z is exactly RSA_KEY_BYTES
    // It must perfectly match the length of the RSA key being used.
    uint8_t seed_Z[RSA_KEY_BYTES] = {0};

    // Generate Random Seed (Z)
    int ret = direct_hardware_rng(NULL, seed_Z, rsa_len);
    if (ret != 0) return ret;

    // KEM SEED FORMATTING: PREVENT JAVA CRASHES & MATH ERRORS
    // We intentionally overwrite the very first byte of random seed with 0x00.
    // We must do this for two critical reasons before doing raw RSA math:
    //
    // 1. RSA Math Rule: The payload MUST be mathematically smaller than the RSA Modulus.
    // 2. Java Compatibility: Java uses a "Sign Bit" to check if numbers are negative.
    //    If the first bit of our random seed is a '1', Java considers as a negative number.
    //    Forcing this to 0x00 guarantees Java reads it as positive.
    seed_Z[0] = 0x00;

    // Derive Symmetric Key (The SHA-256)
    // The array size of hash_output is exactly 32 bytes,
    // which is the fixed output size of a standard SHA-256 cryptographic hash.
    uint8_t hash_output[32] = {0};

    // Hash the seed_Z which is exactly 32 bytes
    mbedtls_sha256(seed_Z, rsa_len, hash_output, 0);

    // Output the AES key for encryption
    memcpy(out_shared_secret, hash_output, secret_len);

    // Encrypt seed using raw Private Key (No Padding)
    ret = mbedtls_rsa_private(rsa,
                              direct_hardware_rng,
                              NULL,
                              seed_Z,
                              out_ciphertext);

    // On error, cleanup key from memory
    if (ret != 0) {
        memset(out_shared_secret, 0, secret_len);
    }

    // Cleanup from memory
    memset(seed_Z, 0, sizeof(seed_Z));
    memset(hash_output, 0, sizeof(hash_output));

    return ret;
}

// ============================================================================
// Generate KEM Payload
// ============================================================================
int generate_kem_payload(uint8_t *out_aes_key, uint8_t *out_ciphertext) {

    // Starting RSA-KEM Encapsulation
    // Initialize the raw RSA context
    mbedtls_rsa_context rsa;
    mbedtls_rsa_init(&rsa);

    // Load the Private Key from file
    file_t *ef_priv = search_file(EF_GLOBALREVOKE);
    if (ef_priv == NULL || !file_has_data(ef_priv)) {
        //  Key not found in file
        mbedtls_rsa_free(&rsa);
        return -1;
    }

    raw_rsa_key_t *raw_key = (raw_rsa_key_t *)file_get_data(ef_priv);

    // Load all math variables (N, P, Q, D, E) into the RSA context
    int import_ret = mbedtls_rsa_import_raw(&rsa,
        raw_key->N, sizeof(raw_key->N),
        raw_key->P, sizeof(raw_key->P), // Private Prime 1
        raw_key->Q, sizeof(raw_key->Q), // Private Prime 2
        raw_key->D, sizeof(raw_key->D), // Private Exponent
        raw_key->E, sizeof(raw_key->E)
    );

    if (import_ret != 0) {
        // Failed to load private key into RSA context
        mbedtls_rsa_free(&rsa);
        return -1;
    }
    mbedtls_rsa_complete(&rsa);

    // Custom RSA-KEM Encapsulation
    int kem_ret = custom_kem_encapsulate(&rsa, out_aes_key, KEM_AES_SIZE, out_ciphertext);

    // Clean up the memory
    mbedtls_rsa_free(&rsa);

    if (kem_ret != 0) {
        // Encapsulation failed
        return -1;
    }

    // RSA-KEM AES Key derived and encrypted
    return 0;
}

/**
 * @brief Encrypts a plaintext payload using AES-256-CTR and prepends a 128-bit nonce.
 *
 * Initializes the AES-CTR stream cipher using a hardware-generated true random nonce (TRNG).
 * As a stream cipher, CTR mode requires no block padding. The resulting output is structured
 * as a self-contained cryptographic payload: [16-byte Initial Nonce] || [Ciphertext].
 *
 * @warning Memory Constraint: `output_data` must be pre-allocated to a minimum capacity
 *          of `(data_len + 16)` bytes to prevent memory corruption via buffer overrun.
 *
 * @param key         Pointer to the 32-byte (256-bit) symmetric AES key.
 * @param input_data  Pointer to the plaintext payload.
 * @param data_len    Length of the plaintext payload in bytes.
 * @param output_data Pointer to the destination buffer (minimum size: data_len + 16).
 *
 * @return 0 on success, or a non-zero mbedTLS error code on failure.
 */
int apply_aes_ctr_with_nonce(const uint8_t *key,
                             const uint8_t *input_data,
                             size_t data_len,
                             uint8_t *output_data) {

    // Generate a random 16-byte nonce
    uint8_t local_nonce[16] = {0};
    int ret = direct_hardware_rng(NULL, local_nonce, 16);
    if (ret != 0) {
        return ret; //Failed to generate random nonce
    }

    // Prepend the initial nonce state to the output buffer
    memcpy(output_data, local_nonce, 16);

    // Initialize AES context and compute the 256-bit key
    mbedtls_aes_context aes;
    mbedtls_aes_init(&aes);
    ret = mbedtls_aes_setkey_enc(&aes, key, 256);

    if (ret != 0) {
        mbedtls_aes_free(&aes);
        return ret; // Key initialization failure
    }

    // Initialize CTR mode state vectors
    size_t nc_off = 0;
    // 128-bit/16-byte buffer to store the intermediate keystream block
    uint8_t stream_block[16] = {0};

    // Execute AES-CTR cipher operation
    // Pointer arithmetic (output_data + 16) preserves the prepended nonce.
    // local_nonce is passed as the mutable counter block and iterates internally.
    ret = mbedtls_aes_crypt_ctr(&aes,
                                data_len,
                                &nc_off,
                                local_nonce,
                                stream_block,
                                input_data,
                                output_data + 16);

    if (ret != 0) {
        return -1; // Cryptographic operation failure
    }

    // Clean up memory
    mbedtls_aes_free(&aes);

    return ret;
}
//////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////

CborError encode_grs_extension(CborEncoder *mapEncoder, mbedtls_ecdsa_context *ekey) {
    CborEncoder nestedMapEncoder;
    CborError error = CborNoError;


    //////////////////////////////////////////
    ////////// generate RN (revocation nonce) and V
    uint8_t RN[RN_SIZE] = {0}; // Declare an array to hold the 32 bytes (initialized to 0)
    // Hash(CredPubKey+RN) = v = vCredPkRN
    uint8_t vCredPkRN[32] = {0};
    // Generate the 32-byte RN
    generate_simple_random_32(RN);
    // Generate the combined hash Hash(CredPubKey+RN) = v = vCredPkRN
    if (calculate_v(ekey, RN, vCredPkRN) != 0) {
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
    // For RSA 1024 A ~400-byte raw RSA key needs at least a ~535-byte buffer.
    //////////////////////////////////////////
    unsigned char pk_r_base64_string[MAX_BASE64_KEY_SIZE] = {0};
    size_t text_length = 0;
    get_fido_pubkey_base64(pk_r_base64_string, sizeof(pk_r_base64_string), &text_length);
    // draw QR Code
    draw_qrcode((const char*)pk_r_base64_string);
    // Clear Private Key (and Public Key) from memory
    mbedtls_pk_free(&keypair);
    //////////////////////////////////////////


    //////////////////////////////////////////
    // Encode V
    //////////////////////////////////////////
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "v"));
    CBOR_CHECK(cbor_encode_byte_string(&nestedMapEncoder, vCredPkRN, 32));
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "vAlg"));
    CBOR_CHECK(cbor_encode_int(&nestedMapEncoder, -16));
    //////////////////////////////////////////

    //////////////////////////////////////////
    /// Generate Reverse RSA KEM, AES CTR for W and C
    ///////////////////////////////////////////////////////
    /// Prepare W = Enc(k, RN)
    //////////////////////////////////////////
    uint8_t shared_aes_key[KEM_AES_SIZE] = {0};
    uint8_t ciphertext[RSA_KEY_BYTES] = {0};
    int status = generate_kem_payload(shared_aes_key, ciphertext);
    if (status != 0) {
        return -1; // exit on error
    }

    // Allocate a buffer for W (Encrypted RN (Revocation Nonce))
    uint8_t w_encrypted[AES_CTR_PAYLOAD_BUFFER_SIZE] = {0};
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
    size_t w_encrypted_size = AES_CTR_PAYLOAD_BUFFER_SIZE;
    CBOR_CHECK(cbor_encode_byte_string(&nestedMapEncoder, w_encrypted, w_encrypted_size));
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "wAlg"));
    CBOR_CHECK(cbor_encode_int(&nestedMapEncoder, -65532));

    // Clear from memory
    memset(shared_aes_key, 0, sizeof(shared_aes_key));

    // Encode C (Ciphertext)
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "c"));
    CBOR_CHECK(cbor_encode_byte_string(&nestedMapEncoder, ciphertext, RSA_KEY_BYTES));
    CBOR_CHECK(cbor_encode_text_stringz(&nestedMapEncoder, "cAlg"));
    CBOR_CHECK(cbor_encode_int(&nestedMapEncoder, -275));

    CBOR_CHECK(cbor_encoder_close_container(mapEncoder, &nestedMapEncoder));

err:
    return error;
}