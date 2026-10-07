package com.sanad.platform.hr.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.audit.HrAuthenticatedContext;
import com.sanad.platform.hr.audit.HrAuditService;
import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.audit.JdbcHrAuditRepository;
import com.sanad.platform.hr.idempotency.JdbcHrRequestIdempotencyService;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.hr.integration.JdbcHrOutboxRepository;
import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.hr.payroll.application.PayrollAuthorizationGuard;
import com.sanad.platform.hr.payroll.application.PayrollLifecycle;
import com.sanad.platform.hr.payroll.application.PayrollLifecycleService;
import com.sanad.platform.hr.payroll.infrastructure.JdbcPayrollLifecycleRepository;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayrollLifecyclePostgresTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private static String isolatedUrl;

    private DriverManagerDataSource dataSource;
    private Connection connection;
    private UUID tenantId;
    private UUID foreignTenantId;
    private UUID legalEntityId;
    private UUID runId;
    private UUID actorId;

    @BeforeAll
    static void requirePostgreSqlDirect() {
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
        dataSource = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();

        connection = dataSource.getConnection();
        connection.setAutoCommit(true);

        tenantId = UUID.randomUUID();
        foreignTenantId = UUID.randomUUID();
        legalEntityId = UUID.randomUUID();
        runId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        seedTenant(tenantId, "t5-payroll");
        seedTenant(foreignTenantId, "t5-foreign");
        setTenant(tenantId);
        seedLegalEntity();
        seedPayrollRun();
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void transitionCommitsMutationAuditOutboxAndIdempotencyAtomicallyAndReplays() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        HrAuthenticatedContext actor = actor(tenantId);

        var first = service.transition(
                actor, runId, PayrollLifecycle.CALCULATED, 0,
                "T5_CALCULATE", "key-calc", "fp-calc");

        assertThat(first.replayed()).isFalse();
        assertThat(first.version()).isEqualTo(1);
        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("CALCULATED");
        assertThat(query("SELECT version::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("1");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE idempotency_key = 'key-calc'"))
                .isEqualTo(1);

        String payload = query(
                "SELECT payload::text FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'");
        assertThat(payload)
                .contains("CALCULATED")
                .doesNotContain("baseAmount")
                .doesNotContain("grossAmount")
                .doesNotContain("netAmount")
                .doesNotContain("bank");

        var replay = service.transition(
                actor, runId, PayrollLifecycle.CALCULATED, 0,
                "T5_CALCULATE", "key-calc", "fp-calc");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.version()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyWithDifferentFingerprintFailsClosed() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        HrAuthenticatedContext actor = actor(tenantId);

        service.transition(actor, runId, PayrollLifecycle.CALCULATED, 0,
                "T5_CALCULATE", "key-fingerprint", "fp-one");

        assertThatThrownBy(() -> service.transition(
                actor, runId, PayrollLifecycle.CALCULATED, 0,
                "T5_CALCULATE", "key-fingerprint", "fp-two"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_IDEMPOTENCY_CONFLICT");

        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE idempotency_key = 'key-fingerprint'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(1);
    }

    @Test
    void governedLifecyclePersistsReviewerApproverAndExportEvidence() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        HrAuthenticatedContext actor = actor(tenantId);

        service.transition(actor, runId, PayrollLifecycle.CALCULATED, 0,
                "CALCULATE", "key-life-1", "fp-life-1");
        service.transition(actor, runId, PayrollLifecycle.REVIEWED, 1,
                "REVIEW", "key-life-2", "fp-life-2");
        service.transition(actor, runId, PayrollLifecycle.APPROVED, 2,
                "APPROVE", "key-life-3", "fp-life-3");
        service.transition(actor, runId, PayrollLifecycle.EXPORTED, 3,
                "EXPORT", "key-life-4", "fp-life-4");

        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("EXPORTED");
        assertThat(query("SELECT version::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("4");
        assertThat(query("SELECT reviewed_by::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo(actorId.toString());
        assertThat(query("SELECT approved_by::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo(actorId.toString());
        assertThat(query("SELECT (exported_at IS NOT NULL)::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("true");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(4);
    }

    @Test
    void staleVersionFailsClosedAndLeavesNoPartialIdempotencyEvidence() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        HrAuthenticatedContext actor = actor(tenantId);

        service.transition(actor, runId, PayrollLifecycle.CALCULATED, 0,
                "T5_CALCULATE", "key-1", "fp-1");

        assertThatThrownBy(() -> service.transition(
                actor, runId, PayrollLifecycle.REVIEWED, 0,
                "T5_REVIEW", "key-stale", "fp-stale"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_VERSION_CONFLICT");

        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("CALCULATED");
        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE idempotency_key = 'key-stale'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(1);
    }

    @Test
    void invalidTransitionRollsBackEverySideEffect() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));

        assertThatThrownBy(() -> service.transition(
                actor(tenantId), runId, PayrollLifecycle.APPROVED, 0,
                "INVALID_DIRECT_APPROVAL", "key-invalid", "fp-invalid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_LIFECYCLE_INVALID");

        assertPristine();
    }

    @Test
    void auditFailureRollsBackMutationAndIdempotency() throws Exception {
        HrTransactionalEvidenceWriter failingAudit = (c, audit, event) -> {
            throw new IllegalStateException("INJECTED_T5_AUDIT_FAILURE");
        };
        PayrollLifecycleService service = service(failingAudit);

        assertThatThrownBy(() -> service.transition(
                actor(tenantId), runId, PayrollLifecycle.CALCULATED, 0,
                "AUDIT_ROLLBACK_PROBE", "key-audit-rollback", "fp-audit-rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INJECTED_T5_AUDIT_FAILURE");

        assertPristine();
    }

    @Test
    void outboxDatabaseFailureAfterAuditRollsBackEverything() throws Exception {
        HrAuditService auditService = new HrAuditService(
                dataSource,
                new HrRedactionGuard(),
                new JdbcHrAuditRepository(new HrRedactionGuard()));
        JdbcHrOutboxRepository outbox = new JdbcHrOutboxRepository();
        ObjectMapper mapper = new ObjectMapper();

        HrTransactionalEvidenceWriter failingOutbox = (c, audit, event) -> {
            auditService.appendMutationAudit(c, audit);
            var forbidden = mapper.createObjectNode();
            forbidden.put("bank_account", "MUST_BE_REJECTED");
            DomainEventEnvelope poisoned = new DomainEventEnvelope(
                    event.eventId(), event.eventType(), event.eventVersion(),
                    event.aggregateType(), event.aggregateId(), event.tenantId(),
                    event.organizationId(), event.actorUserId(), event.occurredAt(),
                    event.correlationId(), event.causationId(), event.idempotencyKey(),
                    event.dataClassification(), forbidden);
            try {
                outbox.append(c, poisoned);
            } catch (SQLException e) {
                throw new IllegalStateException("INJECTED_T5_OUTBOX_FAILURE", e);
            }
        };

        PayrollLifecycleService service = service(failingOutbox);

        assertThatThrownBy(() -> service.transition(
                actor(tenantId), runId, PayrollLifecycle.CALCULATED, 0,
                "OUTBOX_ROLLBACK_PROBE", "key-outbox-rollback", "fp-outbox-rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INJECTED_T5_OUTBOX_FAILURE");

        assertPristine();
    }

    @Test
    void foreignTenantCannotObserveOrMutatePayrollRun() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));

        assertThatThrownBy(() -> service.transition(
                actor(foreignTenantId), runId, PayrollLifecycle.CALCULATED, 0,
                "FOREIGN_TENANT_PROBE", "key-foreign", "fp-foreign"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_RUN_NOT_FOUND");

        setTenant(tenantId);
        assertPristine();
        setTenant(foreignTenantId);
        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE idempotency_key = 'key-foreign'"))
                .isZero();
    }

    @Test
    void concurrentSameVersionTransitionsProduceExactlyOneWinner() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> a = executor.submit(() -> {
                start.await();
                try {
                    return service.transition(actor(tenantId), runId, PayrollLifecycle.CALCULATED, 0,
                            "CONCURRENT_A", "key-concurrent-a", "fp-concurrent-a");
                } catch (RuntimeException e) {
                    return e;
                }
            });
            Future<Object> b = executor.submit(() -> {
                start.await();
                try {
                    return service.transition(actor(tenantId), runId, PayrollLifecycle.CALCULATED, 0,
                            "CONCURRENT_B", "key-concurrent-b", "fp-concurrent-b");
                } catch (RuntimeException e) {
                    return e;
                }
            });
            start.countDown();

            Object ra = a.get();
            Object rb = b.get();

            long successes = java.util.stream.Stream.of(ra, rb)
                    .filter(PayrollLifecycleService.TransitionResult.class::isInstance)
                    .count();
            long conflicts = java.util.stream.Stream.of(ra, rb)
                    .filter(IllegalStateException.class::isInstance)
                    .map(IllegalStateException.class::cast)
                    .filter(e -> e.getMessage().contains("HRM_PAYROLL_VERSION_CONFLICT"))
                    .count();

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);

            setTenant(tenantId);
            assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                    .isEqualTo("CALCULATED");
            assertThat(query("SELECT version::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                    .isEqualTo("1");
            assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                    .isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recalculationIsAtomicVersionedIdempotentAndAmountMinimized() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        HrAuthenticatedContext actor = actor(tenantId);

        service.transition(actor, runId, PayrollLifecycle.CALCULATED, 0,
                "CALCULATE", "key-recalc-seed", "fp-recalc-seed");

        var first = service.recalculate(
                actor, runId, 1,
                "RECALCULATE", "key-recalc", "fp-recalc");

        assertThat(first.replayed()).isFalse();
        assertThat(first.fromStatus()).isEqualTo(PayrollLifecycle.CALCULATED);
        assertThat(first.toStatus()).isEqualTo(PayrollLifecycle.CALCULATED);
        assertThat(first.version()).isEqualTo(2);
        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("CALCULATED");
        assertThat(query("SELECT version::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("2");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(2);

        String payload = query(
                "SELECT payload::text FROM hr_domain_event_outbox "
                        + "WHERE aggregate_id = '" + runId + "' "
                        + "AND event_type = 'HRM.PAYROLL.RECALCULATED.v1'");
        assertThat(payload)
                .contains("CALCULATED")
                .doesNotContain("baseAmount")
                .doesNotContain("grossAmount")
                .doesNotContain("netAmount")
                .doesNotContain("bank");

        var replay = service.recalculate(
                actor, runId, 1,
                "RECALCULATE", "key-recalc", "fp-recalc");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.version()).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(2);

        assertThatThrownBy(() -> service.recalculate(
                actor, runId, 2,
                "RECALCULATE", "key-recalc", "fp-recalc-different"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void sameIdempotencyKeyAcrossDifferentActorsDoesNotCollideInOutbox() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        HrAuthenticatedContext firstActor = actor(tenantId);
        UUID secondActorId = UUID.randomUUID();
        HrAuthenticatedContext secondActor = new HrAuthenticatedContext(
                tenantId, secondActorId, UUID.randomUUID(), UUID.randomUUID());

        service.transition(firstActor, runId, PayrollLifecycle.CALCULATED, 0,
                "CALCULATE", "seed-shared-actor-key", "seed-shared-actor-fp");

        var first = service.recalculate(
                firstActor, runId, 1,
                "RECALCULATE_A", "shared-recalc-key", "shared-recalc-fp-a");
        var second = service.recalculate(
                secondActor, runId, 2,
                "RECALCULATE_B", "shared-recalc-key", "shared-recalc-fp-b");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isFalse();
        assertThat(second.version()).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE idempotency_key = 'shared-recalc-key'"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox "
                + "WHERE aggregate_id = '" + runId + "' "
                + "AND event_type = 'HRM.PAYROLL.RECALCULATED.v1'"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(DISTINCT event_id) FROM hr_domain_event_outbox "
                + "WHERE aggregate_id = '" + runId + "' "
                + "AND event_type = 'HRM.PAYROLL.RECALCULATED.v1'"))
                .isEqualTo(2);
    }

    @Test
    void recalculationFailsClosedAfterReviewAndRollsBackIdempotency() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        HrAuthenticatedContext actor = actor(tenantId);

        service.transition(actor, runId, PayrollLifecycle.CALCULATED, 0,
                "CALCULATE", "key-review-seed-1", "fp-review-seed-1");
        service.transition(actor, runId, PayrollLifecycle.REVIEWED, 1,
                "REVIEW", "key-review-seed-2", "fp-review-seed-2");

        assertThatThrownBy(() -> service.recalculate(
                actor, runId, 2,
                "RECALCULATE_AFTER_REVIEW", "key-recalc-denied", "fp-recalc-denied"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_RECALCULATION_NOT_ALLOWED");

        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("REVIEWED");
        assertThat(query("SELECT version::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("2");
        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE idempotency_key = 'key-recalc-denied'"))
                .isZero();
    }

    @Test
    void cancellationPersistsAtomicallyAndIsTerminal() throws Exception {
        PayrollLifecycleService service = service(new JdbcHrEvidenceWriter(dataSource));
        HrAuthenticatedContext actor = actor(tenantId);

        var cancelled = service.transition(
                actor, runId, PayrollLifecycle.CANCELLED, 0,
                "CANCEL", "key-cancel", "fp-cancel");

        assertThat(cancelled.toStatus()).isEqualTo(PayrollLifecycle.CANCELLED);
        assertThat(cancelled.version()).isEqualTo(1);
        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("CANCELLED");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(1);

        assertThatThrownBy(() -> service.transition(
                actor, runId, PayrollLifecycle.CALCULATED, 1,
                "RESURRECT", "key-cancel-resurrect", "fp-cancel-resurrect"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_LIFECYCLE_INVALID");

        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE idempotency_key = 'key-cancel-resurrect'"))
                .isZero();
    }

    private PayrollLifecycleService service(HrTransactionalEvidenceWriter evidenceWriter) {
        return new PayrollLifecycleService(
                dataSource,
                new JdbcPayrollLifecycleRepository(),
                evidenceWriter,
                new JdbcHrRequestIdempotencyService(dataSource),
                new ObjectMapper(),
                allowAllPayrollAuthorization());
    }

    private PayrollAuthorizationGuard allowAllPayrollAuthorization() {
        CapabilityEvaluationService evaluator = org.mockito.Mockito.mock(CapabilityEvaluationService.class);
        org.mockito.Mockito.when(evaluator.evaluate(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(invocation -> new AccessDecisionResponse(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        null,
                        invocation.getArgument(2),
                        true,
                        "TEST_EXPLICIT_ALLOW",
                        null,
                        "TEST_ROLE"));
        return new PayrollAuthorizationGuard(evaluator);
    }

    private HrAuthenticatedContext actor(UUID tenant) {
        return new HrAuthenticatedContext(tenant, actorId, UUID.randomUUID(), UUID.randomUUID());
    }

    private void assertPristine() throws Exception {
        setTenant(tenantId);
        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("DRAFT");
        assertThat(query("SELECT version::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("0");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records"))
                .isZero();
    }

    private void seedTenant(UUID id, String prefix) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at)
                VALUES (?,?,?,'ACTIVE',NOW(),NOW())
                """)) {
            ps.setObject(1, id);
            ps.setString(2, "G4 T5 " + id);
            ps.setString(3, prefix + "-" + id.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void seedLegalEntity() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO legal_entities (
                    id, tenant_id, code, name, registered_country_code,
                    statutory_country_code, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'T5 Legal Entity', 'SA', 'SA', 'ACTIVE', NOW(), NOW())
                """)) {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, "T5-" + legalEntityId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void seedPayrollRun() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO hr_payroll_runs (
                    id, tenant_id, legal_entity_id, period_start, period_end,
                    currency_code, status, source_cutoff_at, version,
                    created_at, updated_at
                ) VALUES (?, ?, ?, DATE '2026-10-01', DATE '2026-10-31',
                          'SAR', 'DRAFT', ?, 0, NOW(), NOW())
                """)) {
            ps.setObject(1, runId);
            ps.setObject(2, tenantId);
            ps.setObject(3, legalEntityId);
            ps.setObject(4, OffsetDateTime.parse("2026-11-01T00:00:00Z"));
            ps.executeUpdate();
        }
    }

    private void setTenant(UUID tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private String query(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private int count(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
