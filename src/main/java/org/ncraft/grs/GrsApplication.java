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

package org.ncraft.grs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class GrsApplication implements ApplicationRunner {

    @Value("${app.version}")
    private String appVersion;

    @Value("${spring.application.name}")
    private String appName;
 
    public static void main(String[] args) {
        SpringApplication.run(GrsApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("===============================================");
        log.info(" Application Name: {}", appName);
        log.info(" Version Number: {}", appVersion);
        log.info("===============================================");
    }

}
