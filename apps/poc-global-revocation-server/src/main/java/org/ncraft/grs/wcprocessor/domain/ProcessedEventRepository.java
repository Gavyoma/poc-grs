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

package org.ncraft.grs.wcprocessor.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Query(value = """
                SELECT * FROM processed_events
                WHERE processing_status = 'SUCCESS'
                ORDER BY id DESC
                LIMIT :limit
            """, nativeQuery = true)
    List<ProcessedEvent> findFirstPage(@Param("limit") int limit);

    @Query(value = """
                SELECT * FROM processed_events
                WHERE processing_status = 'SUCCESS'
                AND id < cast(:cursorId as uuid)
                ORDER BY id DESC
                LIMIT :limit
            """, nativeQuery = true)
    List<ProcessedEvent> findNextPage(@Param("cursorId") String cursorId, @Param("limit") int limit);
}