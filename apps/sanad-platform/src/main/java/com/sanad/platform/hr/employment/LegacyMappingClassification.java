package com.sanad.platform.hr.employment;

import java.util.UUID;

/**
 * Legacy employee mapping classification result.
 *
 * <p>Migration mapping rule (per HRM-G0 spec):
 * <ul>
 *   <li>{@link #AUTO_MIGRATE} — exactly one authoritative match found</li>
 *   <li>{@link #MIGRATION_REVIEW_REQUIRED} — multiple plausible matches</li>
 *   <li>{@link #MIGRATION_BLOCKED} — no authoritative match</li>
 * </ul>
 * </p>
 *
 * <p>The mapping MUST NOT guess by name/email/employee_number similarity.
 * Authoritative match means an unambiguous identifier (e.g., shared
 * deterministic external_id, prelinked UUID) — not fuzzy similarity.</p>
 */
public enum LegacyMappingClassification {
    AUTO_MIGRATE,
    MIGRATION_REVIEW_REQUIRED,
    MIGRATION_BLOCKED
}
