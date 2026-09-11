package com.sanad.platform.workflow.application;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.storage.PlatformFileReferenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * R1 GATES R1.33/R1.34 — attachment boundary + external participant/action
 * foundations (PostgreSQL Direct).
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowAttachmentExternalFoundationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformFileReferenceService fileReferences;
    @Autowired private WorkflowAttachmentService attachments;
    @Autowired private WorkflowExternalActionService externalActions;

    private final java.util.List<UUID> tenantIds = new java.util.ArrayList<>();
    private final java.util.List<UUID> instanceIds = new java.util.ArrayList<>();
    private final java.util.List<UUID> definitionIds = new java.util.ArrayList<>();
    private final java.util.List<UUID> userIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM workflow_external_actions WHERE tenant_id IN (SELECT id FROM tenants WHERE name LIKE 'R1-AEF-%')");
        jdbc.update("DELETE FROM workflow_external_participants WHERE tenant_id IN (SELECT id FROM tenants WHERE name LIKE 'R1-AEF-%')");
        jdbc.update("DELETE FROM workflow_attachments WHERE tenant_id IN (SELECT id FROM tenants WHERE name LIKE 'R1-AEF-%')");
        jdbc.update("DELETE FROM platform_files WHERE tenant_id IN (SELECT id FROM tenants WHERE name LIKE 'R1-AEF-%')");
        for (UUID id : instanceIds) jdbc.update("DELETE FROM workflow_instances WHERE id = ?", id);
        for (UUID id : definitionIds) jdbc.update("DELETE FROM workflow_definitions WHERE id = ?", id);
        for (UUID id : userIds) jdbc.update("DELETE FROM users WHERE id = ?", id);
        for (UUID id : tenantIds) jdbc.update("DELETE FROM tenants WHERE id = ?", id);
    }

    private UUID newTenant(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?,?,?,?,NOW(),NOW())",
                id, "R1-AEF-" + label, "r1aef-" + label + "-" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE");
        tenantIds.add(id);
        return id;
    }

    private UUID newWorkflowInstance(UUID tenantId) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'AEF User', 'ACTIVE', 'x', NOW(), NOW())", userId, tenantId,
                "r1aef-" + userId + "@test");
        userIds.add(userId);
        UUID defId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_definitions (id, tenant_id, definition_family_id, code, name, module,
                    version, status, trigger_type, created_by, version_lock, engine_generation,
                    publication_state, schema_version, created_at, updated_at)
                VALUES (?,?,?,?,?,'GENERAL', 1, 'ACTIVE', 'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, NOW(), NOW())
                """, defId, tenantId, defId, "R1-AEF-" + defId.toString().substring(0, 8), "AEF def", userId);
        definitionIds.add(defId);
        UUID instId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (id, tenant_id, workflow_definition_id, definition_family_id,
                    workflow_version, business_entity_type, business_entity_id, status, engine_generation,
                    current_step_key, started_by, started_at, created_at, updated_at)
                VALUES (?,?,?,?, 1, 'R1_AEF', ?, 'RUNNING', 'Y2', 'START', ?, NOW(), NOW(), NOW())
                """, instId, tenantId, defId, defId, instId, userId);
        instanceIds.add(instId);
        return instId;
    }

    // ===== R1.33 — attachments =====

    @Test
    void ATTACHMENT_REFERENCE_REGISTERED_WITH_METADATA_AUDITED() {
        UUID tenant = newTenant("att");
        var ref = fileReferences.register(tenant, "CRM", "CUSTOMER", UUID.randomUUID(),
                "application/pdf", 1234L, "a".repeat(64), "CONFIDENTIAL", "test://storage/ref1", null);
        assertThat(ref.tenantId()).isEqualTo(tenant);
        var loaded = fileReferences.require(tenant, ref.id());
        assertThat(loaded.checksumSha256()).hasSize(64);
        // FILE_METADATA_AUDITED: registration timestamp + classification persisted
        java.sql.Timestamp created = jdbc.queryForObject(
                "SELECT created_at FROM platform_files WHERE id = ?", java.sql.Timestamp.class, ref.id());
        assertThat(created.toInstant()).isCloseTo(Instant.now(), within(java.time.Duration.ofMinutes(1)));
        assertThat(jdbc.queryForObject("SELECT classification FROM platform_files WHERE id = ?",
                String.class, ref.id())).isEqualTo("CONFIDENTIAL");
    }

    @Test
    void ATTACHMENT_TENANT_ISOLATION_CROSS_TENANT_REFERENCE_DENIED() {
        UUID tenantA = newTenant("attA");
        UUID tenantB = newTenant("attB");
        var ref = fileReferences.register(tenantA, "CRM", "CUSTOMER", UUID.randomUUID(),
                "image/png", 10L, "b".repeat(64), "INTERNAL", "test://storage/ref2", null);
        // B cannot see (and therefore cannot attach) A's reference:
        assertThatThrownBy(() -> fileReferences.require(tenantB, ref.id()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attachments.attach(tenantB, "WORK_ITEM", UUID.randomUUID(),
                ref.id(), "IMAGE", false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void REQUIRED_ATTACHMENT_BLOCKS_COMPLETION_WHEN_MISSING_AND_ALLOWS_WHEN_PRESENT() {
        UUID tenant = newTenant("req");
        UUID scopeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_attachments (id, tenant_id, scope, scope_id, file_reference_id,
                    attachment_class, required_flag, uploaded_by)
                VALUES (?, ?, ?, ?, NULL, 'PDF', true, NULL)
                """, UUID.randomUUID(), tenant, "WORK_ITEM", scopeId);
        assertThatThrownBy(() -> attachments.requireAttachmentsSatisfied(tenant, "WORK_ITEM", scopeId))
                .isInstanceOf(WorkflowAttachmentService.RequiredAttachmentMissingException.class)
                .hasMessageContaining("WORKFLOW_REQUIRED_ATTACHMENT_MISSING");
        // Provide the required evidence -> satisfied
        var ref = fileReferences.register(tenant, "WORKFLOW", "WORK_ITEM", scopeId,
                "application/pdf", 5L, "c".repeat(64), "INTERNAL", "test://storage/ref3", null);
        attachments.attach(tenant, "WORK_ITEM", scopeId, ref.id(), "PDF", false, null);
        // non-required evidence does not satisfy a required slot:
        assertThatThrownBy(() -> attachments.requireAttachmentsSatisfied(tenant, "WORK_ITEM", scopeId))
                .isInstanceOf(WorkflowAttachmentService.RequiredAttachmentMissingException.class);
        jdbc.update("UPDATE workflow_attachments SET file_reference_id = ? "
                + "WHERE tenant_id = ? AND scope = 'WORK_ITEM' AND scope_id = ? AND required_flag = true",
                ref.id(), tenant, scopeId);
        attachments.requireAttachmentsSatisfied(tenant, "WORK_ITEM", scopeId); // no exception
    }

    @Test
    void NO_LARGE_BINARY_IN_WORKFLOW_RELATIONAL_ROW() {
        java.util.Map<String, Integer> typeCounts = jdbc.query("""
                SELECT table_name, data_type, COUNT(*) AS n FROM information_schema.columns
                 WHERE table_schema='public'
                   AND table_name IN ('workflow_attachments','platform_files',
                                      'workflow_external_actions','workflow_external_participants')
                   AND data_type IN ('bytea','large object','blob')
                 GROUP BY table_name, data_type
                """, rs -> {
            java.util.Map<String, Integer> m = new java.util.HashMap<>();
            while (rs.next()) m.put(rs.getString(1), rs.getInt(3));
            return m;
        });
        assertThat(typeCounts).isEmpty();
    }

    // ===== R1.34 — external participants / actions =====

    @Test
    void EXTERNAL_CUSTOMER_NO_USER_OR_EMPLOYEE_REQUIRED() {
        UUID tenant = newTenant("ext");
        UUID instanceId = newWorkflowInstance(tenant);
        var p = externalActions.registerParticipant(tenant, "CUSTOMER_CONTACT", "CRM",
                "CONTACT", UUID.randomUUID(), "شركة نور", "+966500000000");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND email LIKE 'r1aef-%' AND id IN (SELECT id FROM users WHERE display_name='شركة نور')",
                Integer.class, tenant)).isZero();
        var created = externalActions.create(tenant, instanceId, null, p.id(),
                "APPROVE", Instant.now().plusSeconds(3600), "idem-1", Map.of("outcome", "APPROVE"));
        assertThat(created.action().status()).isEqualTo("PENDING");
        assertThat(created.opaqueToken()).hasSize(64); // 256-bit opaque, non-guessable
        // token stored ONLY as hash
        String storedHash = jdbc.queryForObject(
                "SELECT token_hash FROM workflow_external_actions WHERE id = ?", String.class, created.action().id());
        assertThat(storedHash).isNotEqualTo(created.opaqueToken());
        assertThat(externalActions.findByToken(created.opaqueToken())).isPresent();
        assertThat(externalActions.findByToken("deadbeef")).isEmpty();
    }

    @Test
    void EXTERNAL_PARTICIPANT_SOURCE_REFERENCE_REQUIRED() {
        UUID tenant = newTenant("extref");
        assertThatThrownBy(() -> externalActions.registerParticipant(tenant, "CUSTOMER", null,
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXTERNAL_PARTICIPANT_SOURCE_REFERENCE_REQUIRED");
    }

    @Test
    void EXTERNAL_ACTION_FULL_LIFECYCLE_WITH_SECURITY_FOUNDATION() {
        UUID tenant = newTenant("life");
        UUID instanceId = newWorkflowInstance(tenant);
        var p = externalActions.registerParticipant(tenant, "SUPPLIER_CONTACT", "ERP",
                "SUPPLIER", UUID.randomUUID(), "supplier", "supplier@example.com");

        // IDEMPOTENT creation
        var first = externalActions.create(tenant, instanceId, null, p.id(), "CONFIRM",
                Instant.now().plusSeconds(3600), "idem-life", Map.of());
        assertThatThrownBy(() -> externalActions.create(tenant, instanceId, null, p.id(), "CONFIRM",
                Instant.now().plusSeconds(3600), "idem-life", Map.of()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // VIEWED -> RESPONDED once (EXTERNAL_RESPONSE_ADVANCES_GRAPH_ONCE at
        // the state level; replay is idempotent)
        externalActions.markViewed(tenant, first.action().id());
        var responded = externalActions.respond(tenant, first.action().id(), p.id(), Map.of("confirmed", true));
        assertThat(responded.status()).isEqualTo("RESPONDED");
        var replay = externalActions.respond(tenant, first.action().id(), p.id(), Map.of("confirmed", true));
        assertThat(replay.respondedAt()).isEqualTo(responded.respondedAt()); // single-shot

        // WRONG_PARTICIPANT_DENIED
        UUID tenant2 = newTenant("life2");
        var p2 = externalActions.registerParticipant(tenant2, "CUSTOMER", "CRM",
                "CONTACT", UUID.randomUUID(), "other", "other@example.com");
        UUID instance2 = newWorkflowInstance(tenant2);
        var action2 = externalActions.create(tenant2, instance2, null, p2.id(), "APPROVE",
                Instant.now().plusSeconds(3600), "idem-2", Map.of());
        assertThatThrownBy(() -> externalActions.respond(tenant2, action2.action().id(), p.id(), Map.of()))
                .isInstanceOf(WorkflowExternalActionService.ExternalActionStateException.class)
                .hasMessageContaining("WRONG_PARTICIPANT_DENIED");

        // CROSS_TENANT_EXTERNAL_ACTION_DENIED: tenantA's participant invisible
        assertThatThrownBy(() -> externalActions.respond(tenant, action2.action().id(), p2.id(), Map.of()))
                .isInstanceOf(WorkflowExternalActionService.ExternalActionStateException.class)
                .hasMessageContaining("EXTERNAL_ACTION_NOT_FOUND");

        // EXPIRED_RESPONSE_DENIED
        var action3 = externalActions.create(tenant, instanceId, null, p.id(), "UPLOAD",
                Instant.now().plusSeconds(3600), "idem-3", Map.of());
        jdbc.update("UPDATE workflow_external_actions SET token_expires_at = NOW() - interval '1 minute' "
                + "WHERE tenant_id=? AND id=?", tenant, action3.action().id());
        assertThatThrownBy(() -> externalActions.respond(tenant, action3.action().id(), p.id(), Map.of()))
                .isInstanceOf(WorkflowExternalActionService.ExternalActionStateException.class)
                .hasMessageContaining("EXPIRED_RESPONSE_DENIED");

        // REVOCATION + REVOKED_RESPONSE_DENIED
        var action4 = externalActions.create(tenant, instanceId, null, p.id(), "ACKNOWLEDGE",
                Instant.now().plusSeconds(3600), "idem-4", Map.of());
        var revoked = externalActions.transition(tenant, action4.action().id(), "REVOKED", Map.of());
        assertThat(revoked.status()).isEqualTo("REVOKED");
        assertThatThrownBy(() -> externalActions.respond(tenant, action4.action().id(), p.id(), Map.of()))
                .isInstanceOf(WorkflowExternalActionService.ExternalActionStateException.class)
                .hasMessageContaining("REVOKED_RESPONSE_DENIED");

        // EXPIRY honored by expiry transition (never treated as approval/completion)
        var action5 = externalActions.create(tenant, instanceId, null, p.id(), "PROVIDE_INFORMATION",
                Instant.now().plusSeconds(3600), "idem-5", Map.of());
        var expired = externalActions.transition(tenant, action5.action().id(), "EXPIRED", Map.of());
        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(expired.status()).isNotIn("RESPONDED", "APPROVED");
    }
}
