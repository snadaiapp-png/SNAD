package com.sanad.platform.hr.compliance.domain;

/** Statutory enforcement classes. MANDATORY_HARD is never overrideable. */
public enum ComplianceEnforcementLevel {
    MANDATORY_HARD,
    MANDATORY_WITH_EXCEPTION,
    REGULATORY_GUIDANCE,
    TENANT_POLICY
}
