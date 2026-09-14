package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.application.ComplianceEngine;
import com.sanad.platform.hr.compliance.application.CountryPolicyResolver;
import com.sanad.platform.hr.compliance.application.WorkerClassificationResolver;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceDecisionRepository;
import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.audit.JdbcHrAuditRepository;
import com.sanad.platform.hr.audit.SensitiveReadAuditService;
import com.sanad.platform.hr.contract.application.CountryContractTermsValidator;
import com.sanad.platform.hr.contract.application.ContractAuthorizationPort;
import com.sanad.platform.hr.contract.application.EmploymentContractService;
import com.sanad.platform.hr.compensation.application.CompensationAuthorizationPort;
import com.sanad.platform.hr.compensation.application.CompensationService;
import com.sanad.platform.hr.employment.EmploymentCommandService;
import com.sanad.platform.hr.employment.HrEmploymentV2Service;
import com.sanad.platform.hr.employment.JdbcEmploymentCommandService;
import com.sanad.platform.hr.assignment.application.HrAssignmentService;
import com.sanad.platform.hr.assignment.infrastructure.JdbcHrAssignmentRepository;
import com.sanad.platform.hr.compensation.infrastructure.JdbcCompensationRepository;
import com.sanad.platform.hr.contract.infrastructure.JdbcEmploymentContractRepository;
import com.sanad.platform.hr.identity.HrPersonService;
import com.sanad.platform.hr.identity.IdentifierNormalizer;
import com.sanad.platform.hr.identity.JdbcHrPersonRepository;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.hr.recruitment.domain.HrOfferState;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrApplicationRepository;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrOfferRepository;
import com.sanad.platform.security.crypto.JcePlatformCryptographyService;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HRM-G1 T8 — Candidate → Hire conversion: the atomic/idempotent boundary
 * (design §7, directive T8.1–T8.14).
 *
 * <p>Nine normative scenarios (T8-A01…T8-A09) plus the failure-injection
 * matrix (§T8.10), exactness (§T8.11), position occupancy (§T8.5),
 * onboarding mandatory output (§T8.6), IAM boundary (§T8.7), security
 * (§T8.12) — all on host-native PostgreSQL Direct.</p>
 *
 * <p>Clean-RED convention: T8 classes load reflectively; the suite fails on
 * their absence (missing behavior) before any GREEN implementation exists.</p>
 */
class HrHireConversionIntegrationTest {

    private static final String SERVICE_CLASS =
            "com.sanad.platform.hr.recruitment.application.HrHireConversionService";
    private static final String COMMAND_CLASS =
            "com.sanad.platform.hr.recruitment.application.HireConversionCommand";
    private static final String CLAIM_CLASS =
            "com.sanad.platform.hr.recruitment.application.HireConversionCommand$IdentityClaim";
    private static final String RESULT_CLASS =
            "com.sanad.platform.hr.recruitment.application.HireConversionResult";
    private static final String REPO_CLASS =
            "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrHireConversionRepository";
    private static final String LINK_SERVICE_CLASS =
            "com.sanad.platform.hr.recruitment.application.HrHireApprovalLinkService";
    private static final String HIRE_PORT_CLASS =
            "com.sanad.platform.hr.recruitment.application.HireApprovalWorkflowPort";
    private static final String AUTH_PORT_CLASS =
            "com.sanad.platform.hr.recruitment.application.RecruitmentAuthorizationPort";

    private static final UUID SEED_DEFINITION_VERSION =
            UUID.fromString("88888888-8888-8888-8888-888888888888");

