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

package org.ncraft.grs.revocation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.ncraft.grs.common.validation.ValidBase64;
import org.ncraft.grs.common.validation.ValidRsaPublicKey;

public record RevocationKeyForm(

        @NotBlank(message = "Payload is required")
        @Size(max = 736, message = "Payload exceeds maximum allowed length")
        @ValidBase64(message = "The submitted data could not be decoded")
        @ValidRsaPublicKey
        String key
) {
}
