package com.sanad.platform.workflow;

import com.sanad.platform.workflow.analytics.WorkflowAnalyticsProjectionService;
import com.sanad.platform.workflow.analytics.WorkflowAnalyticsProjectionWorker;
import com.sanad.platform.workflow.analytics.WorkflowAnalyticsQueryService;
import com.sanad.platform.workflow.analytics.WorkflowPerformanceMetricsService;
import com.sanad.platform.workflow.application.WorkflowJourneyService;
import com.sanad.platform.workflow.application.WorkflowNotificationService;
import com.sanad.platform.workflow.experience.WorkflowTimerNotificationWorker;
import com.sanad.platform.workflow.notification.InAppNotificationProvider;
import com.sanad.platform.workflow.notification.WorkflowChannelRegistry;
import com.sanad.platform.workflow.notification.WorkflowNotificationDispatcher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2 MATRIX D (timer notifications), MATRIX E (analytics reconciliation),
 * MATRIX F (dashboards), MATRIX G (performance metrics). PostgreSQL Direct.
 * Deterministic fixtures; every dashboard number must reconcile to
 * Journey/segment/timer source evidence (R2.15).
 */
class WorkflowR2AnalyticsTest {

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static PlatformTransactionManager transactionManager;
    private static WorkflowNotificationService notifications;
    private static WorkflowJourneyService journeyService;
    private static WorkflowTimerNotificationWorker timerWorker;
    private static WorkflowAnalyticsProjectionService projectionService;
    private static WorkflowAnalyticsProjectionWorker projectionWorker;
    private static WorkflowAnalyticsQueryService queryService;
    private static WorkflowPerformanceMetricsService metricsService;
    private static boolean postgresAvailable;

    private final List<UUID> createdTenants = new ArrayList<>();

    @AfterEach
    void sweepFixtures() {
        for (UUID tenantId : createdTenants) {
            WorkflowTenantFixtureSweeper.sweepTenant(jdbc, transactionManager, tenantId);
        }
    }

