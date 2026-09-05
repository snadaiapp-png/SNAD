package com.sanad.platform.hr.compliance.application;

import com.sanad.platform.hr.compliance.domain.ComplianceOverrideAuditEntry;

/**
 * WS3-facing audit port (WS3 Task 4). Implementations (WS4 adapters) append
 * durable, immutable audit evidence. The call must participate in the
 * caller's current transaction — it must NEVER open an independent
 * transaction (no REQUIRES_NEW), so audit evidence commits or rolls back
 * together with the state change it evidences.
 */
public interface ComplianceAuditPort {

    void recordOverrideAction(ComplianceOverrideAuditEntry entry);
}
