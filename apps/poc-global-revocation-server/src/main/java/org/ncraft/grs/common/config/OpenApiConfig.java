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
                        ### Overview
                        This API allows FIDO2 Relying Parties to submit and request information related to the Revocation of FIDO2 authenticators.
                        
                        ## Error Handling & Traceability
                        
                        This API uses standard HTTP status codes and returns [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807) for all error payloads.\s
                        
                        **Traceability**
                        
                        Every API response includes a unique `X-Trace-Id` in the HTTP headers. In the event of a 4xx or 5xx error, the JSON response body will include the standard RFC fields along with custom extensions for convenience:
                        * `trace_id`: Matches the `X-Trace-Id` header to easily correlate logs.
                        * `timestamp`: The exact UTC time the error occurred.\s
                        
                        *Note: Please provide the trace ID when contacting the support team.*
                        """
        )
)
public class OpenApiConfig {
}