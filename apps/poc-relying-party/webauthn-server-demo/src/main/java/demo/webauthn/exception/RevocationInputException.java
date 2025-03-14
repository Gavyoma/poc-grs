package demo.webauthn.exception;

public class RevocationInputException extends IllegalArgumentException {
    public RevocationInputException(String message) { super(message); }
    public RevocationInputException(String message, Throwable cause) { super(message, cause); }
}