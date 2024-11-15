/*
 * Copyright (c) 2025, Nirav Pistolwala
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

package org.ncraft.grs.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.entity.RevocationKeyEntity;
import org.ncraft.grs.mapper.KeyMapper;
import org.ncraft.grs.model.RevocationKey;
import org.ncraft.grs.model.RevocationWC;
import org.ncraft.grs.model.RevocationWDash;
import org.ncraft.grs.repository.RevocationKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class GrsService {

    private final RevocationKeyRepository revocationKeyRepository;

    public List<RevocationWDash> getAll(List<RevocationWC> revocationWCs) {
        List<RevocationWDash> result = new ArrayList<>();
        List<RevocationKeyEntity> entities = revocationKeyRepository.findAll();

        for (RevocationWC revocationWC : revocationWCs) {
            for (RevocationKeyEntity entity : entities) {
                try {
                    byte[] publicKeyBytes = RsaKemHybridCipher.base64ToByteArray(entity.getKey());
                    byte[] rsaCiphertext = RsaKemHybridCipher.base64UrlToByteArray(revocationWC.getC());
                    byte[] aesPayload = RsaKemHybridCipher.base64UrlToByteArray(revocationWC.getW());
                    PublicKey picoPublicKey = RsaKemHybridCipher.buildPublicKeyFromBytes(publicKeyBytes);

                    byte[] extractedAesKey = RsaKemHybridCipher.extractAesKey(rsaCiphertext, picoPublicKey,
                            RsaKemHybridCipher.FIDO_RSA_BYTES);
                    log.debug("      ✅ Success! AES Key extracted.");

                    log.debug("Decrypting the AES-CTR Payload...");
                    String plainTextBase64Url = RsaKemHybridCipher.decryptAesCtrPayload(extractedAesKey, aesPayload);

                    result.add(RevocationWDash.builder().wDash(plainTextBase64Url).build());
                } catch (Exception e) {
                    log.debug("\n❌ Decryption Failed!", e);
                }
            }
        }
        return result;
    }

    public RevocationKey saveRevocationKey(RevocationKey key) {
        RevocationKeyEntity myEntity = new RevocationKeyEntity();
        myEntity.setKey(key.getKey());
        RevocationKeyEntity save = revocationKeyRepository.save(myEntity);
        return KeyMapper.INSTANCE.entityToModel(save);
    }


}