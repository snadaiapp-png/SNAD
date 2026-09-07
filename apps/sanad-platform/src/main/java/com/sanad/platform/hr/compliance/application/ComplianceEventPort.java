package com.sanad.platform.hr.compliance.application;

import com.sanad.platform.hr.compliance.domain.ComplianceOverrideEventEntry;

/**
 * WS3-facing domain event port (WS3 Task 4). Implementations (WS4 adapters)
 * append a durable, producer-local outbox event. The call must participate
 * in the caller's current transaction — never an independent transaction.
 */
public interface ComplianceEventPort {

    void recordOverrideEvent(ComplianceOverrideEventEntry entry);
}
