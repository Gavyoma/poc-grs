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

package org.ncraft.grs.wcprocessor.application;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.common.crypto.WDashDecryptor;
import org.ncraft.grs.common.exception.DomainRuleViolationException;
import org.ncraft.grs.common.exception.InfrastructureOfflineException;
import org.ncraft.grs.common.infrastructure.identifiers.UuidGenerator;
import org.ncraft.grs.wcprocessor.domain.ProcessingStatus;
import org.ncraft.grs.wcprocessor.domain.exception.BatchProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class ProcessingService {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final WDashDecryptor wDashDecryptor;
    private final UuidGenerator uuidGenerator;

    @Transactional
    public void executeChunkedProcessing() {
        int totalProcessed = 0;

        while (true) {
            List<UnprocessedPair> chunk = fetchUnprocessedChunk();

            if (chunk.isEmpty()) {
                break;
            }

            processAndSaveChunk(chunk);
            totalProcessed += chunk.size();
        }

        if (totalProcessed > 0) {
            log.info("Processing complete. Successfully processed {} records.", totalProcessed);
        }
    }

    private List<UnprocessedPair> fetchUnprocessedChunk() {
        String fetchSql = """
                    SELECT e.id AS key_id, e.key, l.id AS event_id, l.w, l.c
                    FROM revocation_keys e
                    CROSS JOIN relying_party_events l
                    WHERE NOT EXISTS (
                        SELECT 1 FROM processed_events pe 
                        WHERE pe.key_id = e.id AND pe.event_id = l.id
                    )
                    LIMIT ?
                """;

        try {
            return jdbcTemplate.query(fetchSql, (rs, rowNum) -> new UnprocessedPair(
                    rs.getObject("key_id", UUID.class),
                    rs.getString("key"),
                    rs.getObject("event_id", UUID.class),
                    rs.getString("w"),
                    rs.getString("c")
            ), BATCH_SIZE);

        } catch (QueryTimeoutException e) {
            throw new BatchProcessingException("Database timed out during fetch.", e);

        } catch (DataAccessResourceFailureException e) {
            throw new InfrastructureOfflineException("Lost connection to database during fetch.", e);
        }
    }

    private void processAndSaveChunk(List<UnprocessedPair> chunk) {
        List<ValidPair> validItems = new ArrayList<>();

        for (UnprocessedPair pair : chunk) {
            try {
                String generatedHash = wDashDecryptor.calculateWDash(
                        pair.key(),
                        pair.w(),
                        pair.c()
                );

                validItems.add(new ValidPair(pair, generatedHash, ProcessingStatus.SUCCESS, ""));

            } catch (DomainRuleViolationException e) {
                validItems.add(new ValidPair(pair, "", ProcessingStatus.FAILED, formatErrorDetails(e)));
                log.warn("Skipping saving wDash (Key UUID: {}, Event UUID: {}) due to domain rule violation: {}",
                        pair.keyId(), pair.eventId(), e.getMessage());
            }
        }

        if (validItems.isEmpty()) {
            return;
        }

        String insertSql = """
                    INSERT INTO processed_events (id, key_id, event_id, w_dash, processing_status, error_details, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, NOW())
                    ON CONFLICT (key_id, event_id) DO NOTHING
                """;

        jdbcTemplate.batchUpdate(insertSql, validItems, BATCH_SIZE, (PreparedStatement ps, ValidPair validItem) -> {
            ps.setObject(1, uuidGenerator.generateV7());
            ps.setObject(2, validItem.pair.keyId());
            ps.setObject(3, validItem.pair.eventId());
            ps.setString(4, validItem.generatedHash);
            ps.setString(5, validItem.processingStatus.name());
            ps.setString(6, validItem.errorDetails);
        });
    }

    private String formatErrorDetails(Exception ex) {
        String errorMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        if (ex.getCause() != null) {
            errorMessage += " | Root Cause: " + ex.getCause().getMessage();
        }
        return errorMessage.length() > 2000
                ? errorMessage.substring(0, 2000) + "... [TRUNCATED]"
                : errorMessage;
    }

    private record ValidPair(UnprocessedPair pair, String generatedHash, ProcessingStatus processingStatus,
                             String errorDetails) {
    }
}