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

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.util.ProxyUtils;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = @UniqueConstraint(name = "unq_key_w_c", columnNames = {"key_id", "event_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvents {

    @Id
    private UUID id;

    @Column(name = "key_id")
    private UUID keyId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "w_dash", length = 2048)
    private String wDash;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;


    public ProcessedEvents(UUID id, UUID keyId, UUID eventId, String wDash) {
        this.id = id;
        this.keyId = keyId;
        this.eventId = eventId;
        this.wDash = wDash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || ProxyUtils.getUserClass(this) != ProxyUtils.getUserClass(o)) {
            return false;
        }
        ProcessedEvents that = (ProcessedEvents) o;
        // If the ID is null, the object hasn't been saved yet.
        // Two unsaved objects are never considered equal.
        return this.getId() != null && this.getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        // Return a constant. This forces collections like HashSet to rely entirely
        // on the proxy-safe equals() method to organize the data safely.
        return getClass().hashCode();
    }

}