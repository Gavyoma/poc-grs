/*
 * Copyright (c) 2024-2025, N. "Gavi" Pistolwala
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ncraft.grs.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.common.config.AppProperties;
import org.ncraft.grs.common.exception.DomainRuleViolationException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Component
public class WDashDecryptor {

    public static final String AES_CTR_NO_PADDING = "AES/CTR/NoPadding";
    public static final String RSA_ECB_NO_PADDING = "RSA/ECB/NoPadding";
    public static final String SHA_256 = "SHA-256";
    public static final String AES = "AES";
    private final int rsaBytes;

    public WDashDecryptor(AppProperties appProperties) {
        this.rsaBytes = appProperties.rsaKeySizeBits() / 8; // Calculates the required buffer size (e.g., 2048 / 8 = 256 bytes)
    }

    /**
     * Decrypts an AES-CTR payload by extracting the prepended Initialization Vector (IV)
     * and restoring the original plaintext.
     * <p>
     * Because AES in Counter (CTR) mode operates as a stream cipher, it requires no
     * block padding ({@code NoPadding}). This method expects the {@code encryptedPayload}
     * to follow the standard unpadded network structure: a strictly 16-byte cleartext
     * IV (Nonce) at index zero, immediately followed by the dynamically sized ciphertext.
     * </p>
     * <p>
     * The method slices the IV from the payload to prime the AES engine, XORs the
     * keystream against the ciphertext to recover the raw data, and finally encodes
     * the recovered plaintext as a Base64Url string.
     * </p>
     *
     * @param sharedAesKey     the symmetric AES key (e.g., the 32-byte key derived via KEM).
     * @param encryptedPayload the raw byte array formatted exactly as {@code [16-byte IV] + [Ciphertext]}.
     * @return a Base64Url-encoded string representation of the decrypted plaintext.
     * @throws IllegalArgumentException if the {@code encryptedPayload} is null or 16 bytes or shorter
     *                                  (meaning it contains an IV but no actual ciphertext data).
     * @throws GeneralSecurityException if the underlying cryptographic engine fails
     *                                  (e.g., invalid key format or unsupported algorithm).
     */
    private String decryptW(byte[] sharedAesKey, byte[] encryptedPayload) throws GeneralSecurityException {
        if (encryptedPayload.length <= 16) {
            throw new IllegalArgumentException("Payload is too short to contain a Nonce and data!");
        }

        byte[] nonce = Arrays.copyOfRange(encryptedPayload, 0, 16);
        byte[] cipherText = Arrays.copyOfRange(encryptedPayload, 16, encryptedPayload.length);

        SecretKeySpec keySpec = new SecretKeySpec(sharedAesKey, AES);
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);

        Cipher cipher = Cipher.getInstance(AES_CTR_NO_PADDING);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decryptedBytes = cipher.doFinal(cipherText);

        log.debug("\n======================================");
        log.trace("DECRYPTED MESSAGE (Raw Hex): {}", HexFormat.of().withUpperCase().formatHex(decryptedBytes));
        log.debug("DECRYPTED MESSAGE (Base64Url): {}", byteArrayToBase64Url(decryptedBytes));
        log.debug("======================================\n");

        // TODO: DEMO ONLY: Emoji added for conference presentation. Remove emoji before production release.
        log.debug("   ✅ Decryption succeeded.");
        return byteArrayToBase64Url(decryptedBytes);
    }

    /**
     * Reconstructs an RSA Public Key object from its X.509 DER-encoded byte array.
     * <p>
     * This method expects the byte array to be strictly formatted according to the
     * X.509 SubjectPublicKeyInfo standard. It is designed to process the exact
     * ~300-byte public key payload exported by the hardware authenticator.
     * </p>
     *
     * @param keyBytes the X.509 DER-encoded byte array representing the RSA public key.
     * @return the reconstructed RSA {@link PublicKey} object, ready for cryptographic operations.
     * @throws IllegalArgumentException if the provided byte array is null or empty.
     * @throws GeneralSecurityException if the RSA algorithm is unavailable in the environment,
     *                                  or if the provided bytes do not form a valid X.509 RSA public key.
     */
    private PublicKey buildPublicKeyFromBytes(byte[] keyBytes) throws GeneralSecurityException {
        if (keyBytes == null || keyBytes.length == 0) {
            throw new IllegalArgumentException("Key bytes cannot be null or empty.");
        }

        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(spec);
    }

    /**
     * Decapsulates RSA-KEM payload to recover a shared AES key.
     * <p>
     * This method performs RSA decryption using the provided public key to extract
     * the raw random seed generated by the authenticator. Because the cipher uses
     * {@code RSA/ECB/NoPadding}, the method dynamically adjusts for Java's leading-zero
     * {@code BigInteger} math quirk to perfectly reconstruct the original seed block.
     * </p>
     * <p>
     * Finally, it passes the restored seed through a SHA-256 Key Derivation Function (KDF)
     * to securely distill it into a 32-byte symmetric AES key.
     * </p>
     * <p>
     * <b>Security Note:</b> Intermediate key material (the plaintext seed) is
     * securely wiped from memory in a {@code finally} block immediately after derivation.
     * </p>
     *
     * @param cBytes           the raw, unpadded RSA ciphertext received from the Authenticator.
     * @param publicKey        the RSA public key used to verify and decapsulate the payload.
     * @param expectedRsaBytes the exact byte length of the RSA modulus (e.g., 256 for RSA-2048).
     * @return a strictly 32-byte array representing the derived AES symmetric key.
     * @throws IllegalArgumentException if the inputs are null, or if the ciphertext
     *                                  length does not exactly match the expected modulus size.
     * @throws GeneralSecurityException if the underlying cryptographic engine fails
     *                                  (e.g., invalid key format or algorithm unavailability).
     */
    private byte[] extractAesKey(byte[] cBytes, PublicKey publicKey, int expectedRsaBytes) throws GeneralSecurityException {

        if (cBytes == null || publicKey == null) {
            throw new IllegalArgumentException("C and Public Key cannot be null.");
        }

        if (cBytes.length != expectedRsaBytes) {
            throw new IllegalArgumentException("Fatal Error: Configured for RSA-" + (expectedRsaBytes * 8) +
                    " but received " + cBytes.length + " bytes!");
        }

        byte[] rawDecryptedBlock = null;
        byte[] seedZ = null;

        try {
            // Raw RSA Math: Decrypt the ciphertext(C) using the Public Key
            // SECURITY NOTE: "NoPadding" is required here for the KEM architecture.
            // The payload is a full-sized, purely random seed that perfectly fills
            // the RSA block. Because the seed is completely random and leaves zero
            // empty space, mathematical "NoPadding" vulnerabilities do not apply.
            Cipher cipher = Cipher.getInstance(RSA_ECB_NO_PADDING);
            cipher.init(Cipher.DECRYPT_MODE, publicKey);

            rawDecryptedBlock = cipher.doFinal(cBytes);

            // ========================================================================
            // KEM SEED RESTORATION: RESTORE DROPPED LEADING ZEROS
            // ========================================================================
            // The C code (Authenticator) intentionally forced the first byte of this seed to 0x00
            // so Java would not treat it as a negative number.
            //
            // When Java finishes the RSA math, it treats the result as a standard number
            // and deletes that 0x00 as a "leading zero" (shrinking the array to 255 bytes).
            // Because we explicitly requested "NoPadding" in the Cipher configuration,
            // Java's crypto engine is strictly forbidden from automatically re-padding
            // it back to the proper 256-byte block size.
            //
            // Therefore, we must manually restore the exact KEM seed formatting by
            // copying the short array into the very end of a new, zero-filled array.
            // ========================================================================
            seedZ = new byte[expectedRsaBytes];
            if (rawDecryptedBlock.length < expectedRsaBytes) {
                int difference = expectedRsaBytes - rawDecryptedBlock.length;
                System.arraycopy(rawDecryptedBlock, 0, seedZ, difference, rawDecryptedBlock.length);
            } else {
                System.arraycopy(rawDecryptedBlock, 0, seedZ, 0, expectedRsaBytes);
            }

            // ========================================================================
            // KEM STEP: KEY DERIVATION FUNCTION (KDF)
            // ========================================================================
            // To safely extract a 32-byte AES key from the 256-byte RSA seed,
            // we use SHA-256 as our Key Derivation Function (KDF).
            MessageDigest kdf = MessageDigest.getInstance(SHA_256);

            return kdf.digest(seedZ);

        } finally {
            if (seedZ != null) {
                Arrays.fill(seedZ, (byte) 0x00);
            }
            if (rawDecryptedBlock != null) {
                Arrays.fill(rawDecryptedBlock, (byte) 0x00);
            }
        }
    }

    private byte[] base64UrlToByteArray(String s) {
        if (s == null || s.isEmpty()) return new byte[0];
        return Base64.getUrlDecoder().decode(s.trim());
    }


    private byte[] base64ToByteArray(String s) {
        if (s == null || s.trim().isEmpty()) {
            return new byte[0];
        }

        return Base64.getDecoder().decode(s.trim());
    }


    private String byteArrayToBase64Url(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
    }

    public String calculateWDash(String key, String w, String c) {
        try {
            byte[] publicKeyBytes = base64ToByteArray(key);
            byte[] wBytes = base64UrlToByteArray(w);
            byte[] cBytes = base64UrlToByteArray(c);

            PublicKey publicKey = buildPublicKeyFromBytes(publicKeyBytes);

            byte[] extractedAesKey = extractAesKey(cBytes, publicKey, rsaBytes);

            log.debug("Decrypting the AES-CTR Payload...");
            return decryptW(extractedAesKey, wBytes);

        } catch (Exception e) {
            // TODO: DEMO ONLY: Emoji added for conference presentation. Remove emoji before production release.
            log.debug("\n ❌ Decryption Failed!", e);
            throw new DomainRuleViolationException("GRS should not be able to validate that decryption failed", e.getMessage());
        }
    }

}