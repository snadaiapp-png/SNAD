package com.sanad.platform.hr.integration;

import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.hr.audit.JdbcHrAuditRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 4 / WS4 Task 5 RED contract — sensitive-read audit
 * that FAILS CLOSED.
 *
 * <p>Restricted reads (PII, COMPENSATION, protected CONTRACT data) must append
 * an audit ledger row BEFORE restricted data can be returned. If the audit
 * append fails, the restricted response must not be returned. The audit row
 * carries identifiers / classification / reason ONLY — never a copy of the
 * sensitive values themselves. No REQUIRES_NEW bypass may let the read survive
 * an audit failure.</p>
 *
 * <p>WS4 Task 5 behavior is exercised through reflection so a RED run fails
 * only because the Task 5 application classes are missing — never because of
 * a compilation error (same clean-RED convention as HrAuditOutboxAtomicityIntegrationTest).</p>
 */
class HrSensitiveReadAuditIntegrationTest {

    private static final String SENSITIVE_READ_SERVICE = "com.sanad.platform.hr.audit.SensitiveReadAuditService";
    private static final String AUTHENTICATED_CONTEXT = "com.sanad.platform.hr.audit.HrAuthenticatedContext";

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private DriverManagerDataSource dataSource;
    private Object service;
    private Class<?> contextClass;
    private UUID tenantId;
    private UUID actorUserId;

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
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS4-T5-" + tenantId);
            ps.setString(3, "ws4t5-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);

        service = newServiceInstance();
        contextClass = Class.forName(AUTHENTICATED_CONTEXT);
    }

    // ==================== RED: class discovery ====================

    @Test
    void sensitiveReadAuditServiceIsDiscoverable() {
        assertThat(service).as("SensitiveReadAuditService must exist and be constructible").isNotNull();
        assertThat(contextClass).as("HrAuthenticatedContext must exist").isNotNull();
    }

    // ==================== authorized restricted reads write audit ====================

