package com.sanad.platform.module.lifecycle;

/**
 * Classification of data within a module's lifecycle.
 *
 * <p>Every table owned by a module is classified as one of:
 * <ul>
 *   <li>{@link #RESETTABLE} — operational data that can be safely deleted during module reset</li>
 *   <li>{@link #PROTECTED} — identity/financial/audit data that MUST NEVER be deleted</li>
 *   <li>{@link #ARCHIVABLE} — historical data that may be archived instead of deleted (future)</li>
 * </ul>
 */
public enum DataClassification {
    /** Operational data safe to reset (e.g., crm_contacts, crm_accounts) */
    RESETTABLE,
    /** Identity/financial/audit data — NEVER reset (e.g., tenants, billing_invoices, platform_audit_logs) */
    PROTECTED,
    /** Historical data that may be archived instead of deleted (future enhancement) */
    ARCHIVABLE
}
