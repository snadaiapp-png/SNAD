package com.sanad.platform.module.registry;

/**
 * Enumeration of capability types supported by the Module Registry.
 *
 * <p>These types determine how the {@code EntitlementResolver} parses and
 * applies the effective value at runtime.
 */
public enum CapabilityType {
    /** Boolean: is the module enabled at all? (e.g., CRM.ENABLED) */
    MODULE_ENABLED,
    /** Boolean: is a specific feature enabled? (e.g., CRM.ADVANCED_PIPELINE) */
    FEATURE_ENABLED,
    /** Integer: a hard limit (e.g., CRM.MAX_CONTACTS = 10000) */
    NUMERIC_LIMIT,
    /** Integer with period: a usage quota (e.g., AI.MONTHLY_OPERATIONS = 50000) */
    QUOTA,
    /** Boolean: a generic boolean toggle (e.g., ANALYTICS.ADVANCED_REPORTS) */
    BOOLEAN_CAPABILITY;

    public static CapabilityType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Capability type must not be blank");
        }
        return CapabilityType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public boolean isBoolean() {
        return this == MODULE_ENABLED || this == FEATURE_ENABLED || this == BOOLEAN_CAPABILITY;
    }

    public boolean isNumeric() {
        return this == NUMERIC_LIMIT || this == QUOTA;
    }
}
