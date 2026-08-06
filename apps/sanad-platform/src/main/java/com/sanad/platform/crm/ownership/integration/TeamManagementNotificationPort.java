package com.sanad.platform.crm.ownership.integration;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Port for sending CRM-008 Team Management notifications.
 *
 * <p>This is an outbound port that must be implemented by the notification
 * adapter. CRM-008 UseCases call this port to send notifications.
 */
public interface TeamManagementNotificationPort {

    /**
     * Send a notification.
     *
     * @param tenantId the tenant scope
     * @param notificationType the notification type from TeamManagementNotificationTypes
     * @param recipientUserId the user to notify
     * @param subjectType the entity type (e.g., CRM_SHIFT_ASSIGNMENT)
     * @param subjectId the entity ID
     * @param payload additional notification data
     * @param occurredAt when the event occurred
     */
    void send(UUID tenantId,
              String notificationType,
              UUID recipientUserId,
              String subjectType,
              UUID subjectId,
              Map<String, Object> payload,
              Instant occurredAt);

    /**
     * Check if notifications are enabled.
     */
    boolean isEnabled();
}
