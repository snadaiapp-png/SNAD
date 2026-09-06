package com.sanad.platform.hr.compliance.domain;

/** Allowed compliance decision types for HRM-G0 WS3. */
public enum ComplianceDecisionType {
    COMPLIANT,
    BLOCKED,
    CONTROLLED_EXCEPTION_REQUIRED,
    LEGAL_REVIEW_REQUIRED,
    GLOBAL_MODE_ALLOWED
}
