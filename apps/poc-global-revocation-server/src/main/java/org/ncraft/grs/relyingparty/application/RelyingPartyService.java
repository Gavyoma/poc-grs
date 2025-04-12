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

package org.ncraft.grs.relyingparty.application;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.common.event.DataSubmittedEvent;
import org.ncraft.grs.common.event.IntakeChannel;
import org.ncraft.grs.common.infrastructure.identifiers.UuidGenerator;
import org.ncraft.grs.common.infrastructure.security.CursorEncryptionService;
import org.ncraft.grs.relyingparty.api.dto.PaginatedResponse;
import org.ncraft.grs.relyingparty.api.dto.ProcessedWDashDto;
import org.ncraft.grs.relyingparty.api.dto.RevocationWcDto;
import org.ncraft.grs.relyingparty.domain.RelyingPartyEvents;
import org.ncraft.grs.relyingparty.infrastructure.RelyingPartyEventsRepository;
import org.ncraft.grs.wcprocessor.domain.ProcessedEvent;
import org.ncraft.grs.wcprocessor.domain.ProcessedEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class RelyingPartyService {

    private final ProcessedEventRepository processedEventRepository;
    private final RelyingPartyEventsRepository relyingPartyEventsRepository;
    private final CursorEncryptionService cursorEncryptionService;
    private final UuidGenerator uuidGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PaginatedResponse<ProcessedWDashDto> fetchWDashes(String cursor, int limit) {
        // Fetch limit + 1 to eassily check if there is a "next page"
        int fetchLimit = limit + 1;
        List<ProcessedEvent> entities;

        if (cursor == null || cursor.isBlank()) {
            entities = processedEventRepository.findFirstPage(fetchLimit);
        } else {
            UUID decodedCursorId = cursorEncryptionService.decryptCursor(cursor);
            entities = processedEventRepository.findNextPage(decodedCursorId.toString(), fetchLimit);
        }

        boolean hasMore = entities.size() > limit;
        if (hasMore) {
            // Remove the extra element fetched, which was just to check 'hasMore'
            entities.removeLast();
        }

        List<ProcessedWDashDto> dtoList = entities.stream()
                .map(ProcessedWDashDto::fromEntity)
                .toList();

        String nextCursor = null;
        if (hasMore && !entities.isEmpty()) {
            UUID lastIdOnPage = entities.getLast().getId();
            nextCursor = cursorEncryptionService.encryptCursor(lastIdOnPage);
        }

        return new PaginatedResponse<>(dtoList, nextCursor, hasMore);
    }

    @Transactional
    public void processWebhookPayload(List<RevocationWcDto> items) {

        List<RelyingPartyEvents> incomingItems = items.stream()
                .map(item -> new RelyingPartyEvents(
                        uuidGenerator.generateV7(),
                        item.w(),
                        item.c()
                ))
                .toList();

        String[] colWs = incomingItems.stream().map(RelyingPartyEvents::getW).toArray(String[]::new);
        String[] colCs = incomingItems.stream().map(RelyingPartyEvents::getC).toArray(String[]::new);
        List<RelyingPartyEvents> existingEntities = relyingPartyEventsRepository.findExactPairs(colWs, colCs);

        Set<UniquePair> existingPairs = existingEntities.stream()
                .map(e -> new UniquePair(e.getW(), e.getC()))
                .collect(Collectors.toSet());

        List<RelyingPartyEvents> entitiesToSave = incomingItems.stream()
                .filter(e -> !existingPairs.contains(new UniquePair(e.getW(), e.getC())))
                .toList();

        if (!entitiesToSave.isEmpty()) {
            relyingPartyEventsRepository.saveAll(entitiesToSave);
            eventPublisher.publishEvent(new DataSubmittedEvent(this, IntakeChannel.RELYING_PARTY_WEBHOOK));
        }
    }

    private record UniquePair(String colW, String colC) {
    }

}