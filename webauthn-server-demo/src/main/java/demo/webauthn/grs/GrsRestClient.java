/*
 * Copyright (c) 2024-2025, Nirav Pistolwala
 * All rights reserved.
 *
 * This source code is licensed under the same terms as the rest of the
 * original project, found in the COPYING file in the root directory.
 */
package demo.webauthn.grs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import demo.webauthn.grs.deserializer.RevocationKeyDeserializer;
import demo.webauthn.grs.dto.RevocationWDash;
import demo.webauthn.grs.dto.RevocationWc;
import demo.webauthn.grs.exception.GrsApiException;
import demo.webauthn.grs.exception.HttpException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * REST client for communicating with the external Global revocation server.
 * <p>
 * Responsible for fetching data, syncing data, and performing batch operations
 * via HTTP using OkHttp and JSON serialization with Gson.
 */
@Slf4j
public class GrsRestClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;

    public GrsRestClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        gson = new GsonBuilder()
                .registerTypeAdapter(RevocationWDash.class, new RevocationKeyDeserializer())
                .create();
    }

    public List<RevocationWDash> getKeys(List<RevocationWc> wcList) throws Exception {
        String url = baseUrl + "/api/data";
        Optional<String> responseBody = post(url, wcList);
        if (responseBody.isPresent()) {
            log.debug("Response: {}", responseBody);
            try {
                return gson.fromJson(responseBody.get(), new TypeToken<List<RevocationWDash>>() {
                }.getType());
            } catch (com.google.gson.JsonSyntaxException e) {
                throw new GrsApiException("Failed to parse JSON response", e);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Generic GET method
     */
    public String get(String endpoint) throws IOException {
        String url = baseUrl + endpoint;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP error: " + response.code());
            }
            return response.body().string();
        }
    }

    /**
     * Sends a POST request with a JSON payload to the specified URL.
     * <p>
     * If the provided body is {@code null} or an empty {@link Collection}, the request
     * is skipped and an empty Optional is returned.
     *
     * @param url  The target URL for the POST request. Must not be null or blank.
     * @param body The object to be serialized into JSON and sent as the request body.
     * @return An {@link Optional} containing the response body as a String, or {@code Optional.empty()}
     * if the request was skipped or the response body was empty.
     * @throws IllegalArgumentException If the URL is null or blank.
     * @throws IOException              If a network error occurs during the request.
     * @throws HttpException            If the server returns a non-2xx HTTP status code.
     */
    public Optional<String> post(String url, Object body) throws IOException {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        if (body == null) {
            log.debug("Skipping POST request: body is null");
            return Optional.empty();
        }

        if (body instanceof Collection && ((Collection<?>) body).isEmpty()) {
            log.debug("Skipping POST request: empty collection");
            return Optional.empty();
        }

        String jsonBody = gson.toJson(body);
        RequestBody requestBody = RequestBody.create(JSON_MEDIA_TYPE, jsonBody);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpException(response.code(), response.message());
            }

            ResponseBody responseBody = response.body();

            if (responseBody == null) {
                return Optional.empty();
            }

            return Optional.of(responseBody.string());
        }
    }

}