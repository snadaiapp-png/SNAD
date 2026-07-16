package com.sanad.platform.crm.query.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only Customer 360 query port.
 * Returns typed read models — never writes.
 */
public interface Customer360QueryPort {
    Customer360View getCustomer360(UUID tenantId, UUID accountId);

    record Customer360View(
            UUID accountId,
            String displayName,
            String accountType,
            String lifecycleStatus,
            int contactCount,
            int opportunityCount,
            int activityCount,
            int timelineEventCount,
            List<ContactSummary> contacts,
            List<OpportunitySummary> opportunities,
            List<ActivitySummary> activities) {}

    record ContactSummary(
            UUID id,
            String displayName,
            String primaryEmail,
            String lifecycleStatus,
            UUID relationshipId,
            String relationshipRole,
            String relationshipStatus,
            boolean primaryRelationship,
            LocalDate validFrom,
            LocalDate validTo,
            String jobTitle,
            String department) {}

    record OpportunitySummary(UUID id, String name, java.math.BigDecimal amount, String currencyCode, String status) {}
    record ActivitySummary(UUID id, String activityType, String subject, String status) {}
}
