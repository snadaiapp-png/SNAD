package com.sanad.platform.security.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local & !test")
@ConditionalOnProperty(
        prefix = "snad.security.notifications",
        name = "provider",
        havingValue = "disabled",
        matchIfMissing = true
)
public class DisabledSecurityNotificationGateway implements SecurityNotificationGateway {
    @Override
    public void deliver(SecurityMessage message) {
        throw new IllegalStateException("Security notification delivery is disabled");
    }
}
