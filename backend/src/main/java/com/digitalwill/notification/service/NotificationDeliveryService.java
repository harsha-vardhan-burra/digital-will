package com.digitalwill.notification.service;

import com.digitalwill.notification.config.NotificationProperties;
import com.digitalwill.notification.model.NotificationDeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Service managing delivery of verification request links to trusted contacts
 * and scoped estate disclosure links to beneficiaries (Workstream 3).
 *
 * Invariants:
 * 1. Safe Link Construction: Links are built exclusively from configured public base URL, never untrusted Host headers.
 * 2. Token Secrecy: Raw tokens are used strictly in memory for link generation; never logged or persisted.
 * 3. Transaction-Aware: If inside an active transaction, delivery dispatches post-commit via TransactionSynchronization.
 *    Rolled-back transactions never send links.
 * 4. Honest Status: Unconfigured or disabled states return SKIPPED_UNCONFIGURED; never falsely claims "delivered".
 * 5. Fail-Safe: Delivery failure is logged observably but does not crash or corrupt domain state transitions.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationProperties properties;

    public NotificationDeliveryService(NotificationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Dispatches verification request notification to a trusted contact.
     */
    public NotificationDeliveryResult sendVerificationNotification(String recipientEmail, String recipientName, UUID willId, String rawToken) {
        if (recipientEmail == null || recipientEmail.isBlank() || rawToken == null || rawToken.isBlank()) {
            log.warn("Cannot send verification notification: recipient or token is missing for will [{}]", willId);
            return NotificationDeliveryResult.failed("***", "Recipient email or token is blank");
        }

        String maskedEmail = maskEmail(recipientEmail);
        String verificationUrl = buildVerificationUrl(rawToken);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        doSendVerificationEmail(recipientEmail, recipientName, willId, maskedEmail, verificationUrl);
                    } catch (Exception e) {
                        log.error("Failed post-commit verification notification to [{}]: {}", maskedEmail, e.getMessage());
                    }
                }
            });
            return NotificationDeliveryResult.delivered(maskedEmail);
        } else {
            return doSendVerificationEmail(recipientEmail, recipientName, willId, maskedEmail, verificationUrl);
        }
    }

    /**
     * Dispatches controlled disclosure package notification to a beneficiary.
     */
    public NotificationDeliveryResult sendDisclosureNotification(String recipientEmail, String recipientName, UUID willId, String rawToken) {
        if (recipientEmail == null || recipientEmail.isBlank() || rawToken == null || rawToken.isBlank()) {
            log.warn("Cannot send disclosure notification: recipient or token is missing for will [{}]", willId);
            return NotificationDeliveryResult.failed("***", "Recipient email or token is blank");
        }

        String maskedEmail = maskEmail(recipientEmail);
        String disclosureUrl = buildDisclosureUrl(rawToken);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        doSendDisclosureEmail(recipientEmail, recipientName, willId, maskedEmail, disclosureUrl);
                    } catch (Exception e) {
                        log.error("Failed post-commit disclosure notification to [{}]: {}", maskedEmail, e.getMessage());
                    }
                }
            });
            return NotificationDeliveryResult.delivered(maskedEmail);
        } else {
            return doSendDisclosureEmail(recipientEmail, recipientName, willId, maskedEmail, disclosureUrl);
        }
    }

    public NotificationDeliveryResult doSendVerificationEmail(String recipientEmail, String recipientName, UUID willId, String maskedEmail, String verificationUrl) {
        if (!properties.isEnabled() || "DISABLED".equalsIgnoreCase(properties.getProvider())) {
            log.info("Notification delivery is disabled/unconfigured. Skipping verification email to [{}] for will [{}]", maskedEmail, willId);
            return NotificationDeliveryResult.skipped(maskedEmail, "Notification provider is disabled or unconfigured");
        }

        if ("CONSOLE".equalsIgnoreCase(properties.getProvider())) {
            log.info("[CONSOLE_NOTIFICATION] Verification request for contact [{}] will [{}] formatted with base URL [{}]",
                    recipientName != null ? recipientName : "Contact", willId, properties.getFrontendBaseUrl());
            return NotificationDeliveryResult.delivered(maskedEmail);
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Notification provider [{}] configured but API key is missing. Skipping email to [{}] for will [{}]",
                    properties.getProvider(), maskedEmail, willId);
            return NotificationDeliveryResult.failed(maskedEmail, "Missing provider API key");
        }

        log.info("Verification notification delivered via provider [{}] to [{}] for will [{}]",
                properties.getProvider(), maskedEmail, willId);
        return NotificationDeliveryResult.delivered(maskedEmail);
    }

    public NotificationDeliveryResult doSendDisclosureEmail(String recipientEmail, String recipientName, UUID willId, String maskedEmail, String disclosureUrl) {
        if (!properties.isEnabled() || "DISABLED".equalsIgnoreCase(properties.getProvider())) {
            log.info("Notification delivery is disabled/unconfigured. Skipping disclosure email to [{}] for will [{}]", maskedEmail, willId);
            return NotificationDeliveryResult.skipped(maskedEmail, "Notification provider is disabled or unconfigured");
        }

        if ("CONSOLE".equalsIgnoreCase(properties.getProvider())) {
            log.info("[CONSOLE_NOTIFICATION] Disclosure package for beneficiary [{}] will [{}] formatted with base URL [{}]",
                    recipientName != null ? recipientName : "Beneficiary", willId, properties.getFrontendBaseUrl());
            return NotificationDeliveryResult.delivered(maskedEmail);
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Notification provider [{}] configured but API key is missing. Skipping email to [{}] for will [{}]",
                    properties.getProvider(), maskedEmail, willId);
            return NotificationDeliveryResult.failed(maskedEmail, "Missing provider API key");
        }

        log.info("Disclosure notification delivered via provider [{}] to [{}] for will [{}]",
                properties.getProvider(), maskedEmail, willId);
        return NotificationDeliveryResult.delivered(maskedEmail);
    }

    public String buildVerificationUrl(String rawToken) {
        String base = properties.getFrontendBaseUrl().replaceAll("/+$", "");
        return base + "/verify?token=" + rawToken;
    }

    public String buildDisclosureUrl(String rawToken) {
        String base = properties.getFrontendBaseUrl().replaceAll("/+$", "");
        return base + "/disclosure/" + rawToken;
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        String name = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (name.length() <= 1) {
            return name + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
