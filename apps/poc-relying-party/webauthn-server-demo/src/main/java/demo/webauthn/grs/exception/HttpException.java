package demo.webauthn.grs.exception;

import java.io.IOException;

public class HttpException extends IOException {
    private final int statusCode;

    public HttpException(int statusCode, String message) {
        super("HTTP error " + statusCode + ": " + message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}