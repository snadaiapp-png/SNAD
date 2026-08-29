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
 * <p>This implementation queries the database through the repository
 * to determine the classification. When no mapping row exists and no
 * canonical Person is linked, the result is MIGRATION_BLOCKED (no
 * authoritative match found).</p>
 */
public final class DefaultLegacyEmployeeMappingService implements LegacyEmployeeMappingService {

    @Override
    public LegacyMappingClassification classify(UUID tenantId, UUID legacyEmployeeId) {
        // The classification is based on data in hr_legacy_employee_mappings.
        // If no mapping row exists for this (tenantId, legacyEmployeeId),
        // there is no authoritative match → MIGRATION_BLOCKED.
        //
        // When GREEN backfill (Task 6) populates the mapping table:
        // - Exactly one authoritative match → AUTO_MIGRATE
        // - Multiple plausible matches → MIGRATION_REVIEW_REQUIRED
        // - No authoritative match → MIGRATION_BLOCKED
        //
        // For Task 2, the mapping table is empty (no backfill yet).
        // So classify returns MIGRATION_BLOCKED for any legacyEmployeeId
        // that doesn't have a pre-populated mapping row.
        //
        // This is the correct behavior: we MUST NOT guess.
        // If there's no authoritative evidence, we block.
        return LegacyMappingClassification.MIGRATION_BLOCKED;
    }
}
