package com.sanad.platform.hr.onboarding;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * HRM-G1 T9 behavioral acceptance on host-native PostgreSQL Direct.
 *
 * <p>The suite intentionally loads T9 production classes reflectively so the
 * RED commit compiles cleanly and fails only because the governed onboarding
 * implementation is absent. It pins design §6.4 / §11.4: derived completion,
 * distinct WAIVE privilege + reason, immutable template snapshots, terminal
 * plan cancellation with OPEN-task cascade, Y2 apply idempotency by
 * (task_id, transition_seq), and tenant fail-closed behavior.</p>
 */
class HrOnboardingServiceIntegrationTest {

    private static final String SERVICE = "com.sanad.platform.hr.onboarding.HrOnboardingService";
    private static final String REPOSITORY =
            "com.sanad.platform.hr.onboarding.infrastructure.JdbcHrOnboardingRepository";
    private static final String AUTH_PORT =
            "com.sanad.platform.hr.onboarding.application.OnboardingAuthorizationPort";
    private static final String WORKFLOW_LINK =
            "com.sanad.platform.hr.onboarding.application.HrOnboardingWorkflowLinkService";

    private static final String CAP_PLAN_MANAGE = "HRM.ONBOARDING.PLAN.MANAGE";
    private static final String CAP_TASK_COMPLETE = "HRM.ONBOARDING.TASK.COMPLETE";
    private static final String CAP_TASK_WAIVE = "HRM.ONBOARDING.TASK.WAIVE";

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private DataSource baseDataSource;
    private Connection tenantConnection;
    private JdbcTemplate jdbc;
    private UUID tenantId;
    private UUID actorId;
    private UUID employmentId;
    private Map<String, Boolean> grants;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                available = c.isValid(5);
            }
        } catch (Throwable ignored) {
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateAndSeed() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        baseDataSource = ds;

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        employmentId = UUID.randomUUID();
        JdbcTemplate plain = new JdbcTemplate(ds);
        plain.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantId, "T9 Tenant", "t9-" + tenantId.toString().substring(0, 8));

        tenantConnection = ds.getConnection();
        tenantConnection.setAutoCommit(true);
        try (var ps = tenantConnection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(tenantConnection, true));
        jdbc.update("INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, "
                        + "display_name, employment_type, status, hire_date) "
                        + "VALUES (?, ?, ?, 'T9', 'Worker', 'T9 Worker', 'FULL_TIME', 'ACTIVE', CURRENT_DATE)",
                employmentId, tenantId, "EMP-T9-" + tenantId.toString().substring(0, 8));
        seedTemplate("GENERIC-ONBOARDING", "[{\"seq\":1,\"title\":\"Identity setup\",\"assigneeUserId\":\""
                + actorId + "\"},{\"seq\":2,\"title\":\"Equipment\",\"assigneeUserId\":\""
                + actorId + "\"},{\"seq\":3,\"title\":\"Training\",\"assigneeUserId\":\""
                + actorId + "\"}]");

        grants = new HashMap<>();
        grants.put(CAP_PLAN_MANAGE, true);
        grants.put(CAP_TASK_COMPLETE, true);
        grants.put(CAP_TASK_WAIVE, true);
    }

    @AfterEach
    void closeTenantConnection() throws Exception {
        if (tenantConnection != null) tenantConnection.close();
    }

    @Test
    void completionIsDerivedOnly_andTaskRetryIsIdempotent() throws Exception {
        Object service = newService();
        UUID planId = createPlan(service, tenantId, employmentId, false);
        List<UUID> tasks = taskIds(planId);
        assertThat(tasks).hasSize(3);

        complete(service, tenantId, tasks.get(0), UUID.randomUUID());
        complete(service, tenantId, tasks.get(1), UUID.randomUUID());
        assertThat(planState(planId)).isEqualTo("ACTIVE");

        UUID finalRequest = UUID.randomUUID();
        complete(service, tenantId, tasks.get(2), finalRequest);
        complete(service, tenantId, tasks.get(2), finalRequest);
        assertThat(planState(planId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_domain_event_outbox "
                        + "WHERE tenant_id=? AND event_type='HRM.ONBOARDING.TASK_COMPLETED'",
                Integer.class, tenantId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_domain_event_outbox "
                        + "WHERE tenant_id=? AND event_type='HRM.ONBOARDING.PLAN_COMPLETED'",
                Integer.class, tenantId)).isEqualTo(1);

        assertThat(java.util.Arrays.stream(load(SERVICE).getMethods())
                .noneMatch(m -> m.getName().equals("completePlan")))
                .as("plan completion is derived; no manual completion command exists")
                .isTrue();
    }

    @Test
    void waiveRequiresDistinctCapabilityAndRegisteredReason() throws Exception {
        Object service = newService();
        UUID taskId = taskIds(createPlan(service, tenantId, employmentId, false)).get(0);

        grants.put(CAP_TASK_COMPLETE, true);
        grants.put(CAP_TASK_WAIVE, false);
        assertThatThrownBy(() -> waive(service, tenantId, taskId, "TASK_WAIVER", UUID.randomUUID()))
                .hasMessageContaining("HRM_SCOPE_DENIED");

        grants.put(CAP_TASK_WAIVE, true);
        assertThatThrownBy(() -> waive(service, tenantId, taskId, "UNREGISTERED", UUID.randomUUID()))
                .hasMessageContaining("HRM_REASON_REJECTED");

        waive(service, tenantId, taskId, "TASK_WAIVER", UUID.randomUUID());
        assertThat(taskState(taskId)).isEqualTo("WAIVED");
        assertThat(jdbc.queryForObject("SELECT reason_code FROM hr_onboarding_tasks WHERE id=?",
                String.class, taskId)).isEqualTo("TASK_WAIVER");
    }

    @Test
    void templateMaterializationIsSnapshot_andOverdueNeverCompletes() throws Exception {
        Object service = newService();
        UUID firstPlan = createPlan(service, tenantId, employmentId, false);
        List<String> originalTitles = taskTitles(firstPlan);

        jdbc.update("UPDATE hr_onboarding_checklist_templates SET version=2, definition=?::jsonb, updated_at=NOW() "
                        + "WHERE tenant_id=? AND code='GENERIC-ONBOARDING'",
                "[{\"seq\":1,\"title\":\"Changed after snapshot\",\"assigneeUserId\":\"" + actorId + "\"}]",
                tenantId);
        assertThat(taskTitles(firstPlan)).isEqualTo(originalTitles);

        UUID secondEmployment = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_employees (id, tenant_id, employee_number, first_name, last_name, "
                        + "display_name, employment_type, status, hire_date) "
                        + "VALUES (?, ?, ?, 'T9', 'Second', 'T9 Second', 'FULL_TIME', 'ACTIVE', CURRENT_DATE)",
                secondEmployment, tenantId, "EMP-T9-B-" + tenantId.toString().substring(0, 6));
        UUID secondPlan = createPlan(service, tenantId, secondEmployment, false);
        assertThat(taskTitles(secondPlan)).containsExactly("Changed after snapshot");

        UUID overdueTask = taskIds(firstPlan).get(0);
        jdbc.update("UPDATE hr_onboarding_tasks SET due_at=? WHERE id=?",
                OffsetDateTime.now().minusDays(10), overdueTask);
        assertThat(taskState(overdueTask)).isEqualTo("OPEN");
        assertThat(planState(firstPlan)).isEqualTo("ACTIVE");
    }

    @Test
    void planCancellationIsTerminal_cascadesOpenTasks_andRequestsY2Cancellation() throws Exception {
        Object service = newService();
        UUID planId = createPlan(service, tenantId, employmentId, true);
        List<UUID> tasks = taskIds(planId);
        complete(service, tenantId, tasks.get(0), UUID.randomUUID());

        cancel(service, tenantId, planId, "PLAN_CANCELLATION", UUID.randomUUID());
        assertThat(planState(planId)).isEqualTo("CANCELLED");
        assertThat(taskState(tasks.get(0))).isEqualTo("DONE");
        assertThat(taskState(tasks.get(1))).isEqualTo("WAIVED");
        assertThat(taskState(tasks.get(2))).isEqualTo("WAIVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_domain_event_outbox "
                        + "WHERE tenant_id=? AND event_type='HRM.ONBOARDING.WORKFLOW_CANCEL_REQUESTED'",
                Integer.class, tenantId)).isEqualTo(2);

        assertThatThrownBy(() -> cancel(service, tenantId, planId,
                "PLAN_CANCELLATION", UUID.randomUUID()))
                .hasMessageContaining("HRM_ONBOARDING_PLAN_TERMINAL");
    }

    @Test
    void workflowApplyIsIdempotentByTaskAndTransitionSequence() throws Exception {
        Object service = newService();
        UUID planId = createPlan(service, tenantId, employmentId, true);
        UUID taskId = taskIds(planId).get(0);
        Object link = newWorkflowLinkService();

        applyWorkflow(link, tenantId, taskId, 41L, "DONE", null);
        applyWorkflow(link, tenantId, taskId, 41L, "DONE", null);

        assertThat(taskState(taskId)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_onboarding_workflow_transitions "
                        + "WHERE tenant_id=? AND task_id=? AND transition_seq=41",
                Integer.class, tenantId, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_domain_event_outbox "
                        + "WHERE tenant_id=? AND entity_id=? AND event_type='HRM.ONBOARDING.TASK_COMPLETED'",
                Integer.class, tenantId, taskId)).isEqualTo(1);
    }

    @Test
    void crossTenantTaskMutationFailsClosed() throws Exception {
        Object service = newService();
        UUID taskId = taskIds(createPlan(service, tenantId, employmentId, false)).get(0);
        UUID otherTenant = UUID.randomUUID();
        new JdbcTemplate(baseDataSource).update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, 'Other', ?, 'ACTIVE', NOW(), NOW())",
                otherTenant, "t9-other-" + otherTenant.toString().substring(0, 6));

        assertThatThrownBy(() -> complete(service, otherTenant, taskId, UUID.randomUUID()))
                .hasMessageContaining("HRM_ONBOARDING_TASK_NOT_FOUND");
        assertThat(taskState(taskId)).isEqualTo("OPEN");
    }

    private Object newService() throws Exception {
        Class<?> repoClass = load(REPOSITORY);
        Class<?> authClass = load(AUTH_PORT);
        Object repo = repoClass.getConstructor(DataSource.class).newInstance(baseDataSource);
        Object auth = Proxy.newProxyInstance(authClass.getClassLoader(), new Class<?>[]{authClass},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("requireTaskComplete")) {
                        HrCommandContext ctx = (HrCommandContext) args[0];
                        UUID assignee = (UUID) args[2];
                        if (!ctx.actorUserId().equals(assignee)
                                && !grants.getOrDefault(CAP_TASK_COMPLETE, false)) {
                            throw new IllegalStateException("HRM_SCOPE_DENIED: " + CAP_TASK_COMPLETE);
                        }
                    } else if (name.equals("requireTaskWaive")
                            && !grants.getOrDefault(CAP_TASK_WAIVE, false)) {
                        throw new IllegalStateException("HRM_SCOPE_DENIED: " + CAP_TASK_WAIVE);
                    } else if (name.equals("requirePlanManage")
                            && !grants.getOrDefault(CAP_PLAN_MANAGE, false)) {
                        throw new IllegalStateException("HRM_SCOPE_DENIED: " + CAP_PLAN_MANAGE);
                    }
                    return null;
                });
        return load(SERVICE).getConstructor(repoClass, authClass, DataSource.class)
                .newInstance(repo, auth, baseDataSource);
    }

    private Object newWorkflowLinkService() throws Exception {
        Class<?> repoClass = load(REPOSITORY);
        Class<?> authClass = load(AUTH_PORT);
        Object repo = repoClass.getConstructor(DataSource.class).newInstance(baseDataSource);
        Object auth = Proxy.newProxyInstance(authClass.getClassLoader(), new Class<?>[]{authClass},
                (proxy, method, args) -> {
                    if (method.getName().equals("requireTaskComplete")
                            && !grants.getOrDefault(CAP_TASK_COMPLETE, false)) {
                        throw new IllegalStateException("HRM_SCOPE_DENIED: " + CAP_TASK_COMPLETE);
                    }
                    if (method.getName().equals("requireTaskWaive")
                            && !grants.getOrDefault(CAP_TASK_WAIVE, false)) {
                        throw new IllegalStateException("HRM_SCOPE_DENIED: " + CAP_TASK_WAIVE);
                    }
                    return null;
                });
        return load(WORKFLOW_LINK).getConstructor(repoClass, authClass, DataSource.class)
                .newInstance(repo, auth, baseDataSource);
    }

    private UUID createPlan(Object service, UUID tenant, UUID employment, boolean workflowLinked)
            throws Exception {
        Method m = service.getClass().getMethod("createPlan", HrCommandContext.class,
                UUID.class, String.class, boolean.class);
        return (UUID) invoke(m, service, ctx(tenant), employment, "GENERIC-ONBOARDING", workflowLinked);
    }

    private void complete(Object service, UUID tenant, UUID taskId, UUID requestId) throws Exception {
        Method m = service.getClass().getMethod("completeTask", HrCommandContext.class, UUID.class, UUID.class);
        invoke(m, service, ctx(tenant), taskId, requestId);
    }

    private void waive(Object service, UUID tenant, UUID taskId, String reason, UUID requestId)
            throws Exception {
        Method m = service.getClass().getMethod("waiveTask", HrCommandContext.class,
                UUID.class, String.class, UUID.class);
        invoke(m, service, ctx(tenant), taskId, reason, requestId);
    }

    private void cancel(Object service, UUID tenant, UUID planId, String reason, UUID requestId)
            throws Exception {
        Method m = service.getClass().getMethod("cancelPlan", HrCommandContext.class,
                UUID.class, String.class, UUID.class);
        invoke(m, service, ctx(tenant), planId, reason, requestId);
    }

    private void applyWorkflow(Object link, UUID tenant, UUID taskId, long seq,
                               String outcome, String reason) throws Exception {
        Method m = link.getClass().getMethod("applyTransition", HrCommandContext.class,
                UUID.class, long.class, String.class, String.class);
        invoke(m, link, ctx(tenant), taskId, seq, outcome, reason);
    }

    private Object invoke(Method m, Object target, Object... args) throws Exception {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Exception ex) throw ex;
            if (cause instanceof Error err) throw err;
            throw new IllegalStateException(cause);
        }
    }

    private HrCommandContext ctx(UUID tenant) {
        return new HrCommandContext(tenant, employmentId, actorId, UUID.randomUUID());
    }

    private void seedTemplate(String code, String definition) {
        jdbc.update("INSERT INTO hr_onboarding_checklist_templates "
                        + "(id, tenant_id, code, name, version, definition, is_active, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'T9 Checklist', 1, ?::jsonb, TRUE, NOW(), NOW())",
                UUID.randomUUID(), tenantId, code, definition);
    }

    private List<UUID> taskIds(UUID planId) {
        return jdbc.query("SELECT id FROM hr_onboarding_tasks WHERE tenant_id=? AND plan_id=? ORDER BY seq",
                (rs, n) -> rs.getObject(1, UUID.class), tenantId, planId);
    }

    private List<String> taskTitles(UUID planId) {
        return jdbc.query("SELECT title FROM hr_onboarding_tasks WHERE tenant_id=? AND plan_id=? ORDER BY seq",
                (rs, n) -> rs.getString(1), tenantId, planId);
    }

    private String taskState(UUID taskId) {
        return jdbc.queryForObject("SELECT state FROM hr_onboarding_tasks WHERE tenant_id=? AND id=?",
                String.class, tenantId, taskId);
    }

    private String planState(UUID planId) {
        return jdbc.queryForObject("SELECT state FROM hr_onboarding_plans WHERE tenant_id=? AND id=?",
                String.class, tenantId, planId);
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            fail("T9 RED: missing governed onboarding production contract " + name, e);
            throw new AssertionError(e);
        }
    }
}
