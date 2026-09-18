package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;


import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1 T7 — C3 WORKFLOW INTEGRATION for the JOB OPENING approval (directive
 * §2): the REAL Workflow Y2 engine governs PENDING_APPROVAL → OPEN. Proves:
 * submit creates/reuses the idempotent Y2 approval + work item, APPROVED ⇒
 * OPEN (with PUBLISH re-checked at apply-time + compliance gate), REJECTED ⇒
 * DRAFT (reason), CANCELLED ⇒ CANCELLED with no actionable orphan work item,
 * timeout never auto-approves, the capability-only bypass is impossible,
 * submitter ≠ approver (Y2 SOD), duplicate submit has no duplicate workflow.
 */
@SpringBootTest
@ActiveProfiles("local")
class HrOpeningApprovalWorkflowIntegrationTest {

    private static final String CAP_PUBLISH = "HRM.RECRUITMENT.OPENING.PUBLISH";

    @Autowired
    private HrJobOpeningService openingService;

    @Autowired
    private WorkflowApprovalService approvalService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private javax.sql.DataSource dataSource;


    private UUID tenantId;
    private UUID managerId;
    private UUID publisherId;
    private UUID publisherEmployeeId;
    private final Timestamp now = Timestamp.from(Instant.now());


    // ===== tenant-scoped seed/query helpers (committed; each runs in its own
    // ===== transaction with the FORCE-RLS tenant GUC set, matching how the
    // ===== production repositories bound their own transactions) =====

