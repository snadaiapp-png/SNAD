package com.sanad.platform.hr.api.v2.dto;

import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 — typed lifecycle transition result projection
 * (closed/opened status period identifiers only; no audit internals).
 */
public record LifecycleCommandResponse(
        UUID employmentId,
        String previousStatus,
        String newStatus,
        UUID closedPeriodId,
        UUID newPeriodId
) {
}
