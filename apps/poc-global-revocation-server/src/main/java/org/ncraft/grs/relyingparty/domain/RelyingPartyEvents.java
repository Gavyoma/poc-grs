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

package org.ncraft.grs.relyingparty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.util.ProxyUtils;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "relying_party_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelyingPartyEvents {

    @Id
    private UUID id;

    @Column(name = "w", nullable = false, length = 2048)
    private String w;

    @Column(name = "c", nullable = false, length = 2048)
    private String c;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public RelyingPartyEvents(UUID id, String w, String c) {
        this.id = id;
        this.w = w;
        this.c = c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || ProxyUtils.getUserClass(this) != ProxyUtils.getUserClass(o))
            return false;
        RelyingPartyEvents that = (RelyingPartyEvents) o;

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