package com.digitalwill.crypto.exception;

public class DecryptionFailedException extends CryptoException {
    public DecryptionFailedException(String message) {
        super(message);
    }

    public DecryptionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
