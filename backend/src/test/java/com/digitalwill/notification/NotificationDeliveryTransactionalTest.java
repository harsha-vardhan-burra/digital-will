package com.digitalwill.notification;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.notification.config.NotificationProperties;
import com.digitalwill.notification.service.NotificationDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestTimeConfig.class)
public class NotificationDeliveryTransactionalTest {

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired NotificationProperties notificationProperties;

    @Test
    @DisplayName("WS3: Rolled-back database transaction does NOT dispatch notification")
    void transactionRollback_doesNotDispatchNotification() {
        AtomicBoolean dispatched = new AtomicBoolean(false);

        // Custom subclass to track invocation
        NotificationDeliveryService service = new NotificationDeliveryService(notificationProperties) {
            @Override
            public com.digitalwill.notification.model.NotificationDeliveryResult doSendVerificationEmail(
                    String recipientEmail, String recipientName, UUID willId, String maskedEmail, String verificationUrl) {
                dispatched.set(true);
                return super.doSendVerificationEmail(recipientEmail, recipientName, willId, maskedEmail, verificationUrl);
            }
        };

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        try {
            tx.execute(status -> {
                service.sendVerificationNotification("test@example.com", "Tester", UUID.randomUUID(), "token-123");
                // Explicitly rollback transaction
                status.setRollbackOnly();
                return null;
            });
        } catch (Exception ignored) {}

        // Notification must NOT have been dispatched
        assertThat(dispatched.get()).isFalse();
    }

    @Test
    @DisplayName("WS3: Committed database transaction dispatches notification post-commit")
    void transactionCommit_dispatchesNotificationPostCommit() {
        AtomicBoolean dispatched = new AtomicBoolean(false);

        NotificationDeliveryService service = new NotificationDeliveryService(notificationProperties) {
            @Override
            public com.digitalwill.notification.model.NotificationDeliveryResult doSendVerificationEmail(
                    String recipientEmail, String recipientName, UUID willId, String maskedEmail, String verificationUrl) {
                dispatched.set(true);
                return super.doSendVerificationEmail(recipientEmail, recipientName, willId, maskedEmail, verificationUrl);
            }
        };

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.execute(status -> {
            service.sendVerificationNotification("test@example.com", "Tester", UUID.randomUUID(), "token-123");
            return null; // Commits successfully
        });

        // Notification must have been dispatched
        assertThat(dispatched.get()).isTrue();
    }
}
