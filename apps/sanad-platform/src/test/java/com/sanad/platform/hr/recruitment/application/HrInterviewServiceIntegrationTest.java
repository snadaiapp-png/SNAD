package com.sanad.platform.hr.recruitment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1 T6 — HrInterview aggregate service integration contract (design
 * §5.1, §13.1 rows 13-16; task card G1-T6).
 *
 * <p>Verifies panel participants resolve within the tenant, the interview
 * outcome machine (SCHEDULED → DONE(PASSED|FAILED) | CANCELLED | NO_SHOW,
 * terminal), the INTERVIEW.SCHEDULE/RECORD_OUTCOME/MANAGE capability rows,
 * and versioned per-participant feedback with latest-wins reads.</p>
 *
 * <p>Clean-RED convention: T6 classes load reflectively.</p>
 */
class HrInterviewServiceIntegrationTest {

    private static final String REPO_CLASS = "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrInterviewRepository";
    private static final String SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrInterviewService";
    private static final String PORT_CLASS = "com.sanad.platform.hr.recruitment.application.RecruitmentAuthorizationPort";
    private static final String APPLICATION_SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrApplicationService";

    private static final String CAP_MANAGE = "HRM.RECRUITMENT.INTERVIEW.MANAGE";
    private static final String CAP_SCHEDULE = "HRM.RECRUITMENT.INTERVIEW.SCHEDULE";
    private static final String CAP_OUTCOME = "HRM.RECRUITMENT.INTERVIEW.RECORD_OUTCOME";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private Object service;
    private Map<String, Boolean> grants;
    private UUID tenantId;
    private UUID panelistUserId;
    private UUID operatorId;
    private UUID applicationId;

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
        panelistUserId = seedTenantUser();

        grants = new HashMap<>();
        grants.put(CAP_MANAGE, true);
        grants.put(CAP_SCHEDULE, true);
        grants.put(CAP_OUTCOME, true);
        grants.put("HRM.RECRUITMENT.CANDIDATE.MANAGE", true);
        grants.put("HRM.RECRUITMENT.CANDIDATE.VIEW", true);
        grants.put("HRM.RECRUITMENT.APPLICATION.MANAGE", true);
        grants.put("HRM.RECRUITMENT.APPLICATION.ADVANCE", true);
        grants.put("HRM.RECRUITMENT.OPENING.MANAGE", true);
        grants.put("HRM.RECRUITMENT.OPENING.PUBLISH", true);
        applicationId = seedApplicationChain();

        Class.forName(REPO_CLASS).getConstructor(DataSource.class).newInstance(ds);

        Object port = stubPort(grants);

