package com.digitalwill.verification.exception;

public class VerificationTokenAlreadyUsedException extends RuntimeException {
    public VerificationTokenAlreadyUsedException(String message) { super(message); }
}