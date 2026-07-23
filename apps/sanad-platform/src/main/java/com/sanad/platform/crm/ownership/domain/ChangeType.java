package com.sanad.platform.crm.ownership.domain;

/** Type of ownership change recorded in ownership history. */
public enum ChangeType {
    INITIAL,
    REASSIGN,
    TRANSFER,
    QUEUE_CLAIM,
    QUEUE_RELEASE,
    TEMPORARY,
    RESTORE,
    BULK
}
