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

package org.ncraft.grs.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GRS: Revocation Processing API",
                version = "v1.0",
                description = """
                        API for handling Relying Party webhooks.
                        ### Traceability & Error Handling
                        A unique `X-Trace-Id` is attached to the HTTP Header of every single API response.
                        In the event of a validation or server error, this exact same Trace ID will also be
                        provided inside the JSON error body for convenience.
                        """
        )
)
public class OpenApiConfig {
}