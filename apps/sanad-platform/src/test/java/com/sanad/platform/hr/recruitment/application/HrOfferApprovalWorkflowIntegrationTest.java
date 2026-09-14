package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
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
 * HRM-G1 T7 — C3 WORKFLOW INTEGRATION: the REAL Workflow Y2 engine drives
 * offer approval end-to-end through the HR-owned port/adapter (directive
 * §T7.5–§T7.8). Proves: authoritative start/work item creation, ANY_ONE
 * approval resolution, exactly-once extension, replay safety, self-approval
 * denial (Y2 SOD), stale/fake workflow refusal, cancellation cleanup,
 * duplicate-submit idempotency and the no-compensation/PII work-item hygiene.
 */
@SpringBootTest
@ActiveProfiles("local")
class HrOfferApprovalWorkflowIntegrationTest {

    private static final String CAP_APPROVE = "HRM.RECRUITMENT.OFFER.APPROVE";

    @Autowired
    private HrOfferService offerService;

    @Autowired
    private WorkflowApprovalService approvalService;

    @Autowired
    private WorkflowApprovalRequestRepository approvalRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private javax.sql.DataSource dataSource;

    private UUID tenantId;
    private UUID submitterId;
    private UUID approverUserId;
    private UUID applicationId;
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
        submitterId = UUID.randomUUID();
        approverUserId = UUID.randomUUID();

