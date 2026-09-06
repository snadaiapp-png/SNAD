package com.sanad.platform.hr.contract;

import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.compliance.application.ComplianceEngine;
import com.sanad.platform.hr.compliance.application.CountryPolicyResolver;
import com.sanad.platform.hr.compliance.application.WorkerClassificationResolver;
import com.sanad.platform.hr.compliance.domain.ComplianceDecisionType;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceDecisionRepository;
import com.sanad.platform.hr.contract.application.ContractAuthorizationPort;
import com.sanad.platform.hr.contract.application.CountryContractTermsValidator;
import com.sanad.platform.hr.contract.application.EmploymentContractService;
import com.sanad.platform.hr.contract.domain.ContractCommandResult;
import com.sanad.platform.hr.contract.domain.EmploymentContractRepository;
import com.sanad.platform.hr.contract.domain.EmploymentContractStatus;
import com.sanad.platform.hr.contract.domain.EmploymentContractVersion;
import com.sanad.platform.hr.contract.infrastructure.JdbcEmploymentContractRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 5 / WS6 Task 2 RED contract — immutable contract
 * versioning with Country Pack validation, compliance gating, scoped
 * authorization and transactional evidence.
 *
 * <p>Service behavior is exercised through direct construction (all
 * collaborators have public constructors); the authorization port uses a
 * deterministic allowing stub so authorization stays out of these cases'
 * scope (the scoped matrix is covered by the WS4 suites).</p>
 */
class HrContractCountryPolicyIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private DriverManagerDataSource dataSource;
    private EmploymentContractService service;
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
        insertJurisdiction();

        ObjectMapper mapper = new ObjectMapper();
        // The compliance stack uses platform JDBC templates — production wires
        // the RLS-wrapped DataSource; the test mirrors that with a tenant-scoped
        // proxy so FORCE RLS resolves the employment/legal-entity rows.
        JdbcTemplate jdbc = new JdbcTemplate(tenantScopedDataSource(ds));
        CountryPolicyResolver resolver = new CountryPolicyResolver(jdbc, new WorkerClassificationResolver(jdbc));
        ComplianceEngine engine = new ComplianceEngine(resolver, java.util.List.of(),
                new JdbcComplianceDecisionRepository(jdbc));
        EmploymentContractRepository repository = new JdbcEmploymentContractRepository(ds,
                new JdbcHrContractEvidenceWriter(mapper), mapper);
        service = new EmploymentContractService(repository, resolver, engine,
                new CountryContractTermsValidator(), allowingAuthorization(), mapper);
    }

    // ==================== immutable history ====================

    @Test
    void amendmentCreatesNewVersionAndPreservesOldVersion() throws Exception {
        ContractCommandResult created = service.createDraft(ctx(), new EmploymentContractService.CreateContractCommand(
                employmentId, "CT-AMEND", true, "INDEFINITE", LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 1, 1), "doc-ref-1", genericTerms()));
        service.activate(ctx(), created.version().contractId(), 1, LocalDate.of(2026, 1, 1));

        ContractCommandResult amended = service.amend(ctx(), created.version().contractId(),
                new EmploymentContractService.AmendContractCommand("INDEFINITE", LocalDate.of(2026, 1, 1),
                        null, LocalDate.of(2026, 7, 1), "doc-ref-2", genericTerms(), "PROMOTION"));

        assertThat(amended.version().versionNumber())
                .as("amendment must create a NEW version")
                .isEqualTo(2);
        EmploymentContractRepository repo = repository();
        assertThat(repo.findVersion(tenantId, created.version().id())).isPresent();
        assertThat(repo.findVersion(tenantId, created.version().id()).orElseThrow().status())
                .as("the amended-away version must be preserved as SUPERSEDED, never overwritten")
                .isEqualTo(EmploymentContractStatus.SUPERSEDED);
        assertThat(repo.findVersion(tenantId, created.version().id()).orElseThrow().documentReference())
                .isEqualTo("doc-ref-1");
    }

    @Test
    void effectiveHistoricalVersionCannotBeUpdatedInPlace() throws Exception {
        ContractCommandResult created = service.createDraft(ctx(), new EmploymentContractService.CreateContractCommand(
                employmentId, "CT-IMMUTABLE", true, "INDEFINITE", LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 1, 1), "doc-ref-1", genericTerms()));
        service.activate(ctx(), created.version().contractId(), 1, LocalDate.of(2026, 1, 1));

        // Repository contract surface: no update-terms operation may exist.
        for (java.lang.reflect.Method m : EmploymentContractRepository.class.getMethods()) {
            assertThat(m.getName()).as("no update-terms operation may be exposed").doesNotContain("updateTerms");
        }

        // Database-level guard: updating term columns on an effective version is rejected.
        assertThatThrownBy(() -> executeUpdate(
                "UPDATE hr_employment_contract_versions SET document_reference = 'TAMPERED' WHERE id = ?",
                ps -> ps.setObject(1, created.version().id())))
                .as("effective contract terms are immutable at the database level")
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("HRM_CONTRACT_VERSION_IMMUTABLE");
    }

    // ==================== Global Mode semantics ====================

    @Test
    void globalModeStoresGenericTermsAndMarksLocalComplianceUnverified() throws Exception {
        ContractCommandResult result = service.createDraft(ctx(), new EmploymentContractService.CreateContractCommand(
                employmentId, "CT-GLOBAL", true, "INDEFINITE", LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 1, 1), "doc-ref", genericTerms()));

        assertThat(result.complianceStatus())
                .as("Global Mode contract terms are stored but statutory correctness is NOT certified")
                .isEqualTo(ContractCommandResult.LOCAL_COMPLIANCE_UNVERIFIED);
        assertThat(result.version().status()).isEqualTo(EmploymentContractStatus.DRAFT);
    }

    @Test
    void globalModeRejectsCountrySpecificTerms() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode countrySpecific = mapper.readTree(
                "{\"probationMonths\": 6, \"saEndOfServiceFormula\": \"custom\"}");

        assertThatThrownBy(() -> service.createDraft(ctx(), new EmploymentContractService.CreateContractCommand(
                employmentId, "CT-COUNTRY", true, "INDEFINITE", LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 1, 1), "doc-ref", countrySpecific)))
                .as("jurisdiction-specific terms without a legally reviewed pack must fail with a "
                        + "structured compliance violation")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HRM_CONTRACT_TERMS_NOT_CERTIFIED");
    }

    @Test
    void globalModeRejectsExecutableContentInTerms() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode scripted = mapper.readTree(
                "{\"script\": \"return 42\"}");

        assertThatThrownBy(() -> service.createDraft(ctx(), new EmploymentContractService.CreateContractCommand(
                employmentId, "CT-SCRIPT", true, "INDEFINITE", LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 1, 1), "doc-ref", scripted)))
                .as("country terms must never carry executable content")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HRM_CONTRACT_TERMS_INVALID");
    }

    // ==================== temporal + compliance gating ====================

    @Test
    void overlappingActivationIsRejectedDeterministically() throws Exception {
        ContractCommandResult first = service.createDraft(ctx(), new EmploymentContractService.CreateContractCommand(
                employmentId, "CT-OVL-A", true, "INDEFINITE", LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 1, 1), "doc-a", genericTerms()));
        service.activate(ctx(), first.version().contractId(), 1, LocalDate.of(2026, 1, 1));

        ContractCommandResult second = service.createDraft(ctx(), new EmploymentContractService.CreateContractCommand(
                employmentId, "CT-OVL-B", true, "FIXED_TERM", LocalDate.of(2027, 1, 1),
                LocalDate.of(2029, 12, 31), LocalDate.of(2027, 1, 1), "doc-b", genericTerms()));

        assertThatThrownBy(() -> service.activate(ctx(), second.version().contractId(), 1, LocalDate.of(2028, 1, 1)))
                .as("activating an overlapping primary contract must surface the deterministic overlap error")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_CONTRACT_OVERLAP");
    }

    @Test
    void contractLifecycleAppendsTransactionalEvidenceWithoutAmounts() throws Exception {
        ContractCommandResult created = service.createDraft(ctx(), new EmploymentContractService.CreateContractCommand(
                employmentId, "CT-EVIDENCE", true, "INDEFINITE", LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 1, 1), "doc-ref", genericTerms()));
        service.activate(ctx(), created.version().contractId(), 1, LocalDate.of(2026, 1, 1));
        service.terminate(ctx(), created.version().contractId(), LocalDate.of(2026, 12, 31), "MUTUAL");

        assertThat(queryScalar("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE event_type = 'HRM.CONTRACT.CREATED.v1'"))
                .isEqualTo("1");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE event_type = 'HRM.CONTRACT.ACTIVATED.v1'"))
                .isEqualTo("1");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE event_type = 'HRM.CONTRACT.TERMINATED.v1'"))
                .isEqualTo("1");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_type = 'HR_EMPLOYMENT_CONTRACT'"))
                .as("each contract mutation must append transactional audit evidence")
                .isEqualTo("3");
        String payloads = queryScalar(
                "SELECT string_agg(payload::text, ',') FROM hr_domain_event_outbox WHERE event_type LIKE 'HRM.CONTRACT.%'");
        assertThat(payloads)
                .as("contract events must never carry compensation amounts or restricted PII")
                .doesNotContain("amount").doesNotContain("BASE_SALARY").doesNotContain("nationalId");
    }

    // ==================== fixtures / plumbing ====================

    private DataSource tenantScopedDataSource(DataSource real) {
        return (DataSource) java.lang.reflect.Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        Connection c = (Connection) method.invoke(real, args);
                        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                            ps.setString(1, tenantId.toString());
                            ps.execute();
                        }
                        return c;
                    }
                    return method.invoke(real, args);
                });
    }

    private com.fasterxml.jackson.databind.JsonNode genericTerms() throws Exception {
        return new ObjectMapper().readTree("{\"probationMonths\": 3, \"noticePeriodDays\": 60}");
    }

    private HrCommandContext ctx() {
        return new HrCommandContext(tenantId, employmentId, actorUserId, UUID.randomUUID());
    }

    private EmploymentContractRepository repository() {
        return new JdbcEmploymentContractRepository(dataSource,
                new JdbcHrContractEvidenceWriter(new ObjectMapper()), new ObjectMapper());
    }

    private ContractAuthorizationPort allowingAuthorization() {
        return new ContractAuthorizationPort() {
            @Override public void requireManage(HrCommandContext c, UUID contractId) { }
            @Override public void requireView(HrCommandContext c, UUID contractId) { }
        };
    }

    /** Minimal transactional evidence writer for the contract aggregate. */
    private static class JdbcHrContractEvidenceWriter implements HrTransactionalEvidenceWriter {
        private final ObjectMapper mapper;

        JdbcHrContractEvidenceWriter(ObjectMapper mapper) {
            this.mapper = mapper;
        }

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
                throw new IllegalStateException("HRM_CONTRACT_EVIDENCE_FAILED: " + e.getMessage(), e);
            }
        }
    }

    private void insertTenant() throws Exception {
        executeUpdate("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "WS6-T2-" + tenantId);
                    ps.setString(3, "ws6t2-" + tenantId.toString().substring(0, 8));
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

    private void insertJurisdiction() throws Exception {
        executeUpdate("INSERT INTO hr_employment_jurisdiction_periods (tenant_id, employment_id, "
                        + "labor_jurisdiction, effective_from, approval_status, approval_reference, approved_by, approved_at) "
                        + "VALUES (?,?,'SA','2026-01-01','APPROVED','WS6-TEST-FIXTURE',?,NOW())",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, employmentId);
                    ps.setObject(3, actorUserId);
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
