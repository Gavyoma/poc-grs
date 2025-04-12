/*
 * Copyright (c) 2024-2025, N. "Gavi" Pistolwala
 * All rights reserved.
 *
 * This source code is licensed under the same terms as the rest of the
 * original project, found in the COPYING file in the root directory.
 */
package demo.webauthn.grs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RevocationWDash {

    @JsonProperty("wDash")
    private String wDash;

}