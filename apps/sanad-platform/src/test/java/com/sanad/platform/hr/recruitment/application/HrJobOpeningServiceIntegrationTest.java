package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.application.ComplianceEngine;
import com.sanad.platform.hr.compliance.application.CountryPolicyResolver;
import com.sanad.platform.hr.compliance.application.WorkerClassificationResolver;
import com.sanad.platform.hr.compliance.domain.ComplianceDecisionType;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceDecisionRepository;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.hr.recruitment.domain.RecruitmentReasonCodes;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
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

/**
 * HRM-G1 T3 — HrJobOpening aggregate service integration contract (design
 * §5.1, §6.1, §9, §12, §13.1; task card G1-T3).
 *
 * <p>Exercises the REAL G0 compliance authority (CountryPolicyResolver +
 * ComplianceEngine via the opening-scoped jurisdiction chain org_unit →
 * organization → ACTIVE legal-entity binding → registered_country_code) and
 * the REAL G0 transactional evidence writer (audit ledger + domain outbox in
 * the SAME transaction as the mutation).</p>
 *
 * <p>Clean-RED convention: the T3 service/repository/port classes are loaded
 * reflectively, so a RED run fails only because those classes are missing —
 * never because of a compilation error.</p>
 */
class HrJobOpeningServiceIntegrationTest {

    private static final String REPO_CLASS = "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository";
    private static final String SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrJobOpeningService";
    private static final String LINK_SERVICE_CLASS =
            "com.sanad.platform.hr.recruitment.application.HrOpeningApprovalLinkService";
    private static final String WORKFLOW_PORT_CLASS =
            "com.sanad.platform.hr.recruitment.application.OpeningApprovalWorkflowPort";
    private static final String PORT_CLASS = "com.sanad.platform.hr.recruitment.application.RecruitmentAuthorizationPort";

    private static final String CAP_MANAGE = "HRM.RECRUITMENT.OPENING.MANAGE";
    private static final String CAP_PUBLISH = "HRM.RECRUITMENT.OPENING.PUBLISH";

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private Object repository;
    private Object service;
    private Map<String, Boolean> grants;
    private UUID tenantId;
    private UUID managerUserId;
    private UUID publisherUserId;
    private UUID saJobId;
    private UUID saOrgUnitId;
    private UUID qaJobId;
    private UUID qaOrgUnitId;

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
    void migrateFreshDatabase() throws Exception {
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
        dataSource = ds;
        tenantId = UUID.randomUUID();
        // FORCE RLS on hr_* tables: every seed/assertion connection carries the
        // test tenant GUC at session scope (single-tenant per test method).
        jdbc = new JdbcTemplate(sessionTenantDataSource(ds, tenantId));

        managerUserId = UUID.randomUUID();
        publisherUserId = UUID.randomUUID();
        seedTenant();
        saJobId = seedJobWithJurisdiction("SA", "JOB-T3-SA", "OU-T3-SA");
        saOrgUnitId = orgUnitOf(saJobId);
        qaJobId = seedJobWithJurisdiction("QA", "JOB-T3-QA", "OU-T3-QA");
        qaOrgUnitId = orgUnitOf(qaJobId);

        // Real G0 evidence writer + real G0 compliance authority over the isolated DB.
        // The repository manages its own transaction-local tenant GUC on the raw
        // DataSource; the compliance engine needs session-scope seeding because its
        // JdbcComplianceDecisionRepository writes FORCE-RLS tables via pooled JDBC.
        JdbcHrEvidenceWriter evidenceWriter = new JdbcHrEvidenceWriter(ds);
        repository = Class.forName(REPO_CLASS)
                .getConstructor(DataSource.class, com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter.class)
                .newInstance(ds, evidenceWriter);

        grants = new HashMap<>();
        grants.put(CAP_MANAGE, true);
        grants.put(CAP_PUBLISH, true);
        Object port = stubPort(grants);

        JdbcTemplate engineJdbc = new JdbcTemplate(sessionTenantDataSource(ds, tenantId));
        JdbcComplianceDecisionRepository decisionRepository = new JdbcComplianceDecisionRepository(engineJdbc);
        CountryPolicyResolver resolver = new CountryPolicyResolver(
                engineJdbc, new WorkerClassificationResolver(engineJdbc));
        ComplianceEngine engine = new ComplianceEngine(resolver, List.of(), decisionRepository);

        Object workflowPort = stubOpeningWorkflowPort();
        Object linkService = Class.forName(LINK_SERVICE_CLASS)
                .getConstructor(Class.forName(WORKFLOW_PORT_CLASS), Class.forName(REPO_CLASS))
                .newInstance(workflowPort, repository);

        service = Class.forName(SERVICE_CLASS)
                .getConstructor(Class.forName(REPO_CLASS),
                        Class.forName(PORT_CLASS),
                        ComplianceEngine.class,
                        Class.forName(LINK_SERVICE_CLASS))
                .newInstance(repository, port, engine, linkService);
    }

