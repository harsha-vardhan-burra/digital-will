package com.digitalwill.audit.exception;

public class AuditPersistenceException extends RuntimeException {
    public AuditPersistenceException(String message) {
        super(message);
    }

    public AuditPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
