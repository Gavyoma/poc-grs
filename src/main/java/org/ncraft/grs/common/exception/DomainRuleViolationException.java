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

package org.ncraft.grs.common.exception;

import lombok.Getter;

/**
 * Base exception for all domain rule violations.
 * Throw this when data is perfectly formatted, but violates a core business/domain invariant.
 */
@Getter
public class DomainRuleViolationException extends RuntimeException {

    private final String errorCode;

    public DomainRuleViolationException(String message) {
        super(message);
        this.errorCode = "DOMAIN_RULE_VIOLATION";
    }

    public DomainRuleViolationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DomainRuleViolationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}