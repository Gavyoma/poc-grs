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

package org.ncraft.grs.common.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.common.config.AppPaginationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * This service is responsible for securely encrypting and decrypting pagination cursors.
 * Secures API pagination by implementing the "Opaque Token" and "Cryptographic Enveloping" patterns.
 * <p>
 * Instead of exposing raw, time-ordered database cursors (UUIDv7) to the client, this service
 * encrypts them using AES-256-GCM. This produces a secure, stateless opaque token that must be
 * passed back by the client for subsequent page requests.
 * </p>
 * * <b>Security Guarantees:</b>
 * <ul>
 * <li><b>Prevents Enumeration & Scraping:</b> Clients cannot guess, increment, or manipulate the cursor to bypass pagination limits.</li>
 * <li><b>Protects Temporal Privacy:</b> Because UUIDv7 contains an embedded timestamp, exposing it in plaintext reveals the exact time data was created.</li>
 * <li><b>Decouples Internal State:</b> API consumers remain completely unaware of the underlying database structure or primary key strategy.</li>
 * </ul>
 * <p>
 * <b>Binary Header Layout:</b><br>
 * The generated token contains a binary header to support zero-downtime key rotation.
 * Before Base64 encoding, the raw byte array is structured as follows:
 * <ul>
 * <li><b>Byte 0:</b> Key Version Identifier (1 byte)</li>
 * <li><b>Bytes 1-12:</b> Cryptographic Initialization Vector (IV) (12 bytes)</li>
 * <li><b>Bytes 13+:</b> The AES-GCM CipherText (Variable length)</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
public class CursorEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    // The current active version of encryption protocol/key
    private static final byte CURRENT_KEY_VERSION = 1;

    private final SecretKeySpec secretKeyV1;
    private final SecureRandom secureRandom = new SecureRandom();

    public CursorEncryptionService(AppPaginationProperties appPaginationProperties) {
        byte[] decodedKey = Base64.getDecoder().decode(appPaginationProperties.secretKeyV1());

        if (decodedKey.length != 32) {
            throw new IllegalStateException(
                    "FATAL: app.pagination.secret-key-v1 must be exactly 32 bytes after Base64 decoding."
            );
        }

        /*
         * ==============================================================================
         * KEY ROTATION STRATEGY
         * ==============================================================================
         * This variable is versioned (V1). To perform a key rotation:
         * - DO NOT overwrite this key. It must remain in memory to decrypt legacy cursor.
         * - Create a new injected property (secret-key-v2) and a new SecretKeySpec.
         * - Update the encryption method to use V2, but allow decryption to fallback to V1.
         */
        this.secretKeyV1 = new SecretKeySpec(decodedKey, "AES");
    }

    /**
     * Encrypts a UUID into an opaque, URL-safe Base64 string.
     *
     * @param uuid The database ID to encrypt (e.g., UUIDv7).
     * @return A URL-safe Base64 string representing the versioned, encrypted cursor.
     * @throws RuntimeException if the cryptographic operation fails.
     */
    public String encryptCursor(UUID uuid) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

            // Always encrypt using the newest key
            cipher.init(Cipher.ENCRYPT_MODE, secretKeyV1, parameterSpec);

            byte[] cipherText = cipher.doFinal(uuid.toString().getBytes());

            // Memory layouut: [ 1 Byte Version ] + [ 12 Bytes IV ] + [ CipherText ]
            ByteBuffer byteBuffer = ByteBuffer.allocate(1 + iv.length + cipherText.length);
            byteBuffer.put(CURRENT_KEY_VERSION);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt cursor", e);
        }
    }

    /**
     * Decrypts an opaque cursor string back into its original UUID.
     * <p>
     * This method reads the first byte of the decoded payload to determine which
     * symmetric key to use, enabling seamless key rotation.
     * </p>
     *
     * @param encryptedCursor The URL-safe Base64 string provided by the client.
     * @return The decrypted {@link UUID}.
     * @throws IllegalArgumentException if the cursor is malformed, tampered with, or uses an unsupported key version.
     */
    public UUID decryptCursor(String encryptedCursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encryptedCursor);

            if (decoded.length < 1 + IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Cursor payload is too short");
            }

            byte version = decoded[0];

            SecretKeySpec decryptionKey;
            if (version == 1) {
                decryptionKey = this.secretKeyV1;
            } else {
                throw new IllegalArgumentException("Unsupported cursor key version: " + version);
            }

            // Extract the IV (Starts at index 1, length 12)
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(decoded, 1, iv, 0, iv.length);

            // Extract the CipherText (Starts at index 13, goes to the end)
            byte[] cipherText = new byte[decoded.length - 1 - iv.length];
            System.arraycopy(decoded, 1 + iv.length, cipherText, 0, cipherText.length);

            // Decrypt uusing the selected key
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return UUID.fromString(new String(plainText));

        } catch (Exception e) {
            log.error("Failed to decrypt cursor", e);
            throw new IllegalArgumentException("Invalid or expired pagination cursor");
        }
    }
}