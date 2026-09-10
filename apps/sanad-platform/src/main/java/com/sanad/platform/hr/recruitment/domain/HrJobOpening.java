package com.sanad.platform.hr.recruitment.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G1 — HrJobOpening aggregate root (design §5.1, §6.1).
 *
 * <p>Pure domain value: identity is the (id, tenant_id) tuple; lifecycle
 * transitions are decided ONLY by {@link HrJobOpeningTransitions}; the
 * {@code filled_headcount} field is derived exclusively from hire-conversion
 * linkage (§7) and has no domain mutator by design.</p>
 */
public record HrJobOpening(
        UUID id,
        UUID tenantId,
        String openingNumber,
        UUID jobId,
        UUID jobVersionId,
        UUID orgUnitId,
        UUID positionId,
        HrJobOpeningState state,
        int requestedHeadcount,
        int filledHeadcount,
        String complianceDecision,
        OffsetDateTime complianceDecidedAt,
        OffsetDateTime opensAt,
        OffsetDateTime closesAt,
        long version) {

    public HrJobOpening {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(state, "state");
        if (requestedHeadcount < 1) {
            throw new IllegalArgumentException("HRM_OPENING_HEADCOUNT_INVALID: requested headcount must be >= 1");
        }
        if (opensAt != null && closesAt != null && closesAt.isBefore(opensAt)) {
            throw new IllegalArgumentException("HRM_OPENING_WINDOW_INVALID: closes_at must not precede opens_at");
        }
    }

    /** Factory for a new DRAFT opening (version 0, no compliance decision yet). */
    public static HrJobOpening draft(
            UUID tenantId, UUID jobId, UUID jobVersionId, UUID orgUnitId, UUID positionId,
            int requestedHeadcount, OffsetDateTime opensAt, OffsetDateTime closesAt) {
        return new HrJobOpening(UUID.randomUUID(), tenantId, null, jobId, jobVersionId, orgUnitId,
                positionId, HrJobOpeningState.DRAFT, requestedHeadcount, 0,
                null, null, opensAt, closesAt, 0L);
    }
}
