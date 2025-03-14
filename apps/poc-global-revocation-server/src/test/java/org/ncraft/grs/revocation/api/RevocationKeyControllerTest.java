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

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequestHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ncraft.grs.common.config.AppDocsProperties;
import org.ncraft.grs.common.config.AppProperties;
import org.ncraft.grs.revocation.application.RevocationKeyService;
import org.ncraft.grs.revocation.application.exception.DuplicateKeyException;
import org.ncraft.grs.revocation.application.exception.SubscriptionSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RevocationKeyController.class)
class RevocationKeyControllerTest {

    private static final String VIEW_PAGE = "revocation-key-form";
    private static final String FRAGMENT_FORM = "revocation-key-form :: formContent";

    private static final String VALID_KEY =
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA3wWg6qRXWj7g0NXUluO6i7Cy3hXdZszp9ZdB8t0saPLE9NBC6fF/StChuBVUpfIN0HYWoS7+wc3PKxMpFzE3Efclu54WwNUqT3rohOgPWaBjbM0ImAR/B7XzTcxkKjlYIKscvKCZRTJ+7aPmtVZ1vpA1m+zYioKIONtAjgxMXHwRreaVlqefSV3qo9r/cUFSqxMyFP+wImdeZYQoSr5/mcrIlOt9gyBirJ+N7rDGeV2J1Ud7LoF/Ab9CdxN4OnlJPALxZ2QFVe1Bs+gBLodcAqiWVlL6l4Zcmcklbk3Z5SBjOVy7zWrLj/hpJP4m5Uh/r7qLIINH0Z4QchI4C9zJPT75pywHxYeSt7YpBqIBa++jGdcqP0Sokz0By7hKTnAwojWr0jrGaK7PO83DFZXDLvlSaDFON1bIGUus0MGoIi65GiRKejZKpsQKVic9XLo/zX/J992+/WEWpSMMhQ38jFqXS+2qtC9B7cUBwr3GXt0HbZ6gb5iSHQ+g7KZqnqTTjMeBdjE/i6xHRRuy/yvHmOMP2v1RPp4PrSN/TNSbXVQpCXNzf4Lcbp2Sy/XcAJyDzJ1gAWtTW2Ui0xBmWt49DsFnsSPahcmFrHzJkXMQveZHKsqzMoFzXJ7UxYBqIhB6AsRn/8zuJFwQEpmwE8Iw/zJppeEuCmuvyu3tq8V2g9ECAwEAAQ==";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RevocationKeyService grsService;

    @MockBean
    private AppDocsProperties appDocsProperties;

    @MockBean
    private AppProperties appProperties;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder htmxPost(String url) {
        return post(url)
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HtmxRequestHeader.HX_REQUEST.getValue(), "true");
    }

    @BeforeEach
    void stubKeyPolicy() {
        when(appProperties.rsaKeySizeBits()).thenReturn(4096);
    }

    @Test
    void showForm_returnsFullPageWithEmptyForm() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name(VIEW_PAGE))
                .andExpect(model().attributeExists("keyForm"));
    }

    @Test
    void saveKey_withoutHtmxHeader_isNotMapped() throws Exception {
        mockMvc.perform(post("/save-key")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("key", VALID_KEY))
                .andExpect(status().isNotFound());

        verify(grsService, never()).saveRevocationKey(anyString());
    }

    @Test
    void saveKey_validPayload_savesAndReturnsFragmentWithSuccessMessage() throws Exception {
        mockMvc.perform(htmxPost("/save-key")
                        .param("key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(view().name(FRAGMENT_FORM))
                .andExpect(model().attribute("successMessage", "Key saved!"))
                .andExpect(model().attributeDoesNotExist("globalError"))
                .andExpect(model().hasNoErrors());

        verify(grsService).saveRevocationKey(VALID_KEY);
    }

    @Test
    void saveKey_blankKey_returnsFragmentWithFieldError() throws Exception {
        mockMvc.perform(htmxPost("/save-key")
                        .param("key", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name(FRAGMENT_FORM))
                .andExpect(model().attributeHasFieldErrors("keyForm", "key"));

        verify(grsService, never()).saveRevocationKey(anyString());
    }

    @Test
    void saveKey_oversizedKey_returnsFragmentWithFieldError() throws Exception {
        mockMvc.perform(htmxPost("/save-key")
                        .param("key", "A".repeat(737)))
                .andExpect(status().isOk())
                .andExpect(view().name(FRAGMENT_FORM))
                .andExpect(model().attributeHasFieldErrors("keyForm", "key"));

        verify(grsService, never()).saveRevocationKey(anyString());
    }

    @Test
    void saveKey_duplicateKey_rejectsFieldAndDoesNotSetSuccess() throws Exception {
        doThrow(new DuplicateKeyException("already registered"))
                .when(grsService).saveRevocationKey(VALID_KEY);

        mockMvc.perform(htmxPost("/save-key")
                        .param("key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(view().name(FRAGMENT_FORM))
                .andExpect(model().attributeHasFieldErrorCode("keyForm", "key", "duplicate"))
                .andExpect(model().attributeDoesNotExist("successMessage"));

        verify(grsService).saveRevocationKey(VALID_KEY);
    }

    @Test
    void saveKey_subscriptionSystemFailure_setsGlobalError() throws Exception {
        doThrow(new SubscriptionSystemException("downstream down", new RuntimeException("boom")))
                .when(grsService).saveRevocationKey(VALID_KEY);

        mockMvc.perform(htmxPost("/save-key")
                        .param("key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(view().name(FRAGMENT_FORM))
                .andExpect(model().attribute(
                        "globalError",
                        "Our system is temporarily unavailable. Please try resubmitting in a moment."))
                .andExpect(model().attributeDoesNotExist("successMessage"));

        verify(grsService).saveRevocationKey(VALID_KEY);
    }
}