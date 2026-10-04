package com.digitalwill.verification.exception;

public class AlreadyConfirmedException extends RuntimeException {
    public AlreadyConfirmedException(String message) { super(message); }
}