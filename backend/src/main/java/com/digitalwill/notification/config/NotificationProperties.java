package com.digitalwill.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for email/link notification delivery for verification and disclosure (Workstream 3).
 */
@Configuration
@ConfigurationProperties(prefix = "digitalwill.notifications")
public class NotificationProperties {

    /**
     * Whether notification delivery is actively enabled.
     * Default is false: system operates in disabled/unconfigured mode without sending real emails.
     */
    private boolean enabled = false;

    /**
     * Delivery provider: DISABLED, CONSOLE, or RESEND / HTTP_API.
     */
    private String provider = "DISABLED";

    /**
     * From address for automated notifications.
     */
    private String fromEmail = "notifications@digitalwill.local";

    /**
     * Public frontend base URL used for link construction (e.g. http://localhost:3000).
     * Must be configured explicitly and never derived from untrusted client Host headers.
     */
    private String frontendBaseUrl = "http://localhost:3000";

    /**
     * Optional API key or secret for external delivery providers (e.g., Resend API key).
     */
    private String apiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
