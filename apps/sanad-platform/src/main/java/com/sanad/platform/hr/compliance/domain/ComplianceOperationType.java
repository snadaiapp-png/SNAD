package com.sanad.platform.hr.compliance.domain;

/**
 * Classification of an HR operation with respect to statutory sensitivity.
 * GLOBAL Mode may authorize GENERIC_HR operations only; LOCAL_STATUTORY
 * operations always require an authoritative localized rule or fail closed.
 */
public enum ComplianceOperationType {
    GENERIC_HR,
    LOCAL_STATUTORY
}
