package com.sanad.platform.crm.integration.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Port for the centralized audit system.
 * CRM modules use this port — never implement their own audit.
 */
public interface AuditPort {
    void record(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId,
                Map<String, Object> before, Map<String, Object> after, Instant timestamp);
}
