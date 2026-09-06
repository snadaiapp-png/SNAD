package com.sanad.platform.hr.compliance.domain;

import java.util.UUID;

/** Tenant-scoped resource reference. Never carries raw PII. */
public record ComplianceResource(
        String resourceType,
        UUID resourceId) {
}
