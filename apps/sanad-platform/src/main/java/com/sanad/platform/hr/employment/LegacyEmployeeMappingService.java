package com.sanad.platform.hr.employment;

import java.util.UUID;

/**
 * Legacy employee mapping evaluator — classifies a legacy employee
 * reference into one of {@link LegacyMappingClassification}.
 *
 * <p>Per HRM-G0 spec:
 * <ul>
 *   <li>Exactly one authoritative match → AUTO_MIGRATE</li>
 *   <li>Multiple plausible matches → MIGRATION_REVIEW_REQUIRED</li>
 *   <li>No authoritative match → MIGRATION_BLOCKED</li>
 * </ul>
 * </p>
 *
 * <p>NEVER guess by fuzzy similarity (name/email/employee_number/Saudi/GCC
 * assumptions). Authoritative means unambiguous deterministic external_id
 * or prelinked UUID.</p>
 *
 * <p>Task 2 RED skeleton: methods throw UnsupportedOperationException.
 * GREEN replaces with real classification logic backed by
 * hr_legacy_employee_mappings + hr_migration_review_items.</p>
 */
public interface LegacyEmployeeMappingService {

    /**
     * Classify a legacy employee reference into a migration action.
     *
     * @param tenantId       the tenant scope
     * @param legacyEmployeeId the legacy hr_employees.id
     * @return classification: AUTO_MIGRATE / MIGRATION_REVIEW_REQUIRED / MIGRATION_BLOCKED
     */
    LegacyMappingClassification classify(UUID tenantId, UUID legacyEmployeeId);
}
