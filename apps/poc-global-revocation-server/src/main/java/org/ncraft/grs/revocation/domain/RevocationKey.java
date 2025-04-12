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

package org.ncraft.grs.revocation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ncraft.grs.common.validation.ValidBase64;
import org.springframework.data.util.ProxyUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "revocation_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevocationKey {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Key is required")
    @ValidBase64
    @Column(name = "key", unique = true, nullable = false, updatable = false)
    private String key;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RevocationKey(UUID id, String key) {
        this.id = Objects.requireNonNull(id, "Internal Error: ID must not be null");
        this.key = (key != null) ? key.strip() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || ProxyUtils.getUserClass(this) != ProxyUtils.getUserClass(o)) {
            return false;
        }
        RevocationKey that = (RevocationKey) o;
        // If the ID is null, the object hasn't been saved yet.
        // Two unsaved objects are never considered equal.
        return this.getKey() != null && this.getKey().equals(that.getKey());
    }

    @Override
    public int hashCode() {
        // Return a constant. This forces collections like HashSet to rely entirely
        // on the proxy-safe equals() method to organize the data safely.
        return getClass().hashCode();
    }
}