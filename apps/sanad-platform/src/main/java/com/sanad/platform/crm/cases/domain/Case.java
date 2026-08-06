package com.sanad.platform.crm.cases.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Case / Ticket domain entity — first-class CRM work item for customer support.
 * <p>
 * Cases track customer issues through a lifecycle:
 * {@code OPEN → IN_PROGRESS → RESOLVED → CLOSED}.
 * A CLOSED case may be reopened back to IN_PROGRESS.
 * <p>
 * Every case is tenant-scoped and carries optimistic concurrency via {@code version}.
 */
public record Case(
        UUID id,
        UUID tenantId,
        long version,
        String subject,
        String description,
        String caseType,
        String status,
        int priority,
        UUID customerId,
        UUID assigneeUserId,
        UUID ownerUserId,
        String relatedType,
        UUID relatedId,
        OffsetDateTime dueAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime closedAt,
        Instant createdAt,
        Instant updatedAt
) {
    /** Compact constructor with validation. */
    public Case {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject must not be blank");
        if (subject.length() > 240) throw new IllegalArgumentException("subject must not exceed 240 characters");
        if (status == null) status = CaseStatus.OPEN;
        if (priority < 0 || priority > 100) throw new IllegalArgumentException("priority must be between 0 and 100");
    }

    /** Status transition check: can this case be started? */
    public boolean canStart() {
        return CaseStatus.OPEN.equals(status);
    }

    /** Status transition check: can this case be resolved? */
    public boolean canResolve() {
        return CaseStatus.OPEN.equals(status) || CaseStatus.IN_PROGRESS.equals(status);
    }

    /** Status transition check: can this case be closed? */
    public boolean canClose() {
        return CaseStatus.RESOLVED.equals(status);
    }

    /** Status transition check: can this case be reopened? */
    public boolean canReopen() {
        return CaseStatus.CLOSED.equals(status);
    }
}
