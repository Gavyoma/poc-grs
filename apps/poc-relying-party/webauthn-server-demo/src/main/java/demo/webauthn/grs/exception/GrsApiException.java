/*
 * Copyright (c) 2024-2025, N. "Gavi" Pistolwala
 * All rights reserved.
 *
 * This source code is licensed under the same terms as the rest of the
 * original project, found in the COPYING file in the root directory.
 */
package demo.webauthn.grs.exception;

/**
 * Exception thrown when a REST API call fails.
 * This handles both client-side network failures (timeouts, serialization issues)
 * and server-side HTTP errors (4xx, 5xx).
 */
public class GrsApiException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    /**
     * Master constructor for API errors.
     * * @param message A descriptive error message.
     *
     * @param statusCode   The HTTP status code, or -1 if the failure happened before a response was received.
     * @param responseBody The raw JSON string returned by the server, or null if unreadable/absent.
     * @param cause        The underlying exception (e.g., IOException), or null if purely an HTTP error.
     */
    public GrsApiException(String message, int statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public GrsApiException(String message, int statusCode) {
        this(message, statusCode, null, null);
    }

    public GrsApiException(String message, int statusCode, String responseBody) {
        this(message, statusCode, responseBody, null);
    }

    public GrsApiException(String message, Throwable cause) {
        this(message, -1, null, cause);
    }

    public GrsApiException(String message, int statusCode, Throwable cause) {
        this(message, statusCode, null, cause);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    /**
     * Checks if the error was a valid HTTP response or a client-side network failure.
     * * @return true if the server returned an HTTP status code, false if it was a network drop or timeout.
     */
    public boolean hasResponse() {
        return statusCode != -1;
    }
}