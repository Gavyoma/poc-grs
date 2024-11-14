package demo.webauthn.exception;

public class InvalidCoseKeyFormatException extends IllegalArgumentException {
    public InvalidCoseKeyFormatException(String message) { super(message); }
    public InvalidCoseKeyFormatException(String message, Throwable cause) { super(message, cause); }
}