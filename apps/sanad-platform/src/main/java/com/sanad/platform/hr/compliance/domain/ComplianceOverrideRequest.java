package com.sanad.platform.hr.compliance.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Governed compliance override aggregate (WS3 Task 4). Backed by the
 * {@code hr_compliance_override_requests} table created in WS3 Task 1.
 *
 * <p>An override never modifies the authoritative compliance rule; it is a
 * tenant-scoped, time-bounded, four-eyes-approved legal exception with
 * redacted requested/compliant value snapshots.</p>
 */
public record ComplianceOverrideRequest(
        UUID id,
        UUID tenantId,
        UUID complianceRuleId,
        String resourceType,
        UUID resourceId,
        JsonNode requestedValueRedacted,
        JsonNode compliantValueRedacted,
        UUID requesterUserId,
        String justification,
        String evidenceReference,
        UUID approvedBy,
        String approvalComment,
        LocalDate validFrom,
        LocalDate validUntil,
        ComplianceOverrideStatus status,
        Instant executedAt,
        String auditReference,
        Instant createdAt,
        Instant updatedAt) {
}
