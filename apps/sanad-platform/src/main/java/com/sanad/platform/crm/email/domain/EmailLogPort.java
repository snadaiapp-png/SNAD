package com.sanad.platform.crm.email.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Email log port — bounded context for email audit trail and tracking.
 * <p>
 * Provides CRUD operations for email log entries and tracking events.
 * All methods are tenant-scoped.
 */
public interface EmailLogPort {

    /**
     * Create a new email log entry before sending.
     *
     * @param tenantId  the tenant scope
     * @param entry     the log entry to create
     * @return the created entry with assigned ID
     */
    EmailLogEntry create(UUID tenantId, EmailLogEntry entry);

    /**
     * Update an existing email log entry after sending.
     *
     * @param tenantId  the tenant scope
     * @param entry     the updated entry
     */
    void update(UUID tenantId, EmailLogEntry entry);

    /**
     * Find an email log entry by ID.
     *
     * @param tenantId  the tenant scope
     * @param logId     the log entry ID
     * @return the entry, or null if not found
     */
    EmailLogEntry findById(UUID tenantId, UUID logId);

    /**
     * Find email log entries by related entity.
     *
     * @param tenantId         the tenant scope
     * @param relatedEntityType the entity type (e.g., "case", "lead")
     * @param relatedEntityId   the entity ID
     * @return matching log entries
     */
    List<EmailLogEntry> findByRelatedEntity(UUID tenantId, String relatedEntityType, String relatedEntityId);

    /**
     * List all email log entries for a tenant, ordered by most recent.
     *
     * @param tenantId the tenant scope
     * @param limit    max results
     * @return log entries
     */
    List<EmailLogEntry> findAll(UUID tenantId, int limit);

    /**
     * Record an email open event.
     *
     * @param tenantId  the tenant scope
     * @param logId     the email log entry ID
     * @param openedAt  when the email was opened
     */
    void recordOpen(UUID tenantId, UUID logId, Instant openedAt);

    /**
     * Record a click event.
     *
     * @param tenantId  the tenant scope
     * @param logId     the email log entry ID
     * @param url       the URL that was clicked
     * @param clickedAt when the click occurred
     */
    void recordClick(UUID tenantId, UUID logId, String url, Instant clickedAt);

    /**
     * Find an email log entry by ID without tenant scope.
     * Used by tracking endpoints that have no authenticated context.
     *
     * @param logId the email log entry ID (globally unique)
     * @return the entry, or null if not found
     */
    EmailLogEntry findByLogId(UUID logId);

    /**
     * Email log entry record.
     */
    record EmailLogEntry(
            UUID id,
            UUID tenantId,
            UUID userId,
            String fromAddress,
            String toAddress,
            String subject,
            String status,
            String provider,
            String providerMessageId,
            String relatedEntityType,
            String relatedEntityId,
            String templateName,
            Instant sentAt,
            Instant openedAt,
            Instant clickedAt,
            String clickUrl,
            String errorMessage,
            Instant createdAt
    ) {}
}