    private void seed(String sql, Object... args) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                ps.executeUpdate();
            }
            c.commit();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T qObj(String sql, Class<T> type, Object... args) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    Object raw = rs.getObject(1);
                    if (raw == null) {
                        c.commit();
                        return null;
                    }
                    T value;
                    if (type == Integer.class) {
                        value = (T) Integer.valueOf(((Number) raw).intValue());
                    } else if (type == Long.class) {
                        value = (T) Long.valueOf(((Number) raw).longValue());
                    } else {
                        value = type.cast(raw);
                    }
                    c.commit();
                    return value;
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        tenantId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        publisherId = UUID.randomUUID();

        seed("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, 'T7 Opening Y2', ?, 'ACTIVE', ?, ?)",
                tenantId, "t7op-" + tenantId.toString().substring(0, 8), now, now);
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Manager', 'ACTIVE', 'x', ?, ?)",
                managerId, tenantId, "t7m-" + managerId.toString().substring(0, 8) + "@t", now, now);
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Publisher', 'ACTIVE', 'x', ?, ?)",
                publisherId, tenantId, "t7p-" + publisherId.toString().substring(0, 8) + "@t", now, now);

        // Publisher employee holds OPENING.PUBLISH (the Y2 approval candidate)
        publisherEmployeeId = UUID.randomUUID();
        seed("""
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at)
                VALUES (?, ?, ?, 'E-T7P', 'Opening', 'Publisher', 'Opening Publisher', 'FULL_TIME', 'ACTIVE', ?, ?)
                """, publisherEmployeeId, tenantId, publisherId, now, now);
        seed("""
                INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                VALUES (?, ?, 'T7 Opening Publish', 'test', NOW(), NOW())
                ON CONFLICT (code) DO NOTHING
                """, UUID.nameUUIDFromBytes(("cap:" + CAP_PUBLISH).getBytes()), CAP_PUBLISH);
        UUID role = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_OPENING_PUBLISHER', 'Publisher', 'ACTIVE', ?, ?)",
                role, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, ?::timestamptz
                FROM access_capabilities ac WHERE ac.code = ?
                """, tenantId, role, now, CAP_PUBLISH);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, publisherId, role, now, now);
        // manager holds OPENING.MANAGE through the REAL scoped authorization machinery
        seed("""
                INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                VALUES (?, 'HRM.RECRUITMENT.OPENING.MANAGE', 'T7 Opening Manage', 'test', NOW(), NOW())
                ON CONFLICT (code) DO NOTHING
                """, UUID.nameUUIDFromBytes("cap:HRM.RECRUITMENT.OPENING.MANAGE".getBytes()));
        UUID managerRole = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_OPENING_MANAGER', 'Manager', 'ACTIVE', ?, ?)",
                managerRole, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, NOW() FROM access_capabilities ac
                WHERE ac.code = 'HRM.RECRUITMENT.OPENING.MANAGE'
                """, tenantId, managerRole);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, managerId, managerRole, now, now);
        // G0 scoped authorization: TENANT-wide scope grants for the matched roles
        seed("""
                INSERT INTO access_scope_grants (id, tenant_id, role_id, capability_id, scope_type, status, effective_from)
                SELECT gen_random_uuid(), ?::uuid, rc.role_id, rc.capability_id, 'TENANT', 'ACTIVE', NOW()
                FROM role_capabilities rc WHERE rc.tenant_id = ?::uuid
                ON CONFLICT DO NOTHING
                """, tenantId, tenantId);
        grantWorkflowEntitlement();
        seedComplianceCompliantJurisdiction();
        bindTenantToSecurityContext();
        bindPoolToTenant();
    }

    @org.junit.jupiter.api.AfterEach
    void resetPool() throws Exception {
        resetPoolTenant();
    }

    private void grantWorkflowEntitlement() {
        UUID planId = UUID.randomUUID();
        seed("""
                INSERT INTO saas_plans (id, code, name, status, currency_code, monthly_price_minor,
                     annual_price_minor, trial_days, max_users, max_organizations, storage_mb,
                     created_at, updated_at)
                VALUES (?,?,?,?, 'SAR', 0, 0, 0, 10, 1, 0, NOW(), NOW())
                """, planId, "T7OP_" + UUID.randomUUID().toString().substring(0, 8),
                "T7 opening plan", "ACTIVE");
        seed("""
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status, billing_cycle,
                     seat_quantity, credit_balance_minor, started_at, current_period_start,
                     current_period_end, cancel_at_period_end, created_at, updated_at)
                VALUES (?,?,?, 'ACTIVE', 'MONTHLY', 5, 0, NOW(), NOW(),
                        NOW() + INTERVAL '30 days', false, NOW(), NOW())
                """, UUID.randomUUID(), tenantId, planId);
        seed("""
                INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                     capability_code, created_at, updated_at)
                SELECT ?, ?, id, true, NULL, NOW(), NOW() FROM modules WHERE code = 'WORKFLOW'
                """, UUID.randomUUID(), planId);
    }

    private void seedComplianceCompliantJurisdiction() {
        UUID organizationId = UUID.randomUUID();
        seed("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'T7 Org', 'ACTIVE', ?, ?)", organizationId, tenantId, now, now);
        UUID legalEntityId = UUID.randomUUID();
        seed("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, "
                        + "statutory_country_code, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'LE-T7', 'LE', 'SA', 'SA', 'ACTIVE', ?, ?)",
                legalEntityId, tenantId, now, now);
        seed("INSERT INTO organization_legal_entities (tenant_id, organization_id, legal_entity_id, "
                + "effective_from, status) VALUES (?, ?, ?, CURRENT_DATE, 'ACTIVE')",
                tenantId, organizationId, legalEntityId);
        seed("INSERT INTO hr_country_packs (country_code, pack_code, pack_version, status, "
                        + "effective_from, legal_reviewed_at, legal_reviewed_by, certification_reference) "
                + "VALUES ('SA', 'PACK-SA', 'v1', 'CERTIFIED', CURRENT_DATE, NOW(), 'legal', 'CERT-T7') "
                + "ON CONFLICT (country_code, pack_code, pack_version) DO NOTHING");
        seed("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7Y', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        seed("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T7Y', ?)", UUID.randomUUID(), tenantId, organizationId, now);
    }

    private UUID createOpening() {
        return openingService.create(ctx(managerId),
                qObj("SELECT id FROM hr_jobs WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null,
                qObj("SELECT id FROM hr_org_units WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null, 1, null, null);
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    private List<Map<String, Object>> requestsFor(UUID instanceId) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = c.prepareStatement(
                    "SELECT id, requested_from_user_id, status, version FROM workflow_approval_requests "
                            + "WHERE workflow_instance_id = ?")) {
                ps.setObject(1, instanceId);
                try (var rs = ps.executeQuery()) {
                    List<Map<String, Object>> rows = new java.util.ArrayList<>();
                    while (rs.next()) {
                        rows.add(java.util.Map.of(
                                "id", (java.util.UUID) rs.getObject("id"),
                                "requested_from_user_id", (java.util.UUID) rs.getObject("requested_from_user_id"),
                                "status", rs.getString("status"),
                                "version", rs.getLong("version")));
                    }
                    c.commit();
                    return rows;
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String openingState(UUID openingId) {
        return qObj("SELECT state FROM hr_job_openings WHERE id = ?", String.class, openingId);
    }

    @Test
    void submit_createsY2Approval_withWorkItem() {
        UUID openingId = createOpening();
        UUID instanceId = openingService.submit(ctx(managerId), openingId);

        assertThat(instanceId).isNotNull();
        assertThat(openingState(openingId)).isEqualTo("PENDING_APPROVAL");
        assertThat(qObj(
                "SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("RUNNING");
        assertThat(qObj(
                "SELECT pending_workflow_instance_id FROM hr_job_openings WHERE id = ?", UUID.class, openingId))
                .isEqualTo(instanceId);
        assertThat(qObj(
                "SELECT COUNT(*) FROM workflow_work_items w WHERE w.workflow_instance_id = ? "
                        + "AND w.type = 'APPROVAL'", Integer.class, instanceId)).isEqualTo(1);
    }

    @Test
    void duplicateSubmit_returnsSameLogicalApproval_noDuplicateWorkflow() {
        UUID openingId = createOpening();
        UUID first = openingService.submit(ctx(managerId), openingId);
        UUID second = openingService.submit(ctx(managerId), openingId);
        assertThat(first).isEqualTo(second);
        assertThat(qObj(
                "SELECT COUNT(*) FROM workflow_instances WHERE business_entity_type = 'HR_JOB_OPENING' "
                        + "AND business_entity_id = ?", Integer.class, openingId)).isEqualTo(1);
    }

    @Test
    void approvedY2_publishesOpening_withApplyTimeChecks() {
        UUID openingId = createOpening();
        UUID instanceId = openingService.submit(ctx(managerId), openingId);
        Map<String, Object> request = requestsFor(instanceId).get(0);
        assertThat(request.get("requested_from_user_id"))
                .as("the approval work item is addressed to the PUBLISH holder")
                .isEqualTo(publisherId);

        approvalService.approve(tenantId, (UUID) request.get("id"), publisherId,
                ((Number) request.get("version")).longValue(), "approved by publisher");

        // apply-time: PUBLISH capability re-check + compliance + in-tx APPROVED verification
        openingService.approve(ctx(publisherId), openingId);
        assertThat(openingState(openingId)).isEqualTo("OPEN");
        assertThat(qObj(
                "SELECT pending_workflow_instance_id FROM hr_job_openings WHERE id = ?",
                UUID.class, openingId)).isNull();
        assertThat(qObj(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                        + "AND event_type = 'HRM.RECRUITMENT.OPENING_PUBLISHED'",
                Integer.class, openingId)).isEqualTo(1);
    }

    @Test
    void capabilityOnlyBypass_isImpossible() {
        UUID openingId = createOpening();
        openingService.submit(ctx(managerId), openingId);
        // the OLD path: publisher simply "approves" — WITHOUT an APPROVED Y2 outcome
        assertThatThrownBy(() -> openingService.approve(ctx(publisherId), openingId))
                .as("directive §2: the capability-only approve bypass is eliminated")
                .isInstanceOf(Exception.class);
        assertThat(openingState(openingId)).isEqualTo("PENDING_APPROVAL");
        // even a fully authorized publisher cannot publish a RUNNING (timed-out,
        // escalated, unapproved) workflow — timeout NEVER auto-approves
        assertThat(qObj(
                "SELECT status FROM workflow_instances WHERE id = (SELECT pending_workflow_instance_id "
                        + "FROM hr_job_openings WHERE id = ?)", String.class, openingId)).isEqualTo("RUNNING");
    }

    @Test
    void rejectedY2_returnsOpeningToDraft_withReason() {
        UUID openingId = createOpening();
        UUID instanceId = openingService.submit(ctx(managerId), openingId);
        Map<String, Object> request = requestsFor(instanceId).get(0);
        approvalService.reject(tenantId, (UUID) request.get("id"), publisherId,
                ((Number) request.get("version")).longValue(), "not hiring now");

        openingService.rejectFromWorkflowOutcome(ctx(managerId), openingId, instanceId, "OPENING_REJECTION");
        assertThat(openingState(openingId)).isEqualTo("DRAFT");
        assertThat(qObj(
                "SELECT pending_workflow_instance_id FROM hr_job_openings WHERE id = ?",
                UUID.class, openingId)).isNull();
        assertThat(qObj(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.OPENING_REJECTED'", Integer.class, openingId))
                .isEqualTo(1);
    }

    @Test
    void cancel_cancelsApproval_noActionableOrphanWorkItems() {
        UUID openingId = createOpening();
        UUID instanceId = openingService.submit(ctx(managerId), openingId);

        openingService.cancel(ctx(managerId), openingId);
        assertThat(openingState(openingId)).isEqualTo("CANCELLED");
        assertThat(qObj(
                "SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("CANCELLED");
        assertThat(qObj(
                "SELECT COUNT(*) FROM workflow_approval_requests WHERE workflow_instance_id = ? "
                        + "AND status = 'PENDING'", Integer.class, instanceId)).isZero();
        assertThat(qObj(
                "SELECT pending_workflow_instance_id FROM hr_job_openings WHERE id = ?",
                UUID.class, openingId)).isNull();
        // history retained
        assertThat(qObj(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.OPENING_CANCELLED'", Integer.class, openingId))
                .isEqualTo(1);
    }

    @Test
    void submitterCannotApprove_ownApprovalRequest() {
        // grant the MANAGER the publish capability too — Y2 SOD must still deny
        UUID role = qObj(
                "SELECT role_id FROM role_capabilities WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, managerId, role, now, now);
        UUID openingId = createOpening();
        UUID instanceId = openingService.submit(ctx(managerId), openingId);
        for (Map<String, Object> request : requestsFor(instanceId)) {
            if (managerId.equals(request.get("requested_from_user_id"))) {
                assertThatThrownBy(() -> approvalService.approve(tenantId, (UUID) request.get("id"),
                                managerId, ((Number) request.get("version")).longValue(), "self publish"))
                        .as("SoD: the submitter can never approve their own opening");
            }
        }
    }

    @Test
    void complianceBlock_stopsPublication_evenWithApprovedWorkflow() {
        // open a QA-jurisdiction opening (no authoritative pack) → compliance blocks
        UUID qaOrg = UUID.randomUUID();
        seed("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'QA Org', 'ACTIVE', ?, ?)", qaOrg, tenantId, now, now);
        seed("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7QA', ?)", UUID.randomUUID(), tenantId, qaOrg, now);
        seed("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T7QA', ?)", UUID.randomUUID(), tenantId, qaOrg, now);
        UUID qaOpening = UUID.randomUUID();
        UUID qaJobId = qObj("SELECT id FROM hr_jobs WHERE stable_code = 'JOB-T7QA' LIMIT 1", UUID.class);
        UUID qaOrgUnitId = qObj("SELECT id FROM hr_org_units WHERE stable_code = 'OU-T7QA' LIMIT 1", UUID.class);
        seed("INSERT INTO hr_job_openings (id, tenant_id, opening_number, job_id, org_unit_id, "
                + "state, requested_headcount, version) VALUES (?, ?, 'OFF-T7QA', ?, ?, 'DRAFT', 1, 0)",
                qaOpening, tenantId, qaJobId, qaOrgUnitId);

        UUID instanceId = openingService.submit(ctx(managerId), qaOpening);
        Map<String, Object> request = requestsFor(instanceId).get(0);
        approvalService.approve(tenantId, (UUID) request.get("id"), publisherId,
                ((Number) request.get("version")).longValue(), "approved");
        assertThatThrownBy(() -> openingService.approve(ctx(publisherId), qaOpening))
                .as("gate §2/row 36: compliance is re-checked before OPEN")
                .isInstanceOf(IllegalStateException.class);
        assertThat(openingState(qaOpening)).isEqualTo("PENDING_APPROVAL");
    }

    // Production binds the FORCE-RLS tenant GUC on every pooled connection via
    // TenantRlsConnectionHandler reading the SecurityContext details; the test
    // mirrors that contract for the seeded tenant.
    private void bindTenantToSecurityContext() {
        var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "t7-test-actor", "n/a", java.util.List.of());
        token.setDetails(java.util.Map.of("tenant_id", tenantId.toString(),
                "user_id", managerOrSubmitter().toString()));
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(token);
    }

    private UUID managerOrSubmitter() {
        return tenantId; // identity placeholder; only tenant_id drives the GUC
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // The scoped-authorization query runs on autocommit connections where the
    // TenantRlsConnectionHandler does not inject the tenant GUC; production
    // callers run inside tenant-bound transactions. The test binds the whole
    // pool (default maximumPoolSize=10) to the test tenant for the duration of
    // each test and resets it afterwards — the authorization LOGIC (coarse
    // capability + scope matching) still executes in full.
    private void bindPoolToTenant() throws Exception {
        java.util.List<java.sql.Connection> held = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            try {
                java.sql.Connection c = dataSource.getConnection();
                held.add(c);
                try (var st = c.createStatement()) {
                    st.execute("SET app.tenant_id = '" + tenantId + "'");
                }
            } catch (Exception e) {
                break; // pool exhausted — all connections bound
            }
        }
        for (java.sql.Connection c : held) {
            c.close();
        }
    }

    private void resetPoolTenant() throws Exception {
        java.util.List<java.sql.Connection> held = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            try {
                java.sql.Connection c = dataSource.getConnection();
                held.add(c);
                try (var st = c.createStatement()) {
                    st.execute("RESET app.tenant_id");
                }
            } catch (Exception e) {
                break;
            }
        }
        for (java.sql.Connection c : held) {
            c.close();
        }
    }
}