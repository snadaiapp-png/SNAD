package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.compliance.domain.ComplianceOverrideRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — override request view. Only REDACTED value nodes are
 * carried (the persistence layer stores redacted JSON by contract).
 */
public record OverrideRequestResponse(
        UUID requestId,
        UUID complianceRuleId,
        String resourceType,
        UUID resourceId,
        UUID requesterUserId,
        String justification,
        String evidenceReference,
        UUID approvedBy,
        String approvalComment,
        LocalDate validFrom,
        LocalDate validUntil,
        String status,
        Instant executedAt
) {

    public static OverrideRequestResponse from(ComplianceOverrideRequest r) {
        return new OverrideRequestResponse(r.id(), r.complianceRuleId(), r.resourceType(), r.resourceId(),
                r.requesterUserId(), r.justification(), r.evidenceReference(), r.approvedBy(),
                r.approvalComment(), r.validFrom(), r.validUntil(),
                r.status() == null ? null : r.status().name(), r.executedAt());
    }
}
