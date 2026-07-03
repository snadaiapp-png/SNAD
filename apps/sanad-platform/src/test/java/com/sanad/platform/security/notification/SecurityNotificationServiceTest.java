package com.sanad.platform.security.notification;

import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityNotificationServiceTest {

    @Test
    void resetMessageContainsSingleUseLinkButNeverContainsPassword() {
        CapturingGateway gateway = new CapturingGateway();
        SecurityNotificationProperties properties = new SecurityNotificationProperties();
        properties.setApplicationBaseUrl("https://app.example");
        SecurityNotificationService service = new SecurityNotificationService(gateway, properties);
        User user = new User(UUID.randomUUID(), "owner@example.com", "Owner", UserStatus.ACTIVE);

        service.deliverResetLink(user, "single-use-token", "ar", true);

        assertTrue(gateway.message.getTextBody().contains("single-use-token"));
        assertTrue(gateway.message.getSubject().contains("كلمة مرور"));
        assertFalse(gateway.message.getTextBody().contains("newPassword"));
        assertFalse(gateway.message.getHtmlBody().contains("credential="));
    }

    @Test
    void passwordChangedMessageContainsNoResetToken() {
        CapturingGateway gateway = new CapturingGateway();
        SecurityNotificationService service = new SecurityNotificationService(gateway, new SecurityNotificationProperties());
        User user = new User(UUID.randomUUID(), "owner@example.com", "Owner", UserStatus.ACTIVE);

        service.deliverPasswordChanged(user, "en");

        assertTrue(gateway.message.getSubject().contains("SNAD"));
        assertFalse(gateway.message.getTextBody().contains("token="));
    }

    private static final class CapturingGateway implements SecurityNotificationGateway {
        private SecurityMessage message;
        @Override
        public void deliver(SecurityMessage message) { this.message = message; }
    }
}
