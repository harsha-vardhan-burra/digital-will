package com.digitalwill.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "digitalwill.ratelimit")
public class RateLimitProperties {

    /**
     * Whether rate limiting is globally active.
     */
    private boolean enabled = true;

    /**
     * Maximum login / register attempts per sliding window.
     */
    private int authLimit = 5;

    /**
     * Maximum verification confirmation attempts per sliding window.
     */
    private int verificationLimit = 10;

    /**
     * Maximum disclosure token access attempts per sliding window.
     */
    private int disclosureLimit = 10;

    /**
     * Sliding window duration in seconds.
     */
    private long windowSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAuthLimit() {
        return authLimit;
    }

    public void setAuthLimit(int authLimit) {
        this.authLimit = authLimit;
    }

    public int getVerificationLimit() {
        return verificationLimit;
    }

    public void setVerificationLimit(int verificationLimit) {
        this.verificationLimit = verificationLimit;
    }

    public int getDisclosureLimit() {
        return disclosureLimit;
    }

    public void setDisclosureLimit(int disclosureLimit) {
        this.disclosureLimit = disclosureLimit;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
