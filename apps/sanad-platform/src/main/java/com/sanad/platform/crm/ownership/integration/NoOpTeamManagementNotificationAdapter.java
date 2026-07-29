package com.sanad.platform.crm.ownership.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * No-op notification adapter for CRM-008 Team Management.
 *
 * <p>Logs notifications instead of sending them. Used in development
 * and testing environments. Replace with a real notification adapter
 * for production.
 */
public class NoOpTeamManagementNotificationAdapter implements TeamManagementNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpTeamManagementNotificationAdapter.class);

    @Override
    public void send(UUID tenantId,
                     String notificationType,
                     UUID recipientUserId,
                     String subjectType,
                     UUID subjectId,
                     Map<String, Object> payload,
                     Instant occurredAt) {
        log.info("CRM-008 Notification: type={} tenant={} recipient={} subjectType={} subjectId={} occurredAt={}",
                notificationType, tenantId, recipientUserId, subjectType, subjectId, occurredAt);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
