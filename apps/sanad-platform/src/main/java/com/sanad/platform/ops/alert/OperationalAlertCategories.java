package com.sanad.platform.ops.alert;

/**
 * Constants for operational alert categories and severity levels.
 * <p>
 * CRM-008 remediation: production-grade alerting for operational events.
 */
public final class OperationalAlertCategories {

    private OperationalAlertCategories() {}

    // --- Severity Levels (ordered low → high) ---
    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARN = "WARN";
    public static final String SEVERITY_ERROR = "ERROR";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    // --- Alert Categories ---
    public static final String CATEGORY_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String CATEGORY_DATABASE_CONNECTIVITY = "DATABASE_CONNECTIVITY";
    public static final String CATEGORY_ERROR_RATE = "ERROR_RATE";
    public static final String CATEGORY_AUTH_FAILURE = "AUTH_FAILURE";
    public static final String CATEGORY_DEPLOYMENT = "DEPLOYMENT";
    public static final String CATEGORY_WORKFLOW_FAILURE = "WORKFLOW_FAILURE";
    public static final String CATEGORY_CIRCUIT_BREAKER = "CIRCUIT_BREAKER";
    public static final String CATEGORY_RATE_LIMIT = "RATE_LIMIT";
    public static final String CATEGORY_HEALTH_DEGRADED = "HEALTH_DEGRADED";
    public static final String CATEGORY_QUEUE_FAILURE = "QUEUE_FAILURE";
    public static final String CATEGORY_CAPACITY_ALERT = "CAPACITY_ALERT";

    // --- Service Names ---
    public static final String SERVICE_SANAD_PLATFORM = "sanad-platform";
    public static final String SERVICE_POSTGRESQL = "postgresql";
    public static final String SERVICE_REDIS = "redis";
    public static final String SERVICE_WEBHOOK = "webhook";
}
