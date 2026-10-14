package com.digitalwill.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeUrlValidatorTest {

    @Test
    @DisplayName("Null or blank URLs pass validation (optional field)")
    void nullOrBlankUrlPasses() {
        assertThatCode(() -> SafeUrlValidator.validateUrl(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> SafeUrlValidator.validateUrl("  "))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.chase.com",
            "https://login.fidelity.com/ftgw/fidelity/login",
            "https://accounts.google.com",
            "http://example.com/asset-info"
    })
    @DisplayName("Valid public HTTPS and HTTP URLs pass validation")
    void validPublicUrlsPass(String url) {
        assertThatCode(() -> SafeUrlValidator.validateUrl(url))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://ftp.example.com/files",
            "file:///etc/passwd",
            "javascript:alert(document.cookie)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "gopher://evil.com"
    })
    @DisplayName("Unsupported URL schemes are rejected")
    void unsupportedSchemesAreRejected(String url) {
        assertThatThrownBy(() -> SafeUrlValidator.validateUrl(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only HTTPS (or HTTP) URLs are permitted");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1",
            "http://127.0.0.1:8080/admin",
            "http://localhost",
            "http://localhost:3000/internal",
            "https://127.0.1.1/secret"
    })
    @DisplayName("Loopback addresses are rejected to prevent SSRF")
    void loopbackUrlsAreRejected(String url) {
        assertThatThrownBy(() -> SafeUrlValidator.validateUrl(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://10.0.0.1/admin",
            "http://192.168.1.1/router",
            "http://172.16.0.5/api",
            "http://172.31.255.255/intranet"
    })
    @DisplayName("Private RFC1918 addresses are rejected to prevent SSRF")
    void privateSubnetUrlsAreRejected(String url) {
        assertThatThrownBy(() -> SafeUrlValidator.validateUrl(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/",
            "https://169.254.1.1/metadata",
            "http://instance-data/latest/meta-data/"
    })
    @DisplayName("Cloud metadata and link-local addresses are rejected to prevent SSRF")
    void metadataAndLinkLocalUrlsAreRejected(String url) {
        assertThatThrownBy(() -> SafeUrlValidator.validateUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("URLs exceeding maximum length bound are rejected")
    void excessivelyLongUrlIsRejected() {
        String longUrl = "https://example.com/" + "a".repeat(2050);
        assertThatThrownBy(() -> SafeUrlValidator.validateUrl(longUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum length");
    }
}
