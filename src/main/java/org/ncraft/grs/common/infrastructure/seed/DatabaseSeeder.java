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

package org.ncraft.grs.common.infrastructure.seed;

import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.common.config.AppProperties;
import org.ncraft.grs.common.infrastructure.identifiers.UuidGenerator;
import org.ncraft.grs.relyingparty.domain.RelyingPartyEvents;
import org.ncraft.grs.relyingparty.infrastructure.RelyingPartyEventsRepository;
import org.ncraft.grs.revocation.domain.RevocationKey;
import org.ncraft.grs.revocation.infrastructure.RevocationKeyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * TODO: This class is in work in progress.
 * Populates the database with initial, reproducible test data for local development.
 * <p>
 * <b>Security & Environment constraints:</b><br>
 * This component is strictly bound to the {@code local} Spring profile. This prevents
 * dummy data from accidentally executing and corrupting higher environments (e.g., QA, UAT, PROD).
 * </p>
 * <p>
 * <b>Execution Lifecycle:</b><br>
 * Implements {@link CommandLineRunner} to execute automatically immediately after the Spring
 * application context is fully loaded and the database connection is established.
 * </p>
 */
@Component
@Profile("local")
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private static final String RSA_ALGORITHM = "RSA";
    private final UuidGenerator uuidGenerator;
    private final RevocationKeyRepository revocationKeyRepository;
    private final RelyingPartyEventsRepository relyingPartyEventsRepository;
    private final AppProperties appProperties;

    public DatabaseSeeder(UuidGenerator uuidGenerator,
                          RevocationKeyRepository revocationKeyRepository,
                          RelyingPartyEventsRepository relyingPartyEventsRepository,
                          AppProperties appProperties) {
        this.uuidGenerator = uuidGenerator;
        this.revocationKeyRepository = revocationKeyRepository;
        this.relyingPartyEventsRepository = relyingPartyEventsRepository;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (revocationKeyRepository.count() > 0 || relyingPartyEventsRepository.count() > 0) {
            log.info("Database already contains data. Skipping development seed phase.");
            return;
        }

        log.info("Starting local database seeding with standard developer dataset...");

        for (int i = 0; i < 10; i++) {
            UUID id = uuidGenerator.generateV7();
            String fakeKeyBase64 = generateBase64PublicKey();
            RevocationKey revocationKey = new RevocationKey(id, fakeKeyBase64);
//            revocationKeyRepository.save(revocationKey);
        }

        relyingPartyEventsRepository.saveAll(getWc());

        log.info("Database seeding complete.");
    }

    /**
     * Generates a new RSA key pair and returns only the public key
     * encoded as a Base64 string.
     *
     * @return Base64 encoded RSA public key
     */
    private String generateBase64PublicKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyPairGenerator.initialize(appProperties.rsaKeySizeBits(), new SecureRandom());

            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PublicKey publicKey = keyPair.getPublic();

            return Base64.getEncoder().encodeToString(publicKey.getEncoded());

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to generate RSA public key", e);
        }
    }

    private List<RelyingPartyEvents> getWc() {
        List<RelyingPartyEvents> result = new ArrayList<>();
        result.add(new RelyingPartyEvents(uuidGenerator.generateV7(), "ymqQjBFymRIapZKJ6Q1kk9gem1jly9woGUaxY0vnBFhe0kXzplHFyVx2F7raCF81", "i8oYaDZAi58UyVg_6wFtC13h0gcuh7lYscglOJgyNeuLzl3IN3_TM5f061coxFEnBPbFndxHzHSZYK2QtWc8S7bOBxiZ1y0CIM3uUuiQqVd18ym_uKy4LwLmI4g2E2S3g_dvVsDXlCKmBFvVIk-h9_aj_8ZRmCnSzZoDg8Ripdo"));
        result.add(new RelyingPartyEvents(uuidGenerator.generateV7(), "BhYJZwwxDqRkbvFmDRKFYcUBaFqs8I95WnN9BSvffgkntGi0Lyi0kkPA55hfoqQM", "oEJZMIWk01DwyGtdF9jLej5QaSqehmKGNfotRpq1jg4qFv9dfotXKlTaHxqJdpk0IwRN4y69A2K2vyOl0WXI185UV2Ew6_7SjcSplLU36jWoNQD94INuCjsKcWZHvUKJQrMeR_5XBt_HkCc_7-fD60EtJGEGzJaShf73kFZGHvU"));
        result.add(new RelyingPartyEvents(uuidGenerator.generateV7(), "0LLwc7PPCOQkZRYPoQlYDMyGAuI9JSh6rtp9SSeGjmPNiM6zrRhDH4ig5xiWK0kY", "jPi5-xrczX2bVvrW_sICx8QFy8krrbtu0Rp_rJ6fnrEjj-L4hogGFb7EAx3-796_ZVXBcWVBAvGPY76BdKPpAvWC4keNQg9Iwy3-rMg8AtdhUBoQpgcM0dP9tHPvrX6GSEhuO8XVveNIegJ3QYVEvxizGMoO14-gTNW6FolhXqU"));
        result.add(new RelyingPartyEvents(uuidGenerator.generateV7(), "1wmPY7EmmOaY7CawmOaMZqwDLRrFwaX4E2td2HWN-08kkW6cKMnFWqqbf3a-t2v3", "qwbmra3cSD6csLi1arCQVmSDFBmmR22udYQTPCPtdQrql1MPd4rBaq_itJaEtVQAreN8ZtdtqZUqd0ntby4SKKEJB5ZN0qFFN6NrN8aC5RNdXoDq9PxTX1DNroIJ0PxrA1awm26Op2SCn7LyDcn-aZFg6zYexNezqs72G0N4S30"));
        result.add(new RelyingPartyEvents(uuidGenerator.generateV7(), "vswmZwaMTCSTmWZmjEwmjKCFWbGb_5QWcsjcZWYrV-jGKSzDeSXm79PU1Z06MnOh", "sa5-cVLnMF63Iib7d2ydb2FpLd10VjtUIwdbqW8DA5AwxaaXQYSM2r9YD44VmBZiTxT2jt75fyWUwviI4mcPh8sLn45h1KyR5Z7KO2BMtqcyoGbSdCcw-QlvRoYdrrEnGFy_D8SFRfzfOG8zg_hZnU4NNUhzjRuLNknD_zz30XU"));
        return result;
    }


}