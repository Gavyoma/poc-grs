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

package org.ncraft.grs.relyingparty.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.relyingparty.api.dto.PaginatedResponse;
import org.ncraft.grs.relyingparty.api.dto.ProcessedWDashDto;
import org.ncraft.grs.relyingparty.api.dto.RelyingPartyWebhookPayload;
import org.ncraft.grs.relyingparty.application.RelyingPartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/relying-party")
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
@Tag(name = "Relying Party Integrations", description = "Endpoints for handling webhooks and retrieving processed W' values.")
public class RelyingPartyController {

    private final RelyingPartyService relyingPartyService;
 
    @Operation(
            summary = "Fetch processed W' values",
            description = "Retrieves a paginated collection of processed W' values. Uses an opaque cursor mechanism to optimize performance while preserving security and privacy."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved the paginated list of processed W' values.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request. Often caused by a malformed or tampered pagination cursor.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error. Processing failure or database timeout.",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @GetMapping("/wdash")
    public ResponseEntity<PaginatedResponse<ProcessedWDashDto>> getProcessedData(
            @Parameter(
                    description = "The opaque pagination cursor retrieved from the 'nextCursor' field of a previous response. Leave empty for the first page.",
                    example = "n5JzYXNZZmFzZGZhc5R..."
            )
            @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(
                    description = "The maximum number of records to return. Maximum allowed is 5000.",
                    example = "500"
            )
            @RequestParam(value = "limit", defaultValue = "500") int limit) {
        if (limit > 5000) limit = 5000;
        PaginatedResponse<ProcessedWDashDto> response = relyingPartyService.fetchWDashes(cursor, limit);
        return ResponseEntity.ok(response);
    }


    @Operation(
            summary = "Ingest Relying Party Webhooks",
            description = """
                    Accepts one or more registration event payloads from Relying Parties. For each new FIDO2 authenticator registration, the payload includes the corresponding W and C values.
                    
                    This endpoint validates the request body and queues the event for asynchronous processing.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Webhook securely received and queued for processing.",
                    headers = @Header(
                            name = "X-Trace-Id",
                            description = "The unique execution Trace ID. Provide this to support when troubleshooting dropped webhooks.",
                            schema = @Schema(type = "string", example = "018f9a2b-7c3d-7d4e-b5f6-1a2b3c4d5e6f")
                    ),
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized.",
                    content = @Content
            )
    })
    @PostMapping(path = "/webhooks/wcs")
    public ResponseEntity<Void> receiveWebhook(
            @Valid @RequestBody RelyingPartyWebhookPayload payload) {
        log.info("Processing webhook intake. Items count: {}", payload.items().size());
        relyingPartyService.processWebhookPayload(payload.items());
        return ResponseEntity.accepted().build();
    }

}
