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

package org.ncraft.grs.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BuildInfoLogger {

    private final BuildProperties buildProperties;

    public BuildInfoLogger(ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logBuildInfo() {
        if (buildProperties != null) {
            log.info("===========================================================");
            log.info("Application : {}", buildProperties.getName());
            log.info("Version     : {}", buildProperties.getVersion());
            log.info("Build Time  : {}", buildProperties.getTime());
            log.info("Build Number: {}", buildProperties.get("buildNumber"));
            log.info("===========================================================");
        } else {
            log.warn("Build properties not found. (Run 'gradle bootRun' or build the project first)");
        }
    }
}