package com.sanad.platform.hr.compensation;

import com.sanad.platform.hr.audit.HrAuthenticatedContext;
import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.hr.audit.JdbcHrAuditRepository;
import com.sanad.platform.hr.audit.SensitiveReadAuditService;
import com.sanad.platform.hr.compensation.application.CompensationAuthorizationPort;
import com.sanad.platform.hr.compensation.application.CompensationService;
import com.sanad.platform.hr.compensation.domain.CompensationComponent;
import com.sanad.platform.hr.compensation.domain.CompensationComponentType;
import com.sanad.platform.hr.compensation.domain.CompensationPackage;
import com.sanad.platform.hr.compensation.domain.CompensationRepository;
import com.sanad.platform.hr.compensation.infrastructure.JdbcCompensationRepository;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 5 / WS6 Task 3 RED contract — effective-dated
 * compensation packages with independent authorization, fail-closed
 * sensitive-read audit, and amount-free change events.
 *
 * <p>Structural rules only; statutory treatment is out of G0 scope.</p>
 */
class HrCompensationAuthorizationAuditIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private DriverManagerDataSource dataSource;
    private CompensationService service;
    private volatile boolean viewAllowed = true;
    private volatile boolean manageAllowed = true;
    private UUID tenantId;
    private UUID actorUserId;
    private UUID employmentId;
    private UUID personId = UUID.randomUUID();
    private UUID legalEntityId = UUID.randomUUID();

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
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        connection = ds.getConnection();
        connection.setAutoCommit(true);
        dataSource = ds;

        tenantId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        employmentId = UUID.randomUUID();
        insertTenant();
        setTenant(tenantId);
        insertLegalEntity();
        insertPerson();
        insertEmployment();

        viewAllowed = true;
        manageAllowed = true;
        ObjectMapper mapper = new ObjectMapper();
        CompensationRepository repository = new JdbcCompensationRepository(ds, new JdbcCompensationEvidenceWriter());
        SensitiveReadAuditService sensitiveReadAuditService =
                new SensitiveReadAuditService(ds, new HrRedactionGuard(), new JdbcHrAuditRepository());
        service = new CompensationService(repository, new StubAuthorizationPort(),
                sensitiveReadAuditService, ds, mapper);
    }

    // ==================== immutable history ====================

    @Test
    void compensationChangeCreatesNewPackageInsteadOfOverwritingHistory() throws Exception {
        CompensationPackage original = service.createPackage(ctx(), new CompensationService.CreateCompensationCommand(
                employmentId, "SAR", "MONTHLY", LocalDate.of(2026, 1, 1),
                List.of(baseSalary("77777.7700"))));

        CompensationPackage successor = service.revisePackage(ctx(), original.id(),
                new CompensationService.ReviseCompensationCommand("SAR", "MONTHLY", LocalDate.of(2026, 7, 1),
                        List.of(baseSalary("88888.8800")), "MERIT"));

        assertThat(successor.predecessorPackageId()).isEqualTo(original.id());
        CompensationRepository repo = repository();
        CompensationPackage reloaded = repo.findPackage(tenantId, original.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo("SUPERSEDED");
        assertThat(reloaded.effectiveTo()).isEqualTo(successor.effectiveFrom().minusDays(1));
        assertThat(reloaded.components().get(0).amount())
                .as("historical package amounts are immutable")
                .isEqualByComparingTo("77777.7700");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_compensation_packages WHERE status = 'ACTIVE'"))
                .as("at most one ACTIVE package per employment")
                .isEqualTo("1");
    }

    @Test
    void viewerWithoutIndependentCapabilityCannotReadCompensation() throws Exception {
        service.createPackage(ctx(), new CompensationService.CreateCompensationCommand(
                employmentId, "SAR", "MONTHLY", LocalDate.of(2026, 1, 1),
                List.of(baseSalary("77777.7700"))));
        viewAllowed = false;

        assertThatThrownBy(() -> service.readActivePackageWithAudit(ctx(), employmentId,
                LocalDate.of(2026, 6, 1), "SHOULD_BE_DENIED"))
                .as("compensation visibility requires the independent HRM.COMPENSATION.VIEW capability")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_SCOPE_DENIED");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_audit_ledger WHERE action = 'HR.SENSITIVE_READ.COMPENSATION'"))
                .as("a denied read must not produce a sensitive-read audit success row")
                .isEqualTo("0");
    }

    @Test
    void authorizedCompensationReadAuditsWithoutCopyingAmounts() throws Exception {
        service.createPackage(ctx(), new CompensationService.CreateCompensationCommand(
                employmentId, "SAR", "MONTHLY", LocalDate.of(2026, 1, 1),
                List.of(baseSalary("77777.7700"))));

        CompensationPackage pkg = service.readActivePackageWithAudit(ctx(), employmentId,
                LocalDate.of(2026, 6, 1), "PAY_REVIEW_SCREEN");

        assertThat(pkg.components().get(0).amount()).isEqualByComparingTo("77777.7700");
        String auditRow = queryScalar(
                "SELECT hr_audit_ledger::text FROM hr_audit_ledger WHERE action = 'HR.SENSITIVE_READ.COMPENSATION'");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_audit_ledger WHERE action = 'HR.SENSITIVE_READ.COMPENSATION'"))
                .isEqualTo("1");
        assertThat(auditRow)
                .as("the sensitive-read audit must NOT copy the compensation amount")
                .doesNotContain("77777.7700");
    }

    @Test
    void changeEventsCarryNoAmounts() throws Exception {
        CompensationPackage original = service.createPackage(ctx(), new CompensationService.CreateCompensationCommand(
                employmentId, "SAR", "MONTHLY", LocalDate.of(2026, 1, 1),
                List.of(baseSalary("77777.7700"))));
        service.revisePackage(ctx(), original.id(), new CompensationService.ReviseCompensationCommand(
                "SAR", "MONTHLY", LocalDate.of(2026, 7, 1), List.of(baseSalary("88888.8800")), "MERIT"));

        String payloads = queryScalar(
                "SELECT string_agg(payload::text, ',') FROM hr_domain_event_outbox "
                        + "WHERE event_type = 'HRM.COMPENSATION.CHANGED.v1'");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_domain_event_outbox "
                + "WHERE event_type = 'HRM.COMPENSATION.CHANGED.v1'")).isEqualTo("2");
        assertThat(payloads)
                .as("the generic change event must never carry compensation amounts")
                .doesNotContain("77777").doesNotContain("88888").doesNotContain("amount");
    }

    @Test
    void atMostOneBaseSalaryComponentPerPackage() {
        assertThatThrownBy(() -> service.createPackage(ctx(), new CompensationService.CreateCompensationCommand(
                employmentId, "SAR", "MONTHLY", LocalDate.of(2026, 1, 1),
                List.of(baseSalary("1000.0000"), baseSalary("2000.0000")))))
                .as("structural rule: at most one BASE_SALARY component")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HRM_COMPENSATION_INVALID");
    }

    // ==================== fixtures / plumbing ====================

    private CompensationComponent baseSalary(String amount) {
        return new CompensationComponent(UUID.randomUUID(), tenantId, UUID.randomUUID(),
                CompensationComponentType.BASE_SALARY, "BASE", new BigDecimal(amount), null);
    }

    private CompensationRepository repository() {
        return new JdbcCompensationRepository(dataSource, new JdbcCompensationEvidenceWriter());
    }

    private HrCommandContext ctx() {
        return new HrCommandContext(tenantId, employmentId, actorUserId, UUID.randomUUID());
    }

    private class StubAuthorizationPort implements CompensationAuthorizationPort {
        @Override
        public void requireManage(HrCommandContext c, UUID packageId) {
            if (!manageAllowed) {
                throw new IllegalStateException("HRM_SCOPE_DENIED: HRM.COMPENSATION.MANAGE");
            }
        }

        @Override
        public void requireView(HrCommandContext c, UUID employmentIdArg) {
            if (!viewAllowed) {
                throw new IllegalStateException("HRM_SCOPE_DENIED: HRM.COMPENSATION.VIEW");
            }
        }
    }

    /** Minimal transactional evidence writer — amounts never enter audit or outbox payloads. */
    private class JdbcCompensationEvidenceWriter implements com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter {
        @Override
        public void writeEvidence(Connection connection, com.sanad.platform.hr.audit.HrAuditRecord auditRecord,
                                  com.sanad.platform.integration.events.DomainEventEnvelope event) {
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO hr_audit_ledger (tenant_id, actor_user_id, action, resource_type, resource_id, "
                                + "data_classification, reason, before_state, after_state, result, correlation_id, "
                                + "request_id, occurred_at) VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,NOW())")) {
                    ps.setObject(1, auditRecord.tenantId());
                    ps.setObject(2, auditRecord.actorUserId());
                    ps.setString(3, auditRecord.action());
                    ps.setString(4, auditRecord.resourceType());
                    ps.setObject(5, auditRecord.resourceId());
                    ps.setString(6, auditRecord.dataClassification());
                    ps.setString(7, auditRecord.reason());
                    ps.setString(8, auditRecord.beforeState() == null ? null : auditRecord.beforeState().toString());
                    ps.setString(9, auditRecord.afterState() == null ? null : auditRecord.afterState().toString());
                    ps.setString(10, auditRecord.result());
                    ps.setObject(11, auditRecord.correlationId());
                    ps.setObject(12, auditRecord.requestId());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO hr_audit_delivery (audit_id, tenant_id, status) "
                                + "SELECT id, tenant_id, 'PENDING' FROM hr_audit_ledger WHERE tenant_id = ? "
                                + "AND action = ? AND resource_id = ? "
                                + "AND id NOT IN (SELECT audit_id FROM hr_audit_delivery)")) {
                    ps.setObject(1, auditRecord.tenantId());
                    ps.setString(2, auditRecord.action());
                    ps.setObject(3, auditRecord.resourceId());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO hr_domain_event_outbox (event_id, tenant_id, event_type, event_version, "
                                + "aggregate_type, aggregate_id, actor_user_id, occurred_at, correlation_id, "
                                + "idempotency_key, data_classification, payload, status) "
                                + "VALUES (?,?,?,?,?,?,?,NOW(),?,?,?,?::jsonb,'READY')")) {
                    ps.setObject(1, event.eventId());
                    ps.setObject(2, event.tenantId());
                    ps.setString(3, event.eventType());
                    ps.setInt(4, event.eventVersion());
                    ps.setString(5, event.aggregateType());
                    ps.setObject(6, event.aggregateId());
                    ps.setObject(7, event.actorUserId());
                    ps.setObject(8, event.correlationId());
                    ps.setString(9, event.idempotencyKey());
                    ps.setString(10, event.dataClassification());
                    ps.setString(11, event.payload() == null ? "{}" : event.payload().toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("HRM_COMPENSATION_EVIDENCE_FAILED: " + e.getMessage(), e);
            }
        }
    }

    private void insertTenant() throws Exception {
        executeUpdate("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "WS6-C3-" + tenantId);
                    ps.setString(3, "ws6c3-" + tenantId.toString().substring(0, 8));
                });
    }

    private void insertLegalEntity() throws Exception {
        executeUpdate("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status) "
                        + "VALUES (?,?,?,?,?,?,?)",
                ps -> {
                    ps.setObject(1, legalEntityId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "LE-" + tenantId.toString().substring(0, 8));
                    ps.setString(4, "WS6 Legal Entity");
                    ps.setString(5, "SA");
                    ps.setString(6, "SA");
                    ps.setString(7, "ACTIVE");
                });
    }

    private void insertPerson() throws Exception {
        executeUpdate("INSERT INTO hr_people (id, tenant_id, first_name, last_name, display_name) VALUES (?,?,?,?,?)",
                ps -> {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "Test");
                    ps.setString(4, "Person");
                    ps.setString(5, "Test Person");
                });
    }

    private void insertEmployment() throws Exception {
        executeUpdate("INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, "
                        + "first_name, last_name, display_name, employment_type, status, version, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?, 'Test','Employee','Test Employee','FULL_TIME','ACTIVE',1,NOW(),NOW())",
                ps -> {
                    ps.setObject(1, employmentId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, personId);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "EMP-" + employmentId.toString().substring(0, 8));
                });
    }

    private void setTenant(UUID tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private String queryScalar(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void executeUpdate(String sql, SqlBinder binder) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (binder != null) binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }
}
