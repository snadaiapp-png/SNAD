package com.sanad.platform.crm.dataquality.domain;

/**
 * Placeholder for future data quality policies.
 * Currently contains only the validation contracts inherited from CRM-G2.
 * Enterprise deduplication, fuzzy matching, and merge queues are OUT OF SCOPE for CRM-004.
 */
public final class DataQualityPolicy {
    /**
     * Validates that a display name is not empty and within length constraints.
     * This is the current validation policy — future stages will extend this.
     */
    public static void validateDisplayName(String displayName, int maxLength) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (displayName.length() > maxLength) {
            throw new IllegalArgumentException("Display name exceeds " + maxLength + " characters");
        }
    }

    private DataQualityPolicy() {}
}
