package demo.webauthn.exception;

public class UnsupportedKeyTypeException extends InvalidCoseKeyFormatException {
    public UnsupportedKeyTypeException(String message) { super(message); }
}