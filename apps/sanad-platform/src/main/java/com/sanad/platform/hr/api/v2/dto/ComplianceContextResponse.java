package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.compliance.domain.ResolvedCountryPolicy;

/** HRM-G0 / WS5 Task 5 — resolved compliance mode metadata (no employee PII). */
public record ComplianceContextResponse(
        String laborJurisdiction,
        String mode,
        String packCode,
        String packVersion,
        String workerClassification,
        String effectiveDate
) {

    public static ComplianceContextResponse from(ResolvedCountryPolicy policy) {
        return new ComplianceContextResponse(policy.laborJurisdiction(),
                policy.mode() == null ? null : policy.mode().name(),
                policy.packCode(), policy.packVersion(), policy.workerClassification(),
                policy.effectiveDate() == null ? null : policy.effectiveDate().toString());
    }
}
