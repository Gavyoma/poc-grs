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

package org.ncraft.grs.revocation.api;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.revocation.application.RevocationKeyService;
import org.ncraft.grs.revocation.application.exception.DuplicateKeyException;
import org.ncraft.grs.revocation.application.exception.SubscriptionSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class RevocationKeyController {

    private static final String VIEW_REVOCATION_KEY_PAGE = "revocation-key-form";
    private static final String FRAGMENT_FORM = "revocation-key-form :: formContent";

    private RevocationKeyService grsService;

    @GetMapping("/")
    public String showForm(Model model) {
        model.addAttribute("keyForm", new RevocationKeyForm(""));
        return VIEW_REVOCATION_KEY_PAGE;
    }

    @HxRequest
    @PostMapping("/save-key")
    public String saveKey(
            @Valid @ModelAttribute("keyForm") RevocationKeyForm keyForm,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            return FRAGMENT_FORM;
        }

        try {
            String rawKey = keyForm.key().trim();
            grsService.saveRevocationKey(rawKey);
            model.addAttribute("successMessage", "Key saved!");
            return FRAGMENT_FORM;
        } catch (DuplicateKeyException e) {
            bindingResult.rejectValue("key", "duplicate", "This key is already registered.");
            return FRAGMENT_FORM;
        } catch (SubscriptionSystemException e) {
            log.error("Key registration failed: {}", e.getMessage(), e);
            model.addAttribute("globalError", "Our system is temporarily unavailable. Please try resubmitting in a moment.");
            return FRAGMENT_FORM;
        }
    }
}