    // ==================== T7 — Workflow Y2 opening-approval wiring ====================

    private final java.util.Map<UUID, UUID> seedInstanceToOpening = new java.util.HashMap<>();
    private static final UUID SEED_DEFINITION_VERSION =
            UUID.fromString("55555555-5555-5555-5555-555555555555");

    /**
     * DB-backed stub of the HR-owned workflow port: start allocates a fresh
     * authoritative instance id; outcome loading reads the SEEDED engine rows
     * (real tables, real shape) so the in-transaction verification and the
     * service-level snapshot always agree.
     */
    private Object stubOpeningWorkflowPort() throws Exception {
        Class<?> portClass = Class.forName(WORKFLOW_PORT_CLASS);
        Class<?> snapshotClass = null;
        Class<?> outcomeEnum = null;
        for (Class<?> c : portClass.getDeclaredClasses()) {
            if (c.getSimpleName().equals("ApprovalSnapshot")) {
                snapshotClass = c;
            }
            if (c.isEnum() && c.getSimpleName().equals("ApprovalOutcome")) {
                outcomeEnum = c;
            }
        }
        final Class<?> snapshot = snapshotClass;
        final Class<?> outcome = outcomeEnum;
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "startOpeningApproval" -> {
                    UUID openingId = (UUID) args[1];
                    UUID instanceId = UUID.randomUUID();
                    seedInstanceToOpening.put(instanceId, openingId);
                    return instanceId;
                }
                case "loadOpeningApprovalOutcome" -> {
                    UUID instanceId = (UUID) args[1];
                    UUID openingId = seedInstanceToOpening.get(instanceId);
                    if (openingId == null) {
                        return null;
                    }
                    String status = "RUNNING";
                    try {
                        status = jdbc.queryForObject(
                                "SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId);
                    } catch (Exception ignored) {
                        // no seeded rows yet — synthetic RUNNING
                    }
                    Object resolved = Enum.valueOf((Class<? extends Enum>) outcome, "NONE");
                    try {
                        String requestStatus = jdbc.queryForObject(
                                "SELECT status FROM workflow_approval_requests WHERE workflow_instance_id = ? "
                                        + "ORDER BY requested_at DESC LIMIT 1", String.class, instanceId);
                        if ("REJECTED".equals(requestStatus)) {
                            resolved = Enum.valueOf((Class<? extends Enum>) outcome, "REJECTED");
                        } else if ("APPROVED".equals(requestStatus) && "COMPLETED".equals(status)) {
                            resolved = Enum.valueOf((Class<? extends Enum>) outcome, "APPROVED");
                        }
                    } catch (Exception ignored) {
                        // no requests seeded
                    }
                    var ctor = snapshot.getConstructor(UUID.class, UUID.class, String.class, UUID.class,
                            String.class, outcome);
                    return ctor.newInstance(instanceId, SEED_DEFINITION_VERSION, "HR_JOB_OPENING", openingId,
                            status, resolved);
                }
                case "cancelOpeningApproval" -> {
                    return null;
                }
                default -> {
                    return null;
                }
            }
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    /** Seeds an authoritative COMPLETED+APPROVED Y2 approval for the open cycle. */
    private void seedApprovedWorkflow(UUID instanceId) {
        UUID openingId = seedInstanceToOpening.get(instanceId);
        com.sanad.platform.hr.recruitment.db.HrY2WorkflowSeedSupport.seedApproval(
                jdbc, tenantId, "HR_JOB_OPENING", openingId, instanceId, SEED_DEFINITION_VERSION, "COMPLETED", "APPROVED");
    }

    // ==================== §6.1 matrix + compliance gate ====================

    @Test
    void create_persistsDraftOpening_withAuditRow() throws Exception {
        UUID openingId = create(saJobId, saOrgUnitId);

        assertThat(stateOf(openingId)).isEqualTo("DRAFT");
        assertThat(filledHeadcount(openingId)).isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.OPENING_CREATED'",
                Integer.class, openingId)).as("create writes an OPENING_CREATED audit row").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ?",
                Integer.class, openingId)).as("create is audit-only (§13.1 row 1) — no outbox event")
                .isEqualTo(0);
    }

    @Test
    void submitApprove_publishes_withComplianceDecision_andOutbox() throws Exception {
        UUID openingId = create(saJobId, saOrgUnitId);
        submit(openingId, managerUserId);
        approve(openingId, publisherUserId);

        assertThat(stateOf(openingId)).isEqualTo("OPEN");
        assertThat(openComplianceDecision(openingId))
                .as("SA pack present → publish proceeds and the decision type is recorded")
                .isEqualTo(ComplianceDecisionType.COMPLIANT.name());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                        + "AND event_type = 'HRM.RECRUITMENT.OPENING_PUBLISHED'",
                Integer.class, openingId)).as("publish appends the OPENING_PUBLISHED outbox event").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_compliance_decisions WHERE resource_type = 'HR_JOB_OPENING' "
                        + "AND resource_id = ? AND decision_type = 'COMPLIANT'",
                Integer.class, openingId)).as("publish persists a resolver-written COMPLIANT decision row")
                .isEqualTo(1);
    }

    @Test
    void approve_absentPack_failsClosed_persistsDecisionRow_noPublish() throws Exception {
        UUID openingId = create(qaJobId, qaOrgUnitId);
        submit(openingId, managerUserId);

        assertThatThrownBy(() -> approve(openingId, publisherUserId))
                .as("publish in a jurisdiction without an authoritative pack must fail closed")
                .isInstanceOf(Exception.class);

        assertThat(stateOf(openingId)).as("opening must stay PENDING_APPROVAL").isEqualTo("PENDING_APPROVAL");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_compliance_decisions WHERE resource_type = 'HR_JOB_OPENING' "
                        + "AND resource_id = ? AND decision_type = 'LEGAL_REVIEW_REQUIRED'",
                Integer.class, openingId))
                .as("blocked publish persists a LEGAL_REVIEW_REQUIRED decision row").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ?",
                Integer.class, openingId)).as("blocked publish emits no outbox event").isEqualTo(0);
    }

    @Test
    void approve_unresolvableJurisdiction_failsClosed() throws Exception {
        UUID orphanOrgUnit = seedOrgUnitWithoutLegalEntity();
        UUID openingId = create(saJobId, orphanOrgUnit);
        submit(openingId, managerUserId);

        assertThatThrownBy(() -> approve(openingId, publisherUserId))
                .isInstanceOf(Exception.class);
        assertThat(stateOf(openingId)).isEqualTo("PENDING_APPROVAL");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_compliance_decisions WHERE resource_type = 'HR_JOB_OPENING' "
                        + "AND resource_id = ? AND decision_type = 'LEGAL_REVIEW_REQUIRED'",
                Integer.class, openingId)).isEqualTo(1);
    }

    @Test
    void fullLifecycleMatrix_pauseResumeClose_andTerminalClosed() throws Exception {
        UUID openingId = create(saJobId, saOrgUnitId);
        submit(openingId, managerUserId);
        approve(openingId, publisherUserId);

        pause(openingId, publisherUserId);
        assertThat(stateOf(openingId)).isEqualTo("PAUSED");
        resume(openingId, publisherUserId);
        assertThat(stateOf(openingId)).isEqualTo("OPEN");
        close(openingId, publisherUserId);
        assertThat(stateOf(openingId)).isEqualTo("CLOSED");

        assertThatThrownBy(() -> pause(openingId, publisherUserId))
                .as("CLOSED is terminal").isInstanceOf(Exception.class);
        assertThatThrownBy(() -> cancel(openingId, publisherUserId))
                .as("CLOSED cannot be cancelled").isInstanceOf(Exception.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? AND event_type IN ("
                        + "'HRM.RECRUITMENT.OPENING_PAUSED','HRM.RECRUITMENT.OPENING_RESUMED',"
                        + "'HRM.RECRUITMENT.OPENING_CLOSED')",
                Integer.class, openingId)).as("pause+resume+close each append their outbox event").isEqualTo(3);
    }

    @Test
    void cancel_fromDraft_and_reject_returnsToDraft_withRegisteredReason() throws Exception {
        UUID cancelId = create(saJobId, saOrgUnitId);
        cancel(cancelId, managerUserId);
        assertThat(stateOf(cancelId)).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> cancel(cancelId, managerUserId))
                .as("CANCELLED is terminal").isInstanceOf(Exception.class);

        UUID rejectId = create(saJobId, saOrgUnitId);
        submit(rejectId, managerUserId);
        reject(rejectId, publisherUserId, RecruitmentReasonCodes.OPENING_REJECTION);
        assertThat(stateOf(rejectId)).isEqualTo("DRAFT");

        submit(rejectId, managerUserId);
        assertThatThrownBy(() -> reject(rejectId, publisherUserId, null))
                .as("reject reason is mandatory").isInstanceOf(Exception.class);
        assertThatThrownBy(() -> reject(rejectId, publisherUserId, "NOT_A_REGISTERED_CODE"))
                .as("reject reason must be a registered code").isInstanceOf(Exception.class);
        assertThat(stateOf(rejectId)).as("failed rejects never mutate state").isEqualTo("PENDING_APPROVAL");
    }

    // ==================== capability + separation of duties ====================

    @Test
    void everyCommand_requiresItsCapability() throws Exception {
        UUID openingId = create(saJobId, saOrgUnitId);

        grants.put(CAP_MANAGE, false);
        assertThatThrownBy(() -> submit(openingId, managerUserId))
                .as("submit requires OPENING.MANAGE").isInstanceOf(Exception.class);
        assertThatThrownBy(() -> pause(openingId, managerUserId))
                .as("pause requires OPENING.MANAGE").isInstanceOf(Exception.class);
        grants.put(CAP_MANAGE, true);

        submit(openingId, managerUserId);
        grants.put(CAP_PUBLISH, false);
        assertThatThrownBy(() -> approve(openingId, publisherUserId))
                .as("approve requires OPENING.PUBLISH").isInstanceOf(Exception.class);
        assertThat(stateOf(openingId)).isEqualTo("PENDING_APPROVAL");
        grants.put(CAP_PUBLISH, true);

        approve(openingId, publisherUserId);
        grants.put(CAP_MANAGE, false);
        assertThatThrownBy(() -> close(openingId, publisherUserId))
                .as("close requires OPENING.MANAGE").isInstanceOf(Exception.class);
        assertThat(stateOf(openingId)).isEqualTo("OPEN");
    }

    @Test
    void approve_sameSubmitterAndApprover_denied_separationOfDuties() throws Exception {
        UUID openingId = create(saJobId, saOrgUnitId);
        submit(openingId, managerUserId);
        assertThatThrownBy(() -> approve(openingId, managerUserId))
                .as("submitter must not approve their own submission (§6.1 separation of duties)")
                .isInstanceOf(Exception.class);
        assertThat(stateOf(openingId)).isEqualTo("PENDING_APPROVAL");
    }

    // ==================== tenant gates ====================

    @Test
    void crossTenant_serviceLevelAccess_isDenied() throws Exception {
        UUID openingId = create(saJobId, saOrgUnitId);
        UUID tenantB = UUID.randomUUID();
        UUID tenantBManager = UUID.randomUUID();

        assertThatThrownBy(() -> invokeService("submit",
                new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(tenantB, tenantBManager), openingId))
                .as("a foreign-tenant context must never resolve the opening")
                .isInstanceOf(Exception.class);
        assertThat(stateOf(openingId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND actor_user_id = ?",
                Integer.class, openingId, tenantBManager)).as("no cross-tenant audit trail").isEqualTo(0);
    }

    @Test
    void filledHeadcount_isNeverWritableThroughServiceCommands() throws Exception {
        UUID openingId = create(saJobId, saOrgUnitId);
        submit(openingId, managerUserId);
        approve(openingId, publisherUserId);
        pause(openingId, publisherUserId);
        resume(openingId, publisherUserId);
        close(openingId, publisherUserId);

        assertThat(filledHeadcount(openingId))
                .as("filled headcount is derived ONLY from conversion linkage (§5.1) — "
                        + "no T3 service command may mutate it")
                .isEqualTo(0);
        for (Method m : service.getClass().getMethods()) {
            assertThat(m.getName().toLowerCase())
                    .as("service must not expose a filled-headcount mutation command")
                    .doesNotContain("fill");
        }
    }

    // ==================== helpers ====================

    private UUID create(UUID jobId, UUID orgUnitId) throws Exception {
        return (UUID) invokeService("create",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class, UUID.class,
                        UUID.class, int.class, OffsetDateTime.class, OffsetDateTime.class},
                ctx(tenantId, managerUserId), jobId, null, orgUnitId, null, 2, null, null);
    }

    private final java.util.Map<UUID, UUID> submittedWorkflow = new java.util.HashMap<>();

    private void submit(UUID openingId, UUID actor) throws Exception {
        Object result = invokeService("submit", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(tenantId, actor), openingId);
        if (result instanceof UUID instanceId) {
            submittedWorkflow.put(openingId, instanceId);
        }
    }

    private void approve(UUID openingId, UUID actor) throws Exception {
        // Seed the authoritative COMPLETED+APPROVED Y2 state for the open
        // cycle (real engine tables, real shape — the Spring integration test
        // proves the real engine produces exactly this state via the port).
        UUID instanceId = submittedWorkflow.get(openingId);
        if (instanceId != null) {
            seedInstanceToOpening.putIfAbsent(instanceId, openingId);
            seedApprovedWorkflow(instanceId);
        }
        invokeService("approve", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(tenantId, actor), openingId);
    }

    private void reject(UUID openingId, UUID actor, String reasonCode) throws Exception {
        invokeService("reject", new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(tenantId, actor), openingId, reasonCode);
    }

    private void pause(UUID openingId, UUID actor) throws Exception {
        invokeService("pause", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(tenantId, actor), openingId);
    }

    private void resume(UUID openingId, UUID actor) throws Exception {
        invokeService("resume", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(tenantId, actor), openingId);
    }

    private void close(UUID openingId, UUID actor) throws Exception {
        invokeService("close", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(tenantId, actor), openingId);
    }

    private void cancel(UUID openingId, UUID actor) throws Exception {
        invokeService("cancel", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(tenantId, actor), openingId);
    }

    private HrCommandContext ctx(UUID tenant, UUID actor) {
        return new HrCommandContext(tenant, null, actor, UUID.randomUUID());
    }

    private Object stubPort(Map<String, Boolean> grants) throws Exception {
        Class<?> portClass = Class.forName(PORT_CLASS);
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "requireOpeningManage" -> require(grants, CAP_MANAGE);
                case "requireOpeningPublish" -> require(grants, CAP_PUBLISH);
                case "requireOpeningView" -> require(grants, "HRM.RECRUITMENT.OPENING.VIEW");
                case "toString" -> returnString(portClass);
                default -> {
                }
            }
            return null;
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    private static String returnString(Class<?> portClass) {
        return portClass.getSimpleName() + " (test stub)";
    }

    private void require(Map<String, Boolean> grants, String capability) {
        if (!grants.getOrDefault(capability, false)) {
            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability + " denied (test stub)");
        }
    }

    private Object invokeService(String method, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = service.getClass().getMethod(method, paramTypes);
        try {
            return m.invoke(service, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new IllegalStateException(cause);
        }
    }

    /**
     * DataSource proxy that sets the session-scope tenant GUC on every new
     * connection — FORCE-RLS tables are writable/readable for the test tenant.
     * Transaction-local GUCs set by the code under test always win inside
     * their transaction, so this never weakens tenant isolation.
     */
    private DataSource sessionTenantDataSource(DataSource base, UUID tenant) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        Connection c = (Connection) method.invoke(base, args);
                        try (var st = c.createStatement()) {
                            st.execute("SELECT set_config('app.tenant_id', '" + tenant + "', false)");
                        }
                        return c;
                    }
                    return method.invoke(base, args);
                });
    }

    private String stateOf(UUID openingId) {
        return jdbc.queryForObject(
                "SELECT state FROM hr_job_openings WHERE id = ? AND tenant_id = ?",
                String.class, openingId, tenantId);
    }

    private int filledHeadcount(UUID openingId) {
        return jdbc.queryForObject(
                "SELECT filled_headcount FROM hr_job_openings WHERE id = ? AND tenant_id = ?",
                Integer.class, openingId, tenantId);
    }

    private String openComplianceDecision(UUID openingId) {
        return jdbc.queryForObject(
                "SELECT compliance_decision FROM hr_job_openings WHERE id = ? AND tenant_id = ?",
                String.class, openingId, tenantId);
    }

    private void seedTenant() {
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantId, "Tenant " + tenantId, "t-" + tenantId.toString().substring(0, 8));
    }

    /** Seeds organization + job + ACTIVE legal entity (country) + optional SA pack. */
    private UUID seedJobWithJurisdiction(String legalEntityCountry, String jobCode, String ouCode) {
        UUID jobId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", organizationId, tenantId,
                "Org-" + organizationId.toString().substring(0, 8));
        jdbc.update("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, ?, NOW())", jobId, tenantId, organizationId, jobCode);

        UUID legalEntityId = UUID.randomUUID();
        jdbc.update("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, "
                        + "statutory_country_code, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Legal Entity', ?, ?, 'ACTIVE', NOW(), NOW())",
                legalEntityId, tenantId, "LE-" + legalEntityId.toString().substring(0, 8),
                legalEntityCountry, legalEntityCountry);
        jdbc.update("INSERT INTO organization_legal_entities (tenant_id, organization_id, legal_entity_id, "
                        + "effective_from, status) VALUES (?, ?, ?, CURRENT_DATE, 'ACTIVE')",
                tenantId, organizationId, legalEntityId);

        jdbc.update("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, ?, NOW())", orgUnitId(jobCode), tenantId, organizationId, ouCode);

        if ("SA".equals(legalEntityCountry)) {
            jdbc.update("INSERT INTO hr_country_packs (country_code, pack_code, pack_version, status, "
                            + "effective_from, legal_reviewed_at, legal_reviewed_by, certification_reference) "
                            + "VALUES ('SA', 'PACK-SA', 'v1', 'CERTIFIED', CURRENT_DATE, NOW(), 'legal', 'CERT-1')");
        }
        return jobId;
    }

    private final Map<String, UUID> orgUnitsByJob = new HashMap<>();

    private UUID orgUnitId(String jobCode) {
        UUID orgUnitId = UUID.randomUUID();
        orgUnitsByJob.put(jobCode, orgUnitId);
        return orgUnitId;
    }

    private UUID orgUnitOf(UUID jobId) {
        String jobCode = jdbc.queryForObject(
                "SELECT stable_code FROM hr_jobs WHERE id = ?", String.class, jobId);
        return orgUnitsByJob.get(jobCode);
    }

    private UUID seedOrgUnitWithoutLegalEntity() {
        UUID organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", organizationId, tenantId,
                "Orphan-" + organizationId.toString().substring(0, 8));
        UUID orgUnitId = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-ORPHAN', NOW())", orgUnitId, tenantId, organizationId);
        return orgUnitId;
    }
}