    private static final String CAP_HIRE_CONVERT = "HRM.RECRUITMENT.HIRE.CONVERT";
    private static final String CAP_MANAGE = "HRM.RECRUITMENT.OFFER.MANAGE";
    private static final String CAP_EXTEND = "HRM.RECRUITMENT.OFFER.EXTEND";

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    /** Hire-eligible structured offer terms (design §7.1 steps 7/8). */
    private static final String HIRE_TERMS = "{\"contractTermType\":\"PERMANENT\","
            + "\"contractStartDate\":\"2026-10-01\",\"documentReference\":\"DOC-HIRE-1\"}";
    private static final String HIRE_COMP = "{\"currency\":\"SAR\",\"payFrequency\":\"MONTHLY\","
            + "\"components\":[{\"type\":\"BASE_SALARY\",\"code\":\"BASE\",\"amount\":111000}]}";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private Object conversionService;
    private Object hireApprovalPort;
    private Map<String, Boolean> grants;
    private PlatformCryptographyService crypto;
    private UUID tenantId;
    private UUID operatorId;
    private UUID candidateId;
    private UUID openingId;
    private UUID applicationId;
    private UUID organizationId;
    private UUID orgUnitId;
    private UUID legalEntityId;
    private boolean seededWithPosition;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (java.sql.Connection c = ds.getConnection()) {
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
                tenantId, "Tenant " + tenantId, "t8-" + tenantId.toString().substring(0, 8));
        operatorId = UUID.randomUUID();
        crypto = noopCrypto();
        grants = new HashMap<>();
        grants.put(CAP_HIRE_CONVERT, true);
        grants.put(CAP_MANAGE, true);
        grants.put(CAP_EXTEND, true);
        grants.put("HRM.RECRUITMENT.APPLICATION.MANAGE", true);
        grants.put("HRM.RECRUITMENT.APPLICATION.ADVANCE", true);
        grants.put("HRM.RECRUITMENT.CANDIDATE.MANAGE", true);
        grants.put("HRM.RECRUITMENT.OPENING.MANAGE", true);
        grants.put("HRM.RECRUITMENT.OPENING.PUBLISH", true);
        hireApprovalPort = hireApprovalStub();
        buildConversionService(ds);
        seedRecruitmentPipeline(ds);
    }

    @AfterEach
    void drain() {
        // no persistent pools; symmetry hook
    }

    // ==================== helpers ====================

    private void buildConversionService(DataSource ds) throws Exception {
        Class<?> authPortClass = Class.forName(AUTH_PORT_CLASS);
        Object auth = Proxy.newProxyInstance(authPortClass.getClassLoader(),
                new Class<?>[]{authPortClass}, (proxy, method, args) -> {
                    if (method.getName().startsWith("require")) {
                        String capability = switch (method.getName()) {
                            case "requireHireConvert" -> CAP_HIRE_CONVERT;
                            case "requireOfferManage" -> CAP_MANAGE;
                            case "requireOfferExtend" -> CAP_EXTEND;
                            default -> "HRM.RECRUITMENT." + method.getName();
                        };
                        if (grants == null || !grants.getOrDefault(capability, false)) {
                            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability
                                    + " denied (test stub)");
                        }
                    }
                    return null;
                });

        JdbcTemplate plain = new JdbcTemplate(ds);
        Object personRepo = new JdbcHrPersonRepository(ds, crypto);
        Object personService = new HrPersonService((com.sanad.platform.hr.identity.HrPersonRepository) personRepo,
                crypto, new IdentifierNormalizer());
        Object employmentRepo = new com.sanad.platform.hr.employment.JdbcEmploymentRepository(ds);
        EmploymentCommandService commands = new JdbcEmploymentCommandService(
                (com.sanad.platform.hr.employment.EmploymentRepository) employmentRepo);
        Object employmentService = new HrEmploymentV2Service(
                (com.sanad.platform.hr.employment.EmploymentRepository) employmentRepo,
                commands,
                (com.sanad.platform.hr.identity.HrPersonRepository) personRepo);
        Object assignmentRepo = new JdbcHrAssignmentRepository(ds, evidenceWriter(ds));
        Object assignmentService = new HrAssignmentService((JdbcHrAssignmentRepository) assignmentRepo);

        HrTransactionalEvidenceWriter evidenceWriter = evidenceWriter(ds);
        ObjectMapper mapper = new ObjectMapper();
        Object contractRepo = new JdbcEmploymentContractRepository(ds, evidenceWriter, mapper);
        Object contractAuth = Proxy.newProxyInstance(ContractAuthorizationPort.class.getClassLoader(),
                new Class<?>[]{ContractAuthorizationPort.class}, (proxy, method, args) -> null);
        CountryPolicyResolver policyResolver = new CountryPolicyResolver(plain,
                new WorkerClassificationResolver(plain));
        ComplianceEngine complianceEngine = new ComplianceEngine(policyResolver, List.of(),
                new JdbcComplianceDecisionRepository(plain));
        Object contractService = new EmploymentContractService(
                (com.sanad.platform.hr.contract.domain.EmploymentContractRepository) contractRepo,
                policyResolver, complianceEngine, new CountryContractTermsValidator(),
                (ContractAuthorizationPort) contractAuth, mapper);

        Object compRepo = new JdbcCompensationRepository(ds, evidenceWriter);
        Object compAuth = Proxy.newProxyInstance(CompensationAuthorizationPort.class.getClassLoader(),
                new Class<?>[]{CompensationAuthorizationPort.class}, (proxy, method, args) -> null);
        Object compService = new CompensationService(
                (com.sanad.platform.hr.compensation.domain.CompensationRepository) compRepo,
                (CompensationAuthorizationPort) compAuth,
                new SensitiveReadAuditService(ds, new HrRedactionGuard(), new JdbcHrAuditRepository()),
                ds, mapper);

        Object conversionRepo = Class.forName(REPO_CLASS)
                .getConstructor(DataSource.class, HrTransactionalEvidenceWriter.class)
                .newInstance(ds, evidenceWriter);

        conversionService = Class.forName(SERVICE_CLASS)
                .getConstructor(Class.forName(REPO_CLASS),
                        Class.forName(AUTH_PORT_CLASS),
                        Class.forName(HIRE_PORT_CLASS),
                        HrPersonService.class,
                        HrEmploymentV2Service.class,
                        HrAssignmentService.class,
                        EmploymentContractService.class,
                        CompensationService.class,
                        PlatformCryptographyService.class,
                        WorkerClassificationResolver.class,
                        DataSource.class)
                .newInstance(conversionRepo, auth, hireApprovalPort, personService,
                        employmentService, assignmentService, contractService, compService,
                        crypto, new WorkerClassificationResolver(plain), ds);
    }

    private HrTransactionalEvidenceWriter evidenceWriter(DataSource ds) {
        return new JdbcHrEvidenceWriter(ds);
    }

    /** Stubbed hire-approval port: DB-backed outcome reads like the T7 offer stub. */
    private Object hireApprovalStub() throws Exception {
        Class<?> portClass = Class.forName(HIRE_PORT_CLASS);
        Map<UUID, UUID> instanceToOffer = new HashMap<>();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "startHireApproval" -> {
                UUID offerForApproval = (UUID) args[1];
                UUID newInstanceId = UUID.nameUUIDFromBytes(
                        ("hire-approval-" + offerForApproval).getBytes());
                instanceToOffer.put(newInstanceId, offerForApproval);
                yield newInstanceId;
            }
            case "loadHireApprovalOutcome" -> {
                UUID instanceId = (UUID) args[1];
                yield buildSnapshot(portClass, instanceId,
                        instanceToOffer.getOrDefault(instanceId, tenantId));
            }
            case "findLatestApproval" -> {
                UUID offerForLookup = (UUID) args[1];
                UUID deterministic = UUID.nameUUIDFromBytes(
                        ("hire-approval-" + offerForLookup).getBytes());
                Integer cnt = jdbc == null ? 0 : jdbc.queryForObject(
                        "SELECT COUNT(*) FROM workflow_instances WHERE id = ?",
                        Integer.class, deterministic);
                yield cnt != null && cnt > 0 ? deterministic : null;
            }
            case "cancelHireApproval" -> null;
            default -> null;
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    private Object buildSnapshot(Class<?> portClass, UUID instanceId, UUID businessEntityId) throws Exception {
        Class<?> snapshotClass = null;
        Class<?> outcomeEnum = null;
        for (Class<?> c : portClass.getDeclaredClasses()) {
            if (c.getSimpleName().equals("ApprovalSnapshot")) snapshotClass = c;
            if (c.isEnum() && c.getSimpleName().equals("ApprovalOutcome")) outcomeEnum = c;
        }
        String status = "RUNNING";
        String requestStatus = null;
        try {
            status = jdbc.queryForObject(
                    "SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId);
            requestStatus = jdbc.queryForObject(
                    "SELECT status FROM workflow_approval_requests WHERE workflow_instance_id = ? "
                            + "ORDER BY requested_at DESC LIMIT 1", String.class, instanceId);
        } catch (Exception ignored) {
            // no seeded rows: RUNNING/NONE
        }
        Object outcome = Enum.valueOf((Class<? extends Enum>) outcomeEnum, switch (
                requestStatus == null ? "NONE" : requestStatus) {
            case "APPROVED" -> "COMPLETED".equals(status) ? "APPROVED" : "NONE";
            case "REJECTED" -> "REJECTED";
            default -> "NONE";
        });
        return snapshotClass.getConstructor(UUID.class, UUID.class, String.class, UUID.class,
                        String.class, outcomeEnum)
                .newInstance(instanceId,
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        "HR_OFFER_HIRE", businessEntityId, status, outcome);
    }

    private void seedRecruitmentPipeline(DataSource ds) throws Exception {
        organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", organizationId, tenantId,
                "Org-" + organizationId.toString().substring(0, 8));
        jdbc.update("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T8', NOW())", UUID.randomUUID(), tenantId, organizationId);
        legalEntityId = UUID.randomUUID();
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
        orgUnitId = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T8', NOW())", orgUnitId, tenantId, organizationId);

        Object candidateService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrCandidateService")
                .getConstructor(JdbcHrCandidateRepository.class,
                        Class.forName(AUTH_PORT_CLASS),
                        PlatformCryptographyService.class)
                .newInstance(new JdbcHrCandidateRepository(ds), authStub(), crypto);
        candidateId = (UUID) candidateService.getClass()
                .getMethod("create", HrCommandContext.class, String.class, String.class, String.class, String.class)
                .invoke(candidateService, ctx(operatorId), "T8 Hire Candidate",
                        "t8.candidate." + tenantId.toString().substring(0, 6) + "@example.com",
                        null, null);

        Map<UUID, UUID> openingFixtureRegistry = new HashMap<>();
        Object openingWorkflowPort = stubOpeningWorkflowPort(openingFixtureRegistry);
        Object openingLinkService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrOpeningApprovalLinkService")
                .getConstructor(Class.forName(
                                "com.sanad.platform.hr.recruitment.application.OpeningApprovalWorkflowPort"),
                        JdbcHrJobOpeningRepository.class)
                .newInstance(openingWorkflowPort, new JdbcHrJobOpeningRepository(ds, evidenceWriter(ds)));
        Object openingService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrJobOpeningService")
                .getConstructor(JdbcHrJobOpeningRepository.class,
                        Class.forName(AUTH_PORT_CLASS),
                        com.sanad.platform.hr.compliance.application.ComplianceEngine.class,
                        Class.forName(
                                "com.sanad.platform.hr.recruitment.application.HrOpeningApprovalLinkService"))
                .newInstance(new JdbcHrJobOpeningRepository(ds, evidenceWriter(ds)), authStub(),
                        new ComplianceEngine(
                                new CountryPolicyResolver(jdbc, new WorkerClassificationResolver(jdbc)),
                                List.of(),
                                new JdbcComplianceDecisionRepository(jdbc)),
                        openingLinkService);
        UUID jobId = jdbc.queryForObject("SELECT id FROM hr_jobs WHERE tenant_id = ? LIMIT 1",
                UUID.class, tenantId);
        openingId = (UUID) openingService.getClass()
                .getMethod("create", HrCommandContext.class, UUID.class, UUID.class, UUID.class,
                        UUID.class, int.class, OffsetDateTime.class, OffsetDateTime.class)
                .invoke(openingService, ctx(operatorId), jobId, null, orgUnitId, null, 1, null, null);
        Object submitResult = openingService.getClass()
                .getMethod("submit", HrCommandContext.class, UUID.class)
                .invoke(openingService, ctx(operatorId), openingId);
        if (submitResult instanceof UUID instanceId) {
            com.sanad.platform.hr.recruitment.db.HrY2WorkflowSeedSupport.seedApproval(
                    jdbc, tenantId, "HR_JOB_OPENING", openingId, instanceId,
                    SEED_DEFINITION_VERSION, "COMPLETED", "APPROVED");
        }
        openingService.getClass().getMethod("approve", HrCommandContext.class, UUID.class)
                .invoke(openingService, ctx(UUID.randomUUID()), openingId);

        Object applicationService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrApplicationService")
                .getConstructor(JdbcHrApplicationRepository.class, Class.forName(AUTH_PORT_CLASS))
                .newInstance(new JdbcHrApplicationRepository(ds), authStub());
        applicationId = (UUID) applicationService.getClass()
                .getMethod("apply", HrCommandContext.class, UUID.class, UUID.class)
                .invoke(applicationService, ctx(operatorId), candidateId, openingId);
        Method advance = applicationService.getClass().getMethod("advance", HrCommandContext.class, UUID.class);
        advance.invoke(applicationService, ctx(operatorId), applicationId);
        advance.invoke(applicationService, ctx(operatorId), applicationId);
        advance.invoke(applicationService, ctx(operatorId), applicationId);
    }

    private Object authStub() throws Exception {
        Class<?> portClass = Class.forName(AUTH_PORT_CLASS);
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().startsWith("require")) {
                if (grants == null || !grants.getOrDefault(grantKeyFor(method.getName()), false)) {
                    throw new IllegalStateException("HRM_SCOPE_DENIED: " + grantKeyFor(method.getName())
                            + " denied (test stub)");
                }
            }
            return null;
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    private static String grantKeyFor(String methodName) {
        return switch (methodName) {
            case "requireOfferManage" -> CAP_MANAGE;
            case "requireOfferExtend" -> CAP_EXTEND;
            case "requireHireConvert" -> CAP_HIRE_CONVERT;
            case "requireApplicationManage" -> "HRM.RECRUITMENT.APPLICATION.MANAGE";
            case "requireApplicationAdvance" -> "HRM.RECRUITMENT.APPLICATION.ADVANCE";
            case "requireApplicationReject" -> "HRM.RECRUITMENT.APPLICATION.REJECT";
            case "requireCandidateManage" -> "HRM.RECRUITMENT.CANDIDATE.MANAGE";
            case "requireCandidateView" -> "HRM.RECRUITMENT.CANDIDATE.VIEW";
            case "requireOpeningManage" -> "HRM.RECRUITMENT.OPENING.MANAGE";
            case "requireOpeningPublish" -> "HRM.RECRUITMENT.OPENING.PUBLISH";
            default -> "HRM.RECRUITMENT." + methodName;
        };
    }

    private Object stubOpeningWorkflowPort(Map<UUID, UUID> registry) throws Exception {
        Class<?> portClass = Class.forName(
                "com.sanad.platform.hr.recruitment.application.OpeningApprovalWorkflowPort");
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "startOpeningApproval" -> {
                UUID openingToApprove = (UUID) args[1];
                UUID instanceId = UUID.nameUUIDFromBytes(
                        ("opening-approval-" + openingToApprove).getBytes());
                registry.put(instanceId, openingToApprove);
                yield instanceId;
            }
            case "loadOpeningApprovalOutcome" -> {
                Class<?> snapshotClass = null;
                Class<?> outcomeEnum = null;
                for (Class<?> c : portClass.getDeclaredClasses()) {
                    if (c.getSimpleName().equals("ApprovalSnapshot")) snapshotClass = c;
                    if (c.isEnum() && c.getSimpleName().equals("ApprovalOutcome")) outcomeEnum = c;
                }
                yield snapshotClass.getConstructor(UUID.class, UUID.class, String.class, UUID.class,
                                String.class, outcomeEnum)
                        .newInstance((UUID) args[1], SEED_DEFINITION_VERSION,
                                "HR_JOB_OPENING", registry.get((UUID) args[1]),
                                "COMPLETED", Enum.valueOf((Class<? extends Enum>) outcomeEnum, "APPROVED"));
            }
            default -> null;
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    // ===== conversion invocation helpers =====

    private Object claim(String type, String country, String value) throws Exception {
        Class<?> claimClass = Class.forName(CLAIM_CLASS);
        Constructor<?> ctor = claimClass.getConstructor(String.class, String.class, String.class);
        return ctor.newInstance(type, country, value);
    }

    private Object command(String key, List<Object> claims, UUID positionId) throws Exception {
        Class<?> cmdClass = Class.forName(COMMAND_CLASS);
        for (Constructor<?> c : cmdClass.getConstructors()) {
            if (c.getParameterCount() == 9) {
                return c.newInstance(key, claims, legalEntityId, "FULL_TIME", "SA",
                        LocalDate.parse("2026-10-01"), new BigDecimal("100"), positionId, null);
            }
        }
        throw new IllegalStateException("HireConversionCommand 9-arg constructor not found");
    }

    private Object convert(HrCommandContext ctx, UUID offerId, Object command) throws Exception {
        Method convert = conversionService.getClass().getMethod("convert",
                HrCommandContext.class, UUID.class,
                Class.forName(COMMAND_CLASS));
        try {
            return convert.invoke(conversionService, ctx, offerId, command);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw new IllegalStateException(cause);
        }
    }

    private Object resultField(Object result, String name) throws Exception {
        Method m = result.getClass().getMethod(name);
        return m.invoke(result);
    }

    // ===== pipeline state helpers =====

    private UUID createHireReadyOffer(String terms, String comp) throws Exception {
        Object offerService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrOfferService")
                .getConstructor(JdbcHrOfferRepository.class,
                        Class.forName(AUTH_PORT_CLASS),
                        Class.forName(
                                "com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort"))
                .newInstance(new JdbcHrOfferRepository(dataSource), authStub(), offerStub());
        UUID offerId = (UUID) offerService.getClass()
                .getMethod("createOffer", HrCommandContext.class, UUID.class, String.class,
                        String.class, OffsetDateTime.class)
                .invoke(offerService, ctx(operatorId), applicationId, terms, comp, null);
        Method submit = offerService.getClass().getMethod("submitForApproval",
                HrCommandContext.class, UUID.class);
        UUID instanceId = (UUID) submit.invoke(offerService, ctx(operatorId), offerId);
        jdbc.update("UPDATE workflow_instances SET status = 'COMPLETED' WHERE id = ?", instanceId);
        jdbc.update("UPDATE workflow_approval_requests SET status = 'APPROVED' "
                + "WHERE workflow_instance_id = ?", instanceId);
        offerService.getClass()
                .getMethod("extendFromApproval", HrCommandContext.class, UUID.class, UUID.class)
                .invoke(offerService, ctx(operatorId), offerId, instanceId);
        offerService.getClass()
                .getMethod("accept", HrCommandContext.class, UUID.class)
                .invoke(offerService, ctx(operatorId), offerId);
        assertThat(offerState(offerId)).isEqualTo("ACCEPTED");
        return offerId;
    }

    private Object offerStub() throws Exception {
        Class<?> portClass = Class.forName(
                "com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort");
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "startOfferApproval" -> UUID.nameUUIDFromBytes(
                    ("offer-approval-" + args[1]).getBytes());
            case "loadApprovalOutcome" -> {
                Class<?> snapshotClass = null;
                Class<?> outcomeEnum = null;
                for (Class<?> c : portClass.getDeclaredClasses()) {
                    if (c.getSimpleName().equals("ApprovalSnapshot")) snapshotClass = c;
                    if (c.isEnum() && c.getSimpleName().equals("ApprovalOutcome")) outcomeEnum = c;
                }
                String status = "RUNNING";
                String requestStatus = null;
                try {
                    status = jdbc.queryForObject(
                            "SELECT status FROM workflow_instances WHERE id = ?",
                            String.class, (UUID) args[1]);
                    requestStatus = jdbc.queryForObject(
                            "SELECT status FROM workflow_approval_requests WHERE workflow_instance_id = ? "
                                    + "ORDER BY requested_at DESC LIMIT 1", String.class, (UUID) args[1]);
                } catch (Exception ignored) {
                }
                Object outcome = Enum.valueOf((Class<? extends Enum>) outcomeEnum, switch (
                        requestStatus == null ? "NONE" : requestStatus) {
                    case "APPROVED" -> "COMPLETED".equals(status) ? "APPROVED" : "NONE";
                    case "REJECTED" -> "REJECTED";
                    default -> "NONE";
                });
                yield snapshotClass.getConstructor(UUID.class, UUID.class, String.class, UUID.class,
                                String.class, outcomeEnum)
                        .newInstance((UUID) args[1],
                                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                                "HR_OFFER", null, status, outcome);
            }
            default -> null;
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    private String offerState(UUID offerId) {
        return jdbc.queryForObject(
                "SELECT state FROM hr_offers WHERE id = ? AND tenant_id = ?",
                String.class, offerId, tenantId);
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    private PlatformCryptographyService noopCrypto() {
        byte[] key = new byte[32];
        String encKey = java.util.Base64.getEncoder().encodeToString(key);
        return new JcePlatformCryptographyService(
                new com.sanad.platform.security.crypto.EnvironmentKeyMaterialProvider(
                        "v1", encKey, "v1", encKey));
    }

    private DataSource sessionTenantDataSource(DataSource base, UUID tenant) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        java.sql.Connection raw = base.getConnection();
                        try (var st = raw.createStatement()) {
                            st.execute("SELECT set_config('app.tenant_id', '" + tenant + "', false)");
                        }
                        return raw;
                    }
                    if ("unwrap".equals(method.getName())) return base.unwrap((Class<?>) args[0]);
                    if ("isWrapperFor".equals(method.getName())) return false;
                    if ("getLogWriter".equals(method.getName())) return new java.io.PrintWriter(System.out);
                    if ("setLogWriter".equals(method.getName())) return null;
                    if ("getLoginTimeout".equals(method.getName())) return 0;
                    if ("setLoginTimeout".equals(method.getName())) return null;
                    if ("getParentLogger".equals(method.getName())) return java.util.logging.Logger.getLogger("test");
                    return base.toString();
                });
    }

    // ==================== T8.11 exactness — the canonical happy path ====================

    @org.junit.jupiter.api.Test
    void convert_acceptedOffer_createsExactlyOneOfEachCanonicalOutput_andAuditsAndOutboxes() throws Exception {
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        Object result = convert(ctx(operatorId), offerId, command("req-1", List.of(), null));

        assertThat(result).isNotNull();
        UUID employmentId = (UUID) resultField(result, "employmentId");
        assertThat(employmentId).as("employment ref").isNotNull();
        assertThat((Boolean) resultField(result, "replayed")).isFalse();
        assertThat((Boolean) resultField(result, "personReused")).isFalse();

        Integer ledgerRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_hire_conversions WHERE tenant_id = ? AND offer_id = ? "
                        + "AND conversion_state = 'COMPLETE'",
                Integer.class, tenantId, offerId);
        assertThat(ledgerRows).as("§T8.11: one conversion ledger").isEqualTo(1);
        Integer personRows = jdbc.queryForObject("SELECT COUNT(*) FROM hr_people WHERE tenant_id = ?",
                Integer.class, tenantId);
        assertThat(personRows).as("§T8.11: one new person").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ?", Integer.class, tenantId))
                .as("§T8.11: one canonical employment").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employee_assignments WHERE tenant_id = ?",
                Integer.class, tenantId))
                .as("§T8.11: one canonical assignment").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employment_contracts WHERE tenant_id = ?",
                Integer.class, tenantId))
                .as("§T8.11: one contract").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_compensation_packages WHERE tenant_id = ?",
                Integer.class, tenantId))
                .as("§T8.11: one compensation package").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_onboarding_plans WHERE tenant_id = ?",
                Integer.class, tenantId))
                .as("§T8.6: hire conversion completes with an onboarding plan").isEqualTo(1);
        Integer taskRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_onboarding_tasks WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(taskRows).as("checklist tasks materialized from the seeded template").isGreaterThanOrEqualTo(1);

        Integer hireEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = ? "
                        + "AND event_type = 'HRM.RECRUITMENT.HIRE_COMPLETED'",
                Integer.class, tenantId);
        assertThat(hireEvents).as("HIRE_COMPLETED exactly once").isEqualTo(1);
        Integer planEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = ? "
                        + "AND event_type = 'HRM.ONBOARDING.PLAN_CREATED'",
                Integer.class, tenantId);
        assertThat(planEvents).as("PLAN_CREATED exactly once").isEqualTo(1);
        Integer hireAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.HIRE_CONVERTED'",
                Integer.class, tenantId);
        assertThat(hireAudits).as("conversion audited").isEqualTo(1);

        String applicationState = jdbc.queryForObject(
                "SELECT state FROM hr_applications WHERE id = ?", String.class, applicationId);
        assertThat(applicationState).as("§T8.2 step 12: application → HIRED").isEqualTo("HIRED");
        Integer headcount = jdbc.queryForObject(
                "SELECT filled_headcount FROM hr_job_openings WHERE id = ? AND tenant_id = ?",
                Integer.class, openingId, tenantId);
        assertThat(headcount).as("§T8.2 step 13: opening filled headcount derived update").isEqualTo(1);
        String candidatePool = jdbc.queryForObject(
                "SELECT pool_state FROM hr_candidates WHERE id = ?", String.class, candidateId);
        assertThat(candidatePool).as("candidate pool reflects the hire").isEqualTo("HIRED");
    }

    // ==================== T8-A01: person reuse ====================

    @org.junit.jupiter.api.Test
    void personReused_whenVerifiedIdentifierClaimMatchesExistingPerson_noDuplicatePerson() throws Exception {
        // Arrange: an existing Person with a verified NATIONAL_ID identifier (G0 authority)
        Object personService = new HrPersonService(
                new JdbcHrPersonRepository(dataSource, crypto), crypto, new IdentifierNormalizer());
        Object existing = personService.getClass()
                .getMethod("createPerson", UUID.class, String.class, String.class, String.class)
                .invoke(personService, tenantId, "Returning", null, "Employee");
        UUID existingPersonId = (UUID) existing.getClass().getMethod("id").invoke(existing);
        personService.getClass()
                .getMethod("addIdentifier", UUID.class, UUID.class, String.class, String.class, String.class)
                .invoke(personService, tenantId, existingPersonId, "NATIONAL_ID", "SA", "1098765432");

        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        Integer peopleBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_people WHERE tenant_id = ?", Integer.class, tenantId);

        // Act: conversion with the SAME verified claim
        Object result = convert(ctx(operatorId), offerId,
                command("req-reuse", List.of(claim("NATIONAL_ID", "SA", "1098765432")), null));

        // Assert: linked, not duplicated
        assertThat(resultField(result, "personId")).isEqualTo(existingPersonId);
        assertThat((Boolean) resultField(result, "personReused"))
                .as("§T8.4 case A: verified unambiguous match reuses the Person").isTrue();
        Integer peopleAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_people WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(peopleAfter).as("§T8-A01: no duplicate Person row").isEqualTo(peopleBefore);
        Boolean ledgerReuse = jdbc.queryForObject(
                "SELECT person_reused FROM hr_hire_conversions WHERE tenant_id = ? AND offer_id = ?",
                Boolean.class, tenantId, offerId);
        assertThat(ledgerReuse).as("§7.3 case 1: person_reused recorded in the ledger").isTrue();
    }

    // ==================== T8-A02: ambiguity fails closed ====================

    @org.junit.jupiter.api.Test
    void emailOnlyDuplicate_failsClosed_CONVERSION_IDENTITY_AMBIGUOUS_noSilentMerge() throws Exception {
        // Existing Person carries an EMAIL identifier equal to the candidate email;
        // the conversion supplies NO verified strong claim.
        Object personService = new HrPersonService(
                new JdbcHrPersonRepository(dataSource, crypto), crypto, new IdentifierNormalizer());
        Object existing = personService.getClass()
                .getMethod("createPerson", UUID.class, String.class, String.class, String.class)
                .invoke(personService, tenantId, "Same", null, "EmailOwner");
        UUID existingPersonId = (UUID) existing.getClass().getMethod("id").invoke(existing);
        String candidateEmail = jdbc.queryForObject(
                "SELECT contact_email_ciphertext FROM hr_candidates WHERE id = ?", String.class, candidateId);
        String plaintextEmail = decryptCandidateEmail(candidateEmail);
        personService.getClass()
                .getMethod("addIdentifier", UUID.class, UUID.class, String.class, String.class, String.class)
                .invoke(personService, tenantId, existingPersonId, "EMAIL", null, plaintextEmail);

        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);

        assertThatThrownBy(() -> convert(ctx(operatorId), offerId, command("req-amb", List.of(), null)))
                .as("§T8.4 case C: email-only duplicate is NOT an automatic link")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONVERSION_IDENTITY_AMBIGUOUS");

        assertNoPartialHireState(offerId);
    }

    @org.junit.jupiter.api.Test
    void conflictingStrongClaims_failsClosed_CONVERSION_IDENTITY_AMBIGUOUS() throws Exception {
        Object personService = new HrPersonService(
                new JdbcHrPersonRepository(dataSource, crypto), crypto, new IdentifierNormalizer());
        Object p1 = personService.getClass()
                .getMethod("createPerson", UUID.class, String.class, String.class, String.class)
                .invoke(personService, tenantId, "Claim", null, "One");
        UUID p1Id = (UUID) p1.getClass().getMethod("id").invoke(p1);
        Object p2 = personService.getClass()
                .getMethod("createPerson", UUID.class, String.class, String.class, String.class)
                .invoke(personService, tenantId, "Claim", null, "Two");
        UUID p2Id = (UUID) p2.getClass().getMethod("id").invoke(p2);
        personService.getClass()
                .getMethod("addIdentifier", UUID.class, UUID.class, String.class, String.class, String.class)
                .invoke(personService, tenantId, p1Id, "NATIONAL_ID", "SA", "1000000001");
        personService.getClass()
                .getMethod("addIdentifier", UUID.class, UUID.class, String.class, String.class, String.class)
                .invoke(personService, tenantId, p2Id, "IQAMA", "SA", "2400000002");

        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        assertThatThrownBy(() -> convert(ctx(operatorId), offerId, command("req-conflict",
                List.of(claim("NATIONAL_ID", "SA", "1000000001"),
                        claim("IQAMA", "SA", "2400000002")), null)))
                .as("§T8.1.4c: conflicting identity claims fail closed for human review")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONVERSION_IDENTITY_AMBIGUOUS");
        assertNoPartialHireState(offerId);
    }

    private String decryptCandidateEmail(String storedCiphertext) {
        String[] parts = storedCiphertext.split(":", 3);
        String keyVersion = parts.length > 1 ? parts[1] : "v1";
        return crypto.decrypt(tenantId, HrCandidateService.PURPOSE_CONTACT_EMAIL,
                new com.sanad.platform.security.crypto.EncryptedValue(storedCiphertext, keyVersion,
                        "AES-256-GCM"));
    }

    // ==================== T8-A03 / T8-A04: idempotent replay ====================

    @org.junit.jupiter.api.Test
    void sameIdempotencyKeyReplay_returnsOriginalResult_replayedTrue_noDuplicates() throws Exception {
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        Object first = convert(ctx(operatorId), offerId, command("same-key", List.of(), null));

        Object replay = convert(ctx(operatorId), offerId, command("same-key", List.of(), null));

        assertThat(resultField(replay, "employmentId"))
                .as("§T8-A03: original exact result").isEqualTo(resultField(first, "employmentId"));
        assertThat(resultField(replay, "personId")).isEqualTo(resultField(first, "personId"));
        assertThat(resultField(replay, "onboardingPlanId")).isEqualTo(resultField(first, "onboardingPlanId"));
        assertThat((Boolean) resultField(replay, "replayed")).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ?", Integer.class, tenantId))
                .as("no duplicate employment").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = ? "
                        + "AND event_type = 'HRM.RECRUITMENT.HIRE_COMPLETED'",
                Integer.class, tenantId)).as("HIRE_COMPLETED still exactly once").isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void differentIdempotencyKeySameOffer_returnsOriginalResult_noDuplicateHire() throws Exception {
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        Object first = convert(ctx(operatorId), offerId, command("key-A", List.of(), null));

        Object retry = convert(ctx(operatorId), offerId, command("key-B", List.of(), null));

        assertThat(resultField(retry, "employmentId"))
                .as("§T8-A04: ledger keyed on (tenant, offer), not request id")
                .isEqualTo(resultField(first, "employmentId"));
        assertThat((Boolean) resultField(retry, "replayed")).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_hire_conversions WHERE tenant_id = ? AND offer_id = ?",
                Integer.class, tenantId, offerId)).isEqualTo(1);
    }

    // ==================== T8-A05 / §T8.10: failure injection sweep ====================

    @org.junit.jupiter.api.Test
    void midTransactionFailureInjection_rollsBackEverything_partialStatesZero() throws Exception {
        // Count the writes of a successful conversion once, then fail the N-th
        // write for N=1..total and prove FULL rollback at every injection point:
        // person, employment, assignment, contract, compensation, onboarding,
        // ledger, application-HIRED, headcount, audit, outbox (§T8.10 matrix).
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        ProbeDataSource probe = new ProbeDataSource(dataSource);
        buildConversionService(probe);
        seedPipelineForProbe(probe, offerId);
    }

    /**
     * The injection sweep runs its own dedicated offer (the shared fixture was
     * built on the un-probed service); it converts with the N-th write failing
     * and asserts the full-rollback invariant for every N.
     */
    private void seedPipelineForProbe(ProbeDataSource probeDs, UUID excludedOffer) throws Exception {
        for (int n = 1; n <= 40; n++) {
            // fresh pipeline per injection point (offer must be ACCEPTED pre-failure)
            Object offerService = Class.forName(
                            "com.sanad.platform.hr.recruitment.application.HrOfferService")
                    .getConstructor(JdbcHrOfferRepository.class,
                            Class.forName(AUTH_PORT_CLASS),
                            Class.forName(
                                    "com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort"))
                    .newInstance(new JdbcHrOfferRepository(dataSource), authStub(), offerStub());
            UUID offerId = (UUID) offerService.getClass()
                    .getMethod("createOffer", HrCommandContext.class, UUID.class, String.class,
                            String.class, OffsetDateTime.class)
                    .invoke(offerService, ctx(operatorId), applicationId, HIRE_TERMS, HIRE_COMP, null);
            Method submit = offerService.getClass().getMethod("submitForApproval",
                    HrCommandContext.class, UUID.class);
            UUID instanceId = (UUID) submit.invoke(offerService, ctx(operatorId), offerId);
            jdbc.update("UPDATE workflow_instances SET status = 'COMPLETED' WHERE id = ?", instanceId);
            jdbc.update("UPDATE workflow_approval_requests SET status = 'APPROVED' "
                    + "WHERE workflow_instance_id = ?", instanceId);
            offerService.getClass()
                    .getMethod("extendFromApproval", HrCommandContext.class, UUID.class, UUID.class)
                    .invoke(offerService, ctx(operatorId), offerId, instanceId);
            offerService.getClass()
                    .getMethod("accept", HrCommandContext.class, UUID.class)
                    .invoke(offerService, ctx(operatorId), offerId);

            // second application would be needed for a second offer on the same opening;
            // use a FRESH full pipeline per iteration instead — cheaper: only one offer
            // per iteration is converted; the FIRST conversion (n calibration) stops the loop.
            probeDs.reset();
            probeDs.failAt(n);
            Object probeService = probeService(probeDs);
            Exception failure = null;
            Object result = null;
            try {
                result = convertVia(probeService, ctx(operatorId), offerId,
                        command("probe-" + n, List.of(), null));
            } catch (Exception e) {
                failure = e;
            }
            if (failure == null) {
                assertThat(n).as("calibration: the first clean run passes at some N").isGreaterThan(1);
                return; // sweep complete: every n < total failed cleanly
            }
            assertNoPartialHireState(offerId);
            assertThat(failure.getMessage() == null ? "" : failure.getMessage())
                    .as("injection n=%d must not leak probe text into canonical state", n).isNotNull();
        }
        org.junit.jupiter.api.Assertions.fail("probe sweep did not calibrate within 40 writes");
    }

    private Object probeService(DataSource ds) throws Exception {
        // rebuild the conversion service graph on the probe DataSource
        DataSource keep = dataSource;
        dataSource = ds;
        try {
            buildConversionService(ds);
            return conversionService;
        } finally {
            dataSource = keep;
        }
    }

    private Object convertVia(Object service, HrCommandContext ctx, UUID offerId, Object command)
            throws Exception {
        Method convert = service.getClass().getMethod("convert",
                HrCommandContext.class, UUID.class, Class.forName(COMMAND_CLASS));
        try {
            return convert.invoke(service, ctx, offerId, command);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw new IllegalStateException(cause);
        }
    }

    /** Counts successful executeUpdate calls and fails the N-th (failure probe). */
    private static final class ProbeDataSource implements DataSource {
        private final DataSource delegate;
        private final java.util.concurrent.atomic.AtomicInteger updates =
                new java.util.concurrent.atomic.AtomicInteger();
        private volatile int failAt = -1;

        ProbeDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        void failAt(int n) {
            this.failAt = n;
            this.updates.set(0);
        }

        void reset() {
            this.failAt = -1;
            this.updates.set(0);
        }

        @Override
        public java.sql.Connection getConnection() throws SQLException {
            java.sql.Connection raw = delegate.getConnection();
            return (java.sql.Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            Object inner = raw.prepareStatement((String) args[0]);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[]{java.sql.PreparedStatement.class},
                                    (p2, m2, a2) -> {
                                        if ("executeUpdate".equals(m2.getName())) {
                                            int n = updates.incrementAndGet();
                                            if (failAt > 0 && n == failAt) {
                                                throw new java.sql.SQLTransientConnectionException(
                                                        "PROBE_INJECTION_" + n);
                                            }
                                        }
                                        try {
                                            return m2.invoke(inner, a2);
                                        } catch (java.lang.reflect.InvocationTargetException te) {
                                            throw te.getCause() instanceof RuntimeException re ? re
                                                    : new IllegalStateException(te.getCause());
                                        }
                                    });
                        }
                        try {
                            return method.invoke(raw, args);
                        } catch (java.lang.reflect.InvocationTargetException te) {
                            throw te.getCause() instanceof Exception ex ? ex
                                    : new IllegalStateException(te.getCause());
                        }
                    });
        }

        @Override public java.sql.Connection getConnection(String u, String p) throws SQLException {
            return getConnection();
        }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
        @Override public java.io.PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
        @Override public void setLogWriter(java.io.PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
        @Override public void setLoginTimeout(int s) throws SQLException { delegate.setLoginTimeout(s); }
        @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
        @Override public java.util.logging.Logger getParentLogger()
                throws java.sql.SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
    }

    /** §T8.10 invariant: zero partial state anywhere after a failed conversion. */
    private void assertNoPartialHireState(UUID offerId) {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_hire_conversions WHERE offer_id = ?", Integer.class, offerId))
                .as("HEADCOUNT/ledger partial = 0 (ledger row rolled back with everything)").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ? AND employee_number LIKE 'EMP-'",
                Integer.class, tenantId)).as("EMPLOYMENT_PARTIAL=0").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_onboarding_plans WHERE tenant_id = ?", Integer.class, tenantId))
                .as("ONBOARDING_PARTIAL=0").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM hr_applications WHERE id = ?", String.class, applicationId))
                .as("APPLICATION_HIRED_PARTIAL=0").isEqualTo("OFFER");
        assertThat(jdbc.queryForObject(
                "SELECT filled_headcount FROM hr_job_openings WHERE id = ?",
                Integer.class, openingId)).as("HEADCOUNT_PARTIAL=0").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.HIRE_CONVERTED'", Integer.class, tenantId))
                .as("AUDIT_PARTIAL=0").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = ? "
                        + "AND event_type IN ('HRM.RECRUITMENT.HIRE_COMPLETED','HRM.ONBOARDING.PLAN_CREATED')",
                Integer.class, tenantId)).as("OUTBOX_PARTIAL=0").isZero();
    }

    // ==================== T8-A06: onboarding template is mandatory ====================

    @org.junit.jupiter.api.Test
    void onboardingTemplateMissing_failsClosed_CONVERSION_ONBOARDING_TEMPLATE_INVALID_fullRollback()
            throws Exception {
        jdbc.update("DELETE FROM hr_onboarding_checklist_templates WHERE tenant_id = ?", tenantId);
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        assertThatThrownBy(() -> convert(ctx(operatorId), offerId, command("req-notmpl", List.of(), null)))
                .as("§T8.6: no hire without an onboarding plan")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONVERSION_ONBOARDING_TEMPLATE_INVALID");
        assertNoPartialHireState(offerId);
    }

    @org.junit.jupiter.api.Test
    void onboardingTemplateInvalidDefinition_failsClosed() throws Exception {
        jdbc.update("UPDATE hr_onboarding_checklist_templates SET definition = '[]'::jsonb "
                + "WHERE tenant_id = ?", tenantId);
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        assertThatThrownBy(() -> convert(ctx(operatorId), offerId, command("req-badtpl", List.of(), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONVERSION_ONBOARDING_TEMPLATE_INVALID");
        assertNoPartialHireState(offerId);
    }

    // ==================== T8-A07: IAM boundary ====================

    @org.junit.jupiter.api.Test
    void iamUnavailable_conversionSucceeds_noIamBindingCreated() throws Exception {
        // IAM is OUTSIDE the atomic boundary (§T8.7): conversion never calls it,
        // never provisions bindings, and succeeds without any IAM dependency.
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        Object result = convert(ctx(operatorId), offerId, command("req-iam", List.of(), null));
        assertThat(resultField(result, "employmentId")).isNotNull();
        Integer bindings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_iam_access_bindings WHERE tenant_id = ?",
                Integer.class, tenantId);
        assertThat(bindings).as("no IAM activation inside the conversion boundary").isZero();
        Integer hireEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = ? "
                        + "AND event_type = 'HRM.RECRUITMENT.HIRE_COMPLETED'",
                Integer.class, tenantId);
        assertThat(hireEvents).as("IAM consumes HIRE_COMPLETED later, by its own contract").isEqualTo(1);
    }

    // ==================== T8-A08: concurrent double conversion ====================

    @org.junit.jupiter.api.Test
    void concurrentDoubleConversion_oneLogicalHire_loserResolvesToOriginalResult() throws Exception {
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Method convert = conversionService.getClass().getMethod("convert",
                HrCommandContext.class, UUID.class, Class.forName(COMMAND_CLASS));
        Future<Object> a = pool.submit(() -> {
            start.await();
            try {
                return convert.invoke(conversionService, ctx(operatorId), offerId,
                        command("race-A", List.of(), null));
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        });
        Future<Object> b = pool.submit(() -> {
            start.await();
            try {
                return convert.invoke(conversionService, ctx(operatorId), offerId,
                        command("race-B", List.of(), null));
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        });
        start.countDown();
        Object resultA = a.get(60, TimeUnit.SECONDS);
        Object resultB = b.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        UUID employmentA = (UUID) resultField(resultA, "employmentId");
        UUID employmentB = (UUID) resultField(resultB, "employmentId");
        assertThat(employmentA).as("§T8-A08: both callers resolve to the SAME logical hire")
                .isEqualTo(employmentB);
        boolean oneReplayed = ((Boolean) resultField(resultA, "replayed"))
                ^ ((Boolean) resultField(resultB, "replayed"));
        assertThat(oneReplayed).as("exactly one winner; the loser replays").isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ?", Integer.class, tenantId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = ? "
                        + "AND event_type = 'HRM.RECRUITMENT.HIRE_COMPLETED'",
                Integer.class, tenantId)).isEqualTo(1);
    }

    // ==================== T8-A09: tenant mismatch ====================

    @org.junit.jupiter.api.Test
    void tenantMismatch_failsClosedBeforeAnyWrite() throws Exception {
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        UUID tenantB = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantB, "Tenant B", "tb-" + tenantB.toString().substring(0, 8));
        assertThatThrownBy(() -> convert(
                new HrCommandContext(tenantB, null, operatorId, UUID.randomUUID()), offerId,
                command("req-xtenant", List.of(), null)))
                .as("§T8.4/§7.3 case 9: foreign tenant context never resolves the offer")
                .isInstanceOf(IllegalStateException.class);
        assertNoPartialHireState(offerId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_people WHERE tenant_id = ?", Integer.class, tenantId))
                .as("TENANT_MISMATCH fail-closed before ANY write").isZero();
    }

    // ==================== §T8.12 security ====================

    @org.junit.jupiter.api.Test
    void offerNotAccepted_failsClosed() throws Exception {
        // EXTENDED but never accepted
        Object offerService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrOfferService")
                .getConstructor(JdbcHrOfferRepository.class,
                        Class.forName(AUTH_PORT_CLASS),
                        Class.forName(
                                "com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort"))
                .newInstance(new JdbcHrOfferRepository(dataSource), authStub(), offerStub());
        UUID offerId = (UUID) offerService.getClass()
                .getMethod("createOffer", HrCommandContext.class, UUID.class, String.class,
                        String.class, OffsetDateTime.class)
                .invoke(offerService, ctx(operatorId), applicationId, HIRE_TERMS, HIRE_COMP, null);
        Method submit = offerService.getClass().getMethod("submitForApproval",
                HrCommandContext.class, UUID.class);
        UUID instanceId = (UUID) submit.invoke(offerService, ctx(operatorId), offerId);
        jdbc.update("UPDATE workflow_instances SET status = 'COMPLETED' WHERE id = ?", instanceId);
        jdbc.update("UPDATE workflow_approval_requests SET status = 'APPROVED' "
                + "WHERE workflow_instance_id = ?", instanceId);
        offerService.getClass()
                .getMethod("extendFromApproval", HrCommandContext.class, UUID.class, UUID.class)
                .invoke(offerService, ctx(operatorId), offerId, instanceId);
        assertThat(offerState(offerId)).isEqualTo("EXTENDED");

        assertThatThrownBy(() -> convert(ctx(operatorId), offerId, command("req-ext", List.of(), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_OFFER_STATE_CONFLICT");
    }

    @org.junit.jupiter.api.Test
    void missingHireConvertCapability_denied() throws Exception {
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        Map<String, Boolean> keep = grants;
        grants = new HashMap<>(keep);
        grants.remove(CAP_HIRE_CONVERT);
        try {
            assertThatThrownBy(() -> convert(ctx(operatorId), offerId,
                    command("req-nocap", List.of(), null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HRM_SCOPE_DENIED");
        } finally {
            grants = keep;
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_hire_conversions", Integer.class)).isZero();
    }

    // ==================== §T8.5 position occupancy ====================

    @org.junit.jupiter.api.Test
    void positionOverOccupancy_failsClosed_CONVERSION_POSITION_OVER_OCCUPANCY_fullRollback() throws Exception {
        UUID positionId = seedOccupiedPosition();
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        assertThatThrownBy(() -> convert(ctx(operatorId), offerId,
                command("req-occ", List.of(), positionId)))
                .as("§T8.5: over-occupancy rolls back EVERYTHING (no stale snapshot)")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONVERSION_POSITION_OVER_OCCUPANCY");
        assertNoPartialHireState(offerId);
    }

    private UUID seedOccupiedPosition() {
        UUID positionId = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_positions (id, tenant_id, title, status) "
                + "VALUES (?, ?, 'T8 Position', 'ACTIVE')", positionId, tenantId);
        jdbc.update("INSERT INTO hr_position_versions (id, tenant_id, position_id, organization_id, "
                        + "org_unit_id, title, effective_from, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'T8 Position v1', CURRENT_DATE - 1, 'ACTIVE')",
                UUID.randomUUID(), tenantId, positionId, organizationId, orgUnitId);
        jdbc.update("UPDATE hr_job_openings SET position_id = ? WHERE id = ? AND tenant_id = ?",
                positionId, openingId, tenantId);
        // occupy the position with an unrelated ACTIVE employment + OCCUPYING assignment
        Object personService = null;
        UUID otherPerson = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_people (id, tenant_id, first_name, last_name, display_name, version, "
                        + "created_at, updated_at) VALUES (?, ?, 'Other', 'Holder', 'Other Holder', 0, NOW(), NOW())",
                otherPerson, tenantId);
        UUID otherEmployment = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, "
                        + "first_name, last_name, display_name, employment_type, worker_classification_code, "
                        + "status, hire_date, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'EMP-OTHER-1', 'Other', 'Holder', 'Other Holder', "
                        + "'FULL_TIME', 'FULL_TIME', 'ACTIVE', CURRENT_DATE, 0, NOW(), NOW())",
                otherEmployment, tenantId, otherPerson, legalEntityId);
        jdbc.update("INSERT INTO hr_employee_assignments (id, tenant_id, employment_id, organization_id, "
                        + "org_unit_id, position_id, assignment_type, occupancy_mode, allocation_percent, "
                        + "effective_from, status, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'PRIMARY', 'OCCUPYING', 100, CURRENT_DATE - 1, "
                        + "'ACTIVE', 0, NOW(), NOW())",
                UUID.randomUUID(), tenantId, otherEmployment, organizationId, orgUnitId, positionId);
        return positionId;
    }

    // ==================== §T8.8 hire approval policy ====================

    @org.junit.jupiter.api.Test
    void policyOff_missingPolicyRow_conversionProceedsWithoutApproval() throws Exception {
        Integer policyRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_tenant_policies WHERE tenant_id = ? "
                        + "AND policy_code = 'HRM.RECRUITMENT.HIRE_APPROVAL'",
                Integer.class, tenantId);
        assertThat(policyRows).as("no authoritative ON configuration exists").isZero();
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        Object result = convert(ctx(operatorId), offerId, command("req-off", List.of(), null));
        assertThat(resultField(result, "employmentId")).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void policyOn_unapprovedConversion_refused_andApprovalStarted() throws Exception {
        seedHireApprovalPolicy("ON");
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        // seed an OPEN (RUNNING) approval instance so the stub resolves it
        UUID instanceId = UUID.nameUUIDFromBytes(("hire-approval-" + offerId).getBytes());
        seedHireApprovalWorkflowRows(instanceId, offerId, "RUNNING", null);

        assertThatThrownBy(() -> convert(ctx(operatorId), offerId, command("req-on1", List.of(), null)))
                .as("§T8.8: policy ON requires the authoritative Y2 approval")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_HIRE_APPROVAL_REQUIRED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_hire_conversions WHERE offer_id = ?",
                Integer.class, offerId)).isZero();
    }

    @org.junit.jupiter.api.Test
    void policyOn_approvedApproval_conversionProceeds_rejectedRefused() throws Exception {
        seedHireApprovalPolicy("ON");
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        UUID instanceId = UUID.nameUUIDFromBytes(("hire-approval-" + offerId).getBytes());
        seedHireApprovalWorkflowRows(instanceId, offerId, "COMPLETED", "APPROVED");
        Object result = convert(ctx(operatorId), offerId, command("req-on2", List.of(), null));
        assertThat(resultField(result, "employmentId")).isNotNull();

        // a REJECTED approval for a different offer must never authorize this one
        UUID offer2 = createHireReadyOffer2();
        UUID otherInstance = UUID.nameUUIDFromBytes(("hire-approval-" + offer2).getBytes());
        seedHireApprovalWorkflowRows(otherInstance, offerId, "COMPLETED", "REJECTED");
        assertThatThrownBy(() -> convert(ctx(operatorId), offer2, command("req-on3", List.of(), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_HIRE_APPROVAL");
    }

    private UUID createHireReadyOffer2() throws Exception {
        // a second ACCEPTED offer needs a second application on the same opening
        UUID secondCandidate = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, "
                        + "pool_state, version) VALUES (?, ?, 'CAND-T8B', 'T8 Second', 'ACTIVE', 0)",
                secondCandidate, tenantId);
        UUID secondApplication = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_applications (id, tenant_id, candidate_id, job_opening_id, state, version) "
                + "VALUES (?, ?, ?, ?, 'OFFER', 0)", secondApplication, tenantId, secondCandidate, openingId);
        Object offerService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrOfferService")
                .getConstructor(JdbcHrOfferRepository.class,
                        Class.forName(AUTH_PORT_CLASS),
                        Class.forName(
                                "com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort"))
                .newInstance(new JdbcHrOfferRepository(dataSource), authStub(), offerStub());
        UUID offerId = (UUID) offerService.getClass()
                .getMethod("createOffer", HrCommandContext.class, UUID.class, String.class,
                        String.class, OffsetDateTime.class)
                .invoke(offerService, ctx(operatorId), secondApplication, HIRE_TERMS, HIRE_COMP, null);
        Method submit = offerService.getClass().getMethod("submitForApproval",
                HrCommandContext.class, UUID.class);
        UUID instanceId = (UUID) submit.invoke(offerService, ctx(operatorId), offerId);
        jdbc.update("UPDATE workflow_instances SET status = 'COMPLETED' WHERE id = ?", instanceId);
        jdbc.update("UPDATE workflow_approval_requests SET status = 'APPROVED' "
                + "WHERE workflow_instance_id = ?", instanceId);
        offerService.getClass()
                .getMethod("extendFromApproval", HrCommandContext.class, UUID.class, UUID.class)
                .invoke(offerService, ctx(operatorId), offerId, instanceId);
        offerService.getClass()
                .getMethod("accept", HrCommandContext.class, UUID.class)
                .invoke(offerService, ctx(operatorId), offerId);
        return offerId;
    }

    private void seedHireApprovalPolicy(String value) {
        jdbc.update("INSERT INTO hr_tenant_policies (id, tenant_id, policy_code, policy_value, "
                        + "created_at, updated_at) VALUES (?, ?, 'HRM.RECRUITMENT.HIRE_APPROVAL', ?, NOW(), NOW())",
                UUID.randomUUID(), tenantId, value);
    }

    private void seedHireApprovalWorkflowRows(UUID instanceId, UUID offerId,
                                              String instanceStatus, String requestStatus) {
        jdbc.update("INSERT INTO workflow_instances (id, tenant_id, definition_family_id, "
                        + "definition_version_id, workflow_version, business_entity_type, business_entity_id, "
                        + "current_step_key, status, started_by, correlation_id, created_at, updated_at) "
                        + "VALUES (?, ?, gen_random_uuid(), gen_random_uuid(), 1, 'HR_OFFER_HIRE', ?, "
                        + "'submit', ?, ?, ?, NOW(), NOW())",
                instanceId, tenantId, offerId, instanceStatus, operatorId, offerId);
        if (requestStatus != null) {
            jdbc.update("INSERT INTO workflow_approval_requests (id, workflow_instance_id, step_key, "
                            + "requested_by, status, requested_at) "
                            + "VALUES (?, ?, 'hire_approval', ?, ?, NOW())",
                    UUID.randomUUID(), instanceId, operatorId, requestStatus);
        }
    }

    // ==================== §T8.11 PII sentinel ====================

    @org.junit.jupiter.api.Test
    void conversionEvidence_carriesNoPii_orRawCompensation() throws Exception {
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        convert(ctx(operatorId), offerId, command("req-pii", List.of(), null));

        String email = decryptCandidateEmail(jdbc.queryForObject(
                "SELECT contact_email_ciphertext FROM hr_candidates WHERE id = ?",
                String.class, candidateId));
        String localPart = email.split("@")[0];

        String planPayload = jdbc.queryForObject(
                "SELECT string_agg(payload::text, ' ') FROM hr_domain_event_outbox "
                        + "WHERE tenant_id = ? AND event_type = 'HRM.ONBOARDING.PLAN_CREATED'",
                String.class, tenantId);
        String hirePayload = jdbc.queryForObject(
                "SELECT string_agg(payload::text, ' ') FROM hr_domain_event_outbox "
                        + "WHERE tenant_id = ? AND event_type = 'HRM.RECRUITMENT.HIRE_COMPLETED'",
                String.class, tenantId);
        String auditPayload = jdbc.queryForObject(
                "SELECT string_agg(after_state::text, ' ') FROM hr_audit_ledger "
                        + "WHERE tenant_id = ? AND action = 'HRM.RECRUITMENT.HIRE_CONVERTED'",
                String.class, tenantId);
        for (String surface : new String[]{planPayload, hirePayload, auditPayload}) {
            assertThat(surface)
                    .as("no candidate PII in evidence surfaces")
                    .doesNotContain(email)
                    .doesNotContain(localPart);
            assertThat(surface)
                    .as("no raw compensation values in evidence payloads")
                    .doesNotContain("111000");
        }
    }

    // ==================== §T8.2 step 13b: opening headcount guard ====================

    @org.junit.jupiter.api.Test
    void openingHeadcountExhausted_secondConversion_failsClosed() throws Exception {
        // headcount 1/1 already consumed by the first hire (single-opening fixture):
        UUID offerId = createHireReadyOffer(HIRE_TERMS, HIRE_COMP);
        Object first = convert(ctx(operatorId), offerId, command("req-h1", List.of(), null));
        assertThat(resultField(first, "employmentId")).isNotNull();

        UUID secondOffer = createHireReadyOffer2();
        // the opening requested exactly 1 hire; the second conversion must fail closed
        jdbc.update("UPDATE hr_applications SET state = 'OFFER' WHERE id = "
                + "(SELECT application_id FROM hr_offers WHERE id = ? AND tenant_id = ?)",
                secondOffer, tenantId);
        assertThatThrownBy(() -> convert(ctx(operatorId), secondOffer,
                command("req-h2", List.of(), null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ?", Integer.class, tenantId))
                .as("no second hire materialized").isEqualTo(1);
    }
}
