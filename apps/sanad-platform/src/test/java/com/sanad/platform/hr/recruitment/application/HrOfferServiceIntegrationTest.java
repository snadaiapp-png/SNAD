package com.sanad.platform.hr.recruitment.application;

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

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1 T7 — HrOffer aggregate + immutable HrOfferVersion + Workflow Y2
 * authoritative approval — service integration contract.
 *
 * <p>Covers the authorized semantic families (directive T7.14) at the
 * application-authority level with a controllable workflow port; the real
 * engine integration (ANY_ONE/ALL/self-approval via Workflow Y2 itself) is
 * certified in {@code HrOfferWorkflowY2IntegrationTest}.</p>
 *
 * <p>Families proven here: aggregate creation; valid initial version;
 * immutable historical version (application + DB trigger); sequential/new
 * version creation; duplicate/concurrent version protection; DRAFT→EXTENDED
 * bypass rejected; DRAFT→PENDING_APPROVAL; authoritative Y2 linkage;
 * incomplete approval cannot extend; rejection; rejection reason; stale
 * approval result; wrong workflow instance; duplicate callback/replay;
 * extended-offer revision creates new version; terminal offer transition
 * denial; candidate/application consistency; failure injection (workflow
 * creation failure, concurrent approval/rejection, concurrent version
 * creation); unauthorized actor denial.</p>
 *
 * <p>Clean-RED convention: T7 classes load reflectively.</p>
 */
class HrOfferServiceIntegrationTest {

    private static final String REPO_CLASS = "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrOfferRepository";
    private static final String SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrOfferService";
    private static final String PORT_CLASS = "com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort";
    private static final String AUTH_PORT_CLASS = "com.sanad.platform.hr.recruitment.application.RecruitmentAuthorizationPort";
    private static final String APPLICATION_SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrApplicationService";
    private static final String APPLICATION_REPO_CLASS = "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrApplicationRepository";
    private static final String OPENING_SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrJobOpeningService";
    private static final String STATE_CLASS = "com.sanad.platform.hr.recruitment.domain.HrOfferState";

    private static final String CAP_MANAGE = "HRM.RECRUITMENT.OFFER.MANAGE";
    private static final String CAP_EXTEND = "HRM.RECRUITMENT.OFFER.EXTEND";

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
    private Object offerWorkflowStub;
    private Map<String, Boolean> grants;
    private UUID tenantId;
    private UUID operatorId;
    private UUID candidateId;
    private UUID openingId;
    private UUID applicationId;

    private final AtomicInteger workflowStartCalls = new AtomicInteger();

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
        workflowStartCalls.set(0);
        seedCandidateOpeningAndOfferStageApplication();

        repository = Class.forName(REPO_CLASS)
                .getConstructor(DataSource.class)
                .newInstance(ds);

        grants = new HashMap<>();
        grants.put(CAP_MANAGE, true);
        grants.put(CAP_EXTEND, true);
        grants.put("HRM.RECRUITMENT.APPLICATION.MANAGE", true);
        grants.put("HRM.RECRUITMENT.APPLICATION.ADVANCE", true);
        grants.put("HRM.RECRUITMENT.APPLICATION.REJECT", true);
        grants.put("HRM.RECRUITMENT.CANDIDATE.MANAGE", true);
        grants.put("HRM.RECRUITMENT.CANDIDATE.VIEW", true);
        grants.put("HRM.RECRUITMENT.OPENING.MANAGE", true);
        grants.put("HRM.RECRUITMENT.OPENING.PUBLISH", true);

