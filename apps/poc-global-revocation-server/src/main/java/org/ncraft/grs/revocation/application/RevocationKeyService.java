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

package org.ncraft.grs.revocation.application;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.common.event.DataSubmittedEvent;
import org.ncraft.grs.common.event.IntakeChannel;
import org.ncraft.grs.common.infrastructure.identifiers.UuidGenerator;
import org.ncraft.grs.revocation.application.exception.DuplicateKeyException;
import org.ncraft.grs.revocation.application.exception.SubscriptionSystemException;
import org.ncraft.grs.revocation.domain.RevocationKey;
import org.ncraft.grs.revocation.infrastructure.RevocationKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class RevocationKeyService {

    private final RevocationKeyRepository revocationKeyRepository;
    private final UuidGenerator uuidGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void saveRevocationKey(String rawKey) {

        if (revocationKeyRepository.existsByKey(rawKey)) {
            throw new DuplicateKeyException("Key is already registered.");
        }

        try {
            revocationKeyRepository.saveAndFlush(new RevocationKey(uuidGenerator.generateV7(), rawKey));
            eventPublisher.publishEvent(new DataSubmittedEvent(this, IntakeChannel.REVOCATION_KEY_FORM));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateKeyException(rawKey);
        } catch (DataAccessException e) {
            throw new SubscriptionSystemException("Storage layer failed during save.", e);
        }
    }

}