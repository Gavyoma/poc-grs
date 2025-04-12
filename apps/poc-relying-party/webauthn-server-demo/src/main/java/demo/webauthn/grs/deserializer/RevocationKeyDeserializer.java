/*
 * Copyright (c) 2024-2025, N. "Gavi" Pistolwala
 * All rights reserved.
 *
 * This source code is licensed under the same terms as the rest of the
 * original project, found in the COPYING file in the root directory.
 */
package demo.webauthn.grs.deserializer;

import com.google.gson.*;
import demo.webauthn.grs.dto.RevocationWDash;

import java.lang.reflect.Type;

public class RevocationKeyDeserializer implements JsonDeserializer<RevocationWDash> {

    @Override
    public RevocationWDash deserialize(JsonElement json,
                                       Type typeOfT,
                                       JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String wDash = jsonObject.get("wDash").getAsString();

        return new RevocationWDash(wDash);
    }
}