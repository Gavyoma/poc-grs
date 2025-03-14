/*
 * Copyright (c) 2024-2025, Nirav Pistolwala
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

import java.util.Base64;

public class Base64Validator implements ConstraintValidator<ValidBase64, String> {

    private boolean isUrlSafe;

    @Override
    public void initialize(ValidBase64 constraintAnnotation) {
        this.isUrlSafe = constraintAnnotation.isUrlSafe();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Let @NotNull or @NotBlank handle this
        }

        try {
            if (isUrlSafe) {
                Base64.getUrlDecoder().decode(value);
            } else {
                Base64.getDecoder().decode(value);
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false; // Safely catch the decode failure
        }
    }
}