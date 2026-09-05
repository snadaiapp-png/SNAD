package com.sanad.platform.hr.integration;

import com.sanad.platform.hr.employment.EmploymentStatus;
import com.sanad.platform.integration.events.DomainEventEnvelope;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 4 / WS4 Task 3 RED contract — immutable HR audit ledger,
 * producer-local domain event outbox, and fail-closed tenant isolation.
 * PostgreSQL Direct only.
 *
 * <p>WS4 Task 4 extends this suite with transactional audit/outbox atomicity:
 * critical Employment/Assignment mutations must append hr_audit_ledger +
 * hr_audit_delivery + hr_domain_event_outbox in the SAME transaction and any
 * evidence append failure must roll back the canonical mutation. Task 4
 * behavior is exercised through reflection so a RED run fails only because
 * the Task 4 application classes are missing — never because of a compilation
 * error (same clean-RED convention as HrComplianceEngineTest).</p>
 */
class HrAuditOutboxAtomicityIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private UUID tenantId;
    private DriverManagerDataSource dataSource;

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
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS4-T3-" + tenantId);
            ps.setString(3, "ws4t3-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);
    }

    // ==================== AUDIT LEDGER ====================

    @Test
    void auditAppendSucceedsAndLedgerRejectsUpdateAndDelete() throws Exception {
        UUID auditId = appendAudit("SUCCESS");

        assertThatThrownBy(() -> executeUpdate("UPDATE hr_audit_ledger SET action = 'TAMPERED' WHERE id = ?",
                ps -> ps.setObject(1, auditId)))
                .as("UPDATE of the immutable audit fact must be rejected by PostgreSQL")
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("HRM_AUDIT_IMMUTABLE");

        assertThatThrownBy(() -> executeUpdate("DELETE FROM hr_audit_ledger WHERE id = ?",
                ps -> ps.setObject(1, auditId)))
                .as("DELETE of the immutable audit fact must be rejected by PostgreSQL")
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("HRM_AUDIT_IMMUTABLE");

        assertThat(queryScalar("SELECT action FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("TEST.ACTION");
    }

    @Test
    void deliveryStateIsStoredSeparatelyAndMutatesWithoutChangingAuditFact() throws Exception {
        UUID auditId = appendAudit("SUCCESS");

        executeUpdate("INSERT INTO hr_audit_delivery (audit_id, tenant_id, status) VALUES (?,?, 'PENDING')",
                ps -> {
                    ps.setObject(1, auditId);
                    ps.setObject(2, tenantId);
                });

        // Worker mutates ONLY the delivery state.
        executeUpdate("UPDATE hr_audit_delivery SET status = 'DELIVERED', delivered_at = NOW(), attempt_count = 1 " +
                        "WHERE audit_id = ?", ps -> ps.setObject(1, auditId));

        assertThat(queryScalar("SELECT status FROM hr_audit_delivery WHERE audit_id = '" + auditId + "'"))
                .isEqualTo("DELIVERED");
        // The audit fact itself remains untouched.
        assertThat(queryScalar("SELECT result FROM hr_audit_ledger WHERE id = '" + auditId + "'"))
                .isEqualTo("SUCCESS");
    }

    @Test
    void auditPayloadsRejectRawSensitiveKeysAtDatabaseLevel() throws Exception {
        assertThat(queryScalar("SELECT to_regclass('public.hr_audit_ledger')::text"))
                .as("schema precondition: hr_audit_ledger must exist for this guard to be meaningful")
                .isEqualTo("hr_audit_ledger");
        assertThatThrownBy(() -> appendAuditWithState("{\"password\":\"raw-secret-value\"}", null, "SUCCESS"))
                .as("raw password key must never be persisted in the audit ledger")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> appendAuditWithState(null, "{\"national_id\":\"RAW-PII-VALUE\"}", "SUCCESS"))
                .as("raw national id key must never be persisted in the audit ledger")
                .isInstanceOf(SQLException.class);
    }

    // ==================== OUTBOX ====================

    @Test
    void outboxDurableRowSupportsFutureAtLeastOnceDelivery() throws Exception {
        UUID eventId = UUID.randomUUID();
        executeUpdate(
                "INSERT INTO hr_domain_event_outbox (event_id, tenant_id, event_type, event_version, aggregate_type, " +
                        "aggregate_id, payload, occurred_at) VALUES (?,?, 'HRM.TEST.EVENT.v1', 1, 'EMPLOYMENT', ?, " +
                        "'{\"state\":\"redacted\"}'::jsonb, NOW())",
                ps -> {
                    ps.setObject(1, eventId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, UUID.randomUUID());
                });

        assertThat(queryScalar("SELECT status FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .isEqualTo("READY");
        assertThat(queryScalar("SELECT attempt_count::text FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .isEqualTo("0");
        // Delivery metadata required by the at-least-once contract is present.
        assertThat(queryScalar(
                "SELECT ((max_attempts > 0) AND (available_at IS NOT NULL) AND (claim_token IS NULL))::text " +
                        "FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .isEqualTo("true");
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*)::text FROM pg_indexes WHERE tablename = 'hr_domain_event_outbox'")))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void outboxRejectsRawSensitiveKeysInPayload() throws Exception {
        assertThat(queryScalar("SELECT to_regclass('public.hr_domain_event_outbox')::text"))
                .as("schema precondition: hr_domain_event_outbox must exist for this guard to be meaningful")
                .isEqualTo("hr_domain_event_outbox");
        assertThatThrownBy(() -> insertOutboxEvent("{\"passport\":\"RAW-PASSPORT-VALUE\"}"))
                .as("raw passport key must never be persisted in the outbox")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertOutboxEvent("{\"api_key\":\"RAW-KEY-VALUE\"}"))
                .as("raw api key must never be persisted in the outbox")
                .isInstanceOf(SQLException.class);
    }

    @Test
    void outboxTenantIsolationFailsClosed() throws Exception {
        UUID eventId = UUID.randomUUID();
        insertOutboxEvent(eventId, tenantId);

        // No tenant context → zero rows (fail closed).
        resetTenant();
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_domain_event_outbox")).isEqualTo("0");

        // Wrong tenant → zero rows.
        UUID otherTenant = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, otherTenant);
            ps.setString(2, "WS4-T3-B-" + otherTenant);
            ps.setString(3, "ws4t3b-" + otherTenant.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(otherTenant);
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_domain_event_outbox")).isEqualTo("0");

        // Cross-tenant write → rejected by WITH CHECK.
        assertThatThrownBy(() -> insertOutboxEvent(UUID.randomUUID(), tenantId))
                .isInstanceOf(SQLException.class);

        // Restore context and confirm the row is still only visible to its owner.
        setTenant(tenantId);
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_domain_event_outbox")).isEqualTo("1");
    }

    // ==================== HELPERS ====================

    private UUID appendAudit(String result) throws Exception {
        return appendAuditWithState("{\"before\":\"redacted\"}", "{\"after\":\"redacted\"}", result);
    }

    private UUID appendAuditWithState(String beforeState, String afterState, String result) throws Exception {
        UUID auditId = UUID.randomUUID();
        String sql = "INSERT INTO hr_audit_ledger (id, tenant_id, actor_user_id, action, resource_type, resource_id, " +
                "data_classification, before_state, after_state, result, occurred_at) " +
                "VALUES (?,?,?, 'TEST.ACTION', 'EMPLOYMENT', ?, 'OPERATIONAL', ?::jsonb, ?::jsonb, ?, NOW())";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, auditId);
            ps.setObject(2, tenantId);
            ps.setObject(3, UUID.randomUUID());
            ps.setObject(4, UUID.randomUUID());
            ps.setString(5, beforeState);
            ps.setString(6, afterState);
            ps.setString(7, result);
            ps.executeUpdate();
        }
        return auditId;
    }

    private void insertOutboxEvent(String payload) throws Exception {
        insertOutboxEvent(UUID.randomUUID(), tenantId, payload);
    }

    private void insertOutboxEvent(UUID eventId, UUID ownerTenantId) throws Exception {
        insertOutboxEvent(eventId, ownerTenantId, "{\"state\":\"redacted\"}");
    }

    private void insertOutboxEvent(UUID eventId, UUID ownerTenantId, String payload) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_domain_event_outbox (event_id, tenant_id, event_type, event_version, aggregate_type, " +
                        "aggregate_id, payload, occurred_at) VALUES (?,?, 'HRM.TEST.EVENT.v1', 1, 'EMPLOYMENT', ?, " +
                        "?::jsonb, NOW())")) {
            ps.setObject(1, eventId);
            ps.setObject(2, ownerTenantId);
            ps.setObject(3, UUID.randomUUID());
            ps.setString(4, payload);
            ps.executeUpdate();
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

    private void setTenant(UUID tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString()); ps.execute();
        }
    }

    private void resetTenant() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
    }

    // ============================================================
    // WS4 TASK 4 — TRANSACTIONAL AUDIT / OUTBOX RED MATRIX
    // ============================================================

    @Test
    void employmentTransitionCommitsStateAuditDeliveryAndOutboxAtomically() throws Throwable {
        UUID employmentId = seedTransitionableEmployment(tenantId);
        Object repository = employmentRepository(defaultEvidenceWriter());

        transition(repository, tenantId, employmentId, EmploymentStatus.PENDING_ONBOARDING,
                EmploymentStatus.ACTIVE, LocalDate.of(2026, 9, 3), "ONBOARD_COMPLETE");

        assertThat(queryScalar("SELECT status FROM hr_employees WHERE id = '" + employmentId + "'"))
                .isEqualTo("ACTIVE");
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "' " +
                        "AND action = 'HRM.EMPLOYEE.ACTIVATED'"))).isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_delivery d JOIN hr_audit_ledger l ON l.id = d.audit_id " +
                        "WHERE l.tenant_id = '" + tenantId + "' AND l.action = 'HRM.EMPLOYEE.ACTIVATED' " +
                        "AND d.status = 'PENDING'"))).isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "' " +
                        "AND event_type = 'HRM.EMPLOYEE.ACTIVATED.v1' AND status = 'READY'"))).isEqualTo(1);
    }

    @Test
    void mutationRollsBackWhenAuditAppendFails() throws Throwable {
        UUID employmentId = seedTransitionableEmployment(tenantId);
        Object repository = employmentRepository(failingEvidenceWriter(defaultEvidenceWriter(), "AUDIT"));

        assertThatThrownBy(() -> transition(repository, tenantId, employmentId,
                EmploymentStatus.PENDING_ONBOARDING, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 9, 3), "ONBOARD_COMPLETE"))
                .hasMessageContaining("INJECTED_AUDIT_FAILURE");
        assertNothingCommitted(employmentId);
    }

    @Test
    void mutationRollsBackWhenAuditDeliveryAppendFails() throws Throwable {
        // If the ledger insert used REQUIRES_NEW, the fact would survive the
        // business rollback — this test rejects that failure mode explicitly.
        UUID employmentId = seedTransitionableEmployment(tenantId);
        Object repository = employmentRepository(failingEvidenceWriter(defaultEvidenceWriter(), "DELIVERY"));

        assertThatThrownBy(() -> transition(repository, tenantId, employmentId,
                EmploymentStatus.PENDING_ONBOARDING, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 9, 3), "ONBOARD_COMPLETE"))
                .hasMessageContaining("INJECTED_DELIVERY_FAILURE");
        assertNothingCommitted(employmentId);
    }

    @Test
    void mutationRollsBackWhenOutboxAppendFails() throws Throwable {
        UUID employmentId = seedTransitionableEmployment(tenantId);
        Object repository = employmentRepository(failingEvidenceWriter(defaultEvidenceWriter(), "OUTBOX"));

        assertThatThrownBy(() -> transition(repository, tenantId, employmentId,
                EmploymentStatus.PENDING_ONBOARDING, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 9, 3), "ONBOARD_COMPLETE"))
                .hasMessageContaining("INJECTED_OUTBOX_FAILURE");
        assertNothingCommitted(employmentId);
    }

    @Test
    void assignmentMutationCommitsStateAuditDeliveryAndOutboxAtomically() throws Throwable {
        UUID employmentId = seedTransitionableEmployment(tenantId);
        UUID organizationId = seedOrganization(tenantId);
        linkOrganizationLegalEntity(tenantId, organizationId, legalEntityId);
        Object repository = assignmentRepository(defaultEvidenceWriter());

        createAssignment(repository, tenantId, employmentId, organizationId,
                LocalDate.of(2026, 9, 1), null);

        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_employee_assignments WHERE tenant_id = '" + tenantId + "'")))
                .isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "' " +
                        "AND action = 'HRM.ASSIGNMENT.CREATED'"))).isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "' " +
                        "AND event_type = 'HRM.ASSIGNMENT.CREATED.v1'"))).isEqualTo(1);
    }

    @Test
    void assignmentMutationRollsBackWhenEvidenceAppendFails() throws Throwable {
        UUID employmentId = seedTransitionableEmployment(tenantId);
        UUID organizationId = seedOrganization(tenantId);
        linkOrganizationLegalEntity(tenantId, organizationId, legalEntityId);
        Object repository = assignmentRepository(failingEvidenceWriter(defaultEvidenceWriter(), "AUDIT"));

        assertThatThrownBy(() -> createAssignment(repository, tenantId, employmentId, organizationId,
                LocalDate.of(2026, 9, 1), null))
                .hasMessageContaining("INJECTED_AUDIT_FAILURE");

        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_employee_assignments WHERE tenant_id = '" + tenantId + "'")))
                .as("assignment mutation must roll back with its evidence")
                .isZero();
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'")))
                .isZero();
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "'")))
                .isZero();
    }

    @Test
    void noPartialCommitStateExistsWithoutAuditAndOutboxEvidence() throws Throwable {
        // Forced OUTBOX failure: full audit append already happened inside the
        // transaction, but the missing outbox append must undo EVERYTHING.
        UUID employmentId = seedTransitionableEmployment(tenantId);
        Object repository = employmentRepository(failingEvidenceWriter(defaultEvidenceWriter(), "OUTBOX"));

        assertThatThrownBy(() -> transition(repository, tenantId, employmentId,
                EmploymentStatus.PENDING_ONBOARDING, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 9, 3), "ONBOARD_COMPLETE"))
                .hasMessageContaining("INJECTED_OUTBOX_FAILURE");

        assertThat(queryScalar("SELECT status FROM hr_employees WHERE id = '" + employmentId + "'"))
                .isEqualTo("PENDING_ONBOARDING");
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'")))
                .as("audit without required outbox must be impossible")
                .isZero();
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "'")))
                .isZero();
    }

    @Test
    void mutationRetryAfterRollbackDoesNotDuplicateEventFacts() throws Throwable {
        UUID employmentId = seedTransitionableEmployment(tenantId);
        Object failing = employmentRepository(failingEvidenceWriter(defaultEvidenceWriter(), "AUDIT"));
        assertThatThrownBy(() -> transition(failing, tenantId, employmentId,
                EmploymentStatus.PENDING_ONBOARDING, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 9, 3), "ONBOARD_COMPLETE"))
                .hasMessageContaining("INJECTED_AUDIT_FAILURE");

        Object healthy = employmentRepository(defaultEvidenceWriter());
        transition(healthy, tenantId, employmentId, EmploymentStatus.PENDING_ONBOARDING,
                EmploymentStatus.ACTIVE, LocalDate.of(2026, 9, 3), "ONBOARD_COMPLETE");

        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'")))
                .as("retry must not duplicate audit facts")
                .isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "'")))
                .as("retry must not duplicate outbox facts")
                .isEqualTo(1);
    }

    @Test
    void tenantIsolationPreventsCrossTenantEvidenceAppends() throws Throwable {
        UUID employmentId = seedTransitionableEmployment(tenantId);
        Object repository = employmentRepository(defaultEvidenceWriter());

        UUID tenantB = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantB);
            ps.setString(2, "WS4-T4-B-" + tenantB);
            ps.setString(3, "ws4t4b-" + tenantB.toString().substring(0, 8));
            ps.executeUpdate();
        }

        assertThatThrownBy(() -> transition(repository, tenantB, employmentId,
                EmploymentStatus.PENDING_ONBOARDING, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 9, 3), "CROSS_TENANT"))
                .as("tenant B must not mutate (and therefore not evidence) tenant A employment")
                .isInstanceOf(RuntimeException.class);

        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantB + "'"))).isZero();
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'"))).isZero();

        transition(repository, tenantId, employmentId, EmploymentStatus.PENDING_ONBOARDING,
                EmploymentStatus.ACTIVE, LocalDate.of(2026, 9, 3), "ONBOARD_COMPLETE");
        assertThat(queryScalar(
                "SELECT tenant_id::text FROM hr_audit_ledger WHERE action = 'HRM.EMPLOYEE.ACTIVATED'"))
                .isEqualTo(tenantId.toString());
    }

    @Test
    void auditAppendOutsideBusinessTransactionFailsClosed() throws Throwable {
        Object auditService = newAuditService();
        Object record = auditRecord(tenantId, null, "HRM.EMPLOYEE.ACTIVATED", "EMPLOYMENT", null,
                JSON.readTree("{\"safe\":\"metadata\"}"), JSON.readTree("{\"safe\":\"metadata\"}"));

        assertThatThrownBy(() -> invoke(auditService, "appendMutationAudit",
                new Class<?>[]{record.getClass()}, record))
                .as("audit must never commit outside the business transaction (REQUIRES_NEW ban)")
                .hasMessageContaining("HRM_AUDIT_NOT_TRANSACTIONAL");
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'")))
                .isZero();
    }

    @Test
    void auditAndOutboxRedactSensitiveKeysIncludingCaseAndNesting() throws Throwable {
        setTenant(tenantId);
        Object auditService = newAuditService();
        Object before = JSON.readTree("{\"worker\":{\"nationalId\":\"RAW-PII-SENTINEL-1\",\"displayName\":\"Safe Display\"},\"PassWord\":\"RAW-SECRET-SENTINEL-2\"}");
        Object after = JSON.readTree("{\"notes\":[{\"Iban\":\"RAW-PII-SENTINEL-3\"},\"safe-note\"],\"apiKey\":\"RAW-KEY-SENTINEL-4\"}");
        Object record = auditRecord(tenantId, null, "HRM.EMPLOYEE.ACTIVATED", "EMPLOYMENT",
                UUID.randomUUID(), before, after);
        try (Connection raw = dataSource.getConnection()) {
            raw.setAutoCommit(true);
            setTenantOn(raw, tenantId);
            invoke(auditService, "appendMutationAudit",
                    new Class<?>[]{Connection.class, record.getClass()}, raw, record);
        }

        String ledgerText = queryScalar(
                "SELECT COALESCE(before_state::text,'') || COALESCE(after_state::text,'') " +
                        "FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "' AND action = 'HRM.EMPLOYEE.ACTIVATED'");
        assertThat(ledgerText)
                .doesNotContain("RAW-PII-SENTINEL-1")
                .doesNotContain("RAW-SECRET-SENTINEL-2")
                .doesNotContain("RAW-PII-SENTINEL-3")
                .doesNotContain("RAW-KEY-SENTINEL-4")
                .as("non-sensitive business context must survive redaction")
                .contains("Safe Display")
                .contains("safe-note");

        Object publisher = newDomainEventPublisher();
        JsonNode payload = JSON.readTree("{\"passport\":\"RAW-PII-SENTINEL-5\"," +
                "\"meta\":{\"nested\":{\"API_KEY\":\"RAW-KEY-SENTINEL-6\"}},\"employeeName\":\"Safe Name\"}");
        DomainEventEnvelope envelope = new DomainEventEnvelope(UUID.randomUUID(),
                "HRM.EMPLOYEE.ACTIVATED.v1", 1, "EMPLOYMENT", UUID.randomUUID(), tenantId,
                null, null, Instant.now(), null, null, null, "OPERATIONAL", payload);
        try (Connection raw = dataSource.getConnection()) {
            raw.setAutoCommit(true);
            setTenantOn(raw, tenantId);
            invoke(publisher, "publish", new Class<?>[]{Connection.class, DomainEventEnvelope.class},
                    raw, envelope);
        }

        String outboxText = queryScalar(
                "SELECT payload::text FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "' " +
                        "AND event_type = 'HRM.EMPLOYEE.ACTIVATED.v1'");
        assertThat(outboxText)
                .doesNotContain("RAW-PII-SENTINEL-5")
                .doesNotContain("RAW-KEY-SENTINEL-6")
                .contains("Safe Name");
    }

    @Test
    void governedOverrideLifecycleProducesDurableEvidenceWithExactEventNames() throws Throwable {
        Object auditService = newAuditService();
        Object publisher = newDomainEventPublisher();
        Object auditAdapter = newComplianceAuditAdapter(auditService);
        Object eventAdapter = newComplianceEventAdapter(publisher);
        Object service = newOverrideServiceWith(allowAllPort(), auditAdapter, eventAdapter);

        UUID requester = seedUser(tenantId);
        UUID approver = seedUser(tenantId);
        UUID ruleId = seedExceptionRule();
        UUID resourceId = UUID.randomUUID();

        UUID requestId = (UUID) invoke(service, "requestOverride", requestIdempotentArgs(),
                commandContext(tenantId, requester), ruleId,
                complianceResource("EMPLOYMENT", resourceId),
                "governance-approved exception", "TEST-EVIDENCE-002",
                JSON.readTree("{\"maxHoursPerWeek\":48}"), JSON.readTree("{\"maxHoursPerWeek\":40}"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        invoke(service, "approve", new Class<?>[]{UUID.class, UUID.class, UUID.class, String.class},
                tenantId, requestId, approver, "approved by governance");

        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "' " +
                        "AND action = 'HRM.COMPLIANCE_OVERRIDE.REQUESTED'"))).isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "' " +
                        "AND action = 'HRM.COMPLIANCE_OVERRIDE.APPROVED'"))).isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "' " +
                        "AND event_type = 'HRM.COMPLIANCE_OVERRIDE.REQUESTED.v1'"))).isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "' " +
                        "AND event_type = 'HRM.COMPLIANCE_OVERRIDE.APPROVED.v1'"))).isEqualTo(1);
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_delivery d JOIN hr_audit_ledger l ON l.id = d.audit_id " +
                        "WHERE l.tenant_id = '" + tenantId + "' AND l.action LIKE 'HRM.COMPLIANCE_OVERRIDE%'")))
                .isEqualTo(2);
    }

    // ============================================================
    // WS4 TASK 4 — REFLECTION PLUMBING + FIXTURES
    // ============================================================

    private static final ObjectMapper JSON = new ObjectMapper();

    private Object employmentRepository(Object evidenceWriter) throws Exception {
        Class<?> repoClass = Class.forName("com.sanad.platform.hr.employment.JdbcEmploymentRepository");
        if (evidenceWriter == null) {
            return repoClass.getConstructor(DataSource.class).newInstance(dataSource);
        }
        Class<?> writerClass = Class.forName("com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter");
        return repoClass.getConstructor(DataSource.class, writerClass).newInstance(dataSource, evidenceWriter);
    }

    private Object assignmentRepository(Object evidenceWriter) throws Exception {
        Class<?> repoClass = Class.forName("com.sanad.platform.hr.assignment.infrastructure.JdbcHrAssignmentRepository");
        if (evidenceWriter == null) {
            return repoClass.getConstructor(DataSource.class).newInstance(dataSource);
        }
        Class<?> writerClass = Class.forName("com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter");
        return repoClass.getConstructor(DataSource.class, writerClass).newInstance(dataSource, evidenceWriter);
    }

    private Object defaultEvidenceWriter() throws Exception {
        Class<?> writerClass = Class.forName("com.sanad.platform.hr.integration.JdbcHrEvidenceWriter");
        return writerClass.getConstructor(DataSource.class).newInstance(dataSource);
    }

    /**
     * Deterministic stage-failure wrapper around the default evidence writer.
     * AUDIT    — throws before any evidence append.
     * DELIVERY — inserts the ledger fact, then throws before delivery append.
     * OUTBOX   — appends the full audit fact, then throws before outbox append.
     */
    private Object failingEvidenceWriter(Object delegate, String failAt) throws Exception {
        Class<?> writerClass = Class.forName("com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter");
        Class<?> auditRecordClass = Class.forName("com.sanad.platform.hr.audit.HrAuditRecord");
        Class<?> envelopeClass = Class.forName("com.sanad.platform.integration.events.DomainEventEnvelope");
        Class<?> auditServiceClass = Class.forName("com.sanad.platform.hr.audit.HrAuditService");
        ClassLoader loader = writerClass.getClassLoader();
        return Proxy.newProxyInstance(loader, new Class<?>[]{writerClass}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "writeEvidence": {
                    Connection conn = (Connection) args[0];
                    switch (failAt) {
                        case "AUDIT":
                            throw new IllegalStateException("INJECTED_AUDIT_FAILURE");
                        case "DELIVERY": {
                            Object repo = Class.forName("com.sanad.platform.hr.audit.JdbcHrAuditRepository")
                                    .getConstructor().newInstance();
                            invoke(repo, "insertLedgerRow",
                                    new Class<?>[]{Connection.class, auditRecordClass}, conn, args[1]);
                            throw new IllegalStateException("INJECTED_DELIVERY_FAILURE");
                        }
                        case "OUTBOX": {
                            Object service = auditServiceClass
                                    .getConstructor(DataSource.class,
                                            Class.forName("com.sanad.platform.hr.audit.HrRedactionGuard"),
                                            Class.forName("com.sanad.platform.hr.audit.JdbcHrAuditRepository"))
                                    .newInstance(dataSource,
                                            Class.forName("com.sanad.platform.hr.audit.HrRedactionGuard")
                                                    .getConstructor().newInstance(),
                                            Class.forName("com.sanad.platform.hr.audit.JdbcHrAuditRepository")
                                                    .getConstructor().newInstance());
                            invoke(service, "appendMutationAudit",
                                    new Class<?>[]{Connection.class, auditRecordClass}, conn, args[1]);
                            throw new IllegalStateException("INJECTED_OUTBOX_FAILURE");
                        }
                        default:
                            throw new IllegalStateException("Unknown failAt: " + failAt);
                    }
                }
                case "toString": return "FailingEvidenceWriter(" + failAt + ")";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                default: throw new IllegalStateException("Unexpected: " + method.getName());
            }
        });
    }

    private Object newAuditService() throws Exception {
        return Class.forName("com.sanad.platform.hr.audit.HrAuditService")
                .getConstructor(DataSource.class,
                        Class.forName("com.sanad.platform.hr.audit.HrRedactionGuard"),
                        Class.forName("com.sanad.platform.hr.audit.JdbcHrAuditRepository"))
                .newInstance(dataSource,
                        Class.forName("com.sanad.platform.hr.audit.HrRedactionGuard").getConstructor().newInstance(),
                        Class.forName("com.sanad.platform.hr.audit.JdbcHrAuditRepository").getConstructor().newInstance());
    }

    private Object newDomainEventPublisher() throws Exception {
        return Class.forName("com.sanad.platform.hr.integration.HrDomainEventPublisher")
                .getConstructor(DataSource.class,
                        Class.forName("com.sanad.platform.hr.audit.HrRedactionGuard"),
                        Class.forName("com.sanad.platform.hr.integration.JdbcHrOutboxRepository"))
                .newInstance(dataSource,
                        Class.forName("com.sanad.platform.hr.audit.HrRedactionGuard").getConstructor().newInstance(),
                        Class.forName("com.sanad.platform.hr.integration.JdbcHrOutboxRepository").getConstructor().newInstance());
    }

    private Object newComplianceAuditAdapter(Object auditService) throws Exception {
        return Class.forName("com.sanad.platform.hr.integration.Ws4ComplianceAuditAdapter")
                .getConstructor(DataSource.class,
                        Class.forName("com.sanad.platform.hr.audit.HrAuditService"))
                .newInstance(dataSource, auditService);
    }

    private Object newComplianceEventAdapter(Object publisher) throws Exception {
        return Class.forName("com.sanad.platform.hr.integration.Ws4ComplianceEventAdapter")
                .getConstructor(DataSource.class,
                        Class.forName("com.sanad.platform.hr.integration.HrDomainEventPublisher"))
                .newInstance(dataSource, publisher);
    }

    private Object allowAllPort() throws Exception {
        Class<?> portClass = Class.forName(
                "com.sanad.platform.hr.compliance.application.ComplianceOverrideAuthorizationPort");
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "requireApprovalAuthorization": return null;
                        case "toString": return "AllowAllAuthorizationPort";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new IllegalStateException("Unexpected: " + method.getName());
                    }
                });
    }

    private Object newOverrideServiceWith(Object authPort, Object auditPort, Object eventPort) throws Exception {
        Class<?> serviceClass = Class.forName(
                "com.sanad.platform.hr.compliance.application.ComplianceOverrideService");
        Class<?> authClass = Class.forName(
                "com.sanad.platform.hr.compliance.application.ComplianceOverrideAuthorizationPort");
        Class<?> auditClass = Class.forName(
                "com.sanad.platform.hr.compliance.application.ComplianceAuditPort");
        Class<?> eventClass = Class.forName(
                "com.sanad.platform.hr.compliance.application.ComplianceEventPort");
        return serviceClass.getConstructor(DataSource.class, authClass, auditClass, eventClass)
                .newInstance(dataSource, authPort, auditPort, eventPort);
    }

    private Object commandContext(UUID tenantId, UUID actorUserId) throws Exception {
        return Class.forName("com.sanad.platform.hr.compliance.domain.HrCommandContext")
                .getDeclaredConstructor(UUID.class, UUID.class, UUID.class, UUID.class)
                .newInstance(tenantId, UUID.randomUUID(), actorUserId, null);
    }

    private Object complianceResource(String resourceType, UUID resourceId) throws Exception {
        return Class.forName("com.sanad.platform.hr.compliance.domain.ComplianceResource")
                .getDeclaredConstructor(String.class, UUID.class)
                .newInstance(resourceType, resourceId);
    }

    private Class<?>[] requestIdempotentArgs() throws Exception {
        return new Class<?>[]{
                Class.forName("com.sanad.platform.hr.compliance.domain.HrCommandContext"),
                UUID.class,
                Class.forName("com.sanad.platform.hr.compliance.domain.ComplianceResource"),
                String.class, String.class, JsonNode.class, JsonNode.class,
                LocalDate.class, LocalDate.class};
    }

    private Object auditRecord(UUID tenantId, UUID actorUserId, String action, String resourceType,
                               UUID resourceId, Object beforeState, Object afterState) throws Exception {
        return Class.forName("com.sanad.platform.hr.audit.HrAuditRecord")
                .getConstructor(UUID.class, UUID.class, String.class, String.class, UUID.class,
                        UUID.class, UUID.class, String.class, String.class,
                        JsonNode.class, JsonNode.class, String.class, UUID.class, UUID.class, Instant.class)
                .newInstance(tenantId, actorUserId, action, resourceType, resourceId,
                        null, null, "OPERATIONAL", "task4-red-test",
                        beforeState, afterState, "SUCCESS", null, null, Instant.now());
    }

    private void transition(Object repository, UUID tenantId, UUID employmentId,
                            EmploymentStatus from, EmploymentStatus to,
                            LocalDate effectiveDate, String reasonCode) throws Throwable {
        Method execute = repository.getClass().getMethod("executeTransition",
                UUID.class, UUID.class, EmploymentStatus.class, EmploymentStatus.class,
                long.class, LocalDate.class, String.class);
        try {
            execute.invoke(repository, tenantId, employmentId, from, to, 0L, effectiveDate, reasonCode);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private void createAssignment(Object repository, UUID tenantId, UUID employmentId, UUID organizationId,
                                  LocalDate effectiveFrom, LocalDate effectiveTo) throws Throwable {
        Method create = repository.getClass().getMethod("createAssignmentAtomically",
                UUID.class, UUID.class, UUID.class, UUID.class, UUID.class, UUID.class, UUID.class, UUID.class,
                Class.forName("com.sanad.platform.hr.assignment.domain.AssignmentType"),
                Class.forName("com.sanad.platform.hr.assignment.domain.OccupancyMode"),
                java.math.BigDecimal.class, LocalDate.class, LocalDate.class);
        Object primary = Enum.valueOf(
                (Class<Enum>) Class.forName("com.sanad.platform.hr.assignment.domain.AssignmentType"), "PRIMARY");
        Object occupying = Enum.valueOf(
                (Class<Enum>) Class.forName("com.sanad.platform.hr.assignment.domain.OccupancyMode"), "OCCUPYING");
        try {
            create.invoke(repository, tenantId, employmentId, organizationId,
                    null, null, null, null, null,
                    primary, occupying, new java.math.BigDecimal("100"), effectiveFrom, effectiveTo);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private Object invoke(Object target, String methodName, Class<?>[] types, Object... args) throws Throwable {
        Method method = target.getClass().getMethod(methodName, types);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private void assertNothingCommitted(UUID employmentId) throws Exception {
        assertThat(queryScalar("SELECT status FROM hr_employees WHERE id = '" + employmentId + "'"))
                .as("canonical mutation must be absent after rollback")
                .isEqualTo("PENDING_ONBOARDING");
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = '" + tenantId + "'")))
                .as("audit fact must be absent after rollback")
                .isZero();
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_audit_delivery WHERE tenant_id = '" + tenantId + "'")))
                .as("delivery state must be absent after rollback")
                .isZero();
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_domain_event_outbox WHERE tenant_id = '" + tenantId + "'")))
                .as("outbox fact must be absent after rollback")
                .isZero();
    }

    private UUID legalEntityId;

    private UUID seedOrganization(UUID tenantId) throws Exception {
        UUID orgId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) " +
                        "VALUES (?, ?, 'Task4 Org', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, orgId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
        return orgId;
    }

    private void linkOrganizationLegalEntity(UUID tenantId, UUID organizationId, UUID legalEntityId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO organization_legal_entities (tenant_id, organization_id, legal_entity_id, effective_from, status) " +
                        "VALUES (?, ?, ?, DATE '2026-01-01', 'ACTIVE')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, organizationId);
            ps.setObject(3, legalEntityId);
            ps.executeUpdate();
        }
    }

    private UUID seedUser(UUID tenantId) throws Exception {
        UUID userId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, userId + "@ws4t4.example.invalid");
            ps.executeUpdate();
        }
        return userId;
    }

    private UUID seedExceptionRule() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_country_packs (country_code, pack_code, pack_version, status, effective_from, " +
                        "legal_reviewed_at, legal_reviewed_by, certification_reference) " +
                        "VALUES ('SA', 'WS4-T4-PACK', '1', 'ACTIVE', DATE '2026-01-01', NOW(), 'legal-review', 'TEST-CERT') " +
                        "RETURNING id")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                UUID packId = UUID.fromString(rs.getString(1));
                try (PreparedStatement ins = connection.prepareStatement(
                        "INSERT INTO hr_compliance_rules (country_pack_id, rule_code, rule_version, operation_code, " +
                                "enforcement_level, exception_allowed, parameters, official_source_uri, legal_citation, " +
                                "source_snapshot_sha256, effective_from, last_legal_review_at, reviewed_by, status) " +
                                "VALUES (?, 'WS4_T4_RULE', '1', 'HRM.STATUTORY.LOCAL_ACTION', 'MANDATORY_WITH_EXCEPTION', " +
                                "TRUE, '{}'::jsonb, 'https://official-source.test/rule', 'Test citation', REPEAT('a', 64), " +
                                "DATE '2026-01-01', NOW(), 'legal-review', 'ACTIVE') RETURNING id")) {
                    ins.setObject(1, packId);
                    try (ResultSet rs2 = ins.executeQuery()) {
                        rs2.next();
                        return UUID.fromString(rs2.getString(1));
                    }
                }
            }
        }
    }

    private UUID seedTransitionableEmployment(UUID tenantId) throws Exception {
        legalEntityId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'Task4 LE', 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, "LE-" + legalEntityId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_people (id, tenant_id, first_name, last_name, display_name) VALUES (?, ?, 'Task', 'Four', 'Task Four')")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                        "first_name, last_name, display_name, employment_type, status, hire_date, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, 'Task', 'Four', 'Task Four', 'FULL_TIME', 'PENDING_ONBOARDING', DATE '2026-01-01', 0, NOW(), NOW())")) {
            ps.setObject(1, employmentId);
            ps.setObject(2, tenantId);
            ps.setObject(3, personId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, "E-" + employmentId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return employmentId;
    }

    private void setTenantOn(Connection raw, UUID tenant) throws Exception {
        try (PreparedStatement ps = raw.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }
}
