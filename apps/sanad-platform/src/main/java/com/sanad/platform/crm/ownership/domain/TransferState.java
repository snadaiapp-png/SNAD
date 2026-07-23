package com.sanad.platform.crm.ownership.domain;

/** State machine for transfer requests. */
public enum TransferState {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED,
    COMPLETED,
    FAILED
}
