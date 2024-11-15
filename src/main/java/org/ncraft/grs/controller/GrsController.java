/*
 * Copyright (c) 2025, Nirav Pistolwala
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

package org.ncraft.grs.controller;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.model.RevocationKey;
import org.ncraft.grs.model.RevocationWC;
import org.ncraft.grs.model.RevocationWDash;
import org.ncraft.grs.service.GrsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/")
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class GrsController {

    private GrsService grsService;

    @PostMapping(path = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Retrieve a list of W Dash",
            notes = "Returns a list of all W Dash created after the specified timestamp")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Keys retrieved successfully"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    public List<RevocationWDash> getAllData(@RequestBody List<RevocationWC> request) {
        log.debug("getAllData called");
        return grsService.getAll(request);
    }

    @PostMapping(path = "/api/keys", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Stores a revocation new key", notes = "Stores a new revocation key with the provided details")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Revocation Key stored successfully"),
            @ApiResponse(code = 400, message = "Invalid request data")
    })
    public ResponseEntity<RevocationKey> revokeKey(@RequestBody RevocationKey key) {
        log.debug("revokeKey called");
        try {
            RevocationKey revocationKey = grsService.saveRevocationKey(key);
            log.debug("Revocation key stored successfully: {}", key);
            return ResponseEntity.ok(revocationKey);
        } catch (Exception e) {
            log.error("Revoke key failed", e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getCause().getMessage());
        }
    }
}