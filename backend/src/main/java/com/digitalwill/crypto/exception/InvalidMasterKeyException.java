package com.digitalwill.crypto.exception;

public class InvalidMasterKeyException extends CryptoException {
    public InvalidMasterKeyException(String message) {
        super(message);
    }

    public InvalidMasterKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
