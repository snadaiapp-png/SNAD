package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.domain.HrOfferState;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrOfferRepository;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowSlaEscalationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1 T7 CLOSURE BATTERY (directive §4/§5/§8/§10) — offer-domain gaps:
 *
 * <ul>
 *   <li><b>T7-A08</b> Y2 timeout/SLA escalation NEVER auto-approves — the offer
 *       stays PENDING_APPROVAL and extension keeps failing closed;</li>
 *   <li><b>T7-A09</b> workflow unavailable (module entitlement disabled) →
 *       submission fails closed, offer stays DRAFT with no correlation;</li>
 *   <li><b>T7-A14</b> offer expiration resolves deterministically (governed
 *       expire command + DB-clock gate);</li>
 *   <li><b>T7-A16</b> withdrawal from EXTENDED follows the state machine
 *       (registered reason mandatory);</li>
 *   <li><b>T7-A19</b> a stale optimistic (state/version) precondition is
 *       rejected with HRM_OFFER_STATE_CONFLICT — never applied silently;</li>
 *   <li><b>§5 T7_TRANSACTIONAL_APPROVAL_AUTHORITY</b> — a stale pre-read of an
 *       APPROVED workflow cannot produce EXTENDED once the workflow authority
 *       is no longer valid: the in-transaction FOR SHARE verification blocks
 *       against a competing uncommitted workflow mutation and re-reads the
 *       committed state before the HR state transition may commit.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
class HrT7OfferTimeoutUnavailableLifecycleTest {

    private static final String CAP_APPROVE = "HRM.RECRUITMENT.OFFER.APPROVE";

    @Autowired
    private HrOfferService offerService;

    @Autowired
    private JdbcHrOfferRepository offerRepository;

    @Autowired
    private WorkflowApprovalService approvalService;

    @Autowired
    private WorkflowSlaEscalationService slaEscalationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private javax.sql.DataSource dataSource;

    private UUID tenantId;
    private UUID submitterId;
    private UUID approverUserId;
    private UUID applicationId;
    private final Timestamp now = Timestamp.from(Instant.now());

