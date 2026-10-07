package com.sanad.platform.hr.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.audit.JdbcHrAuditRepository;
import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.integration.HrDomainEventPublisher;
import com.sanad.platform.hr.integration.JdbcHrOutboxRepository;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import com.sanad.platform.hr.payroll.application.PayrollLifecycleService;
import com.sanad.platform.hr.payroll.application.PayrollLifecycleService.PayrollRunStatus;
import com.sanad.platform.hr.payroll.infrastructure.JdbcPayrollLifecycleRepository;
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
import java.util.concurrent.Callable;
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
    private PayrollLifecycleService service;

    private UUID tenantId;
    private UUID foreignTenantId;
    private UUID legalEntityId;
    private UUID actorId;
    private UUID runId;

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
    void setUp() throws Exception {
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
        actorId = UUID.randomUUID();
        runId = UUID.randomUUID();

        seedTenant(tenantId, "g4t5");
        seedTenant(foreignTenantId, "g4t5f");
        setTenant(tenantId);
        seedUser();
        seedLegalEntity();
        seedRun("DRAFT", 0L);

        var redaction = new HrRedactionGuard();
        service = new PayrollLifecycleService(
                dataSource,
                new JdbcPayrollLifecycleRepository(),
                new JdbcHrAuditRepository(redaction),
                new HrDomainEventPublisher(dataSource, redaction, new JdbcHrOutboxRepository()),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void executesCanonicalLifecycleAndWritesAmountMinimizedEvidenceAtomically() throws Exception {
        var ctx = context();
        var calculated = service.transition(
                ctx, runId, PayrollRunStatus.CALCULATED, 0L,
                "idem-calc", "fp-calc");
        assertThat(calculated.status()).isEqualTo(PayrollRunStatus.CALCULATED);
        assertThat(calculated.version()).isEqualTo(1L);

        var reviewed = service.transition(
                ctx, runId, PayrollRunStatus.REVIEWED, 1L,
                "idem-review", "fp-review");
        assertThat(reviewed.status()).isEqualTo(PayrollRunStatus.REVIEWED);

        var approved = service.transition(
                ctx, runId, PayrollRunStatus.APPROVED, 2L,
                "idem-approve", "fp-approve");
        assertThat(approved.status()).isEqualTo(PayrollRunStatus.APPROVED);

        var exported = service.transition(
                ctx, runId, PayrollRunStatus.EXPORTED, 3L,
                "idem-export", "fp-export");
        assertThat(exported.status()).isEqualTo(PayrollRunStatus.EXPORTED);
        assertThat(exported.version()).isEqualTo(4L);

        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("EXPORTED");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(4);
        assertThat(query("""
                SELECT COALESCE(string_agg(payload::text, '' ORDER BY occurred_at), '')
                  FROM hr_domain_event_outbox
                 WHERE aggregate_id = '""" + runId + "'"))
                .doesNotContain("base_amount")
                .doesNotContain("gross_amount")
                .doesNotContain("net_amount")
                .doesNotContain("deduction_total");
    }

    @Test
    void rejectsIllegalTransitionAndApprovedRecalculation() {
        var ctx = context();

        assertThatThrownBy(() -> service.transition(
                ctx, runId, PayrollRunStatus.APPROVED, 0L,
                "idem-illegal", "fp-illegal"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_LIFECYCLE_INVALID");

        service.transition(ctx, runId, PayrollRunStatus.CALCULATED, 0L, "c", "c");
        service.transition(ctx, runId, PayrollRunStatus.REVIEWED, 1L, "r", "r");
        service.transition(ctx, runId, PayrollRunStatus.APPROVED, 2L, "a", "a");

        assertThatThrownBy(() -> service.recalculate(
                ctx, runId, 3L, "idem-recalc-approved", "fp-recalc-approved"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_RECALCULATION_NOT_ALLOWED");
    }

    @Test
    void rejectsStaleOptimisticVersionWithoutEvidenceSideEffects() throws Exception {
        service.transition(context(), runId, PayrollRunStatus.CALCULATED, 0L, "k1", "f1");

        assertThatThrownBy(() -> service.transition(
                context(), runId, PayrollRunStatus.REVIEWED, 0L,
                "k2", "f2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_VERSION_CONFLICT");

        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("CALCULATED");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(1);
    }

    @Test
    void exactIdempotencyReplayDoesNotDuplicateMutationAuditOrOutbox() throws Exception {
        var first = service.transition(
                context(), runId, PayrollRunStatus.CALCULATED, 0L,
                "idem-replay", "same-fingerprint");
        var replay = service.transition(
                context(), runId, PayrollRunStatus.CALCULATED, 0L,
                "idem-replay", "same-fingerprint");

        assertThat(replay).isEqualTo(first);
        assertThat(query("SELECT version::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("1");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyDifferentFingerprintFailsClosed() {
        service.transition(
                context(), runId, PayrollRunStatus.CALCULATED, 0L,
                "idem-conflict", "fingerprint-a");

        assertThatThrownBy(() -> service.transition(
                context(), runId, PayrollRunStatus.CALCULATED, 0L,
                "idem-conflict", "fingerprint-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void cancellationIsGuardedAndTerminal() {
        var cancelled = service.transition(
                context(), runId, PayrollRunStatus.CANCELLED, 0L,
                "idem-cancel", "fp-cancel");
        assertThat(cancelled.status()).isEqualTo(PayrollRunStatus.CANCELLED);

        assertThatThrownBy(() -> service.transition(
                context(), runId, PayrollRunStatus.CALCULATED, 1L,
                "idem-after-cancel", "fp-after-cancel"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_LIFECYCLE_INVALID");
    }

    @Test
    void recalculationIsExplicitAndOnlyAllowedFromCalculated() throws Exception {
        service.transition(context(), runId, PayrollRunStatus.CALCULATED, 0L, "calc", "calc");
        var recalculated = service.recalculate(
                context(), runId, 1L, "recalc", "recalc");

        assertThat(recalculated.status()).isEqualTo(PayrollRunStatus.CALCULATED);
        assertThat(recalculated.version()).isEqualTo(2L);
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isEqualTo(2);
    }

    @Test
    void auditFailureRollsBackMutationOutboxAndIdempotencyAdmission() throws Exception {
        JdbcHrAuditRepository failingAudit = new JdbcHrAuditRepository(new HrRedactionGuard()) {
            @Override
            public UUID insertLedgerRow(Connection tx, com.sanad.platform.hr.audit.HrAuditRecord record)
                    throws SQLException {
                throw new SQLException("INJECTED_AUDIT_FAILURE");
            }
        };
        PayrollLifecycleService failingService = new PayrollLifecycleService(
                dataSource,
                new JdbcPayrollLifecycleRepository(),
                failingAudit,
                new HrDomainEventPublisher(
                        dataSource, new HrRedactionGuard(), new JdbcHrOutboxRepository()),
                new ObjectMapper());

        assertThatThrownBy(() -> failingService.transition(
                context(), runId, PayrollRunStatus.CALCULATED, 0L,
                "audit-failure", "audit-failure"))
                .hasMessageContaining("HRM_PAYROLL_LIFECYCLE_FAILED");

        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("DRAFT");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE operation_reference = '" + runId + "'"))
                .isZero();
    }

    @Test
    void outboxFailureRollsBackMutationAuditAndIdempotencyAdmission() throws Exception {
        HrDomainEventPublisher failingPublisher = new HrDomainEventPublisher(
                dataSource, new HrRedactionGuard(), new JdbcHrOutboxRepository()) {
            @Override
            public void publish(Connection tx, DomainEventEnvelope envelope) {
                throw new IllegalStateException("INJECTED_OUTBOX_FAILURE");
            }
        };
        PayrollLifecycleService failingService = new PayrollLifecycleService(
                dataSource,
                new JdbcPayrollLifecycleRepository(),
                new JdbcHrAuditRepository(new HrRedactionGuard()),
                failingPublisher,
                new ObjectMapper());

        assertThatThrownBy(() -> failingService.transition(
                context(), runId, PayrollRunStatus.CALCULATED, 0L,
                "outbox-failure", "outbox-failure"))
                .hasMessageContaining("INJECTED_OUTBOX_FAILURE");

        assertThat(query("SELECT status FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                .isEqualTo("DRAFT");
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_idempotency_records WHERE operation_reference = '" + runId + "'"))
                .isZero();
    }

    @Test
    void concurrentSameVersionTransitionsAllowExactlyOneWinner() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> first = () -> {
                try {
                    service.transition(
                            context(), runId, PayrollRunStatus.CALCULATED, 0L,
                            "concurrent-a", "concurrent-a");
                    return "SUCCESS";
                } catch (IllegalStateException e) {
                    return e.getMessage();
                }
            };
            Callable<String> second = () -> {
                try {
                    service.transition(
                            context(), runId, PayrollRunStatus.CALCULATED, 0L,
                            "concurrent-b", "concurrent-b");
                    return "SUCCESS";
                } catch (IllegalStateException e) {
                    return e.getMessage();
                }
            };

            Future<String> a = pool.submit(first);
            Future<String> b = pool.submit(second);
            String ra = a.get();
            String rb = b.get();

            assertThat(java.util.List.of(ra, rb).stream().filter("SUCCESS"::equals).count())
                    .isEqualTo(1);
            assertThat(java.util.List.of(ra, rb).stream()
                    .filter(v -> v.contains("HRM_PAYROLL_VERSION_CONFLICT")).count())
                    .isEqualTo(1);

            assertThat(query("SELECT version::text FROM hr_payroll_runs WHERE id = '" + runId + "'"))
                    .isEqualTo("1");
            assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = '" + runId + "'"))
                    .isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = '" + runId + "'"))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void crossTenantMutationFailsClosedAndCreatesNoForeignEvidence() throws Exception {
        var foreignContext = new HrCommandContext(
                foreignTenantId, null, actorId, UUID.randomUUID());

        assertThatThrownBy(() -> service.transition(
                foreignContext, runId, PayrollRunStatus.CALCULATED, 0L,
                "foreign", "foreign"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_RUN_NOT_FOUND");

        setTenant(foreignTenantId);
        assertThat(count("SELECT COUNT(*) FROM hr_audit_ledger")).isZero();
        assertThat(count("SELECT COUNT(*) FROM hr_domain_event_outbox")).isZero();
    }

    private HrCommandContext context() {
        return new HrCommandContext(tenantId, null, actorId, UUID.randomUUID());
    }

    private void seedTenant(UUID id, String prefix) throws Exception {
        execute("""
                INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, ps -> {
            ps.setObject(1, id);
            ps.setString(2, "G4 T5 " + id);
            ps.setString(3, prefix + "-" + id.toString().substring(0, 8));
        });
    }

    private void seedUser() throws Exception {
        execute("""
                INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at)
                VALUES (?, ?, ?, 'Payroll Actor', 'ACTIVE', 'x', NOW(), NOW())
                """, ps -> {
            ps.setObject(1, actorId);
            ps.setObject(2, tenantId);
            ps.setString(3, "g4t5-" + actorId.toString().substring(0, 8) + "@example.test");
        });
    }

    private void seedLegalEntity() throws Exception {
        execute("""
                INSERT INTO legal_entities (
                    id,tenant_id,code,name,registered_country_code,
                    statutory_country_code,status,created_at,updated_at
                ) VALUES (?, ?, ?, 'G4 T5 Entity', 'SA', 'SA', 'ACTIVE', NOW(), NOW())
                """, ps -> {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, "G4T5-" + legalEntityId.toString().substring(0, 8));
        });
    }

    private void seedRun(String status, long version) throws Exception {
        execute("""
                INSERT INTO hr_payroll_runs (
                    id,tenant_id,legal_entity_id,period_start,period_end,
                    currency_code,status,source_cutoff_at,version,created_by,created_at,updated_at
                ) VALUES (?, ?, ?, DATE '2026-10-01', DATE '2026-10-31',
                          'SAR', ?, NOW(), ?, ?, NOW(), NOW())
                """, ps -> {
            ps.setObject(1, runId);
            ps.setObject(2, tenantId);
            ps.setObject(3, legalEntityId);
            ps.setString(4, status);
            ps.setLong(5, version);
            ps.setObject(6, actorId);
        });
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
            rs.next();
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

    private void execute(String sql, SqlBinder binder) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }
}
