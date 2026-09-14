package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1 T5 — HrApplication aggregate service integration contract (design
 * §5.1, §6.2, §13.1 rows 9-12; task card G1-T5).
 *
 * <p>Verifies the single-active-application invariant (candidate, opening),
 * the forward-only §6.2 matrix with registered mandatory reasons on
 * reject/withdraw, append-only stage-period history, the conversion-only HIRED
 * gate (no T5 service path can reach HIRED), capability matrix rows
 * MANAGE/ADVANCE/REJECT, and the advance-vs-reject race (optimistic
 * state-conflict for the loser).</p>
 *
 * <p>Clean-RED convention: T5 classes load reflectively.</p>
 */
class HrApplicationServiceIntegrationTest {

    private static final String REPO_CLASS = "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrApplicationRepository";
    private static final String SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrApplicationService";
    private static final String PORT_CLASS = "com.sanad.platform.hr.recruitment.application.RecruitmentAuthorizationPort";
    private static final String OPENING_SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrJobOpeningService";

    private static final String CAP_MANAGE = "HRM.RECRUITMENT.APPLICATION.MANAGE";
    private static final String CAP_ADVANCE = "HRM.RECRUITMENT.APPLICATION.ADVANCE";
    private static final String CAP_REJECT = "HRM.RECRUITMENT.APPLICATION.REJECT";

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
    private Object openingService;
    private Map<String, Boolean> grants;
    private UUID tenantId;
    private UUID operatorId;
    private UUID seedPublisherId;
    private UUID candidateId;
    private UUID openingId;

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
        jdbc = new JdbcTemplate(sessionTenantDataSource(ds, tenantId));
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantId, "Tenant " + tenantId, "t-" + tenantId.toString().substring(0, 8));
        operatorId = UUID.randomUUID();
        seedPublisherId = UUID.randomUUID();
        seedCandidateAndOpening();

        repository = Class.forName(REPO_CLASS)
                .getConstructor(DataSource.class)
                .newInstance(ds);

        grants = new HashMap<>();
        grants.put(CAP_MANAGE, true);
        grants.put(CAP_ADVANCE, true);
        grants.put(CAP_REJECT, true);
        grants.put("HRM.RECRUITMENT.CANDIDATE.MANAGE", true);
        grants.put("HRM.RECRUITMENT.CANDIDATE.VIEW", true);
        grants.put("HRM.RECRUITMENT.OPENING.MANAGE", true);
        grants.put("HRM.RECRUITMENT.OPENING.PUBLISH", true);
        Object port = stubPort(grants);

        // The opening service constructor is reused only for seeding fixtures;
        // T5 itself tests HrApplicationService.
        Object candidateRepo = Class.forName(
                        "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository")
                .getConstructor(DataSource.class).newInstance(ds);
        Object candidatePort = stubPort(grants);
        Object candidateService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrCandidateService")
                .getConstructor(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository"),
                        Class.forName(PORT_CLASS),
                        com.sanad.platform.security.crypto.PlatformCryptographyService.class)
                .newInstance(candidateRepo, candidatePort, noopCrypto());

        candidateId = (UUID) candidateService.getClass()
                .getMethod("create", HrCommandContext.class, String.class, String.class, String.class, String.class)
                .invoke(candidateService, ctx(operatorId), "Seed Candidate", null, null, null);

        Object openingWorkflowPort = stubOpeningWorkflowPort(fixtureInstanceToOpening);
        Object openingLinkService = Class.forName(LINK_SERVICE_CLASS)
                .getConstructor(Class.forName(WORKFLOW_PORT_CLASS),
                        Class.forName("com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository"))
                .newInstance(openingWorkflowPort,
                        Class.forName("com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository")
                                .getConstructor(DataSource.class,
                                        com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter.class)
                                .newInstance(ds, new com.sanad.platform.hr.integration.JdbcHrEvidenceWriter(ds)));
        openingService = Class.forName(OPENING_SERVICE_CLASS)
                .getConstructor(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository"),
                        Class.forName(PORT_CLASS),
                        com.sanad.platform.hr.compliance.application.ComplianceEngine.class,
                        Class.forName(LINK_SERVICE_CLASS))
                .newInstance(
                        Class.forName("com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository")
                                .getConstructor(DataSource.class,
                                        com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter.class)
                                .newInstance(ds, new com.sanad.platform.hr.integration.JdbcHrEvidenceWriter(ds)),
                        stubPort(grants),
                        new com.sanad.platform.hr.compliance.application.ComplianceEngine(
                                new com.sanad.platform.hr.compliance.application.CountryPolicyResolver(
                                        jdbc, new com.sanad.platform.hr.compliance.application.WorkerClassificationResolver(jdbc)),
                                List.of(),
                                new com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceDecisionRepository(jdbc)),
                        openingLinkService);

        openingId = (UUID) openingService.getClass()
                .getMethod("create", HrCommandContext.class, UUID.class, UUID.class, UUID.class,
                        UUID.class, int.class, java.time.OffsetDateTime.class, java.time.OffsetDateTime.class)
                .invoke(openingService, ctx(operatorId), jobId(), null, orgUnitId(), null, 1, null, null);
        submitAndPublishFixtureOpening(openingService, openingId);

        service = Class.forName(SERVICE_CLASS)
                .getConstructor(Class.forName(REPO_CLASS), Class.forName(PORT_CLASS))
                .newInstance(repository, port);
    }

    // ==================== invariants ====================

    @Test
    void apply_persistsAppliedState_andFirstStagePeriod() throws Exception {
        UUID applicationId = apply();

        assertThat(stateOf(applicationId)).isEqualTo("APPLIED");
        Integer periods = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_application_stage_periods WHERE application_id = ? AND stage = 'APPLIED'",
                Integer.class, applicationId);
        assertThat(periods).as("the APPLIED stage period is the first history row").isEqualTo(1);
        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.APPLICATION_SUBMITTED'",
                Integer.class, applicationId);
        assertThat(auditRows).as("apply writes the SUBMITTED audit row").isEqualTo(1);
    }

    @Test
    void singleActiveApplication_perCandidateAndOpening() throws Exception {
        apply();
        assertThatThrownBy(() -> apply())
                .as("one ACTIVE application per (candidate, opening) — re-application only after terminal")
                .isInstanceOf(Exception.class);

        // After a terminal state, a NEW application row is allowed.
        UUID first = jdbc.queryForObject(
                "SELECT id FROM hr_applications WHERE tenant_id = ? ORDER BY created_at LIMIT 1",
                UUID.class, tenantId);
        invokeService("reject", new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), first, "APPLICATION_REJECTION");
        apply(); // must succeed now
        Integer activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_applications WHERE tenant_id = ? AND state NOT IN "
                        + "('REJECTED','WITHDRAWN','HIRED')",
                Integer.class, tenantId);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void advanceMatrix_forwardOnly_withStageHistory() throws Exception {
        UUID applicationId = apply();
        advance(applicationId);
        assertThat(stateOf(applicationId)).isEqualTo("SCREENING");
        advance(applicationId);
        assertThat(stateOf(applicationId)).isEqualTo("INTERVIEW");
        advance(applicationId);
        assertThat(stateOf(applicationId)).isEqualTo("OFFER");

        // HIRED is conversion-only (§7/T8): the T5 service cannot reach it.
        assertThatThrownBy(() -> advance(applicationId))
                .as("advance from OFFER must be refused — conversion-only HIRED gate")
                .isInstanceOf(Exception.class);
        assertThat(stateOf(applicationId)).isEqualTo("OFFER");
        for (Method m : service.getClass().getMethods()) {
            assertThat(m.getName().toLowerCase())
                    .as("no T5 service path may reach HIRED")
                    .doesNotContain("hire").doesNotContain("convert");
        }

        List<String> stages = jdbc.queryForList(
                "SELECT stage FROM hr_application_stage_periods WHERE application_id = ? "
                        + "ORDER BY from_at ASC, id ASC",
                String.class, applicationId);
        assertThat(stages).as("append-only deterministic history").containsExactly(
                "APPLIED", "SCREENING", "INTERVIEW", "OFFER");
    }

    @Test
    void rejectAndWithdraw_requireRegisteredReason_andAreTerminal() throws Exception {
        UUID rejected = apply();
        assertThatThrownBy(() -> invokeService("reject",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), rejected, "NOT_REGISTERED"))
                .isInstanceOf(Exception.class);
        invokeService("reject", new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), rejected, "APPLICATION_REJECTION");
        assertThat(stateOf(rejected)).isEqualTo("REJECTED");
        assertThatThrownBy(() -> advance(rejected))
                .as("REJECTED is terminal").isInstanceOf(Exception.class);

        UUID withdrawn = apply();
        invokeService("withdraw", new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), withdrawn, "APPLICATION_WITHDRAWAL");
        assertThat(stateOf(withdrawn)).isEqualTo("WITHDRAWN");
        assertThatThrownBy(() -> invokeService("withdraw",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), withdrawn, "APPLICATION_WITHDRAWAL"))
                .as("WITHDRAWN is terminal").isInstanceOf(Exception.class);
    }

    @Test
    void advanceVsRejectRace_exactlyOneWinner() throws Exception {
        UUID applicationId = apply();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Method advance = service.getClass().getMethod("advance", HrCommandContext.class, UUID.class);
        Method reject = service.getClass().getMethod("reject", HrCommandContext.class,
                UUID.class, String.class);
        HrCommandContext ctx = ctx(operatorId);

        Future<Boolean> adv = pool.submit(() -> {
            start.await();
            try {
                advance.invoke(service, ctx, applicationId);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        Future<Boolean> rej = pool.submit(() -> {
            start.await();
            try {
                reject.invoke(service, ctx, applicationId, "APPLICATION_REJECTION");
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        start.countDown();
        boolean advWon = adv.get(30, TimeUnit.SECONDS);
        boolean rejWon = rej.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(advWon ^ rejWon).as("exactly one of advance/reject commits").isTrue();
        String finalState = stateOf(applicationId);
        assertThat(finalState).isIn("SCREENING", "REJECTED");
        Integer periodCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_application_stage_periods WHERE application_id = ?",
                Integer.class, applicationId);
        assertThat(periodCount).as("history is consistent with the winner")
                .isEqualTo("SCREENING".equals(finalState) ? 2 : 1);
    }

    @Test
    void everyCommand_requiresItsCapability() throws Exception {
        grants.put(CAP_MANAGE, false);
        assertThatThrownBy(() -> apply()).as("apply requires APPLICATION.MANAGE").isInstanceOf(Exception.class);
        grants.put(CAP_MANAGE, true);
        UUID applicationId = apply();

        grants.put(CAP_ADVANCE, false);
        assertThatThrownBy(() -> advance(applicationId))
                .as("advance requires APPLICATION.ADVANCE").isInstanceOf(Exception.class);
        grants.put(CAP_ADVANCE, true);

        grants.put(CAP_REJECT, false);
        assertThatThrownBy(() -> invokeService("reject",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), applicationId, "APPLICATION_REJECTION"))
                .as("reject requires APPLICATION.REJECT").isInstanceOf(Exception.class);
        grants.put(CAP_REJECT, true);
        assertThat(stateOf(applicationId)).isEqualTo("APPLIED");
    }

    @Test
    void crossTenant_serviceLevelAccess_isDenied() throws Exception {
        UUID applicationId = apply();
        UUID tenantB = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantB, "Tenant B", "tb-" + tenantB.toString().substring(0, 8));
        HrCommandContext ctxB = new HrCommandContext(tenantB, null, operatorId, UUID.randomUUID());
        assertThatThrownBy(() -> invokeService("advance",
                new Class<?>[]{HrCommandContext.class, UUID.class}, ctxB, applicationId))
                .as("a foreign-tenant context must never resolve the application")
                .isInstanceOf(Exception.class);
        assertThat(stateOf(applicationId)).isEqualTo("APPLIED");
    }


    // ==================== T7 — opening-approval fixture wiring ====================

    private static final String LINK_SERVICE_CLASS =
            "com.sanad.platform.hr.recruitment.application.HrOpeningApprovalLinkService";
    private static final String WORKFLOW_PORT_CLASS =
            "com.sanad.platform.hr.recruitment.application.OpeningApprovalWorkflowPort";
    private static final UUID SEED_DEFINITION_VERSION =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private final java.util.Map<UUID, UUID> fixtureInstanceToOpening = new java.util.HashMap<>();

    /** DB-backed stub of the HR-owned opening workflow port (engine rows are seeded). */
    private Object stubOpeningWorkflowPort(java.util.Map<UUID, UUID> registry) throws Exception {
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
        java.util.UUID tenant = tenantId;
        org.springframework.jdbc.core.JdbcTemplate template = jdbc;
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "startOpeningApproval" -> {
                    UUID openingId = (UUID) args[1];
                    UUID instanceId = UUID.randomUUID();
                    registry.put(instanceId, openingId);
                    return instanceId;
                }
                case "loadOpeningApprovalOutcome" -> {
                    UUID instanceId = (UUID) args[1];
                    UUID openingId = registry.get(instanceId);
                    if (openingId == null) {
                        return null;
                    }
                    String status = "RUNNING";
                    try {
                        status = template.queryForObject(
                                "SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId);
                    } catch (Exception ignored) {
                        // no seeded rows yet
                    }
                    Object resolved = Enum.valueOf((Class<? extends Enum>) outcome, "NONE");
                    try {
                        String requestStatus = template.queryForObject(
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
                default -> {
                    return null;
                }
            }
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    /**
     * Submits + publishes a fixture opening through the REAL service with the
     * authoritative COMPLETED+APPROVED Y2 state seeded between the two
     * commands (real engine tables, real shape).
     */
    private void submitAndPublishFixtureOpening(Object openingService, UUID openingId) throws Exception {
        Object submitResult = openingService.getClass()
                .getMethod("submit", HrCommandContext.class, UUID.class)
                .invoke(openingService, ctx(operatorId), openingId);
        if (submitResult instanceof UUID instanceId) {
            com.sanad.platform.hr.recruitment.db.HrY2WorkflowSeedSupport.seedApproval(
                    jdbc, tenantId, "HR_JOB_OPENING", openingId, instanceId, SEED_DEFINITION_VERSION, "COMPLETED", "APPROVED");
        }
        openingService.getClass()
                .getMethod("approve", HrCommandContext.class, UUID.class)
                .invoke(openingService, ctx(UUID.randomUUID()), openingId);
    }

    // ==================== helpers ====================

    private UUID jobId;
    private UUID orgUnitId;

    private UUID apply() throws Exception {
        return (UUID) invokeService("apply",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), candidateId, openingId);
    }

    private void advance(UUID applicationId) throws Exception {
        invokeService("advance", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(operatorId), applicationId);
    }

    private void seedCandidateAndOpening() {
        UUID organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", organizationId, tenantId,
                "Org-" + organizationId.toString().substring(0, 8));
        jdbc.update("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T5', NOW())", jobId = UUID.randomUUID(), tenantId, organizationId);
        UUID legalEntityId = UUID.randomUUID();
        jdbc.update("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, "
                        + "statutory_country_code, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'LE', 'SA', 'SA', 'ACTIVE', NOW(), NOW())",
                legalEntityId, tenantId, "LE-" + legalEntityId.toString().substring(0, 8));
        jdbc.update("INSERT INTO organization_legal_entities (tenant_id, organization_id, legal_entity_id, "
                + "effective_from, status) VALUES (?, ?, ?, CURRENT_DATE, 'ACTIVE')",
                tenantId, organizationId, legalEntityId);
        jdbc.update("INSERT INTO hr_country_packs (country_code, pack_code, pack_version, status, "
                        + "effective_from, legal_reviewed_at, legal_reviewed_by, certification_reference) "
                        + "VALUES ('SA', 'PACK-SA', 'v1', 'CERTIFIED', CURRENT_DATE, NOW(), 'legal', 'CERT-1')");
        jdbc.update("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T5', NOW())", orgUnitId = UUID.randomUUID(), tenantId, organizationId);
    }

    private UUID jobId() {
        return jobId;
    }

    private UUID orgUnitId() {
        return orgUnitId;
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    private Object stubPort(Map<String, Boolean> grants) throws Exception {
        Class<?> portClass = Class.forName(PORT_CLASS);
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "requireApplicationManage" -> require(grants, CAP_MANAGE);
                case "requireApplicationAdvance" -> require(grants, CAP_ADVANCE);
                case "requireApplicationReject" -> require(grants, CAP_REJECT);
                case "requireCandidateManage" -> require(grants, "HRM.RECRUITMENT.CANDIDATE.MANAGE");
                case "requireCandidateView" -> require(grants, "HRM.RECRUITMENT.CANDIDATE.VIEW");
                case "requireOpeningManage" -> require(grants, "HRM.RECRUITMENT.OPENING.MANAGE");
                case "requireOpeningPublish" -> require(grants, "HRM.RECRUITMENT.OPENING.PUBLISH");
                default -> {
                }
            }
            return null;
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    private void require(Map<String, Boolean> grants, String capability) {
        if (!grants.getOrDefault(capability, false)) {
            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability + " denied (test stub)");
        }
    }

    private com.sanad.platform.security.crypto.PlatformCryptographyService noopCrypto() {
        byte[] key = new byte[32];
        String encKey = java.util.Base64.getEncoder().encodeToString(key);
        return new com.sanad.platform.security.crypto.JcePlatformCryptographyService(
                new com.sanad.platform.security.crypto.EnvironmentKeyMaterialProvider("v1", encKey, "v1", encKey));
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

    private String stateOf(UUID applicationId) {
        return jdbc.queryForObject(
                "SELECT state FROM hr_applications WHERE id = ? AND tenant_id = ?",
                String.class, applicationId, tenantId);
    }

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
}