        seed("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                + "VALUES (?, 'T7 Offer Y2', ?, 'ACTIVE', ?, ?)",
                tenantId, "t7o-" + tenantId.toString().substring(0, 8), now, now);
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Submitter', 'ACTIVE', 'x', ?, ?)",
                submitterId, tenantId, "t7s-" + submitterId.toString().substring(0, 8) + "@t", now, now);
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Approver', 'ACTIVE', 'x', ?, ?)",
                approverUserId, tenantId, "t7a-" + approverUserId.toString().substring(0, 8) + "@t", now, now);

        // Approver employee holding the OFFER.APPROVE capability (WORK_POOL candidate)
        UUID employeeId = UUID.randomUUID();
        seed("""
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at)
                VALUES (?, ?, ?, 'E-T7', 'Offer', 'Approver', 'Offer Approver', 'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId, approverUserId, now, now);
        seed("""
                INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                VALUES (?, ?, 'T7 Offer Approve', 'test', NOW(), NOW())
                ON CONFLICT (code) DO NOTHING
                """, UUID.nameUUIDFromBytes(("cap:" + CAP_APPROVE).getBytes()), CAP_APPROVE);
        UUID role = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_OFFER_APPROVER', 'Offer Approver', 'ACTIVE', ?, ?)",
                role, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, ?::timestamptz
                FROM access_capabilities ac WHERE ac.code = ?
                """, tenantId, role, now, CAP_APPROVE);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, approverUserId, role, now, now);
        // submitter holds OFFER.MANAGE + OFFER.EXTEND through real RBAC
        for (String cap : new String[]{"HRM.RECRUITMENT.OFFER.MANAGE", "HRM.RECRUITMENT.OFFER.EXTEND"}) {
            seed("""
                    INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                    VALUES (?, ?, 'T7 Offer Cap', 'test', NOW(), NOW())
                    ON CONFLICT (code) DO NOTHING
                    """, UUID.nameUUIDFromBytes(("cap:" + cap).getBytes()), cap);
        }
        UUID submitterRole = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_OFFER_MANAGER', 'Offer Manager', 'ACTIVE', ?, ?)",
                submitterRole, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, NOW() FROM access_capabilities ac
                WHERE ac.code IN ('HRM.RECRUITMENT.OFFER.MANAGE', 'HRM.RECRUITMENT.OFFER.EXTEND')
                """, tenantId, submitterRole);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, submitterId, submitterRole, now, now);
        // G0 scoped authorization: TENANT-wide scope grants for the matched roles
        seed("""
                INSERT INTO access_scope_grants (id, tenant_id, role_id, capability_id, scope_type, status, effective_from)
                SELECT gen_random_uuid(), ?::uuid, rc.role_id, rc.capability_id, 'TENANT', 'ACTIVE', NOW()
                FROM role_capabilities rc WHERE rc.tenant_id = ?::uuid
                ON CONFLICT DO NOTHING
                """, tenantId, tenantId);
        grantWorkflowEntitlement();
        applicationId = seedCandidateOpeningApplication();
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
                """, planId, "T7O_" + UUID.randomUUID().toString().substring(0, 8),
                "T7 offer plan", "ACTIVE");
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

    private UUID seedCandidateOpeningApplication() {
        UUID candidateId = UUID.randomUUID();
        seed("INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state, version) "
                + "VALUES (?, ?, 'CAND-T7W', 'T7 Workflow Candidate', 'ACTIVE', 0)", candidateId, tenantId);
        UUID organizationId = UUID.randomUUID();
        seed("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'T7 Org', 'ACTIVE', ?, ?)", organizationId, tenantId, now, now);
        seed("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7W', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        seed("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T7W', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        UUID openingId = UUID.randomUUID();
        seed("""
                INSERT INTO hr_job_openings (id, tenant_id, opening_number, job_id, org_unit_id,
                    state, requested_headcount, version)
                SELECT ?::uuid, j.tenant_id, 'OFF-T7W-1', j.id, u.id, 'OPEN', 1, 0
                FROM hr_jobs j, hr_org_units u WHERE j.tenant_id = ?::uuid AND u.tenant_id = ?::uuid LIMIT 1
                """, openingId, tenantId, tenantId);
        UUID appId = UUID.randomUUID();
        seed("INSERT INTO hr_applications (id, tenant_id, candidate_id, job_opening_id, state, version) "
                + "VALUES (?, ?, ?, ?, 'OFFER', 0)", appId, tenantId, candidateId, openingId);
        return appId;
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    private UUID createOffer() {
        return offerService.createOffer(ctx(submitterId), applicationId,
                "{\"base_salary\":111000,\"currency\":\"SAR\"}", "{\"band\":\"B3\"}", null);
    }

    private UUID submit(UUID offerId) {
        return offerService.submitForApproval(ctx(submitterId), offerId);
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

    private void approveRequest(UUID instanceId, UUID approver) {
        Map<String, Object> row = requestsFor(instanceId).stream()
                .filter(r -> r.get("requested_from_user_id").equals(approver))
                .findFirst().orElseThrow();
        approvalService.approve(tenantId, (UUID) row.get("id"), approver,
                ((Number) row.get("version")).longValue(), "T7 integration approve");
    }

    private String offerState(UUID offerId) {
        return qObj("SELECT state FROM hr_offers WHERE id = ?", String.class, offerId);
    }

    @Test
    void realY2Approval_extendsOffer_exactlyOnce() {
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);

        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
        assertThat(requestsFor(instanceId)).as("the Y2 pool resolution addressed the capable approver")
                .isNotEmpty();

        approveRequest(instanceId, approverUserId);

        assertThat(qObj(
                "SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("COMPLETED");
        offerService.extendFromApproval(ctx(submitterId), offerId, instanceId);
        assertThat(offerState(offerId)).isEqualTo("EXTENDED");

        // replay of the same completion is side-effect-free
        offerService.extendFromApproval(ctx(submitterId), offerId, instanceId);
        assertThat(qObj(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = "
                        + "'HRM.RECRUITMENT.OFFER_EXTENDED'", Integer.class, offerId)).isEqualTo(1);
        assertThat(qObj(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? AND event_type = "
                        + "'HRM.RECRUITMENT.OFFER_EXTENDED'", Integer.class, offerId)).isEqualTo(1);
    }

    @Test
    void duplicateSubmit_reusesTheSameAuthoritativeInstance() {
        UUID offerId = createOffer();
        UUID first = submit(offerId);
        UUID second = submit(offerId);
        assertThat(first).isEqualTo(second);
        assertThat(qObj(
                "SELECT COUNT(*) FROM workflow_instances WHERE business_entity_type = 'HR_OFFER' "
                        + "AND business_entity_id = ?", Integer.class, offerId)).isEqualTo(1);
    }

    @Test
    void incompleteApproval_cannotExtend() {
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);
        assertThatThrownBy(() -> offerService.extendFromApproval(ctx(submitterId), offerId, instanceId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void selfApproval_isDeniedByTheEngine() {
        // make the SUBMITTER an additional capable approver — he is the
        // requester of the approval request, so Y2 SOD must deny his approval
        UUID approverEmployeeRole = qObj(
                "SELECT role_id FROM role_capabilities WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId);
        UUID submitterEmployeeId = UUID.randomUUID();
        seed("INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name, "
                + "display_name, employment_type, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'E-T7S', 'Submitter', 'Emp', 'Submitter Emp', 'FULL_TIME', 'ACTIVE', ?, ?)",
                submitterEmployeeId, tenantId, submitterId, now, now);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, submitterId, approverEmployeeRole, now, now);

        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);
        List<Map<String, Object>> requests = requestsFor(instanceId);
        assertThat(requests).isNotEmpty();
        for (Map<String, Object> request : requests) {
            assertThatThrownBy(() -> approvalService.approve(tenantId, (UUID) request.get("id"),
                            submitterId, ((Number) request.get("version")).longValue(), "self"))
                    .as("Y2 SOD: the requester can never satisfy their own approval");
        }
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void rejectedY2Outcome_returnsOfferToDraft() {
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);
        Map<String, Object> row = requestsFor(instanceId).get(0);
        approvalService.reject(tenantId, (UUID) row.get("id"), approverUserId,
                ((Number) row.get("version")).longValue(), "business rejection");

        offerService.rejectFromApproval(ctx(submitterId), offerId, instanceId, "OFFER_REJECTION");
        assertThat(offerState(offerId)).isEqualTo("DRAFT");
        // the obsolete result can never extend
        assertThatThrownBy(() -> offerService.extendFromApproval(ctx(submitterId), offerId, instanceId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fakeOrForeignWorkflow_isRefused() {
        UUID offerId = createOffer();
        submit(offerId);
        assertThatThrownBy(() -> offerService.extendFromApproval(ctx(submitterId), offerId, UUID.randomUUID()))
                .as("a fabricated workflow id can never extend an offer")
                .isInstanceOf(IllegalStateException.class);
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void cancelledApprovalCycle_cancelsWorkItems_andReturnsOfferToDraft() {
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);
        assertThat(requestsFor(instanceId).stream()
                .anyMatch(r -> "PENDING".equals(r.get("status")))).isTrue();

        offerService.cancelApprovalCycle(ctx(submitterId), offerId, instanceId, "OFFER_REJECTION");

        assertThat(offerState(offerId)).isEqualTo("DRAFT");
        assertThat(qObj(
                "SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("CANCELLED");
        assertThat(qObj(
                "SELECT COUNT(*) FROM workflow_approval_requests WHERE workflow_instance_id = ? "
                        + "AND status = 'PENDING'", Integer.class, instanceId)).isZero();
        assertThatThrownBy(() -> offerService.extendFromApproval(ctx(submitterId), offerId, instanceId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void workItems_andEvidence_carryNoRawCompensation() {
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);

        String workItems = qObj(
                "SELECT coalesce(string_agg(w.type || w.status || coalesce(w.assignee_employee_id::text,''), ' '), '') "
                        + "FROM workflow_work_items w JOIN workflow_instances i ON i.id = w.workflow_instance_id "
                        + "WHERE i.business_entity_id = ?", String.class, offerId);
        String definitions = qObj(
                "SELECT coalesce(string_agg(s.configuration::text, ' '), '') FROM workflow_steps s "
                        + "JOIN workflow_instances i ON i.workflow_definition_id = s.workflow_definition_id "
                        + "WHERE i.id = ?", String.class, instanceId);
        String audit = qObj(
                "SELECT coalesce(string_agg(action || coalesce(after_state::text,''), ' '), '') "
                        + "FROM hr_audit_ledger WHERE resource_id = ?", String.class, offerId);
        String outbox = qObj(
                "SELECT coalesce(string_agg(event_type || coalesce(payload::text,''), ' '), '') "
                        + "FROM hr_domain_event_outbox WHERE aggregate_id = ?", String.class, offerId);

        for (String surface : List.of(workItems, definitions, audit, outbox)) {
            assertThat(surface)
                    .as("gate §10/§11: no raw compensation or PII in Y2 surfaces")
                    .doesNotContain("111000").doesNotContain("B3").doesNotContain("base_salary")
                    .doesNotContain("@t");
        }
    }

    @Test
    void wrongVersionCorrelation_isRefused_atApplyTime() {
        // the offer's pending definition version pins the instance: a fabricated
        // snapshot mismatch is refused by the in-transaction verification
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);
        seed("UPDATE hr_offers SET pending_workflow_definition_version_id = ? WHERE id = ?",
                UUID.randomUUID(), offerId);
        approveRequest(instanceId, approverUserId);
        assertThatThrownBy(() -> offerService.extendFromApproval(ctx(submitterId), offerId, instanceId))
                .as("a stale definition-version binding can never move the offer")
                .isInstanceOf(IllegalStateException.class);
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
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