        Object applicationService = Class.forName(APPLICATION_SERVICE_CLASS)
                .getConstructor(Class.forName(APPLICATION_REPO_CLASS), Class.forName(AUTH_PORT_CLASS))
                .newInstance(Class.forName(APPLICATION_REPO_CLASS)
                                .getConstructor(DataSource.class).newInstance(ds),
                        authStub());
        service = Class.forName(SERVICE_CLASS)
                .getConstructor(Class.forName(REPO_CLASS),
                        Class.forName(AUTH_PORT_CLASS),
                        Class.forName(PORT_CLASS))
                .newInstance(repository, authStub(), offerWorkflowStub());
    }

    @AfterEach
    void drainExecutors() {
        // nothing persistent; hook kept for symmetry with concurrency tests
    }

    // ==================== T7.3/T7.4 — aggregate + immutable versions ====================

    @Test
    void createOffer_persistsDraftAggregate_withValidInitialVersion_andAudit() throws Exception {
        UUID offerId = createOffer();

        assertThat(offerState(offerId)).isEqualTo("DRAFT");
        Map<String, Object> offer = jdbc.queryForMap(
                "SELECT current_version_id, pending_offer_version_id, pending_workflow_instance_id, version "
                        + "FROM hr_offers WHERE id = ? AND tenant_id = ?", offerId, tenantId);
        UUID currentVersionId = (UUID) offer.get("current_version_id");
        assertThat(currentVersionId).as("a DRAFT offer always references its current version").isNotNull();
        assertThat(offer.get("pending_workflow_instance_id"))
                .as("no approval correlation before submission").isNull();
        assertThat(((Number) offer.get("version")).longValue()).isZero();

        Map<String, Object> version = jdbc.queryForMap(
                "SELECT version_number, contract_terms, compensation, created_by "
                        + "FROM hr_offer_versions WHERE id = ? AND tenant_id = ?", currentVersionId, tenantId);
        assertThat(((Number) version.get("version_number")).intValue()).isEqualTo(1);
        assertThat(version.get("contract_terms").toString()).contains("\"base_salary\":");
        assertThat(version.get("created_by")).isEqualTo(operatorId);

        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.OFFER_CREATED'",
                Integer.class, offerId);
        assertThat(auditRows).as("creation is audited").isEqualTo(1);
    }

    @Test
    void createOffer_requiresApplicationInOfferStage_sameTenant() throws Exception {
        // application is at OFFER stage: creation succeeds (covered elsewhere);
        // a fresh application at APPLIED must be refused.
        UUID appliedId = newApplicationAtAppliedStage();
        assertThatThrownBy(() -> invokeService("createOffer",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class, OffsetDateTime.class},
                ctx(operatorId), appliedId, TERMS_V1, COMP_V1, null))
                .as("T7.10: the application must be in the legally allowed recruiting stage (OFFER)")
                .isInstanceOf(Exception.class);

        UUID tenantB = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantB, "Tenant B", "tb-" + tenantB.toString().substring(0, 8));
        assertThatThrownBy(() -> invokeService("createOffer",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class, OffsetDateTime.class},
                new HrCommandContext(tenantB, null, operatorId, UUID.randomUUID()),
                applicationId, TERMS_V1, COMP_V1, null))
                .as("T7.10: a foreign-tenant context must never resolve the application")
                .isInstanceOf(Exception.class);
    }

    @Test
    void reviseOnDraft_createsSequentialVersion_oldVersionRemainsByteHistoricallyUnchanged() throws Exception {
        UUID offerId = createOffer();
        UUID v1 = currentVersionId(offerId);
        Map<String, Object> v1Before = rawVersionRow(v1);

        UUID v2 = (UUID) invokeService("reviseOffer",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class, OffsetDateTime.class},
                ctx(operatorId), offerId, TERMS_V2, COMP_V2, null);

        assertThat(v2).isNotEqualTo(v1);
        Map<String, Object> v1After = rawVersionRow(v1);
        assertThat(v1After).as("T7.4: the old version remains field-historically unchanged")
                .isEqualTo(v1Before);

        Map<String, Object> v2Row = jdbc.queryForMap(
                "SELECT version_number, contract_terms FROM hr_offer_versions WHERE id = ?", v2);
        assertThat(((Number) v2Row.get("version_number")).intValue()).isEqualTo(2);
        assertThat(v2Row.get("contract_terms").toString()).contains("\"base_salary\":222000");
        assertThat(currentVersionId(offerId)).isEqualTo(v2);
    }

    @Test
    void historicalVersions_areImmutableAtTheDatabase() throws Exception {
        UUID offerId = createOffer();
        UUID v1 = currentVersionId(offerId);
        revise(offerId, TERMS_V2, COMP_V2);

        assertThatThrownBy(() -> jdbc.execute("UPDATE hr_offer_versions SET contract_terms = '{}'::jsonb "
                + "WHERE id = '" + v1 + "'"))
                .as("T7.4/T7.16: no UPDATE path may mutate an offer version (DB guard)")
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.execute("DELETE FROM hr_offer_versions WHERE id = '" + v1 + "'"))
                .as("T7.4: version history is append-only (DB guard)")
                .isInstanceOf(Exception.class);
        Map<String, Object> v1After = rawVersionRow(v1);
        assertThat(v1After.get("contract_terms").toString()).contains("\"base_salary\":111000");
    }

    @Test
    void duplicateVersionNumbersForOneOffer_areRejectedByConstraint() throws Exception {
        UUID offerId = createOffer();
        Integer uniqueConstraints = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'hr_offer_versions'::regclass "
                        + "AND contype = 'u' AND pg_get_constraintdef(oid) ILIKE '%offer_id%version_number%'",
                Integer.class);
        assertThat(uniqueConstraints).as("T7.4: DB constraint prevents duplicate version numbers").isGreaterThanOrEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO hr_offer_versions (tenant_id, offer_id, version_number, contract_terms, compensation) "
                        + "VALUES (?, ?, 1, '{}'::jsonb, '{}'::jsonb)", tenantId, offerId))
                .as("duplicate (offer_id, version_number) must violate the unique constraint")
                .isInstanceOf(Exception.class);
    }

    @Test
    void concurrentRevisions_exactlyOneVersionPerNumber_noTwoCurrents() throws Exception {
        UUID offerId = createOffer();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Method revise = service.getClass().getMethod("reviseOffer",
                HrCommandContext.class, UUID.class, String.class, String.class, OffsetDateTime.class);
        Future<Boolean> a = pool.submit(() -> {
            start.await();
            try {
                revise.invoke(service, ctx(operatorId), offerId, TERMS_V2, COMP_V2, null);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        Future<Boolean> b = pool.submit(() -> {
            start.await();
            try {
                revise.invoke(service, ctx(operatorId), offerId, TERMS_V2B, COMP_V2, null);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        start.countDown();
        boolean aWon = a.get(60, TimeUnit.SECONDS);
        boolean bWon = b.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        int wins = (aWon ? 1 : 0) + (bWon ? 1 : 0);
        assertThat(wins).as("T7.15: concurrent revision cannot produce two currents — each commit is serialized").isGreaterThanOrEqualTo(1);
        Integer versionRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_offer_versions WHERE offer_id = ?", Integer.class, offerId);
        assertThat(versionRows).as("each committed revision is exactly one immutable row (v1 + wins)")
                .isEqualTo(1 + wins);
        Integer duplicateNumbers = jdbc.queryForObject(
                "SELECT COUNT(*) - COUNT(DISTINCT version_number) FROM hr_offer_versions WHERE offer_id = ?",
                Integer.class, offerId);
        assertThat(duplicateNumbers).as("no duplicate version numbers even under a race").isZero();
    }

    // ==================== T7.2 — the bypass is gone ====================

    @Test
    void noServicePath_promotesDraftDirectlyToExtended() throws Exception {
        UUID offerId = createOffer();
        for (Method m : service.getClass().getMethods()) {
            assertThat(m.getName().toLowerCase())
                    .as("no T7 service command may extend a DRAFT offer directly")
                    .doesNotContain("directextend").doesNotContain("forceextend");
        }
        // extension command requires the authoritative workflow evidence
        assertThatThrownBy(() -> invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, UUID.randomUUID()))
                .as("T7.2: extension without an approval-backed correlation is refused (DRAFT offer)")
                .isInstanceOf(Exception.class);
        assertThat(offerState(offerId)).as("DRAFT remains DRAFT — bypass eliminated").isEqualTo("DRAFT");
    }

    // ==================== T7.6 — approval submission flow ====================

    @Test
    void submitForApproval_movesDraftToPendingApproval_andPersistsY2Correlation() throws Exception {
        UUID offerId = createOffer();
        UUID versionId = currentVersionId(offerId);
        UUID instanceId = submitForApproval(offerId);

        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
        Map<String, Object> offer = jdbc.queryForMap(
                "SELECT pending_workflow_instance_id, pending_workflow_definition_version_id, pending_offer_version_id "
                        + "FROM hr_offers WHERE id = ?", offerId);
        assertThat(offer.get("pending_workflow_instance_id")).isEqualTo(instanceId);
        assertThat(offer.get("pending_offer_version_id"))
                .as("the submitted version is frozen by correlation").isEqualTo(versionId);
        assertThat(offer.get("pending_workflow_definition_version_id")).isNotNull();

        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.OFFER_SUBMITTED_FOR_APPROVAL'",
                Integer.class, offerId);
        assertThat(auditRows).isEqualTo(1);
        assertThat(workflowStartCalls.get()).isEqualTo(1);
    }

    @Test
    void submitForApproval_isIdempotent_whileApprovalOpen_noDuplicateWorkflowInstances() throws Exception {
        UUID offerId = createOffer();
        UUID first = submitForApproval(offerId);
        UUID second = submitForApproval(offerId);

        assertThat(second).as("repeated submission returns the SAME authoritative correlation")
                .isEqualTo(first);
        assertThat(workflowStartCalls.get())
                .as("T7.6: repeated submission must not create duplicate Workflow approval instances")
                .isEqualTo(1);
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void submitForApproval_requiresOfferExtendCapability() throws Exception {
        UUID offerId = createOffer();
        grants.put(CAP_EXTEND, false);
        assertThatThrownBy(() -> submitForApproval(offerId))
                .as("submission is the extend actor's command (design §11.2)")
                .isInstanceOf(Exception.class);
        grants.put(CAP_EXTEND, true);
        assertThat(offerState(offerId)).isEqualTo("DRAFT");
    }

    @Test
    void submitForApproval_workflowCreationFailure_leavesDraftWithoutCorrelation() throws Exception {
        UUID offerId = createOffer();
        stub.startShouldThrow = new IllegalStateException("workflow unavailable");

        assertThatThrownBy(() -> submitForApproval(offerId))
                .as("T7.6: a failing workflow start must not falsely move the offer")
                .isInstanceOf(Exception.class);

        assertThat(offerState(offerId)).as("offer stays DRAFT on failure").isEqualTo("DRAFT");
        Map<String, Object> offer = jdbc.queryForMap(
                "SELECT pending_workflow_instance_id, pending_offer_version_id FROM hr_offers WHERE id = ?", offerId);
        assertThat(offer.get("pending_workflow_instance_id"))
                .as("no recoverable-less correlation is persisted").isNull();
        assertThat(offer.get("pending_offer_version_id")).isNull();
    }

    // ==================== T7.7 — approval success flow ====================

    @Test
    void incompleteApproval_cannotExtend() throws Exception {
        UUID offerId = createOffer();
        UUID instanceId = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.NONE;
        stub.instanceStatus = "RUNNING";

        assertThatThrownBy(() -> invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instanceId))
                .as("T7.7: PENDING_APPROVAL→EXTENDED only on a valid final APPROVED outcome")
                .isInstanceOf(Exception.class);
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void authoritativeApprovedOutcome_extendsExactlyOnce_andBindsSubmittedVersion() throws Exception {
        UUID offerId = createOffer();
        UUID versionId = currentVersionId(offerId);
        UUID instanceId = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.APPROVED;
        stub.instanceStatus = "COMPLETED";

        invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instanceId);

        assertThat(offerState(offerId)).isEqualTo("EXTENDED");
        assertThat(currentVersionId(offerId)).as("the extended terms are exactly the submitted version")
                .isEqualTo(versionId);
        Map<String, Object> offer = jdbc.queryForMap(
                "SELECT pending_workflow_instance_id FROM hr_offers WHERE id = ?", offerId);
        assertThat(offer.get("pending_workflow_instance_id"))
                .as("the approval correlation is closed after extension").isNull();

        Integer extensionAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.OFFER_EXTENDED'",
                Integer.class, offerId);
        assertThat(extensionAudits).isEqualTo(1);
        Integer outboxRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                        + "AND event_type = 'HRM.RECRUITMENT.OFFER_EXTENDED'",
                Integer.class, offerId);
        assertThat(outboxRows).as("design §12: OFFER_EXTENDED outbox event").isEqualTo(1);
    }

    @Test
    void replayedApprovalCompletion_isSideEffectFree() throws Exception {
        UUID offerId = createOffer();
        UUID instanceId = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.APPROVED;
        stub.instanceStatus = "COMPLETED";
        invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instanceId);

        // duplicate callback with the SAME authoritative instance
        invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instanceId);

        Integer extensionAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.OFFER_EXTENDED'",
                Integer.class, offerId);
        assertThat(extensionAudits).as("T7.7: replay is idempotent — no duplicate side effect").isEqualTo(1);
        assertThat(offerState(offerId)).isEqualTo("EXTENDED");
    }

    @Test
    void staleOrWrongWorkflowInstance_isRefused() throws Exception {
        UUID offerId = createOffer();
        UUID instanceId = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.APPROVED;
        stub.instanceStatus = "COMPLETED";

        // wrong instance id (never correlated with this offer)
        assertThatThrownBy(() -> invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, UUID.randomUUID()))
                .as("T7.7: a random Workflow instance must never be usable to extend the offer")
                .isInstanceOf(Exception.class);

        // late completion of a superseded cycle: reject W1, revise, submit W2, replay W1
        stub.approvalOutcome = Outcome.REJECTED;
        rejectForApproval(offerId, instanceId, "OFFER_REJECTION");
        UUID v2 = revise(offerId, TERMS_V2, COMP_V2);
        UUID instance2 = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.APPROVED;
        assertThatThrownBy(() -> invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instanceId))
                .as("T7.15: an approval arriving after a superseding submission is stale")
                .isInstanceOf(Exception.class);
        invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instance2);
        assertThat(offerState(offerId)).isEqualTo("EXTENDED");
        assertThat(currentVersionId(offerId)).isEqualTo(v2).as("W2 extended the version it actually approved");
    }

    @Test
    void concurrentExtendAndReject_exactlyOneWinner() throws Exception {
        UUID offerId = createOffer();
        UUID instanceId = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.APPROVED;
        stub.instanceStatus = "COMPLETED";

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Method extend = service.getClass().getMethod("extendFromApproval",
                HrCommandContext.class, UUID.class, UUID.class);
        Method reject = service.getClass().getMethod("rejectFromApproval",
                HrCommandContext.class, UUID.class, UUID.class, String.class);
        Future<Boolean> ext = pool.submit(() -> {
            start.await();
            try {
                extend.invoke(service, ctx(operatorId), offerId, instanceId);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        Future<Boolean> rej = pool.submit(() -> {
            start.await();
            try {
                reject.invoke(service, ctx(operatorId), offerId, instanceId, "OFFER_REJECTION");
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        start.countDown();
        boolean extWon = ext.get(60, TimeUnit.SECONDS);
        boolean rejWon = rej.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(extWon ^ rejWon).as("exactly one of extend/reject commits").isTrue();
        String state = offerState(offerId);
        assertThat(state).isIn("EXTENDED", "DRAFT");
        Integer mutations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action IN "
                        + "('HRM.RECRUITMENT.OFFER_EXTENDED','HRM.RECRUITMENT.OFFER_REJECTED')",
                Integer.class, offerId);
        assertThat(mutations).as("no double business side effect").isEqualTo(1);
    }

    // ==================== T7.8 — rejection flow ====================

    @Test
    void rejection_requiresRegisteredReason_andReturnsOfferToDraftWithHistoryIntact() throws Exception {
        UUID offerId = createOffer();
        UUID v1 = currentVersionId(offerId);
        Map<String, Object> v1Before = rawVersionRow(v1);
        UUID instanceId = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.REJECTED;
        stub.instanceStatus = "COMPLETED";

        assertThatThrownBy(() -> rejectForApproval(offerId, instanceId, "NOT_REGISTERED"))
                .as("T7.8: governed rejection reason/evidence is mandatory").isInstanceOf(Exception.class);
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");

        rejectForApproval(offerId, instanceId, "OFFER_REJECTION");
        assertThat(offerState(offerId)).as("T7.8: the restored rejection semantics return the offer to DRAFT")
                .isEqualTo("DRAFT");
        Map<String, Object> offer = jdbc.queryForMap(
                "SELECT pending_workflow_instance_id, pending_offer_version_id FROM hr_offers WHERE id = ?", offerId);
        assertThat(offer.get("pending_workflow_instance_id")).isNull();
        assertThat(rawVersionRow(v1)).as("the rejected submitted version stays immutable historical evidence")
                .isEqualTo(v1Before);
    }

    @Test
    void rejectedCycle_cannotExtendViaReplayedOutcome() throws Exception {
        UUID offerId = createOffer();
        UUID instanceId = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.REJECTED;
        stub.instanceStatus = "COMPLETED";
        rejectForApproval(offerId, instanceId, "OFFER_REJECTION");

        assertThatThrownBy(() -> invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instanceId))
                .as("an obsolete (rejected) approval result can never extend the offer")
                .isInstanceOf(Exception.class);
        assertThat(offerState(offerId)).isEqualTo("DRAFT");
    }

    // ==================== T7.9 — post-extension revision ====================

    @Test
    void extendedOfferRevision_createsNewVersion_withoutMutatingExtendedTerms() throws Exception {
        UUID offerId = createOffer();
        UUID v1 = currentVersionId(offerId);
        Map<String, Object> v1Before = rawVersionRow(v1);
        approveAndExtend(offerId);

        UUID v2 = revise(offerId, TERMS_V2, COMP_V2);
        assertThat(offerState(offerId)).as("no EXTENDED→DRAFT demotion — the offer stays EXTENDED")
                .isEqualTo("EXTENDED");
        assertThat(currentVersionId(offerId))
                .as("the extended version remains the current extended terms until re-approval")
                .isEqualTo(v1);
        assertThat(rawVersionRow(v1)).isEqualTo(v1Before);
        Map<String, Object> pending = jdbc.queryForMap(
                "SELECT pending_offer_version_id, pending_workflow_instance_id FROM hr_offers WHERE id = ?", offerId);
        assertThat(pending.get("pending_offer_version_id")).isEqualTo(v2);
        assertThat(pending.get("pending_workflow_instance_id")).isNull();

        // re-extension only after a NEW approved cycle
        assertThatThrownBy(() -> invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, UUID.randomUUID()))
                .isInstanceOf(Exception.class);
        UUID instance2 = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.APPROVED;
        stub.instanceStatus = "COMPLETED";
        invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instance2);
        assertThat(currentVersionId(offerId)).as("T7.9: re-extension swaps to the newly approved version")
                .isEqualTo(v2);
        assertThat(offerState(offerId)).isEqualTo("EXTENDED");
        assertThat(rawVersionRow(v1)).as("the previously extended version remains immutable").isEqualTo(v1Before);
    }

    // ==================== T7.10 — candidate/application consistency ====================

    @Test
    void offerAcceptance_neverMovesApplicationToHired() throws Exception {
        UUID offerId = createOffer();
        approveAndExtend(offerId);
        invokeService("accept", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(operatorId), offerId);

        assertThat(offerState(offerId)).isEqualTo("ACCEPTED");
        String applicationState = jdbc.queryForObject(
                "SELECT state FROM hr_applications WHERE id = ?", String.class, applicationId);
        assertThat(applicationState)
                .as("T7.10: Application HIRED is reachable ONLY through the governed conversion path (T8)")
                .isEqualTo("OFFER");
        for (Method m : service.getClass().getMethods()) {
            assertThat(m.getName().toLowerCase())
                    .as("T7.10: no second hiring conversion writer inside T7")
                    .doesNotContain("hire").doesNotContain("convert");
        }
    }

    // ==================== lifecycle + terminal denial ====================

    @Test
    void extendedOffer_resolvesToEveryTerminalOutcome_andTerminalsAreDenial() throws Exception {
        UUID offerId = createOffer();
        approveAndExtend(offerId);

        invokeService("expire", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(operatorId), offerId);
        assertThat(offerState(offerId)).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> invokeService("accept",
                new Class<?>[]{HrCommandContext.class, UUID.class}, ctx(operatorId), offerId))
                .as("EXPIRED is terminal").isInstanceOf(Exception.class);

        UUID declined = createOffer();
        approveAndExtend(declined);
        invokeService("decline", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(operatorId), declined);
        assertThat(offerState(declined)).isEqualTo("DECLINED");
        assertThatThrownBy(() -> invokeService("withdraw",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), declined, "OFFER_WITHDRAWN"))
                .as("DECLINED is terminal").isInstanceOf(Exception.class);

        UUID withdrawn = createOffer();
        approveAndExtend(withdrawn);
        assertThatThrownBy(() -> invokeService("withdraw",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), withdrawn, "NOT_REGISTERED"))
                .as("withdraw requires a registered reason").isInstanceOf(Exception.class);
        invokeService("withdraw", new Class<?>[]{HrCommandContext.class, UUID.class, String.class},
                ctx(operatorId), withdrawn, "OFFER_WITHDRAWN");
        assertThat(offerState(withdrawn)).isEqualTo("WITHDRAWN");
    }

    // ==================== cross-tenant + capability matrix ====================

    @Test
    void crossTenant_offerAccess_isDenied() throws Exception {
        UUID offerId = createOffer();
        UUID tenantB = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantB, "Tenant B", "tb-" + tenantB.toString().substring(0, 8));
        HrCommandContext ctxB = new HrCommandContext(tenantB, null, operatorId, UUID.randomUUID());
        assertThatThrownBy(() -> submitForApprovalVia(ctxB, offerId))
                .as("a foreign-tenant context must never resolve the offer").isInstanceOf(Exception.class);
        assertThat(offerState(offerId)).isEqualTo("DRAFT");
    }

    @Test
    void everyOfferCommand_requiresItsCapability() throws Exception {
        grants.put(CAP_MANAGE, false);
        assertThatThrownBy(() -> createOffer())
                .as("create requires OFFER.MANAGE").isInstanceOf(Exception.class);
        grants.put(CAP_MANAGE, true);
        UUID offerId = createOffer();

        grants.put(CAP_MANAGE, false);
        assertThatThrownBy(() -> revise(offerId, TERMS_V2, COMP_V2))
                .as("revision requires OFFER.MANAGE").isInstanceOf(Exception.class);
        grants.put(CAP_MANAGE, true);

        grants.put(CAP_EXTEND, false);
        assertThatThrownBy(() -> submitForApproval(offerId))
                .as("submission requires OFFER.EXTEND").isInstanceOf(Exception.class);
        grants.put(CAP_EXTEND, true);
    }

    // ==================== helpers ====================

    private static final String TERMS_V1 = "{\"base_salary\":111000,\"currency\":\"SAR\"}";
    private static final String TERMS_V2 = "{\"base_salary\":222000,\"currency\":\"SAR\"}";
    private static final String TERMS_V2B = "{\"base_salary\":223000,\"currency\":\"SAR\"}";
    private static final String COMP_V1 = "{\"band\":\"B3\"}";
    private static final String COMP_V2 = "{\"band\":\"B4\"}";

    private StubPortState stub;

    private enum Outcome { APPROVED, REJECTED, NONE }

    private static final class StubPortState {
        volatile Outcome approvalOutcome = Outcome.NONE;
        volatile String instanceStatus = "RUNNING";
        volatile RuntimeException startShouldThrow;
        final Map<UUID, UUID> instanceToOffer = new HashMap<>();
    }

    private Object offerWorkflowStub() throws Exception {
        stub = new StubPortState();
        Class<?> portClass = Class.forName(PORT_CLASS);
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
                case "startOfferApproval" -> {
                    if (stub.startShouldThrow != null) {
                        throw stub.startShouldThrow;
                    }
                    workflowStartCalls.incrementAndGet();
                    UUID offerId = (UUID) args[1];
                    UUID instanceId = UUID.nameUUIDFromBytes(("stub-instance-" + offerId).getBytes());
                    stub.instanceToOffer.put(instanceId, offerId);
                    return instanceId;
                }
                case "loadApprovalOutcome" -> {
                    UUID instanceId = (UUID) args[1];
                    UUID offerId = stub.instanceToOffer.get(instanceId);
                    if (offerId == null) {
                        return null;
                    }
                    var ctor = snapshot.getConstructor(UUID.class, UUID.class, String.class, UUID.class,
                            String.class, outcome);
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object resolved = Enum.valueOf((Class<? extends Enum>) outcome, stub.approvalOutcome.name());
                    return ctor.newInstance(instanceId, FIXED_DEFINITION_VERSION, "HR_OFFER", offerId,
                            stub.instanceStatus, resolved);
                }
                default -> {
                    return null;
                }
            }
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    private static final UUID FIXED_DEFINITION_VERSION = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private Object authStub() throws Exception {
        Class<?> portClass = Class.forName(AUTH_PORT_CLASS);
        Map<String, Boolean> g = grants;
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "requireOfferManage" -> require(g, CAP_MANAGE);
                case "requireOfferExtend" -> require(g, CAP_EXTEND);
                case "requireApplicationManage" -> require(g, "HRM.RECRUITMENT.APPLICATION.MANAGE");
                case "requireApplicationAdvance" -> require(g, "HRM.RECRUITMENT.APPLICATION.ADVANCE");
                case "requireApplicationReject" -> require(g, "HRM.RECRUITMENT.APPLICATION.REJECT");
                case "requireCandidateManage" -> require(g, "HRM.RECRUITMENT.CANDIDATE.MANAGE");
                case "requireCandidateView" -> require(g, "HRM.RECRUITMENT.CANDIDATE.VIEW");
                case "requireOpeningManage" -> require(g, "HRM.RECRUITMENT.OPENING.MANAGE");
                case "requireOpeningPublish" -> require(g, "HRM.RECRUITMENT.OPENING.PUBLISH");
                default -> {
                }
            }
            return null;
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    private void require(Map<String, Boolean> g, String capability) {
        if (!g.getOrDefault(capability, false)) {
            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability + " denied (test stub)");
        }
    }

    private void seedCandidateOpeningAndOfferStageApplication() throws Exception {
        UUID organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", organizationId, tenantId,
                "Org-" + organizationId.toString().substring(0, 8));
        jdbc.update("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7', NOW())", UUID.randomUUID(), tenantId, organizationId);
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
                + "VALUES (?, ?, ?, 'OU-T7', NOW())", UUID.randomUUID(), tenantId, organizationId);

        Object candidateService = Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrCandidateService")
                .getConstructor(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository"),
                        Class.forName(AUTH_PORT_CLASS),
                        com.sanad.platform.security.crypto.PlatformCryptographyService.class)
                .newInstance(Class.forName("com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository")
                                .getConstructor(DataSource.class).newInstance(dataSource),
                        authStub(), noopCrypto());
        candidateId = (UUID) candidateService.getClass()
                .getMethod("create", HrCommandContext.class, String.class, String.class, String.class, String.class)
                .invoke(candidateService, ctx(operatorId), "T7 Candidate", null, null, null);

        Object openingService = Class.forName(OPENING_SERVICE_CLASS)
                .getConstructor(Class.forName(
                                "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository"),
                        Class.forName(AUTH_PORT_CLASS),
                        com.sanad.platform.hr.compliance.application.ComplianceEngine.class)
                .newInstance(
                        Class.forName("com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository")
                                .getConstructor(DataSource.class,
                                        com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter.class)
                                .newInstance(dataSource, new com.sanad.platform.hr.integration.JdbcHrEvidenceWriter(dataSource)),
                        authStub(),
                        new com.sanad.platform.hr.compliance.application.ComplianceEngine(
                                new com.sanad.platform.hr.compliance.application.CountryPolicyResolver(
                                        jdbc, new com.sanad.platform.hr.compliance.application.WorkerClassificationResolver(jdbc)),
                                List.of(),
                                new com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceDecisionRepository(jdbc)));
        UUID jobId = jdbc.queryForObject("SELECT id FROM hr_jobs WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId);
        UUID orgUnitId = jdbc.queryForObject("SELECT id FROM hr_org_units WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId);
        openingId = (UUID) openingService.getClass()
                .getMethod("create", HrCommandContext.class, UUID.class, UUID.class, UUID.class,
                        UUID.class, int.class, OffsetDateTime.class, OffsetDateTime.class)
                .invoke(openingService, ctx(operatorId), jobId, null, orgUnitId, null, 1, null, null);
        openingService.getClass().getMethod("submit", HrCommandContext.class, UUID.class)
                .invoke(openingService, ctx(operatorId), openingId);
        openingService.getClass().getMethod("approve", HrCommandContext.class, UUID.class)
                .invoke(openingService, ctx(UUID.randomUUID()), openingId);

        Object applicationService = Class.forName(APPLICATION_SERVICE_CLASS)
                .getConstructor(Class.forName(APPLICATION_REPO_CLASS), Class.forName(AUTH_PORT_CLASS))
                .newInstance(Class.forName(APPLICATION_REPO_CLASS)
                                .getConstructor(DataSource.class).newInstance(dataSource),
                        authStub());
        applicationId = (UUID) applicationService.getClass()
                .getMethod("apply", HrCommandContext.class, UUID.class, UUID.class)
                .invoke(applicationService, ctx(operatorId), candidateId, openingId);
        Method advance = applicationService.getClass().getMethod("advance", HrCommandContext.class, UUID.class);
        advance.invoke(applicationService, ctx(operatorId), applicationId);
        advance.invoke(applicationService, ctx(operatorId), applicationId);
        advance.invoke(applicationService, ctx(operatorId), applicationId);
    }

    private UUID newApplicationAtAppliedStage() throws Exception {
        Object applicationService = Class.forName(APPLICATION_SERVICE_CLASS)
                .getConstructor(Class.forName(APPLICATION_REPO_CLASS), Class.forName(AUTH_PORT_CLASS))
                .newInstance(Class.forName(APPLICATION_REPO_CLASS)
                                .getConstructor(DataSource.class).newInstance(dataSource),
                        authStub());
        return (UUID) applicationService.getClass()
                .getMethod("apply", HrCommandContext.class, UUID.class, UUID.class)
                .invoke(applicationService, ctx(operatorId), candidateId, openingId);
    }

    private UUID createOffer() throws Exception {
        return (UUID) invokeService("createOffer",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class, OffsetDateTime.class},
                ctx(operatorId), applicationId, TERMS_V1, COMP_V1, null);
    }

    private UUID revise(UUID offerId, String terms, String comp) throws Exception {
        return (UUID) invokeService("reviseOffer",
                new Class<?>[]{HrCommandContext.class, UUID.class, String.class, String.class, OffsetDateTime.class},
                ctx(operatorId), offerId, terms, comp, null);
    }

    private UUID submitForApproval(UUID offerId) throws Exception {
        stub.approvalOutcome = Outcome.NONE;
        stub.instanceStatus = "RUNNING";
        return submitForApprovalVia(ctx(operatorId), offerId);
    }

    private UUID submitForApprovalVia(HrCommandContext ctx, UUID offerId) throws Exception {
        return (UUID) invokeService("submitForApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class}, ctx, offerId);
    }

    private void rejectForApproval(UUID offerId, UUID instanceId, String reason) throws Exception {
        invokeService("rejectFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class, String.class},
                ctx(operatorId), offerId, instanceId, reason);
    }

    private void approveAndExtend(UUID offerId) throws Exception {
        UUID instanceId = submitForApproval(offerId);
        stub.approvalOutcome = Outcome.APPROVED;
        stub.instanceStatus = "COMPLETED";
        invokeService("extendFromApproval",
                new Class<?>[]{HrCommandContext.class, UUID.class, UUID.class},
                ctx(operatorId), offerId, instanceId);
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

    private String offerState(UUID offerId) {
        return jdbc.queryForObject(
                "SELECT state FROM hr_offers WHERE id = ? AND tenant_id = ?",
                String.class, offerId, tenantId);
    }

    private UUID currentVersionId(UUID offerId) {
        return jdbc.queryForObject(
                "SELECT current_version_id FROM hr_offers WHERE id = ? AND tenant_id = ?",
                UUID.class, offerId, tenantId);
    }

    private Map<String, Object> rawVersionRow(UUID versionId) {
        return jdbc.queryForMap(
                "SELECT id, tenant_id, offer_id, version_number, contract_terms::text AS contract_terms, "
                        + "compensation::text AS compensation, created_by, created_at "
                        + "FROM hr_offer_versions WHERE id = ?", versionId);
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    private com.sanad.platform.security.crypto.PlatformCryptographyService noopCrypto() {
        byte[] key = new byte[32];
        String encKey = java.util.Base64.getEncoder().encodeToString(key);
        return new com.sanad.platform.security.crypto.JcePlatformCryptographyService(
                new com.sanad.platform.security.crypto.EnvironmentKeyMaterialProvider("v1", encKey, "v1", encKey));
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