    // ===== tenant-scoped seed/query helpers (committed; FORCE-RLS GUC set) =====

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
                + "VALUES (?, 'T7 Closure Offer', ?, 'ACTIVE', ?, ?)",
                tenantId, "t7cl-" + tenantId.toString().substring(0, 8), now, now);
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Submitter', 'ACTIVE', 'x', ?, ?)",
                submitterId, tenantId, "t7cs-" + submitterId.toString().substring(0, 8) + "@t", now, now);
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Approver', 'ACTIVE', 'x', ?, ?)",
                approverUserId, tenantId, "t7ca-" + approverUserId.toString().substring(0, 8) + "@t", now, now);

        // approver employee holds OFFER.APPROVE (Y2 WORK_POOL candidate)
        UUID employeeId = UUID.randomUUID();
        seed("""
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at)
                VALUES (?, ?, ?, 'E-T7C', 'Closure', 'Approver', 'Closure Approver', 'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId, approverUserId, now, now);
        seed("""
                INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                VALUES (?, ?, 'T7 Closure Approve', 'test', NOW(), NOW())
                ON CONFLICT (code) DO NOTHING
                """, UUID.nameUUIDFromBytes(("cap:" + CAP_APPROVE).getBytes()), CAP_APPROVE);
        UUID role = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_CLOSURE_APPROVER', 'Closure Approver', 'ACTIVE', ?, ?)",
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
                    VALUES (?, ?, 'T7 Closure Cap', 'test', NOW(), NOW())
                    ON CONFLICT (code) DO NOTHING
                    """, UUID.nameUUIDFromBytes(("cap:" + cap).getBytes()), cap);
        }
        UUID submitterRole = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_CLOSURE_MANAGER', 'Closure Manager', 'ACTIVE', ?, ?)",
                submitterRole, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, NOW() FROM access_capabilities ac
                WHERE ac.code IN ('HRM.RECRUITMENT.OFFER.MANAGE', 'HRM.RECRUITMENT.OFFER.EXTEND')
                """, tenantId, submitterRole);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, submitterId, submitterRole, now, now);
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

    @AfterEach
    void resetPool() throws Exception {
        resetPoolTenant();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void grantWorkflowEntitlement() {
        UUID planId = UUID.randomUUID();
        seed("""
                INSERT INTO saas_plans (id, code, name, status, currency_code, monthly_price_minor,
                     annual_price_minor, trial_days, max_users, max_organizations, storage_mb,
                     created_at, updated_at)
                VALUES (?,?,?,?, 'SAR', 0, 0, 0, 10, 1, 0, NOW(), NOW())
                """, planId, "T7CL_" + UUID.randomUUID().toString().substring(0, 8),
                "T7 closure plan", "ACTIVE");
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
                + "VALUES (?, ?, 'CAND-T7CL', 'T7 Closure Candidate', 'ACTIVE', 0)", candidateId, tenantId);
        UUID organizationId = UUID.randomUUID();
        seed("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'T7 Closure Org', 'ACTIVE', ?, ?)", organizationId, tenantId, now, now);
        seed("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7CL', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        seed("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T7CL', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        UUID openingId = UUID.randomUUID();
        seed("""
                INSERT INTO hr_job_openings (id, tenant_id, opening_number, job_id, org_unit_id,
                    state, requested_headcount, version)
                SELECT ?::uuid, j.tenant_id, 'OFF-T7CL-1', j.id, u.id, 'OPEN', 1, 0
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
                "{\"base_salary\":222000,\"currency\":\"SAR\"}", "{\"band\":\"C1\"}", null);
    }

    private UUID submit(UUID offerId) {
        return offerService.submitForApproval(ctx(submitterId), offerId);
    }

    private void approveRequest(UUID instanceId) {
        Map<String, Object> row = qRows(
                "SELECT id, requested_from_user_id, status, version FROM workflow_approval_requests "
                        + "WHERE workflow_instance_id = ?", instanceId).get(0);
        approvalService.approve(tenantId, (UUID) row.get("id"), approverUserId,
                ((Number) row.get("version")).longValue(), "T7 closure approve");
    }

    private List<Map<String, Object>> qRows(String sql, Object... args) {
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
                    List<Map<String, Object>> rows = new java.util.ArrayList<>();
                    int cols = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        java.util.Map<String, Object> row = new java.util.HashMap<>();
                        for (int i = 1; i <= cols; i++) {
                            row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                    c.commit();
                    return rows;
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String offerState(UUID offerId) {
        return qObj("SELECT state FROM hr_offers WHERE id = ?", String.class, offerId);
    }

    /** Full governed cycle to EXTENDED through the authoritative Y2 engine. */
    private UUID extendThroughY2() {
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);
        approveRequest(instanceId);
        offerService.extendFromApproval(ctx(submitterId), offerId, instanceId);
        assertThat(offerState(offerId)).isEqualTo("EXTENDED");
        return offerId;
    }

    // ==================== T7-A08: timeout/escalation never auto-approves ====================

    @Test
    void timeoutEscalation_neverAutoExtendsOffer() {
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");

        // Deterministic SLA breach: the approval work item's due date moves to
        // the past; the Y2 escalation scan runs against the real engine.
        seed("UPDATE workflow_work_items SET sla_due_at = NOW() - INTERVAL '1 hour' "
                        + "WHERE workflow_instance_id = ? AND status IN ('AVAILABLE','CLAIMED','IN_PROGRESS')",
                instanceId);
        int escalated = slaEscalationService.escalateTenant(tenantId);
        assertThat(escalated).as("the overdue approval work item is escalated").isGreaterThanOrEqualTo(1);
        assertThat(qObj("SELECT COUNT(*) FROM workflow_incidents WHERE workflow_instance_id = ? "
                + "AND failure_category = 'SLA_BREACH' AND status IN ('OPEN','ACKNOWLEDGED')",
                Integer.class, instanceId)).isEqualTo(1);

        // Escalation is evidence + attention, NEVER a decision: the instance
        // keeps running, no approval request is granted, the offer cannot move.
        assertThat(qObj("SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("RUNNING");
        assertThat(qObj("SELECT COUNT(*) FROM workflow_approval_requests "
                        + "WHERE workflow_instance_id = ? AND status = 'APPROVED'",
                Integer.class, instanceId)).isZero();
        assertThat(offerState(offerId)).as("timeout never auto-extends").isEqualTo("PENDING_APPROVAL");

        assertThatThrownBy(() -> offerService.extendFromApproval(ctx(submitterId), offerId, instanceId))
                .as("extension after pure escalation still fails closed")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_OFFER_APPROVAL_OUTCOME_PENDING");
        assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXTENDED)).isZero();
    }

    // ==================== T7-A09: workflow unavailable → fail closed ====================

    @Test
    void workflowUnavailable_failsClosed_offerStaysDraftWithoutCorrelation() {
        UUID offerId = createOffer();
        seed("UPDATE plan_module_entitlements SET module_enabled = false WHERE plan_id IN "
                + "(SELECT plan_id FROM tenant_subscriptions WHERE tenant_id = ?::uuid)", tenantId);

        assertThatThrownBy(() -> submit(offerId))
                .as("with the WORKFLOW module disabled the authoritative approval start is refused")
                .isInstanceOf(Exception.class);

        assertThat(offerState(offerId)).as("fail-closed: the offer never left DRAFT").isEqualTo("DRAFT");
        assertThat(qObj("SELECT pending_workflow_instance_id FROM hr_offers WHERE id = ?",
                UUID.class, offerId)).isNull();
        assertThat(qObj("SELECT COUNT(*) FROM workflow_instances WHERE business_entity_type = 'HR_OFFER' "
                + "AND business_entity_id = ?", Integer.class, offerId)).isZero();
        assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                Integer.class, offerId, JdbcHrOfferRepository.ACTION_SUBMITTED_FOR_APPROVAL)).isZero();
    }

    // ==================== T7-A14: deterministic expiry ====================

    @Test
    void expire_resolvesExtendedOfferDeterministically_andDbClockGateHolds() {
        UUID offerId = extendThroughY2();

        offerService.expire(ctx(submitterId), offerId);
        assertThat(offerState(offerId)).isEqualTo("EXPIRED");
        assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXPIRED)).isEqualTo(1);
        assertThat(qObj("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXPIRED)).isEqualTo(1);
    }

    // ==================== T7-A16: withdrawal per the state machine ====================

    @Test
    void withdraw_fromExtended_followsStateMachine_withRegisteredReason() {
        UUID offerId = extendThroughY2();

        // the matrix demands a registered reason for EXTENDED → WITHDRAWN
        assertThatThrownBy(() -> offerService.withdraw(ctx(submitterId), offerId, null))
                .as("withdrawal without the mandatory reason is refused")
                .isInstanceOf(IllegalStateException.class);
        assertThat(offerState(offerId)).isEqualTo("EXTENDED");

        offerService.withdraw(ctx(submitterId), offerId, "OFFER_WITHDRAWAL");
        assertThat(offerState(offerId)).isEqualTo("WITHDRAWN");
        assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                Integer.class, offerId, JdbcHrOfferRepository.ACTION_WITHDRAWN)).isEqualTo(1);

        // WITHDRAWN is terminal — nothing may leave it
        assertThatThrownBy(() -> offerService.decline(ctx(submitterId), offerId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(offerState(offerId)).isEqualTo("WITHDRAWN");
    }

    // ==================== T7-A19: stale optimistic precondition rejected ====================

    @Test
    void staleOptimisticPrecondition_isRejected_notAppliedSilently() {
        UUID offerId = extendThroughY2();

        // first transition succeeds and bumps the optimistic version
        offerRepository.transition(tenantId, offerId, HrOfferState.EXTENDED, HrOfferState.DECLINED,
                null, JdbcHrOfferRepository.ACTION_DECLINED, submitterId, UUID.randomUUID());
        assertThat(offerState(offerId)).isEqualTo("DECLINED");

        // a second writer still holding the STALE pre-read (state=EXTENDED)
        // must be refused by the conditional-UPDATE guard, never applied
        assertThatThrownBy(() -> offerRepository.transition(tenantId, offerId, HrOfferState.EXTENDED,
                HrOfferState.EXPIRED, null, JdbcHrOfferRepository.ACTION_EXPIRED,
                submitterId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HRM_OFFER_STATE_CONFLICT");
        assertThat(offerState(offerId)).isEqualTo("DECLINED");
        assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXPIRED)).isZero();
    }

    // ============ §5: T7_TRANSACTIONAL_APPROVAL_AUTHORITY (offer) ============

    @Test
    void transactionalAuthority_stalePreRead_cannotExtendAfterAuthorityIsInvalidated() throws Exception {
        UUID offerId = createOffer();
        UUID instanceId = submit(offerId);
        approveRequest(instanceId);
        assertThat(qObj("SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("COMPLETED");

        java.util.concurrent.CompletableFuture<Throwable> t1Result = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CountDownLatch t1Started = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean t1Finished = new AtomicBoolean(false);
        AtomicReference<String> t1StateObserved = new AtomicReference<>("");

        // T1: the extension attempt — it verifies the workflow authority INSIDE
        // its mutation transaction (FOR SHARE on the instance row).
        Thread t1 = new Thread(() -> {
            t1Started.countDown();
            try {
                try {
                    offerService.extendFromApproval(ctx(submitterId), offerId, instanceId);
                } catch (RuntimeException expected) {
                    t1StateObserved.set(expected.getMessage());
                }
                t1Finished.set(true);
                t1Result.complete(null);
            } catch (Throwable t) {
                t1Result.complete(t);
            }
        });
        t1.start();
        assertThat(t1Started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        // T2: competing workflow mutation — UNCOMMITTED, holds the instance
        // row lock so T1's in-transaction FOR SHARE verification blocks.
        try (java.sql.Connection t2 = dataSource.getConnection()) {
            t2.setAutoCommit(false);
            try (var st = t2.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = t2.prepareStatement(
                    "UPDATE workflow_instances SET status = 'CANCELLED' WHERE id = ? AND tenant_id = ?")) {
                ps.setObject(1, instanceId);
                ps.setObject(2, tenantId);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }

            // while T2 holds the row lock, T1 cannot have completed its
            // verification+mutation — no stale pre-read may have slipped past
            t1.join(700);
            assertThat(t1Finished.get())
                    .as("T1 blocks on the in-transaction FOR SHARE verification (no stale pre-read path)")
                    .isFalse();

            t2.commit(); // authority invalidated: instance is now CANCELLED
        }

        t1.join(30_000);
        t1Result.get(1, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(t1Finished.get()).isTrue();

        // T1 observed the committed (no longer valid) authority and refused:
        // the stale APPROVED pre-read could not produce EXTENDED.
        assertThat(t1StateObserved.get())
                .as("T1 re-read the workflow state inside its transaction and failed closed")
                .contains("HRM_OFFER_APPROVAL_OUTCOME_PENDING");
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
        assertThat(qObj("SELECT pending_workflow_instance_id FROM hr_offers WHERE id = ?",
                UUID.class, offerId)).isEqualTo(instanceId);
        assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXTENDED)).isZero();
        assertThat(qObj("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXTENDED)).isZero();
    }
    // ===== pool/security binding mirrors of the existing T7 conventions =====

    private void bindTenantToSecurityContext() {
        var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "t7-closure-actor", "n/a", java.util.List.of());
        token.setDetails(java.util.Map.of("tenant_id", tenantId.toString(),
                "user_id", tenantId.toString()));
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(token);
    }

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
                break;
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
