package com.digitalwill.notification.model;

/**
 * Result of a notification delivery attempt.
 * Ensures delivery state is observable without falsely claiming "delivered" when skipped or failed.
 */
public record NotificationDeliveryResult(
        boolean delivered,
        String status, // DELIVERED, SKIPPED_UNCONFIGURED, FAILED
        String recipientMasked,
        String reason
) {
    public static NotificationDeliveryResult delivered(String recipientMasked) {
        return new NotificationDeliveryResult(true, "DELIVERED", recipientMasked, null);
    }

    public static NotificationDeliveryResult skipped(String recipientMasked, String reason) {
        return new NotificationDeliveryResult(false, "SKIPPED_UNCONFIGURED", recipientMasked, reason);
    }

    public static NotificationDeliveryResult failed(String recipientMasked, String reason) {
        return new NotificationDeliveryResult(false, "FAILED", recipientMasked, reason);
    }
}