    /** Runs a body inside a transaction-local app.tenant_id scope
     *  (workflow tables are FORCE RLS - V20261029_1). */
    private void inTenantScope(UUID tenantId, Runnable body) {
        tx.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            body.run();
        });
    }

    /** Result-bearing variant of {@link #inTenantScope(UUID, Runnable)}. */
    private <T> T inTenantScopeWithResult(UUID tenantId, java.util.function.Supplier<T> body) {
        return tx.execute(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            return body.get();
        });
    }

    @BeforeAll
    static void setup() {
        String url = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String pass = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "sanad_pass");
        try {
            Flyway.configure()
                    .dataSource(url, user, pass)
                    .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                    .cleanDisabled(true)
                    .load()
                    .migrate();
            postgresAvailable = true;
        } catch (Exception unavailable) {
            System.err.println("[WorkflowR2AnalyticsTest] PostgreSQL Direct "
                    + "unavailable, skipping: " + unavailable);
            postgresAvailable = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct unavailable — skipping in non-CI environment");

        DataSource dataSource = new DriverManagerDataSource(url, user, pass);
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(transactionManager);
        notifications = new WorkflowNotificationService(jdbc,
                new com.fasterxml.jackson.databind.ObjectMapper());
        journeyService = new WorkflowJourneyService(jdbc, new com.fasterxml.jackson.databind.ObjectMapper());
        timerWorker = new WorkflowTimerNotificationWorker(jdbc, notifications,
                journeyService, true, 72, 24);
        projectionService = new WorkflowAnalyticsProjectionService(jdbc);
        // FORCE RLS (V20261029_1): the projection worker needs the trusted
        // transaction-local tenant scope for its per-tenant pass.
        com.sanad.platform.security.rls.TenantRlsTransactionContext workerRls =
                new com.sanad.platform.security.rls.TenantRlsTransactionContext(jdbc);
        projectionWorker = new WorkflowAnalyticsProjectionWorker(jdbc, projectionService,
                transactionManager, workerRls, true);
        queryService = new WorkflowAnalyticsQueryService(jdbc);
        metricsService = new WorkflowPerformanceMetricsService(jdbc);
    }

    private record Fixture(UUID tenant, UUID user, UUID employee, UUID instance,
                           UUID stepId, UUID stepInstance, UUID workItem, UUID segment,
                           UUID timer) {}

    /**
     * Deterministic reconciliation fixture: one RUNNING instance with one
     * HUMAN_TASK work item, one ASSIGNED responsibility segment (fixed
     * 30-minute window), one EXTERNAL_WAIT segment (fixed 10-minute
     * window), one breached timer, journey events (process start, one
     * reassignment, one timeout), and a completion marker for the work item.
     */
    private Fixture reconciliationFixture(String tag) {
        // Scan-window determinism: WorkflowTimerNotificationWorker.runScan()
        // processes "SELECT id FROM tenants WHERE status='ACTIVE' ORDER BY id
        // LIMIT 200". In a full-suite run the shared database accumulates
        // hundreds of ACTIVE fixture tenants with random UUIDs, so a random
        // fixture id usually lands OUTSIDE the scanned window and the worker
        // never sees this fixture's timers (observed in Run 6: 0 notifications).
        // A low-order UUID (00000000-0000-4000-8000-xxxxxxxxxxxx) sorts before
        // every random v4 UUID, guaranteeing the fixture is inside the window;
        // the 48-bit random tail keeps inserts collision-free across runs.
        UUID tenant = UUID.fromString("00000000-0000-4000-8000-"
                + String.format("%012x", java.util.concurrent.ThreadLocalRandom.current().nextLong()
                        & 0xffffffffffffL));
        UUID user = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID definition = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        UUID stepInstance = UUID.randomUUID();
        UUID workItem = UUID.randomUUID();
        UUID segment = UUID.randomUUID();
        UUID timer = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        Instant start = Instant.now().minusSeconds(3600);

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)", tenant, "R2An-" + tag,
                "r2-an-" + tag + "-" + tenant.toString().substring(0, 8), now, now);
        tx.executeWithoutResult(txStatus -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenant.toString());
            // public.users and hr_employees are both FORCE RLS (V20261011_3 + V20260905_5).
            // DriverManagerDataSource is NOT a pool — every jdbc.execute()/jdbc.update()
            // OUTSIDE a tx-bound context gets a fresh physical connection that is closed
            // after the call. A session-scoped GUC set on conn N dies with conn N, so
            // the next INSERT on conn N+1 has no tenant context and RLS rejects it.
            // Fix: wrap set_config + INSERT users + INSERT hr_employees in ONE
            // tx.executeWithoutResult block so they share the tx-bound connection.
            // is_local=true (not false) scopes the GUC to this transaction only — no
            // session-level leak when the connection is returned to the (non-)pool.
            tx.executeWithoutResult(status -> {
                jdbc.execute("SELECT set_config('app.tenant_id', '" + tenant + "', true)");
                jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,"
                                + "created_at,updated_at) VALUES (?, ?, ?, 'R2 An', 'ACTIVE', 'dummy', ?, ?)",
                        user, tenant, "r2-an-" + user.toString().substring(0, 8) + "@test", now, now);
                jdbc.update("""
                        INSERT INTO hr_employees (
                            id, tenant_id, user_id, employee_number, first_name, last_name,
                            display_name, employment_type, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'R2', 'Fixture', 'R2 Fixture', 'FULL_TIME', 'ACTIVE', ?, NOW())
                        """, employee, tenant, user,
                        "R2-" + employee.toString().substring(0, 8), now);
            });
            jdbc.update("""
                    INSERT INTO workflow_definitions (
                        id, tenant_id, definition_family_id, code, name, module, version, status,
                        trigger_type, created_by, version_lock, engine_generation, publication_state,
                        schema_version, created_at, updated_at
                    ) VALUES (?, ?, ?, 'WF-R2-AN', 'R2Analytics', 'GENERAL', 1, 'ACTIVE',
                              'EVENT', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                    """, definition, tenant, definition, user, now, now);
            jdbc.update("""
                    INSERT INTO workflow_steps (
                        id, tenant_id, workflow_definition_id, step_key, name, step_type,
                        sequence_order, configuration, sla_hours, version, created_at, updated_at)
                    VALUES (?, ?, ?, 'review-step', 'Review step', 'HUMAN_TASK', 1,
                            CAST('{}' AS jsonb), NULL, 0, ?, NOW())
                    """, stepId, tenant, definition, Timestamp.from(start));
            jdbc.update("""
                    INSERT INTO workflow_instances (
                        id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                        business_entity_id, status, started_by, started_at, engine_generation,
                        context_json, context_schema_version, version, created_at, updated_at
                    ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, NOW(), 'Y2',
                              CAST('{}' AS jsonb), 1, 0, ?, ?)
                    """, instance, tenant, definition, user, now, now);
            jdbc.update("""
                    INSERT INTO workflow_step_instances (
                        id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                        status, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'review-step', 'IN_PROGRESS', 0, ?, NOW())
                    """, stepInstance, tenant, instance, stepId, Timestamp.from(start));
            jdbc.update("""
                    INSERT INTO workflow_work_items (
                        id, tenant_id, workflow_instance_id, workflow_step_instance_id, type,
                        status, assignment_mode, source_module, source_entity_type,
                        source_entity_id, title, assignee_employee_id, claimed_by_employee_id,
                        completed_at, sla_due_at, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'HUMAN_TASK', 'COMPLETED', 'DIRECT', 'CRM', 'account',
                            gen_random_uuid(), 'Reconcile me', ?, ?, ?, ?, 0, ?, NOW())
                    """, workItem, tenant, instance, stepInstance, employee, employee,
                    Timestamp.from(start.plusSeconds(1800)),
                    Timestamp.from(start.minusSeconds(600)),
                    Timestamp.from(start));
            // responsibility segments: 30 min ASSIGNED + 10 min EXTERNAL_WAIT
            jdbc.update("""
                    INSERT INTO workflow_responsibility_segments (
                        id, tenant_id, work_item_id, workflow_instance_id, segment_type,
                        owner_employee_id, started_at, ended_at, reason, created_at)
                    VALUES (?, ?, ?, ?, 'ASSIGNED', ?, ?, ?, 'fixture', NOW())
                    """, segment, tenant, workItem, instance, employee,
                    Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)));
            jdbc.update("""
                    INSERT INTO workflow_responsibility_segments (
                        id, tenant_id, work_item_id, workflow_instance_id, segment_type,
                        owner_employee_id, started_at, ended_at, reason, created_at)
                    VALUES (?, ?, ?, ?, 'EXTERNAL_WAIT', NULL, ?, ?, 'fixture', NOW())
                    """, UUID.randomUUID(), tenant, workItem, instance,
                    Timestamp.from(start), Timestamp.from(start.plusSeconds(600)));
            // timer: breached deadline
            jdbc.update("""
                    INSERT INTO workflow_timers (
                        id, tenant_id, scope, scope_id, purpose, state, policy,
                        due_at, breached_at, workflow_instance_id, work_item_id,
                        created_at, updated_at)
                    VALUES (?, ?, 'WORK_ITEM', ?, 'EXECUTION_DEADLINE', 'BREACHED',
                            'ESCALATE', ?, NOW(), ?, ?, NOW(), NOW())
                    """, timer, tenant, workItem, Timestamp.from(start.minusSeconds(300)),
                    instance, workItem);
            // journey events (idempotent keys)
            appendJourney(tenant, instance, "PROCESS_STARTED", "ps:" + instance, start);
            appendJourney(tenant, instance, "TASK_REASSIGNED", "tr:" + instance, start);
            appendJourney(tenant, instance, "TIMEOUT_EVENT", "te:" + timer, start);
        });
        createdTenants.add(tenant);
        return new Fixture(tenant, user, employee, instance, stepId, stepInstance,
                workItem, segment, timer);
    }

    private void appendJourney(UUID tenant, UUID instance, String eventType,
                               String eventKey, Instant at) {
        jdbc.update("""
                INSERT INTO workflow_journey (
                    tenant_id, event_type, workflow_instance_id, actor_type,
                    occurred_at, event_key)
                VALUES (?, ?, ?, 'SYSTEM', ?, ?)
                """, tenant, eventType, instance, Timestamp.from(at), eventKey);
    }

    // ===== MATRIX E: deterministic reconciliation (R2.15) =====

    @Test
    void projectionsReconcileExactlyToJourneySegmentAndTimerEvidence() {
        Fixture fx = reconciliationFixture("reconcile");
        // FORCE RLS (V20261029_1): the @Transactional projection service and the
        // facts verification run under the fixture tenant's transaction-local scope.
        inTenantScope(fx.tenant(), () -> {
        projectionService.projectInstance(fx.tenant(), fx.instance());
        Map<String, Object> facts = jdbc.queryForMap("""
                SELECT sla_breach_count, timeout_count, reassignment_count,
                       customer_wait_seconds, employee_responsibility_seconds,
                       derived_from_event_id
                  FROM workflow_analytics_process_facts
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, fx.tenant(), fx.instance());
        // every number traceable to source evidence:
        assertThat(((Number) facts.get("sla_breach_count")).intValue()).isEqualTo(1);
        assertThat(((Number) facts.get("timeout_count")).intValue()).isEqualTo(1);
        assertThat(((Number) facts.get("reassignment_count")).intValue()).isEqualTo(1);
        assertThat(((Number) facts.get("customer_wait_seconds")).longValue())
                .isEqualTo(600L);
        assertThat(((Number) facts.get("employee_responsibility_seconds")).longValue())
                .isEqualTo(1800L);
        Long journeyHead = jdbc.queryForObject("""
                SELECT COALESCE(MAX(id), 0) FROM workflow_journey
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Long.class, fx.tenant(), fx.instance());
        assertThat(((Number) facts.get("derived_from_event_id")).longValue())
                .isEqualTo(journeyHead);
        // step facts: 30-min employee segment, SLA breached (completed after due)
        Map<String, Object> step = jdbc.queryForMap("""
                SELECT employee_responsibility_seconds, sla_breached, step_key
                  FROM workflow_analytics_step_facts
                 WHERE tenant_id = ? AND workflow_step_instance_id = ?
                """, fx.tenant(), fx.stepInstance());
        assertThat(((Number) step.get("employee_responsibility_seconds")).longValue())
                .isEqualTo(1800L);
        assertThat(step.get("sla_breached")).isEqualTo(Boolean.TRUE);
        // deterministic rebuild -> identical facts (idempotent)
        projectionService.projectInstance(fx.tenant(), fx.instance());
        Map<String, Object> rebuilt = jdbc.queryForMap("""
                SELECT sla_breach_count, timeout_count, reassignment_count,
                       customer_wait_seconds, employee_responsibility_seconds,
                       derived_from_event_id
                  FROM workflow_analytics_process_facts
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, fx.tenant(), fx.instance());
        assertThat(rebuilt).isEqualTo(facts);
        // count of rows stays exactly one (upsert, not append)
        Integer rows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_analytics_process_facts
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Integer.class, fx.tenant(), fx.instance());
        assertThat(rows).isEqualTo(1);
        });
    }

    // ===== MATRIX F: dashboards aggregate the reconciled facts =====

    @Test
    void dashboardsAggregateReconciledFactsWithoutCrossTenantLeakage() {
        Fixture fx = reconciliationFixture("dashboard");
        inTenantScope(fx.tenant(), () -> {
            projectionService.projectInstance(fx.tenant(), fx.instance());
            Map<String, Object> service = queryService.serviceDashboard(
                    fx.tenant(), Instant.now().minusSeconds(86400), Instant.now());
            assertThat(((Number) service.get("total_instances")).longValue()).isEqualTo(1);
            assertThat(((Number) service.get("sla_breaches")).longValue()).isEqualTo(1);
        });
        // a different tenant sees ZERO (no cross-tenant aggregation); each
        // dashboard read runs under ITS OWN tenant's RLS scope.
        UUID otherTenant = UUID.randomUUID();
        inTenantScope(otherTenant, () -> {
            Map<String, Object> isolated = queryService.serviceDashboard(
                    otherTenant, Instant.now().minusSeconds(86400), Instant.now());
            assertThat(((Number) isolated.get("total_instances")).longValue()).isEqualTo(0);
        });
        inTenantScope(fx.tenant(), () -> {
            Map<String, Object> sla = queryService.slaDashboard(
                    fx.tenant(), Instant.now().minusSeconds(86400), Instant.now());
            assertThat(((Number) sla.get("compliance_percent")).doubleValue()).isEqualTo(0.0);
            Map<String, Object> bottleneck = queryService.bottleneckDashboard(
                    fx.tenant(), Instant.now().minusSeconds(86400), Instant.now(), 10);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bottlenecks =
                    (List<Map<String, Object>>) bottleneck.get("bottlenecks");
            assertThat(bottlenecks).hasSize(1);
            assertThat(bottlenecks.get(0).get("step_key")).isEqualTo("review-step");
        });
    }

    // ===== MATRIX G: explainable metrics =====

    @Test
    void performanceMetricsAreExplainableAndCarryNoAutomatedDecision() {
        Fixture fx = reconciliationFixture("metrics");
        // FORCE RLS (V20261029_1): the metrics read runs under the fixture
        // tenant's transaction-local scope (workflow_responsibility_segments,
        // workflow_work_items).
        var result = inTenantScopeWithResult(fx.tenant(), () -> metricsService.employeeMetrics(
                fx.tenant(), fx.employee(), Instant.now().minusSeconds(86400), Instant.now()));
        Map<String, Object> metrics = result.metrics();
        assertThat(((Number) metrics.get("totalResponsibilitySeconds")).longValue())
                .isEqualTo(1800L);
        assertThat(((Number) metrics.get("completedWorkItems")).longValue())
                .isEqualTo(1L);
        // explanation embeds definition/period/segments/exclusions + decision guard
        Map<String, Object> explanation = result.explanation();
        assertThat(String.valueOf(explanation.get("includedResponsibilitySegments")))
                .contains("ASSIGNED");
        assertThat(String.valueOf(explanation.get("excludedWaits")))
                .contains("EXTERNAL_WAIT");
        assertThat(String.valueOf(explanation.get("automatedDecisionPolicy")))
                .contains("NO_AUTOMATED_EMPLOYMENT_DECISION");
    }

    // ===== MATRIX D: timer notifications (dedup + worker separation) =====

    @Test
    void timerNotificationsFirePerStageWithDedupAndNeverMutateState() {
        Fixture fx = reconciliationFixture("timer");
        // FORCE RLS (V20261029_1): timer seeds, the notification worker scan,
        // the dispatcher pass and every verification read run under the fixture
        // tenant's transaction-local scope.
        inTenantScope(fx.tenant(), () -> {
        System.err.println("[DIAG-A] timers_before_insert=" + jdbc.queryForObject(
            "SELECT COUNT(*) FROM workflow_timers WHERE tenant_id=?", Integer.class, fx.tenant()));
        // WARNING window: due in 1 hour (within 24h warning window)
        UUID warningTimer = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_timers (
                    id, tenant_id, scope, scope_id, purpose, state, policy,
                    due_at, workflow_instance_id, work_item_id, created_at, updated_at)
                VALUES (?, ?, 'WORK_ITEM', ?, 'EXECUTION_DEADLINE', 'RUNNING',
                        'WARN_ONLY', NOW() + INTERVAL '1 hour', ?, ?, NOW(), NOW())
                """, warningTimer, fx.tenant(), fx.workItem(), fx.instance(),
                fx.workItem());
        System.err.println("[DIAG-B] timers_after_warn_insert=" + jdbc.queryForObject(
            "SELECT COUNT(*) FROM workflow_timers WHERE tenant_id=?", Integer.class, fx.tenant()));
        // REMINDER window: due in 48 hours (inside 72h reminder window)
        UUID reminderTimer = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_timers (
                    id, tenant_id, scope, scope_id, purpose, state, policy,
                    due_at, workflow_instance_id, work_item_id, created_at, updated_at)
                VALUES (?, ?, 'WORK_ITEM', ?, 'EXECUTION_DEADLINE', 'RUNNING',
                        'MONITOR_ONLY', NOW() + INTERVAL '48 hours', ?, ?, NOW(), NOW())
                """, reminderTimer, fx.tenant(), fx.workItem(), fx.instance(),
                fx.workItem());
        // Both scans run inside the SAME transaction-local tenant scope as the
        // timer seeds and the verification reads below — one connection, one
        // transaction, no cross-connection commit-visibility timing.
        System.err.println("[DIAG-C] timers_after_both_inserts=" + jdbc.queryForObject(
            "SELECT COUNT(*) FROM workflow_timers WHERE tenant_id=?", Integer.class, fx.tenant()));
        int enqueued = timerWorker.runScan();
        System.err.println("[DIAG-D] timers_after_scan=" + jdbc.queryForObject(
            "SELECT COUNT(*) FROM workflow_timers WHERE tenant_id=?", Integer.class, fx.tenant()));
        assertThat(enqueued).isGreaterThanOrEqualTo(2);
        // dedup: repeated scan collapses to the same intents (no spam)
        int secondScanEnqueued = timerWorker.runScan();
        assertThat(secondScanEnqueued).isZero();
        Integer warningIntents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                 WHERE tenant_id = ? AND event_type = 'DEADLINE_WARNING'
                   AND deduplication_key = ?
                """, Integer.class, fx.tenant(), "wf-warn:" + warningTimer);
        System.err.println("[DIAG] timers=" + jdbc.queryForList(
            "SELECT id, state, purpose, due_at FROM workflow_timers WHERE tenant_id=? ORDER BY due_at NULLS LAST", fx.tenant()));
        System.err.println("[DIAG] all_intents=" + jdbc.queryForList(
            "SELECT event_type, deduplication_key FROM workflow_notification_intents WHERE tenant_id=?", fx.tenant()));
        System.err.println("[DIAG] expected warn_key=wf-warn:" + warningTimer + " rem_key=wf-rem:" + reminderTimer);
        assertThat(warningIntents).isEqualTo(1);
        Integer reminderIntents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                 WHERE tenant_id = ? AND event_type = 'DEADLINE_REMINDER'
                   AND deduplication_key = ?
                """, Integer.class, fx.tenant(), "wf-rem:" + reminderTimer);
        assertThat(reminderIntents).isEqualTo(1);
        // journey evidence per stage
        Integer journeyWarn = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_journey
                 WHERE tenant_id = ? AND event_type = 'DEADLINE_WARNING'
                """, Integer.class, fx.tenant());
        assertThat(journeyWarn).isEqualTo(1);
        // WORKER SEPARATION: notification worker never mutates timer state
        String warningState = jdbc.queryForObject(
                "SELECT state FROM workflow_timers WHERE id = ?", String.class,
                warningTimer);
        assertThat(warningState).isEqualTo("RUNNING");
        String reminderState = jdbc.queryForObject(
                "SELECT state FROM workflow_timers WHERE id = ?", String.class,
                reminderTimer);
        assertThat(reminderState).isEqualTo("RUNNING");
        // breached timer (from fixture) gets DEADLINE_BREACH intent
        Integer breachIntents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                 WHERE tenant_id = ? AND event_type = 'DEADLINE_BREACH'
                   AND deduplication_key = ?
                """, Integer.class, fx.tenant(), "wf-breach:" + fx.timer());
        assertThat(breachIntents).isEqualTo(1);
        // dispatcher delivers IN_APP stages into the feed exactly once each
        InAppNotificationProvider inApp = new InAppNotificationProvider(jdbc);
        WorkflowNotificationDispatcher dispatcher = new WorkflowNotificationDispatcher(
                jdbc, new WorkflowChannelRegistry(List.of(inApp)), true);
        dispatcher.dispatchBatch();
        Integer feedRows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND event_type IN ('DEADLINE_WARNING',
                       'DEADLINE_REMINDER', 'DEADLINE_BREACH')
                """, Integer.class, fx.tenant());
        assertThat(feedRows).isEqualTo(3);
        });
    }

    // ===== staleness-driven projection worker =====

    @Test
    void projectionWorkerRebuildsStaleInstances() {
        Fixture fx = reconciliationFixture("worker");
        // FORCE RLS (V20261029_1): the worker pass runs inside a transaction and
        // self-scopes app.tenant_id per tenant; verification reads run under the
        // fixture tenant's scope.
        tx.execute(s -> projectionWorker.runProjectionPass());
        inTenantScope(fx.tenant(), () -> {
            Integer facts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_analytics_process_facts
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Integer.class, fx.tenant(), fx.instance());
            assertThat(facts).isEqualTo(1);
        });
        // new journey evidence -> worker projects again (staleness detected)
        inTenantScope(fx.tenant(), () -> appendJourney(fx.tenant(), fx.instance(),
                "TASK_REASSIGNED", "tr2:" + fx.instance(), Instant.now()));
        tx.execute(s -> projectionWorker.runProjectionPass());
        inTenantScope(fx.tenant(), () -> {
            Integer reassignments = jdbc.queryForObject("""
                SELECT reassignment_count FROM workflow_analytics_process_facts
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Integer.class, fx.tenant(), fx.instance());
            assertThat(reassignments).isEqualTo(2);
        });
    }
}
