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

package org.ncraft.grs.relyingparty.infrastructure;

import org.ncraft.grs.relyingparty.domain.RelyingPartyEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RelyingPartyEventsRepository extends JpaRepository<RelyingPartyEvents, UUID> {

    @Query(value = "SELECT * FROM relying_party_events WHERE (w, c) IN (SELECT * FROM UNNEST(:colWs, :colCs))", nativeQuery = true)
    List<RelyingPartyEvents> findExactPairs(@Param("colWs") String[] colWs, @Param("colCs") String[] colCs);

}