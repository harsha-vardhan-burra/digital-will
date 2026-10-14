package com.digitalwill.common.validation;

/**
 * Utility for safely masking sensitive account numbers, serials, and identifiers
 * so that full numbers are not exposed in logs or UI summaries.
 */
public final class IdentifierMasker {

    private IdentifierMasker() {}

    /**
     * Masks an identifier, displaying only the last 4 characters if long enough.
     */
    public static String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        int len = trimmed.length();
        if (len <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(len - 4);
    }
}
