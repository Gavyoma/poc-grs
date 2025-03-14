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

package org.ncraft.grs.common.api;

import lombok.Getter;

@Getter
public enum ApiErrorType {
    VALIDATION_FAILED("/validation-failed", "Payload Validation Failed"),
    INVALID_CURSOR("/invalid-pagination-cursor", "Invalid Pagination Cursor"),
    WEBHOOK_VERIFICATION_FAILED("/webhook-verification-failed", "Webhook Signature Invalid"),
    BAD_REQUEST("/bad-request", "Bad Request"),
    INTERNAL_SERVER_ERROR("/internal-server-error", "Internal Server Error");

    private final String path;
    private final String defaultTitle;

    ApiErrorType(String path, String defaultTitle) {
        this.path = path;
        this.defaultTitle = defaultTitle;
    }
}