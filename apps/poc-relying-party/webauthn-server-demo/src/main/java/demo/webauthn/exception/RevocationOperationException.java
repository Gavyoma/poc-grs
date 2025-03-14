package demo.webauthn.exception;

public class RevocationOperationException extends IllegalStateException {
    public RevocationOperationException(String message) { super(message); }
    public RevocationOperationException(String message, Throwable cause) { super(message, cause); }
}