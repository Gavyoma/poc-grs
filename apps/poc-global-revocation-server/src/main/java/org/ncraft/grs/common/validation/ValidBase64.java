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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = Base64Validator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidBase64 {
    String message() default "Invalid Base64 encoded string";

    // Required by Jakarta Validation for grouping constraints
    Class<?>[] groups() default {};

    // Required by Jakarta Validation to assign custom payload objects
    Class<? extends Payload>[] payload() default {};

    /**
     * Determines which Base64 decoding alphabet to use.
     * * @return true if the string should be validated against the URL and Filename safe
     * Base64 Alphabet (RFC 4648). Defaults to false (standard Base64).
     */
    boolean isUrlSafe() default false;
}