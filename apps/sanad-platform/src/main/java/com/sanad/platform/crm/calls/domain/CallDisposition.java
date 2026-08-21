package com.sanad.platform.crm.calls.domain;

/** Call outcome modelled after the call (G8-03 §25). */
public enum CallDisposition {
    CONNECTED,
    NO_ANSWER,
    BUSY,
    REJECTED,
    FAILED,
    CALLBACK_REQUESTED,
    FOLLOW_UP_REQUIRED,
    OTHER
}
