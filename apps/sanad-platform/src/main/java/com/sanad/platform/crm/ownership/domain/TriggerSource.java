package com.sanad.platform.crm.ownership.domain;

/** What triggered an ownership change. */
public enum TriggerSource {
    MANUAL,
    RULE,
    TRANSFER_REQUEST,
    WORKFLOW,
    ABSENCE_POLICY
}
