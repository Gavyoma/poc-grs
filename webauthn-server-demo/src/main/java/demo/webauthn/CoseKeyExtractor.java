/*
 * Copyright (c) 2024-2025, Nirav Pistolwala
 * All rights reserved.
 *
 * This source code is licensed under the same terms as the rest of the
 * original project, found in the COPYING file in the root directory.
 */
package demo.webauthn;

import com.upokecenter.cbor.CBORObject;
import com.yubico.webauthn.data.ByteArray;
import demo.webauthn.exception.InvalidCoordinateLengthException;
import demo.webauthn.exception.InvalidCoseKeyFormatException;

public final class CoseKeyExtractor {

    private CoseKeyExtractor() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Extracts the 65-byte uncompressed raw key (0x04 || X || Y) directly from a COSE CBOR map.
     *
     * @param coseKeyBytes The ByteArray containing the COSE key from the Yubico library
     * @return A 65-byte array representing the uncompressed raw ES256 key
     */
    public static byte[] getRawUncompressedEs256Key(ByteArray coseKeyBytes) {

        if (coseKeyBytes == null || coseKeyBytes.getBytes().length == 0) {
            throw new IllegalArgumentException("COSE key bytes must not be null or empty");
        }

        try {
        // Parse the raw COSE bytes into a CBOR Map
        CBORObject coseMap = CBORObject.DecodeFromBytes(coseKeyBytes.getBytes());

        // Validate that it is an Elliptic Curve (EC2) key
        // CBOR Key 1 == Key Type. A value of 2 means EC2.
        if (coseMap.get(CBORObject.FromObject(1)).AsInt32Value() != 2) {
            throw new InvalidCoseKeyFormatException("The provided COSE key is not an Elliptic Curve key!");
        }

        // Extract the X and Y coordinates
        // In COSE, Key -2 holds X, and Key -3 holds Y.
        byte[] xBytes = coseMap.get(CBORObject.FromObject(-2)).GetByteString();
        byte[] yBytes = coseMap.get(CBORObject.FromObject(-3)).GetByteString();

        // Safety check to ensure the coordinates are exactly 32 bytes (P-256)
        if (xBytes.length != 32 || yBytes.length != 32) {
            throw new InvalidCoordinateLengthException(
                    "Expected 32-byte coordinates for ES256, got x=" + xBytes.length + ", y=" + yBytes.length
            );
        }

        // Assemble the 65-byte uncompressed format [0x04, X..., Y...]
        byte[] rawUncompressedKey = new byte[65];

        // Set the uncompressed format indicator
        rawUncompressedKey[0] = 0x04;

        // Copy X and Y into the final array
        System.arraycopy(xBytes, 0, rawUncompressedKey, 1, 32);
        System.arraycopy(yBytes, 0, rawUncompressedKey, 33, 32);

        return rawUncompressedKey;

        } catch (com.upokecenter.cbor.CBORException e) {
            throw new InvalidCoseKeyFormatException("Failed to parse COSE CBOR structure", e);
        }
    }
}