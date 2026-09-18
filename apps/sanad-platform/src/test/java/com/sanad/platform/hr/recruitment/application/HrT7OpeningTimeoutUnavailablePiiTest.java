package com.sanad.platform.hr.recruitment.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowSlaEscalationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * HRM-G1 T7 CLOSURE BATTERY (directive §4/§5/§10/§11) — opening-domain gaps
 * on top of {@link HrOpeningApprovalWorkflowIntegrationTest}:
 *
 * <ul>
 *   <li><b>T7-A33</b> Y2 timeout/SLA escalation NEVER publishes the opening —
 *       no auto-approval path exists anywhere on the HRM side;</li>
 *   <li><b>T7-A09</b> workflow unavailable (WORKFLOW module entitlement
 *       disabled) → submission fails closed, opening stays DRAFT;</li>
 *   <li><b>§5 T7_TRANSACTIONAL_APPROVAL_AUTHORITY (opening)</b> — a stale
 *       APPROVED pre-read cannot produce OPEN after the workflow authority is
 *       invalidated: the in-transaction FOR SHARE verification blocks against
 *       a competing uncommitted workflow mutation and re-reads the committed
 *       state before PENDING_APPROVAL → OPEN may commit;</li>
 *   <li><b>§10/§11 PII / compensation sentinel gate</b> — candidate PII and
 *       raw compensation sentinels planted at the tenant level never surface
 *       in workflow work items, workflow configuration, audit ledger, outbox
 *       payloads or application log events of the opening approval flow.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
class HrT7OpeningTimeoutUnavailablePiiTest {

    private static final String CAP_PUBLISH = "HRM.RECRUITMENT.OPENING.PUBLISH";

    /** §10/§11 sentinels — must NEVER appear on any governed surface. */
    private static final String EMAIL_SENTINEL = "t7-pii-sentinel@example.invalid";
    private static final String PHONE_SENTINEL = "+966500000007";
    private static final String NATIONAL_ID_SENTINEL = "NID-SENTINEL-7C4E9";
    private static final String COMPENSATION_SENTINEL = "99999999-RAW-COMP-SENTINEL";

    @Autowired
    private HrJobOpeningService openingService;

    @Autowired
    private WorkflowApprovalService approvalService;

    @Autowired
    private WorkflowSlaEscalationService slaEscalationService;

    @Autowired
    private javax.sql.DataSource dataSource;

