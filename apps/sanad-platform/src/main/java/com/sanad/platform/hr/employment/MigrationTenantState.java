package com.sanad.platform.hr.employment;

/**
 * Migration tenant state for HRM-G0 WS2 cutover governance.
 *
 * <p>States:
 * <ul>
 *   <li>{@link #LEGACY} — tenant not yet started migration; legacy model authoritative</li>
 *   <li>{@link #MIGRATING} — Task 2 schema in place; backfill in progress</li>
 *   <li>{@link #CANONICAL} — backfill complete; canonical model authoritative</li>
 *   <li>{@link #BLOCKED} — unresolved migration review items prevent cutover</li>
 * </ul>
 * </p>
 */
public enum MigrationTenantState {
    LEGACY,
    MIGRATING,
    CANONICAL,
    BLOCKED
}
