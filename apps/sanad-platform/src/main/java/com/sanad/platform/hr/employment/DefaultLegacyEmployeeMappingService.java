package com.sanad.platform.hr.employment;

import java.util.UUID;

/**
 * Default implementation of {@link LegacyEmployeeMappingService}.
 *
 * <p>Classification rule (per HRM-G0 spec):
 * <ul>
 *   <li>Exactly one authoritative match → AUTO_MIGRATE</li>
 *   <li>Multiple plausible matches → MIGRATION_REVIEW_REQUIRED</li>
 *   <li>No authoritative match → MIGRATION_BLOCKED</li>
 * </ul>
 * </p>
 *
 * <p>NEVER guesses by fuzzy name/email/employee_number similarity.
 * Authoritative means an unambiguous deterministic external_id or
 * prelinked UUID.</p>
 *
 * <p>Task 2 RED skeleton: method throws UnsupportedOperationException.
 * GREEN replaces with real classification logic.</p>
 */
public final class DefaultLegacyEmployeeMappingService implements LegacyEmployeeMappingService {

    @Override
    public LegacyMappingClassification classify(UUID tenantId, UUID legacyEmployeeId) {
        throw new UnsupportedOperationException(
                "DefaultLegacyEmployeeMappingService.classify — Task 2 RED skeleton, implement in GREEN");
    }
}