        service = Class.forName(SERVICE_CLASS)
                .getConstructor(Class.forName(REPO_CLASS), Class.forName(PORT_CLASS))
                .newInstance(Class.forName(REPO_CLASS).getConstructor(DataSource.class).newInstance(ds), port);
    }

    // ==================== schedule + panel ====================

    @Test
    void schedule_persistsScheduledInterview_withTenantPanel() throws Exception {
        UUID interviewId = schedule(panelistUserId);

        assertThat(stateOf(interviewId)).isEqualTo("SCHEDULED");
        Integer panel = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_interview_participants WHERE interview_id = ?",
                Integer.class, interviewId);
        assertThat(panel).isEqualTo(1);
        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.INTERVIEW_SCHEDULED'",
                Integer.class, interviewId);
        assertThat(audits).as("schedule writes its audit row").isEqualTo(1);
    }

    @Test
    void schedule_rejectsUserOutsideTenant() throws Exception {
        UUID foreignUser = UUID.randomUUID();
        assertThatThrownBy(() -> schedule(foreignUser))
                .as("panel participants must resolve within the tenant (IAM)")
                .isInstanceOf(Exception.class);
    }

    @Test
    void schedule_requiresScheduleCapability_andValidatesMode() throws Exception {
        grants.put(CAP_SCHEDULE, false);
        assertThatThrownBy(() -> schedule(panelistUserId))
                .as("schedule requires INTERVIEW.SCHEDULE").isInstanceOf(Exception.class);
        grants.put(CAP_SCHEDULE, true);

        assertThatThrownBy(() -> invokeService("schedule",
                new Class<?>[]{HrCommandContext.class, UUID.class, OffsetDateTime.class,
                        String.class, Integer.class, List.class},
                ctx(operatorId), applicationId, OffsetDateTime.now().plusDays(1), "HOLOGRAM", null,
                List.of(panelistUserId)))
                .as("mode must be ONSITE|REMOTE|PHONE").isInstanceOf(Exception.class);
    }

    // ==================== outcome machine ====================

    @Test
    void outcomeMatrix_terminal_andCapabilityGated() throws Exception {
        UUID done = schedule(panelistUserId);
        invokeService("recordOutcome",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class},
                ctx(operatorId), done, "DONE", "PASSED");
        assertThat(stateOf(done)).isEqualTo("DONE");
        assertThat(outcomeOf(done)).isEqualTo("PASSED");
        assertThatThrownBy(() -> invokeService("recordOutcome",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class},
                ctx(operatorId), done, "CANCELLED", null))
                .as("DONE is terminal").isInstanceOf(Exception.class);

        UUID cancelled = schedule(panelistUserId);
        invokeService("recordOutcome",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class},
                ctx(operatorId), cancelled, "CANCELLED", null);
        assertThat(stateOf(cancelled)).isEqualTo("CANCELLED");

        UUID noShow = schedule(panelistUserId);
        grants.put(CAP_OUTCOME, false);
        assertThatThrownBy(() -> invokeService("recordOutcome",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class},
                ctx(operatorId), noShow, "NO_SHOW", null))
                .as("outcome requires INTERVIEW.RECORD_OUTCOME").isInstanceOf(Exception.class);
        grants.put(CAP_OUTCOME, true);
        assertThat(stateOf(noShow)).isEqualTo("SCHEDULED");

        assertThatThrownBy(() -> invokeService("recordOutcome",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class},
                ctx(operatorId), noShow, "DONE", null))
                .as("DONE requires an outcome PASSED|FAILED").isInstanceOf(Exception.class);
    }

    // ==================== feedback ====================

    @Test
    void feedback_versioned_latestWins_participantOrManage() throws Exception {
        UUID interviewId = schedule(panelistUserId);

        String v1 = JSON.createObjectNode().put("overall", 3).toString();
        invokeService("submitFeedback",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(panelistUserId), interviewId, v1);

        String v2 = JSON.createObjectNode().put("overall", 4).toString();
        invokeService("submitFeedback",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(panelistUserId), interviewId, v2);

        String latest = jdbc.queryForObject(
                "SELECT f.scorecard::text FROM hr_interview_feedback f "
                        + "JOIN hr_interview_participants p ON p.id = f.participant_id "
                        + "WHERE f.interview_id = ? AND p.user_id = ? ORDER BY f.version DESC LIMIT 1",
                String.class, interviewId, panelistUserId);
        assertThat(JSON.readTree(latest).get("overall").asInt())
                .as("latest feedback version wins").isEqualTo(4);

        // A non-participant without INTERVIEW.MANAGE may not submit feedback.
        UUID outsider = UUID.randomUUID();
        grants.put(CAP_MANAGE, false);
        assertThatThrownBy(() -> invokeService("submitFeedback",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(outsider), interviewId, v1))
                .isInstanceOf(Exception.class);
        grants.put(CAP_MANAGE, true);
    }

    @Test
    void crossTenant_serviceLevelAccess_isDenied() throws Exception {
        UUID interviewId = schedule(panelistUserId);
        UUID tenantB = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantB, "Tenant B", "tb-" + tenantB.toString().substring(0, 8));
        HrCommandContext ctxB = new HrCommandContext(tenantB, null, operatorId, UUID.randomUUID());
        assertThatThrownBy(() -> invokeService("recordOutcome",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class},
                ctxB, interviewId, "CANCELLED", null))
                .isInstanceOf(Exception.class);
        assertThat(stateOf(interviewId)).isEqualTo("SCHEDULED");
    }

    // ==================== helpers ====================

    private UUID schedule(UUID panelUser) throws Exception {
        return (UUID) invokeService("schedule",
                new Class<?>[]{HrCommandContext.class, UUID.class, OffsetDateTime.class,
                        String.class, Integer.class, List.class},
                ctx(operatorId), applicationId, OffsetDateTime.now().plusDays(2), "ONSITE", 45,
                List.of(panelUser));
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    private UUID seedTenantUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, password_hash, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'x', 'ACTIVE', NOW(), NOW())",
                userId, tenantId, "u-" + userId.toString().substring(0, 8) + "@example.com");
        return userId;
    }

    /** Seeds candidate + published opening + application through the real services. */
    private UUID seedApplicationChain() throws Exception {
        UUID organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", organizationId, tenantId,
                "Org-" + organizationId.toString().substring(0, 8));
        UUID jobId = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T6', NOW())", jobId, tenantId, organizationId);
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
        UUID orgUnitId = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T6', NOW())", orgUnitId, tenantId, organizationId);

        Object openingRepo = Class.forName(
                        "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository")
                .getConstructor(DataSource.class, com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter.class)
                .newInstance(dataSource, new com.sanad.platform.hr.integration.JdbcHrEvidenceWriter(dataSource));
        Object openingService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrJobOpeningService")
                .getConstructor(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository"),
                        Class.forName(PORT_CLASS),
                        com.sanad.platform.hr.compliance.application.ComplianceEngine.class)
                .newInstance(openingRepo, stubPort(grants),
                        new com.sanad.platform.hr.compliance.application.ComplianceEngine(
                                new com.sanad.platform.hr.compliance.application.CountryPolicyResolver(
                                        jdbc, new com.sanad.platform.hr.compliance.application.WorkerClassificationResolver(jdbc)),
                                List.of(),
                                new com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceDecisionRepository(jdbc)));
        UUID openingId = (UUID) openingService.getClass()
                .getMethod("create", HrCommandContext.class, UUID.class, UUID.class, UUID.class,
                        UUID.class, int.class, OffsetDateTime.class, OffsetDateTime.class)
                .invoke(openingService, ctx(operatorId), jobId, null, orgUnitId, null, 1, null, null);
        openingService.getClass().getMethod("submit", HrCommandContext.class, UUID.class)
                .invoke(openingService, ctx(operatorId), openingId);
        openingService.getClass().getMethod("approve", HrCommandContext.class, UUID.class)
                .invoke(openingService, ctx(panelistUserId), openingId);

        Object candidateService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrCandidateService")
                .getConstructor(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository"),
                        Class.forName(PORT_CLASS),
                        com.sanad.platform.security.crypto.PlatformCryptographyService.class)
                .newInstance(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository")
                                .getConstructor(DataSource.class).newInstance(dataSource),
                        stubPort(grants), testCrypto());
        UUID candidateId = (UUID) candidateService.getClass()
                .getMethod("create", HrCommandContext.class, String.class, String.class, String.class, String.class)
                .invoke(candidateService, ctx(operatorId), "Seed Candidate", null, null, null);

        Object applicationService = Class.forName(APPLICATION_SERVICE_CLASS)
                .getConstructor(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrApplicationRepository"),
                        Class.forName(PORT_CLASS))
                .newInstance(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrApplicationRepository")
                                .getConstructor(DataSource.class).newInstance(dataSource),
                        stubPort(grants));
        UUID applicationId = (UUID) applicationService.getClass()
                .getMethod("apply", HrCommandContext.class, UUID.class, UUID.class)
                .invoke(applicationService, ctx(operatorId), candidateId, openingId);
        applicationService.getClass()
                .getMethod("advance", HrCommandContext.class, UUID.class)
                .invoke(applicationService, ctx(operatorId), applicationId);
        return applicationId;
    }

    private com.sanad.platform.security.crypto.PlatformCryptographyService testCrypto() {
        byte[] key = new byte[32];
        String encKey = java.util.Base64.getEncoder().encodeToString(key);
        return new com.sanad.platform.security.crypto.JcePlatformCryptographyService(
                new com.sanad.platform.security.crypto.EnvironmentKeyMaterialProvider("v1", encKey, "v1", encKey));
    }

    private Object stubPort(Map<String, Boolean> grants) throws Exception {
        Class<?> portClass = Class.forName(PORT_CLASS);
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "requireInterviewManage" -> require(grants, CAP_MANAGE);
                case "requireInterviewSchedule" -> require(grants, CAP_SCHEDULE);
                case "requireInterviewRecordOutcome" -> require(grants, CAP_OUTCOME);
                case "requireCandidateManage" -> require(grants, "HRM.RECRUITMENT.CANDIDATE.MANAGE");
                case "requireCandidateView" -> require(grants, "HRM.RECRUITMENT.CANDIDATE.VIEW");
                case "requireApplicationManage" -> require(grants, "HRM.RECRUITMENT.APPLICATION.MANAGE");
                case "requireApplicationAdvance" -> require(grants, "HRM.RECRUITMENT.APPLICATION.ADVANCE");
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

    private String stateOf(UUID interviewId) {
        return jdbc.queryForObject("SELECT state FROM hr_interviews WHERE id = ? AND tenant_id = ?",
                String.class, interviewId, tenantId);
    }

    private String outcomeOf(UUID interviewId) {
        return jdbc.queryForObject("SELECT outcome FROM hr_interviews WHERE id = ? AND tenant_id = ?",
                String.class, interviewId, tenantId);
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
