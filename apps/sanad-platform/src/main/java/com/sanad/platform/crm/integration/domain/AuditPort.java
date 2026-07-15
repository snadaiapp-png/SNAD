package com.sanad.platform.crm.integration.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Port for the centralized audit system.
 * CRM modules use this port — never implement their own audit.
 * Uses JsonNode for before/after states to avoid double-encoding.
 */
public interface AuditPort {
    void record(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId,
                AuditChange change, Instant timestamp);

    record AuditChange(JsonNode beforeState, JsonNode afterState) {}
}
