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

package org.ncraft.grs.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.ncraft.grs.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class RsaPublicKeyValidator implements ConstraintValidator<ValidRsaPublicKey, String> {

    public static final String RSA = "RSA";
    private final AppProperties appProperties;

    private int[] allowedBitLengths;
    private boolean urlSafe;

    public RsaPublicKeyValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void initialize(ValidRsaPublicKey annotation) {
        this.allowedBitLengths = annotation.bitLengths();
        this.urlSafe = annotation.urlSafe();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // let @NotBlank handle it
        }

        try {
            Base64.Decoder decoder = urlSafe
                    ? Base64.getUrlDecoder()
                    : Base64.getDecoder();

            byte[] decoded = decoder.decode(value.trim());

            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decoded));

            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                return false;
            }

            int actualBitLength = rsaPublicKey.getModulus().bitLength();

            int[] lengthsToCheck = allowedBitLengths.length > 0
                    ? allowedBitLengths
                    : new int[]{appProperties.rsaKeySizeBits()};

            for (int allowed : lengthsToCheck) {
                if (actualBitLength == allowed) {
                    return true;
                }
            }

            return false;

        } catch (IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            return false;
        }
    }
}