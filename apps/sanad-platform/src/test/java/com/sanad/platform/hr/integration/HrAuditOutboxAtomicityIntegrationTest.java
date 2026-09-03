package com.sanad.platform.hr.integration;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 4 / WS4 Task 3 RED contract — immutable HR audit ledger,
 * producer-local domain event outbox, and fail-closed tenant isolation.
 * PostgreSQL Direct only.
 *
 * <p>RED fails because the Task 3 schema (hr_audit_ledger, hr_audit_delivery,
 * hr_domain_event_outbox) is missing — a clean schema-missing RED, never an
 * environment or compilation failure.</p>
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
}
