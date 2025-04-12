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

package org.ncraft.grs.relyingparty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ncraft.grs.common.config.AppDocsProperties;
import org.ncraft.grs.relyingparty.api.dto.PaginatedResponse;
import org.ncraft.grs.relyingparty.api.dto.ProcessedWDashDto;
import org.ncraft.grs.relyingparty.api.dto.RelyingPartyWebhookPayload;
import org.ncraft.grs.relyingparty.api.dto.RevocationWcDto;
import org.ncraft.grs.relyingparty.application.RelyingPartyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RelyingPartyController.class)
class RelyingPartyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RelyingPartyService relyingPartyService;

    @MockBean
    private AppDocsProperties appDocsProperties;

    @Test
    void getProcessedData_shouldReturnPaginatedResponseAndCallService() throws Exception {
        ProcessedWDashDto dto = new ProcessedWDashDto("wdash_value_xyz");
        PaginatedResponse<ProcessedWDashDto> mockResponse = new PaginatedResponse<>(
                List.of(dto), "cursor_abc123", true
        );

        when(relyingPartyService.fetchWDashes(eq("test-cursor"), eq(25))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/relying-party/wdash")
                        .param("cursor", "test-cursor")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].wDash").value("wdash_value_xyz"))
                .andExpect(jsonPath("$.nextCursor").value("cursor_abc123"))
                .andExpect(jsonPath("$.hasMore").value(true));

        verify(relyingPartyService).fetchWDashes(eq("test-cursor"), eq(25));
    }

    @Test
    void getProcessedData_shouldUseDefaultLimitWhenNotProvided() throws Exception {
        ProcessedWDashDto dto = new ProcessedWDashDto("wdash_value_xyz");
        PaginatedResponse<ProcessedWDashDto> mockResponse = new PaginatedResponse<>(List.of(dto), null,
                false);

        when(relyingPartyService.fetchWDashes(eq(null), eq(500))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/relying-party/wdash")
                        .param("cursor", ""))
                .andExpect(status().isOk());

        verify(relyingPartyService).fetchWDashes(eq(""), eq(500));
    }

    @Test
    void getProcessedData_shouldCapLimitAt5000() throws Exception {
        ProcessedWDashDto dto = new ProcessedWDashDto("val");
        PaginatedResponse<ProcessedWDashDto> mockResponse = new PaginatedResponse<>(List.of(dto), null,
                false);

        when(relyingPartyService.fetchWDashes(eq(null), eq(5000))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/relying-party/wdash")
                        .param("cursor", "")
                        .param("limit", "9999"))
                .andExpect(status().isOk());

        verify(relyingPartyService).fetchWDashes(eq(""), eq(5000));
    }

    @Test
    void receiveWebhook_returnsAcceptedAndCallsService() throws Exception {
        RevocationWcDto revocationWcDto = new RevocationWcDto("wValue", "cValue");
        RelyingPartyWebhookPayload payload = new RelyingPartyWebhookPayload(List.of(revocationWcDto));
        String json = objectMapper.writeValueAsString(payload);

        doNothing().when(relyingPartyService).processWebhookPayload(payload.items());

        mockMvc.perform(post("/api/v1/relying-party/webhooks/wcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void receiveWebhook_returnsBadRequestOnInvalidPayload() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post("/api/v1/relying-party/webhooks/wcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}