package com.sanad.platform.subscription.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Typed audit-trail response row for the executive console ({@code /audit/v2}).
 *
 * <p>R0C-12 Blocker B correction: the endpoint previously serialized the raw
 * JDBC row {@code Map<String, Object>} whose keys are the physical column
 * names ({@code resource_id}, {@code created_at}, {@code actor_tenant_id},
 * …). The web console's {@code AuditEntry} contract is camelCase and the
 * audit page dereferences {@code entry.resourceId}, so every legacy-shaped
 * response crashed the page at runtime ({@code undefined.slice}) even though
 * the HTTP layer returned 200.
 *
 * <p>This record fixes the JSON contract at the source: Jackson serializes
 * the component names verbatim ({@code resourceId}, {@code createdAt},
 * {@code actorTenantId}, {@code actorUserId}, {@code targetTenantId}, …) and
 * nullable columns map to {@code null} JSON members instead of absent keys.
 * Raw JDBC maps must never cross the API boundary again (contract test:
 * {@code ModuleRegistryUatPostgresAcceptanceTest#uatB_auditContract_camelCaseKeys}).
 */
public record AuditEntryResponse(
        UUID id,
        UUID actorTenantId,
        UUID actorUserId,
        UUID targetTenantId,
        String action,
        String resourceType,
        String resourceId,
        String reason,
        String result,
        String correlationId,
        Instant createdAt) {
}
