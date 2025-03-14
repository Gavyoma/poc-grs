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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.ncraft.grs.relyingparty.api.dto.PaginatedResponse;
import org.ncraft.grs.relyingparty.api.dto.ProcessedWDashDto;
import org.ncraft.grs.relyingparty.api.dto.RelyingPartyWebhookPayload;
import org.ncraft.grs.relyingparty.api.dto.RevocationWcDto;
import org.ncraft.grs.relyingparty.domain.RelyingPartyEvents;
import org.ncraft.grs.relyingparty.infrastructure.RelyingPartyEventsRepository;
import org.ncraft.grs.revocation.domain.RevocationKey;
import org.ncraft.grs.revocation.infrastructure.RevocationKeyRepository;
import org.ncraft.grs.wcprocessor.domain.ProcessedEvent;
import org.ncraft.grs.wcprocessor.domain.ProcessedEventRepository;
import org.ncraft.grs.wcprocessor.domain.ProcessingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RelyingPartyControllerIT {

    private static final String BASE_URL = "/api/v1/relying-party";
    private static final String WDASH_PATH = BASE_URL + "/wdash";
    private static final String WEBHOOK_PATH = BASE_URL + "/webhooks/wcs";

    private static final ParameterizedTypeReference<PaginatedResponse<ProcessedWDashDto>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.0-alpine");

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Autowired
    RevocationKeyRepository revocationKeyRepository;

    @Autowired
    RelyingPartyEventsRepository relyingPartyEventsRepository;

    private static String validBase64Key(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlSafeBase64(String raw) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void cleanDatabase() {
        processedEventRepository.deleteAll();
        revocationKeyRepository.deleteAll();
        relyingPartyEventsRepository.deleteAll();
    }

    private void seedProcessed(String... wDashes) {
        List<ProcessedEvent> events = IntStream.range(0, wDashes.length)
                .mapToObj(i -> {
                    RevocationKey key = revocationKeyRepository.save(
                            new RevocationKey(
                                    UUID.randomUUID(),
                                    validBase64Key("rk-" + i + "-" + UUID.randomUUID())
                            )
                    );
                    RelyingPartyEvents rpEvent = relyingPartyEventsRepository.save(
                            new RelyingPartyEvents(
                                    UUID.randomUUID(),
                                    urlSafeBase64("w-" + i),
                                    urlSafeBase64("c-" + i)
                            )
                    );
                    return new ProcessedEvent(
                            UUID.randomUUID(),
                            key.getId(),
                            rpEvent.getId(),
                            wDashes[i],
                            ProcessingStatus.SUCCESS,
                            ""
                    );
                })
                .toList();
        processedEventRepository.saveAll(events);
    }

    @Nested
    @DisplayName("GET /api/v1/relying-party/wdash")
    class GetProcessedWDash {

        @Test
        @DisplayName("returns empty first page when no events exist")
        void emptyFirstPage() {
            webTestClient.get()
                    .uri(WDASH_PATH)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PAGE_TYPE)
                    .value(page -> {
                        assertThat(page.data()).isEmpty();
                        assertThat(page.hasMore()).isFalse();
                        assertThat(page.nextCursor()).isNull();
                    });
        }

        @Test
        @DisplayName("returns seeded W' values on the first page")
        void firstPageContainsSeededValues() {
            seedProcessed("w-dash-aaa", "w-dash-bbb", "w-dash-ccc");

            webTestClient.get()
                    .uri(uri -> uri.path(WDASH_PATH).queryParam("limit", 10).build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PAGE_TYPE)
                    .value(page -> {
                        assertThat(page.data())
                                .extracting(ProcessedWDashDto::wDash)
                                .containsExactlyInAnyOrder("w-dash-aaa", "w-dash-bbb", "w-dash-ccc");
                        assertThat(page.hasMore()).isFalse();
                    });
        }

        @Test
        @DisplayName("honours limit and walks pages via nextCursor")
        void cursorPagination() {
            seedProcessed("w-01", "w-02", "w-03", "w-04", "w-05");

            PaginatedResponse<ProcessedWDashDto> firstPage = webTestClient.get()
                    .uri(uri -> uri.path(WDASH_PATH).queryParam("limit", 2).build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PAGE_TYPE)
                    .returnResult()
                    .getResponseBody();

            assertThat(firstPage).isNotNull();
            assertThat(firstPage.data()).hasSize(2);
            assertThat(firstPage.hasMore()).isTrue();
            assertThat(firstPage.nextCursor()).isNotBlank();

            PaginatedResponse<ProcessedWDashDto> secondPage = webTestClient.get()
                    .uri(uri -> uri.path(WDASH_PATH)
                            .queryParam("cursor", firstPage.nextCursor())
                            .queryParam("limit", 2)
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PAGE_TYPE)
                    .returnResult()
                    .getResponseBody();

            assertThat(secondPage).isNotNull();
            assertThat(secondPage.data()).hasSize(2);

            Set<String> firstValues = firstPage.data().stream()
                    .map(ProcessedWDashDto::wDash)
                    .collect(Collectors.toSet());
            Set<String> secondValues = secondPage.data().stream()
                    .map(ProcessedWDashDto::wDash)
                    .collect(Collectors.toSet());
            assertThat(firstValues).isNotEmpty();
            assertThat(secondValues).isNotEmpty();
            assertThat(firstValues).doesNotContainAnyElementsOf(secondValues);

            webTestClient.get()
                    .uri(uri -> uri.path(WDASH_PATH)
                            .queryParam("cursor", secondPage.nextCursor())
                            .queryParam("limit", 2)
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PAGE_TYPE)
                    .value(thirdPage -> {
                        assertThat(thirdPage.data()).hasSize(1);
                        assertThat(thirdPage.hasMore()).isFalse();
                    });
        }

        @Test
        @DisplayName("caps requested limit at 5000 and still returns 200")
        void limitIsCappedAt5000() {
            seedProcessed("only-one");

            webTestClient.get()
                    .uri(uri -> uri.path(WDASH_PATH).queryParam("limit", 99_999).build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PAGE_TYPE)
                    .value(page -> assertThat(page.data()).hasSize(1));
        }

        @Test
        @DisplayName("returns 400 problem+json for a tampered cursor")
        void malformedCursorReturns400() {
            webTestClient.get()
                    .uri(uri -> uri.path(WDASH_PATH)
                            .queryParam("cursor", "not-a-valid-opaque-cursor")
                            .build())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody(ProblemDetail.class)
                    .value(problem -> assertThat(problem.getStatus()).isEqualTo(400));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/relying-party/webhooks/wcs")
    class ReceiveWebhook {

        @Test
        @DisplayName("accepts a valid payload with 202")
        void acceptedWhenPayloadIsValid() {
            RelyingPartyWebhookPayload payload = new RelyingPartyWebhookPayload(List.of(
                    new RevocationWcDto(
                            "ymqQjBFymRIapZKJ6Q1kk9gem1jly9woGUaxY0vnBFhe0kXzplHFyVx2F7raCF81",
                            "i8oYaDZAi58UyVg_6wFtC13h0gcuh7lYscglOJgyNeuLzl3IN3_TM5f061coxFEnBPbFndxHzHSZYK2QtWc8S7bOBxiZ1y0CIM3uUuiQqVd18ym_uKy4LwLmI4g2E2S3g_dvVsDXlCKmBFvVIk-h9_aj_8ZRmCnSzZoDg8Ripdo"
                    ),
                    new RevocationWcDto(
                            "BhYJZwwxDqRkbvFmDRKFYcUBaFqs8I95WnN9BSvffgkntGi0Lyi0kkPA55hfoqQM",
                            "oEJZMIWk01DwyGtdF9jLej5QaSqehmKGNfotRpq1jg4qFv9dfotXKlTaHxqJdpk0IwRN4y69A2K2vyOl0WXI185UV2Ew6_7SjcSplLU36jWoNQD94INuCjsKcWZHvUKJQrMeR_5XBt_HkCc_7-fD60EtJGEGzJaShf73kFZGHvU"
                    )
            ));

            webTestClient.post()
                    .uri(WEBHOOK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .exchange()
                    .expectStatus().isAccepted()
                    .expectBody().isEmpty();
        }

        @Test
        @DisplayName("rejects an empty items list with 400")
        void emptyItemsRejected() {
            webTestClient.post()
                    .uri(WEBHOOK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new RelyingPartyWebhookPayload(List.of()))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("rejects a body with null items with 400")
        void missingItemsRejected() {
            webTestClient.post()
                    .uri(WEBHOOK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void webhook_isAccepted() {
            long before = relyingPartyEventsRepository.count();

            String w = urlSafeBase64("w-1");
            String c = urlSafeBase64("c-1");

            var payload = new RelyingPartyWebhookPayload(List.of(
                    new RevocationWcDto(w, c)
            ));

            webTestClient.post()
                    .uri(WEBHOOK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .exchange()
                    .expectStatus().isAccepted()
                    .expectBody().isEmpty();

            assertThat(relyingPartyEventsRepository.count()).isEqualTo(before + 1);
            assertThat(relyingPartyEventsRepository.findAll())
                    .anySatisfy(event -> {
                        assertThat(event.getW()).isEqualTo(w);
                        assertThat(event.getC()).isEqualTo(c);
                    });
        }

    }
}