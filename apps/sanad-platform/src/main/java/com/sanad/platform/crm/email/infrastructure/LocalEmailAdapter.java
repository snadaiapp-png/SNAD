package com.sanad.platform.crm.email.infrastructure;

import com.sanad.platform.crm.email.domain.EmailMessage;
import com.sanad.platform.crm.email.domain.EmailPort;
import com.sanad.platform.crm.email.domain.EmailSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Local no-op adapter for CRM email delivery.
 * <p>
 * Silently discards all email send requests. Used in local development
 * and test profiles to avoid external API calls.
 * Activated when {@code snad.crm.email.provider=local}.
 */
@Component
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "snad.crm.email", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(LocalEmailAdapter.class);

    @Override
    public EmailSendResult send(UUID tenantId, EmailMessage message) {
        String toAddresses = message.to().stream()
                .map(a -> a.value())
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown");

        log.info("[LOCAL EMAIL] tenant={}, to={}, subject={}", tenantId, toAddresses, message.subject());
        return EmailSendResult.success(null, "local-" + UUID.randomUUID(), "local");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String providerName() {
        return "local";
    }
}