    private UUID tenantId;
    private UUID managerId;
    private UUID publisherId;
    private UUID publisherEmployeeId;
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
                        + "VALUES (?, 'T7 Closure Opening', ?, 'ACTIVE', ?, ?)",
                tenantId, "t7co-" + tenantId.toString().substring(0, 8), now, now);
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Manager', 'ACTIVE', 'x', ?, ?)",
                managerId, tenantId, "t7cm-" + managerId.toString().substring(0, 8) + "@t", now, now);
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Publisher', 'ACTIVE', 'x', ?, ?)",
                publisherId, tenantId, "t7cp-" + publisherId.toString().substring(0, 8) + "@t", now, now);

        publisherEmployeeId = UUID.randomUUID();
        seed("""
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at)
                VALUES (?, ?, ?, 'E-T7CO', 'Closure', 'Publisher', 'Closure Publisher', 'FULL_TIME', 'ACTIVE', ?, ?)
                """, publisherEmployeeId, tenantId, publisherId, now, now);
        seed("""
                INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                VALUES (?, ?, 'T7 Closure Publish', 'test', NOW(), NOW())
                ON CONFLICT (code) DO NOTHING
                """, UUID.nameUUIDFromBytes(("cap:" + CAP_PUBLISH).getBytes()), CAP_PUBLISH);
        UUID role = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_CLOSURE_PUBLISHER', 'Closure Publisher', 'ACTIVE', ?, ?)",
                role, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, ?::timestamptz
                FROM access_capabilities ac WHERE ac.code = ?
                """, tenantId, role, now, CAP_PUBLISH);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, publisherId, role, now, now);
        seed("""
                INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                VALUES (?, 'HRM.RECRUITMENT.OPENING.MANAGE', 'T7 Closure Manage', 'test', NOW(), NOW())
                ON CONFLICT (code) DO NOTHING
                """, UUID.nameUUIDFromBytes("cap:HRM.RECRUITMENT.OPENING.MANAGE".getBytes()));
        UUID managerRole = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_CLOSURE_MGR', 'Closure Manager', 'ACTIVE', ?, ?)",
                managerRole, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, NOW() FROM access_capabilities ac
                WHERE ac.code = 'HRM.RECRUITMENT.OPENING.MANAGE'
                """, tenantId, managerRole);
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, managerId, managerRole, now, now);
        seed("""
                INSERT INTO access_scope_grants (id, tenant_id, role_id, capability_id, scope_type, status, effective_from)
                SELECT gen_random_uuid(), ?::uuid, rc.role_id, rc.capability_id, 'TENANT', 'ACTIVE', NOW()
                FROM role_capabilities rc WHERE rc.tenant_id = ?::uuid
                ON CONFLICT DO NOTHING
                """, tenantId, tenantId);
        grantWorkflowEntitlement();
        seedComplianceCompliantJurisdiction();
        seedCandidatePiiSentinel();
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
                """, planId, "T7CO_" + UUID.randomUUID().toString().substring(0, 8),
                "T7 closure opening plan", "ACTIVE");
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
                + "VALUES (?, ?, 'T7 Closure Org', 'ACTIVE', ?, ?)", organizationId, tenantId, now, now);
        UUID legalEntityId = UUID.randomUUID();
        seed("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, "
                        + "statutory_country_code, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'LE-T7CO', 'LE', 'SA', 'SA', 'ACTIVE', ?, ?)",
                legalEntityId, tenantId, now, now);
        seed("INSERT INTO organization_legal_entities (tenant_id, organization_id, legal_entity_id, "
                        + "effective_from, status) VALUES (?, ?, ?, CURRENT_DATE, 'ACTIVE')",
                tenantId, organizationId, legalEntityId);
        seed("INSERT INTO hr_country_packs (country_code, pack_code, pack_version, status, "
                        + "effective_from, legal_reviewed_at, legal_reviewed_by, certification_reference) "
                        + "VALUES ('SA', 'PACK-SA', 'v1', 'CERTIFIED', CURRENT_DATE, NOW(), 'legal', 'CERT-T7CO') "
                        + "ON CONFLICT (country_code, pack_code, pack_version) DO NOTHING");
        seed("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7CO', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        seed("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T7CO', ?)", UUID.randomUUID(), tenantId, organizationId, now);
    }

    /**
     * §10/§11 probe: the tenant genuinely HOLDS sensitive candidate data and a
     * raw compensation value — the gate proves that none of it reaches the
     * opening approval surfaces.
     */
    private void seedCandidatePiiSentinel() {
        UUID candidateId = UUID.randomUUID();
        seed("INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state, version) "
                + "VALUES (?, ?, 'CAND-T7CO', 'PII Sentinel Candidate', 'ACTIVE', 0)", candidateId, tenantId);
        // best-effort contact columns (schema evolution tolerant)
        try {
            seed("UPDATE hr_candidates SET email = ?, phone = ? WHERE id = ?",
                    EMAIL_SENTINEL, PHONE_SENTINEL, candidateId);
        } catch (RuntimeException ignored) {
            // column set differs — the display_name sentinel below still probes
        }
        seed("UPDATE hr_candidates SET display_name = ? WHERE id = ?",
                "Candidate " + NATIONAL_ID_SENTINEL + " " + COMPENSATION_SENTINEL, candidateId);
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    private UUID createOpening() {
        return openingService.create(ctx(managerId),
                qObj("SELECT id FROM hr_jobs WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null,
                qObj("SELECT id FROM hr_org_units WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null, 1, null, null);
    }

    private String openingState(UUID openingId) {
        return qObj("SELECT state FROM hr_job_openings WHERE id = ?", String.class, openingId);
    }

    private Map<String, Object> firstRequest(UUID instanceId) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = c.prepareStatement(
                    "SELECT id, requested_from_user_id, status, version FROM workflow_approval_requests "
                            + "WHERE workflow_instance_id = ? ORDER BY created_at LIMIT 1")) {
                ps.setObject(1, instanceId);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    Map<String, Object> row = Map.of(
                            "id", (UUID) rs.getObject("id"),
                            "requested_from_user_id", (UUID) rs.getObject("requested_from_user_id"),
                            "status", rs.getString("status"),
                            "version", rs.getLong("version"));
                    c.commit();
                    return row;
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== T7-A33: timeout never auto-publishes ====================

    @Test
    void timeoutEscalation_neverAutoPublishesOpening() {
        UUID openingId = createOpening();
        UUID instanceId = openingService.submit(ctx(managerId), openingId);
        assertThat(openingState(openingId)).isEqualTo("PENDING_APPROVAL");

        seed("UPDATE workflow_work_items SET sla_due_at = NOW() - INTERVAL '1 hour' "
                        + "WHERE workflow_instance_id = ? AND status IN ('AVAILABLE','CLAIMED','IN_PROGRESS')",
                instanceId);
        int escalated = slaEscalationService.escalateTenant(tenantId);
        assertThat(escalated).as("the overdue opening approval work item is escalated")
                .isGreaterThanOrEqualTo(1);
        assertThat(qObj("SELECT COUNT(*) FROM workflow_incidents WHERE workflow_instance_id = ? "
                + "AND failure_category = 'SLA_BREACH' AND status IN ('OPEN','ACKNOWLEDGED')",
                Integer.class, instanceId)).isEqualTo(1);

        // Y2 escalation is attention + evidence, NEVER a decision: no approval
        // request is granted and the opening cannot reach OPEN by any path.
        assertThat(qObj("SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("RUNNING");
        assertThat(qObj("SELECT COUNT(*) FROM workflow_approval_requests "
                        + "WHERE workflow_instance_id = ? AND status = 'APPROVED'",
                Integer.class, instanceId)).isZero();
        assertThat(openingState(openingId)).as("timeout never auto-publishes").isEqualTo("PENDING_APPROVAL");

        // the governed apply path still fails closed after pure escalation
        assertThatThrownBy(() -> openingService.approve(ctx(publisherId), openingId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HRM_OPENING_APPROVAL_OUTCOME_PENDING");
        assertThat(openingState(openingId)).isEqualTo("PENDING_APPROVAL");
    }

    // ==================== T7-A09: workflow unavailable → fail closed ====================

    @Test
    void workflowUnavailable_failsClosed_openingStaysDraft() {
        UUID openingId = createOpening();
        seed("UPDATE plan_module_entitlements SET module_enabled = false WHERE plan_id IN "
                + "(SELECT plan_id FROM tenant_subscriptions WHERE tenant_id = ?::uuid)", tenantId);

        assertThatThrownBy(() -> openingService.submit(ctx(managerId), openingId))
                .as("with the WORKFLOW module disabled the authoritative approval start is refused")
                .isInstanceOf(Exception.class);

        assertThat(openingState(openingId)).as("fail-closed: the opening never left DRAFT").isEqualTo("DRAFT");
        assertThat(qObj("SELECT pending_workflow_instance_id FROM hr_job_openings WHERE id = ?",
                UUID.class, openingId)).isNull();
        assertThat(qObj("SELECT COUNT(*) FROM workflow_instances WHERE business_entity_type = 'HR_JOB_OPENING' "
                + "AND business_entity_id = ?", Integer.class, openingId)).isZero();
    }

    // ============ §5: T7_TRANSACTIONAL_APPROVAL_AUTHORITY (opening) ============

    @Test
    void transactionalAuthority_stalePreRead_cannotPublishAfterAuthorityIsInvalidated() throws Exception {
        UUID openingId = createOpening();
        UUID instanceId = openingService.submit(ctx(managerId), openingId);
        Map<String, Object> request = firstRequest(instanceId);
        assertThat(request.get("requested_from_user_id")).isEqualTo(publisherId);
        approvalService.approve(tenantId, (UUID) request.get("id"), publisherId,
                ((Number) request.get("version")).longValue(), "closure approve");
        assertThat(qObj("SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("COMPLETED");

        java.util.concurrent.CompletableFuture<Throwable> t1Result = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CountDownLatch t1Started = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean t1Finished = new AtomicBoolean(false);
        AtomicReference<String> t1Error = new AtomicReference<>("");

        // T1: governed apply-time publication (PUBLISH re-check + compliance +
        // in-transaction FOR SHARE verification + conditional transition).
        Thread t1 = new Thread(() -> {
            t1Started.countDown();
            try {
                try {
                    openingService.approve(ctx(publisherId), openingId);
                } catch (RuntimeException expected) {
                    t1Error.set(expected.getMessage());
                }
                t1Finished.set(true);
                t1Result.complete(null);
            } catch (Throwable t) {
                t1Result.complete(t);
            }
        });
        t1.start();
        assertThat(t1Started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        // T2: competing workflow mutation — UNCOMMITTED, holds the instance row.
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

            t1.join(700);
            assertThat(t1Finished.get())
                    .as("T1 blocks on the in-transaction FOR SHARE verification (no stale pre-read path)")
                    .isFalse();

            t2.commit(); // authority invalidated: instance is now CANCELLED
        }

        t1.join(30_000);
        t1Result.get(1, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(t1Finished.get()).isTrue();

        assertThat(t1Error.get())
                .as("T1 re-read the workflow state inside its transaction and failed closed")
                .contains("HRM_OPENING_APPROVAL_OUTCOME_PENDING");
        assertThat(openingState(openingId)).isEqualTo("PENDING_APPROVAL");
        assertThat(qObj("SELECT pending_workflow_instance_id FROM hr_job_openings WHERE id = ?",
                UUID.class, openingId)).isEqualTo(instanceId);
        assertThat(qObj("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                + "AND event_type = 'HRM.RECRUITMENT.OPENING_PUBLISHED'",
                Integer.class, openingId)).isZero();
    }

    // ============ §10/§11: PII + raw-compensation sentinel gate (opening) ============

    @Test
    void openingApproval_surfaces_carryNoPiiOrRawCompensation_includingLogs() {
        ListAppender<ILoggingEvent> logCapture = new ListAppender<>();
        logCapture.start();
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logCapture);
        try {
            UUID openingId = createOpening();
            UUID instanceId = openingService.submit(ctx(managerId), openingId);
            Map<String, Object> request = firstRequest(instanceId);
            approvalService.approve(tenantId, (UUID) request.get("id"), publisherId,
                    ((Number) request.get("version")).longValue(), "closure approve");
            openingService.approve(ctx(publisherId), openingId);
            assertThat(openingState(openingId)).isEqualTo("OPEN");

            // every surface is scanned as FULL ROW JSON — nothing can hide in a
            // column the test forgot to project
            String workItems = qObj(
                    "SELECT coalesce(string_agg(row_to_json(w)::text, ' '), '') "
                            + "FROM workflow_work_items w WHERE w.workflow_instance_id = ?",
                    String.class, instanceId);
            String instances = qObj(
                    "SELECT coalesce(string_agg(row_to_json(i)::text, ' '), '') "
                            + "FROM workflow_instances i WHERE i.id = ?",
                    String.class, instanceId);
            String stepConfig = qObj(
                    "SELECT coalesce(string_agg(s.configuration::text, ' '), '') "
                            + "FROM workflow_steps s JOIN workflow_instances i "
                            + "ON i.workflow_definition_id = s.workflow_definition_id WHERE i.id = ?",
                    String.class, instanceId);
            String audit = qObj(
                    "SELECT coalesce(string_agg(row_to_json(a)::text, ' '), '') "
                            + "FROM hr_audit_ledger a WHERE a.resource_id = ?",
                    String.class, openingId);
            String outbox = qObj(
                    "SELECT coalesce(string_agg(row_to_json(o)::text, ' '), '') "
                            + "FROM hr_domain_event_outbox o WHERE o.aggregate_id = ?",
                    String.class, openingId);
            String incidents = qObj(
                    "SELECT coalesce(string_agg(row_to_json(x)::text, ' '), '') "
                            + "FROM workflow_incidents x WHERE x.workflow_instance_id = ?",
                    String.class, instanceId);

            List<String> surfaces = List.of(workItems, instances, stepConfig, audit, outbox, incidents);
            List<String> sentinels = List.of(EMAIL_SENTINEL, PHONE_SENTINEL, NATIONAL_ID_SENTINEL,
                    COMPENSATION_SENTINEL, "example.invalid", "RAW-COMP-SENTINEL");
            for (String surface : surfaces) {
                assertThat(surface)
                        .as("gate §10/§11: no PII/compensation sentinel on this governed surface")
                        .doesNotContain(sentinels.toArray(new String[0]));
            }
            for (ILoggingEvent event : logCapture.list) {
                String line = event.getFormattedMessage();
                assertThat(line)
                        .as("gate §11: application log events never carry the planted sentinels")
                        .doesNotContain(EMAIL_SENTINEL)
                        .doesNotContain(PHONE_SENTINEL)
                        .doesNotContain(NATIONAL_ID_SENTINEL)
                        .doesNotContain(COMPENSATION_SENTINEL);
            }
        } finally {
            logCapture.stop();
            rootLogger.detachAppender(logCapture);
        }
    }

    // ===== pool/security binding mirrors of the existing T7 conventions =====

    private void bindTenantToSecurityContext() {
        var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "t7-closure-opening-actor", "n/a", java.util.List.of());
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
