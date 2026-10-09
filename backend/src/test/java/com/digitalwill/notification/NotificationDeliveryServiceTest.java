package com.digitalwill.notification;

import com.digitalwill.notification.config.NotificationProperties;
import com.digitalwill.notification.model.NotificationDeliveryResult;
import com.digitalwill.notification.service.NotificationDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class NotificationDeliveryServiceTest {

    @Test
    @DisplayName("WS3: Verification URL is constructed strictly from configured frontendBaseUrl")
    void buildVerificationUrl_usesConfiguredFrontendBaseUrl() {
        NotificationProperties props = new NotificationProperties();
        props.setFrontendBaseUrl("https://digitalwill.example.com");
        NotificationDeliveryService service = new NotificationDeliveryService(props);

        String url = service.buildVerificationUrl("secret-token-12345");
        assertThat(url).isEqualTo("https://digitalwill.example.com/verify?token=secret-token-12345");
        assertThat(url).doesNotContain("localhost");
    }

    @Test
    @DisplayName("WS3: Disclosure URL is constructed strictly from configured frontendBaseUrl")
    void buildDisclosureUrl_usesConfiguredFrontendBaseUrl() {
        NotificationProperties props = new NotificationProperties();
        props.setFrontendBaseUrl("https://digitalwill.example.com/");
        NotificationDeliveryService service = new NotificationDeliveryService(props);

        String url = service.buildDisclosureUrl("disclosure-token-abcde");
        assertThat(url).isEqualTo("https://digitalwill.example.com/disclosure/disclosure-token-abcde");
    }

    @Test
    @DisplayName("WS3: Recipient email masking protects PII in audit and log output")
    void maskEmail_masksSafely() {
        assertThat(NotificationDeliveryService.maskEmail("alice@example.com")).isEqualTo("a***e@example.com");
        assertThat(NotificationDeliveryService.maskEmail("bob.smith@company.org")).isEqualTo("b***h@company.org");
        assertThat(NotificationDeliveryService.maskEmail("invalid-email")).isEqualTo("***");
        assertThat(NotificationDeliveryService.maskEmail(null)).isEqualTo("***");
    }

    @Test
    @DisplayName("WS3: Disabled provider returns SKIPPED_UNCONFIGURED and does not claim delivered")
    void disabledProvider_returnsSkippedWithoutFalseSuccess() {
        NotificationProperties props = new NotificationProperties();
        props.setEnabled(false);
        props.setProvider("DISABLED");
        NotificationDeliveryService service = new NotificationDeliveryService(props);

        NotificationDeliveryResult result = service.doSendVerificationEmail(
                "contact@example.com", "Alice Contact", UUID.randomUUID(), "c***t@example.com", "https://app/verify?token=tok"
        );

        assertThat(result.delivered()).isFalse();
        assertThat(result.status()).isEqualTo("SKIPPED_UNCONFIGURED");
        assertThat(result.reason()).contains("disabled or unconfigured");
    }

    @Test
    @DisplayName("WS3: Console provider returns delivered without crashing")
    void consoleProvider_returnsDelivered() {
        NotificationProperties props = new NotificationProperties();
        props.setEnabled(true);
        props.setProvider("CONSOLE");
        NotificationDeliveryService service = new NotificationDeliveryService(props);

        NotificationDeliveryResult result = service.doSendDisclosureEmail(
                "beneficiary@example.com", "Bob Beneficiary", UUID.randomUUID(), "b***y@example.com", "https://app/disclosure/tok"
        );

        assertThat(result.delivered()).isTrue();
        assertThat(result.status()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("WS3: Configured external provider without required API key fails safely with clear status")
    void externalProviderWithoutApiKey_failsSafely() {
        NotificationProperties props = new NotificationProperties();
        props.setEnabled(true);
        props.setProvider("RESEND");
        props.setApiKey(null); // Missing API key
        NotificationDeliveryService service = new NotificationDeliveryService(props);

        NotificationDeliveryResult result = service.doSendVerificationEmail(
                "contact@example.com", "Charlie Contact", UUID.randomUUID(), "c***t@example.com", "https://app/verify?token=tok"
        );

        assertThat(result.delivered()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.reason()).contains("Missing provider API key");
    }

    @Test
    @DisplayName("WS3: Blank recipient email or token fails gracefully without throwing unhandled exceptions")
    void blankInputs_failGracefully() {
        NotificationProperties props = new NotificationProperties();
        NotificationDeliveryService service = new NotificationDeliveryService(props);

        NotificationDeliveryResult res1 = service.sendVerificationNotification(null, "Name", UUID.randomUUID(), "token");
        assertThat(res1.delivered()).isFalse();

        NotificationDeliveryResult res2 = service.sendDisclosureNotification("email@example.com", "Name", UUID.randomUUID(), " ");
        assertThat(res2.delivered()).isFalse();
    }
}
