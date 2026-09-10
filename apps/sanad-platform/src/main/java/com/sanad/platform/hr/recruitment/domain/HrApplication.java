package com.sanad.platform.hr.recruitment.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G1 — HrApplication aggregate root (design §5.1, §6.2) + its
 * append-only stage-period history row (§5.2).
 *
 * <p>The Candidate↔JobOpening edge. ONE active application per
 * (tenant, candidate, opening) — re-application after a terminal state is a
 * NEW row (history preserved). The HIRED state is reachable ONLY inside the
 * §7 hire conversion transaction (T8): the T5 service exposes no path to it.</p>
 */
public record HrApplication(
        UUID id,
        UUID tenantId,
        UUID candidateId,
        UUID jobOpeningId,
        HrApplicationState state,
        long version) {

    public HrApplication {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(jobOpeningId, "jobOpeningId");
        Objects.requireNonNull(state, "state");
    }

    /** The application's pipeline stage (same name as its state by design §5.1). */
    public String stage() {
        return state.name();
    }

    /** Append-only pipeline history row (immutable by convention; no UPDATE path). */
    public record StagePeriod(
            UUID id,
            UUID tenantId,
            UUID applicationId,
            String stage,
            OffsetDateTime fromAt) {
    }
}
