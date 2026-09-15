package com.sanad.platform.hr.recruitment.db;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * HRM-G1 T7 test support — seeds Workflow Y2 rows in the EXACT shape the real
 * engine persists (definition → step → instance → step instance → approval
 * request), so plain-JUnit isolated-DB harnesses can drive the in-transaction
 * authoritative verification without booting a Spring context.
 *
 * <p>The Spring integration tests prove the real engine produces exactly this
 * shape via the real adapters; the seed exists ONLY to arrange engine-owned
 * state in non-Spring harnesses. It never bypasses an assertion.</p>
 */
public final class HrY2WorkflowSeedSupport {

    private HrY2WorkflowSeedSupport() {
    }

    public record Seed(UUID instanceId, UUID definitionVersionId) {
    }

    /**
     * Seeds a Y2 approval workflow for one business entity.
     *
     * @param instanceId    the authoritative instance id to seed (must be the
     *                      correlation target the service recorded)
     * @param status        workflow_instances.status (RUNNING/COMPLETED/CANCELLED/FAILED)
     * @param requestStatus workflow_approval_requests.status (APPROVED/REJECTED/PENDING) or null for none
     */
    public static Seed seedApproval(JdbcTemplate jdbc, UUID tenantId, String businessType, UUID businessId,
                                    UUID instanceId, UUID definitionVersionId, String status, String requestStatus) {
        Timestamp now = Timestamp.from(Instant.now());
        // Synthetic engine actor (workflow FKs: definitions.created_by,
        // instances.started_by, approval requests) — mirrors what the real
        // engine sees: every workflow actor is a real user row of the tenant.
        UUID seedUserId = UUID.nameUUIDFromBytes((tenantId + "|seed-user").getBytes());
        jdbc.update("""
                        INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                        VALUES (?, ?, ?, 'T7 Seed User', 'ACTIVE', 'seed', ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                seedUserId, tenantId, "t7-seed-" + seedUserId.toString().substring(0, 8) + "@seed", now, now);
        UUID definitionId = definitionVersionId != null ? definitionVersionId : UUID.nameUUIDFromBytes(
                (tenantId + "|seed|" + businessType + "|def").getBytes());
        UUID familyId = definitionId;
        jdbc.update("""
                        INSERT INTO workflow_definitions (
                            id, tenant_id, definition_family_id, code, name, module, version, status,
                            trigger_type, created_by, version_lock, engine_generation, publication_state,
                            schema_version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'HRM', 1, 'ACTIVE', 'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                definitionId, tenantId, familyId, "SEED_" + businessType, "Seed " + businessType, seedUserId, now, now);
        UUID stepId = UUID.nameUUIDFromBytes(
                (tenantId + "|seed|" + businessType + "|step").getBytes());
        jdbc.update("""
                        INSERT INTO workflow_steps (
                            id, tenant_id, workflow_definition_id, step_key, name, step_type,
                            sequence_order, configuration, version, created_at, updated_at
                        ) VALUES (?, ?, ?, 'offer_approval', 'Seed Approval', 'APPROVAL', 1,
                                  '{"approvalPolicy":"ANY_ONE"}', 0, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                stepId, tenantId, definitionId, now, now);
        jdbc.update("""
                        INSERT INTO workflow_instances (
                            id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                            business_entity_id, status, current_step_key, started_by, started_at,
                            engine_generation, definition_family_id, definition_version_id,
                            context_json, context_schema_version, version, created_at, updated_at
                        ) VALUES (?, ?, ?, 1, ?, ?, ?, NULL, ?, ?, 'Y2', ?, ?,
                                  CAST('{}' AS jsonb), 1, 0, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                instanceId, tenantId, definitionId, businessType, businessId, status, seedUserId, now,
                familyId, definitionId, now, now);
        UUID stepInstanceId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO workflow_step_instances (
                            id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                            status, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, 'offer_approval', 'COMPLETED', 0, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                stepInstanceId, tenantId, instanceId, stepId, now, now);
        if (requestStatus != null) {
            jdbc.update("""
                            INSERT INTO workflow_approval_requests (
                                id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                                requested_from_user_id, status, requested_at, version, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                            ON CONFLICT (id) DO NOTHING
                            """,
                    UUID.randomUUID(), tenantId, instanceId, stepInstanceId, seedUserId, requestStatus, now, now, now);
        }
        return new Seed(instanceId, definitionId);
    }
}