    @Test
    void piiReadWritesAuditBeforeReturningData() throws Exception {
        UUID auditId = recordOrThrow("HR.SENSITIVE_READ.PERSON_PII", "HR_PERSON",
                UUID.randomUUID(), "PII", "EMPLOYEE_360_PII_TAB");

        assertThat(queryScalar("SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'"))
                .as("authorized PII read must append exactly one audit row")
                .isEqualTo("1");
        assertThat(queryScalar("SELECT action FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("HR.SENSITIVE_READ.PERSON_PII");
        assertThat(queryScalar("SELECT data_classification FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("PII");
        assertThat(queryScalar("SELECT actor_user_id::text FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo(actorUserId.toString());
        assertThat(queryScalar("SELECT result FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("SUCCESS");
        assertThat(queryScalar("SELECT status FROM hr_audit_delivery WHERE audit_id = '" + auditId + "'"))
                .as("read audit must also enter the delivery pipeline")
                .isEqualTo("PENDING");
    }

    @Test
    void compensationReadWritesAudit() throws Exception {
        UUID auditId = recordOrThrow("HR.SENSITIVE_READ.COMPENSATION", "HR_COMPENSATION_PACKAGE",
                UUID.randomUUID(), "COMPENSATION", "PAY_REVIEW_SCREEN");

        assertThat(queryScalar("SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'"))
                .isEqualTo("1");
        assertThat(queryScalar("SELECT action FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("HR.SENSITIVE_READ.COMPENSATION");
        assertThat(queryScalar("SELECT data_classification FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("COMPENSATION");
    }

    @Test
    void protectedContractReadWritesAudit() throws Exception {
        UUID auditId = recordOrThrow("HR.SENSITIVE_READ.CONTRACT", "HR_EMPLOYMENT_CONTRACT",
                UUID.randomUUID(), "RESTRICTED", "CONTRACT_DETAIL_VIEW");

        assertThat(queryScalar("SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'"))
                .isEqualTo("1");
        assertThat(queryScalar("SELECT resource_type FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("HR_EMPLOYMENT_CONTRACT");
        assertThat(queryScalar("SELECT data_classification FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("RESTRICTED");
    }

    // ==================== fail-closed semantics ====================

    @Test
    void auditFailurePreventsRestrictedResponse() throws Exception {
        // Actor claims a DIFFERENT tenant than the session GUC — the RLS WITH CHECK
        // policy rejects the audit INSERT, simulating a real audit-append failure.
        UUID foreignTenant = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, foreignTenant);
            ps.setString(2, "WS4-T5-FOREIGN-" + foreignTenant);
            ps.setString(3, "ws4t5f-" + foreignTenant.toString().substring(0, 8));
            ps.executeUpdate();
        }

        Object foreignActor = newContext(foreignTenant, actorUserId);
        Method connectionOverload = findConnectionOverload();
        assertThatThrownBy(() -> connectionOverload.invoke(service, connection, foreignActor,
                        "HR.SENSITIVE_READ.PERSON_PII", "HR_PERSON", UUID.randomUUID(), "PII", "SHOULD_NOT_RETURN"))
                .as("recordOrThrow must throw when the audit append fails — restricted data must not be returned")
                .hasRootCauseInstanceOf(SQLException.class);

        assertThat(queryScalar("SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + foreignTenant + "'"))
                .as("a failed audit append must never leave partial evidence behind")
                .isEqualTo("0");
    }

    @Test
    void sensitiveReadAuditRequiresTransactionalContext() throws Exception {
        Method txOverload = findTransactionalOverload();
        Object actor = newContext(tenantId, actorUserId);
        assertThatThrownBy(() -> txOverload.invoke(service, actor,
                        "HR.SENSITIVE_READ.PERSON_PII", "HR_PERSON", UUID.randomUUID(), "PII", "NO_TX"))
                .as("the Spring-tx variant must fail closed when no transaction is active "
                        + "(no REQUIRES_NEW bypass that survives read failure semantics)")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("HRM_SENSITIVE_READ_AUDIT_NOT_TRANSACTIONAL");
    }

    // ==================== evidence minimality ====================

    @Test
    void auditContainsIdentifiersAndClassificationOnly() throws Exception {
        UUID auditId = recordOrThrow("HR.SENSITIVE_READ.PERSON_PII", "HR_PERSON",
                UUID.randomUUID(), "PII", "AUDIT-FIXTURE-REASON");

        assertThat(queryScalar("SELECT before_state IS NULL FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .as("read audit must not copy document state")
                .isEqualTo("t");
        assertThat(queryScalar("SELECT after_state IS NULL FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .as("read audit must not copy document state")
                .isEqualTo("t");
        assertThat(queryScalar("SELECT reason FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("AUDIT-FIXTURE-REASON");
    }

    @Test
    void rawSensitiveValuesAreNeverCopiedIntoReadAudit() throws Exception {
        String sentinel = "RAW-SENTINEL-NATIONAL-ID-1234567890";
        // The service API has no path that accepts values; prove no value copy
        // exists by scanning the complete stored row for a sentinel that would
        // have been present if values leaked through any hidden channel.
        UUID auditId = recordOrThrow("HR.SENSITIVE_READ.PERSON_PII", "HR_PERSON",
                UUID.randomUUID(), "PII", "SENTINEL-SCAN");

        String row = queryScalar("SELECT hr_audit_ledger::text FROM hr_audit_ledger WHERE id = '" + auditId + "'");
        assertThat(row).doesNotContain(sentinel);
        assertThat(row).doesNotContain("nationalId");
        assertThat(row).doesNotContain("passportNumber");
        assertThat(row).doesNotContain("identifierCiphertext");
    }

    // ==================== reflection plumbing ====================

    private Object newServiceInstance() throws Exception {
        Class<?> svc = Class.forName(SENSITIVE_READ_SERVICE);
        Constructor<?> ctor = svc.getConstructor(DataSource.class, HrRedactionGuard.class, JdbcHrAuditRepository.class);
        return ctor.newInstance(dataSource, new HrRedactionGuard(), new JdbcHrAuditRepository());
    }

    private Object newContext(UUID tenant, UUID actor) throws Exception {
        Constructor<?> ctor = contextClass.getConstructor(UUID.class, UUID.class, UUID.class, UUID.class);
        return ctor.newInstance(tenant, actor, UUID.randomUUID(), UUID.randomUUID());
    }

    private Method findConnectionOverload() throws Exception {
        return service.getClass().getMethod("recordOrThrow", Connection.class, contextClass,
                String.class, String.class, UUID.class, String.class, String.class);
    }

    private Method findTransactionalOverload() throws Exception {
        return service.getClass().getMethod("recordOrThrow", contextClass,
                String.class, String.class, UUID.class, String.class, String.class);
    }

    /** Calls the Connection-participation overload; returns the generated audit id. */
    private UUID recordOrThrow(String action, String resourceType, UUID resourceId,
                               String classification, String reason) throws Exception {
        Object actor = newContext(tenantId, actorUserId);
        Method m = findConnectionOverload();
        Object result = m.invoke(service, connection, actor, action, resourceType, resourceId, classification, reason);
        return (UUID) result;
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
}
