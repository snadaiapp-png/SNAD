package com.sanad.platform.hr.compliance.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Immutable input bundle handed to a {@link ComplianceRuleHandler}. */
public record ComplianceEvaluationContext(
        UUID tenantId,
        UUID employmentId,
        UUID actorUserId,
        String operationCode,
        ComplianceOperationType operationType,
        LocalDate effectiveDate,
        String laborJurisdiction,
        CountryOperatingMode operatingMode,
        String packCode,
        String packVersion,
        String workerClassification,
        String resourceType,
        UUID resourceId) {
}
