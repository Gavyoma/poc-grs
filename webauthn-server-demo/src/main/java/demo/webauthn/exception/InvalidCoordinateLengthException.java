package demo.webauthn.exception;

public class InvalidCoordinateLengthException extends InvalidCoseKeyFormatException {
    public InvalidCoordinateLengthException(String message) { super(message); }
}