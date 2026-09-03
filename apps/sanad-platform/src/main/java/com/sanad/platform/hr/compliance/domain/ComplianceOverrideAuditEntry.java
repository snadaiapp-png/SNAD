package com.sanad.platform.hr.compliance.domain;

import java.util.UUID;

/**
 * WS3-facing audit entry handed to {@code ComplianceAuditPort}. Contains
 * identifiers, codes and reason metadata only — never raw PII or secrets.
 * The WS4 adapter translates this into an immutable hr_audit_ledger fact
 * (plus delivery state) inside the caller's transaction.
 */
public record ComplianceOverrideAuditEntry(
        UUID tenantId,
        UUID requestId,
        String action,
        UUID actorUserId,
        String result,
        String reasonCode) {
}
