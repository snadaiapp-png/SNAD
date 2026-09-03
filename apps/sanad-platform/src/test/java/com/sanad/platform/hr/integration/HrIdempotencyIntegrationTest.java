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
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 4 / WS4 Task 3 RED contract — durable producer-local HR
 * idempotency records and the HR-managed IAM access binding persistence.
 * PostgreSQL Direct only.
 *
 * <p>RED fails because the Task 3 schema (hr_idempotency_records,
 * hr_iam_access_bindings) is missing — a clean schema-missing RED.</p>
 */
class HrIdempotencyIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private UUID tenantId;
    private UUID principalId;

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
        principalId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS4-T3I-" + tenantId);
            ps.setString(3, "ws4t3i-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);
    }

    // ==================== RLS FAIL-CLOSED INVENTORY ====================

    @Test
    void allTask3TenantOwnedTablesHaveForcedRls() throws Exception {
        for (String table : new String[]{
                "hr_audit_ledger", "hr_audit_delivery", "hr_domain_event_outbox",
                "hr_idempotency_records", "hr_iam_access_bindings"}) {
            assertThat(queryScalar(
                    "SELECT (relrowsecurity AND relforcerowsecurity)::text FROM pg_class WHERE relname = '" + table + "'"))
                    .as(table + " must have ENABLE + FORCE ROW LEVEL SECURITY")
                    .isEqualTo("t");
        }
    }

    // ==================== IDEMPOTENCY ====================

    @Test
    void sameKeyWithSameFingerprintIsReplayCompatible() throws Exception {
        String fingerprint = repeat('a');
        insertIdempotencyRecord(tenantId, principalId, "HRM.CREATE.EMPLOYMENT", "key-1", fingerprint, 200, null);

        // Conditional upsert: same fingerprint → the stored outcome is replayed in place.
        int updated = conditionalUpsertIdempotency(tenantId, principalId, "HRM.CREATE.EMPLOYMENT", "key-1",
                fingerprint, 200);
        assertThat(updated).isEqualTo(1);
        assertThat(queryScalar("SELECT COUNT(*)::text FROM hr_idempotency_records")).isEqualTo("1");
        assertThat(queryScalar("SELECT request_fingerprint FROM hr_idempotency_records")).isEqualTo(fingerprint);
    }

    @Test
    void sameKeyWithDifferentFingerprintConflicts() throws Exception {
        String original = repeat('a');
        insertIdempotencyRecord(tenantId, principalId, "HRM.CREATE.EMPLOYMENT", "key-2", original, 200, null);

        // Different fingerprint → the conditional upsert must NOT overwrite the stored outcome.
        int updated = conditionalUpsertIdempotency(tenantId, principalId, "HRM.CREATE.EMPLOYMENT", "key-2",
                repeat('b'), 200);
        assertThat(updated).as("fingerprint mismatch must be detectable as a conflict").isEqualTo(0);
        assertThat(queryScalar("SELECT request_fingerprint FROM hr_idempotency_records")).isEqualTo(original);

        // A blind second insert violates the unique boundary.
        assertThatThrownBy(() -> insertIdempotencyRecord(tenantId, principalId, "HRM.CREATE.EMPLOYMENT",
                "key-2", repeat('b'), 200, null))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23505"));
    }

    @Test
    void idempotencyKeyIsIsolatedByTenantPrincipalAndOperation() throws Exception {
        UUID tenantB = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantB);
            ps.setString(2, "WS4-T3I-B-" + tenantB);
            ps.setString(3, "ws4t3ib-" + tenantB.toString().substring(0, 8));
            ps.executeUpdate();
        }

        // Same key, different tenant → distinct record.
        insertIdempotencyRecord(tenantId, principalId, "HRM.OP", "shared-key", repeat('a'), 200, null);
        setTenant(tenantB);
        insertIdempotencyRecord(tenantB, principalId, "HRM.OP", "shared-key", repeat('a'), 200, null);

        // Same key, different principal → distinct record.
        insertIdempotencyRecord(tenantB, UUID.randomUUID(), "HRM.OP", "shared-key", repeat('a'), 200, null);

        // Same key, different operation → distinct record.
        insertIdempotencyRecord(tenantB, principalId, "HRM.OTHER.OP", "shared-key", repeat('a'), 200, null);

        assertThat(queryScalar("SELECT COUNT(*)::text FROM hr_idempotency_records")).isEqualTo("4");
        setTenant(tenantId);
        assertThat(queryScalar("SELECT COUNT(*)::text FROM hr_idempotency_records")).isEqualTo("1");
    }

    @Test
    void expirationIsRepresentedCorrectly() throws Exception {
        insertIdempotencyRecordWithExpiry(tenantId, principalId, "HRM.OP", "key-exp", repeat('a'),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        assertThat(queryScalar(
                "SELECT (expires_at < NOW())::text FROM hr_idempotency_records WHERE idempotency_key = 'key-exp'"))
                .as("a past expiry must be stored and recognizable as expired")
                .isEqualTo("t");
        // Cleanup index for expired records must exist.
        assertThat(Integer.parseInt(queryScalar(
                "SELECT COUNT(*)::text FROM pg_indexes WHERE tablename = 'hr_idempotency_records' " +
                        "AND indexdef LIKE '%expires_at%'")))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void idempotencyRecordsRejectRawSensitiveKeysInResponseMetadata() throws Exception {
        assertThat(queryScalar("SELECT to_regclass('public.hr_idempotency_records')::text"))
                .as("schema precondition: hr_idempotency_records must exist for this guard to be meaningful")
                .isEqualTo("hr_idempotency_records");
        assertThatThrownBy(() -> insertIdempotencyRecord(tenantId, principalId, "HRM.OP", "key-sec", repeat('a'),
                200, "{\"token\":\"RAW-TOKEN-VALUE\"}"))
                .as("raw token key must never be persisted in idempotency response metadata")
                .isInstanceOf(SQLException.class);
    }

    // ==================== HR IAM ACCESS BINDINGS ====================

    @Test
    void hrManagedLifecycleIsExplicitlyDistinguishable() throws Exception {
        UUID userId = UUID.randomUUID();
        executeUpdate("INSERT INTO hr_iam_access_bindings (tenant_id, person_id, user_id, access_mode) " +
                        "VALUES (?,?,?, 'HR_MANAGED')",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, UUID.randomUUID());
                    ps.setObject(3, userId);
                });

        // Unrelated IAM access must NOT default to HR-managed.
        UUID unmanagedUserId = UUID.randomUUID();
        executeUpdate("INSERT INTO hr_iam_access_bindings (tenant_id, person_id, user_id) VALUES (?,?,?)",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, UUID.randomUUID());
                    ps.setObject(3, unmanagedUserId);
                });
        assertThat(queryScalar("SELECT access_mode FROM hr_iam_access_bindings WHERE user_id = " + unmanagedUserId))
                .as("unrelated IAM access is not implicitly HR-managed")
                .isEqualTo("NON_HR_MANAGED");
        assertThat(queryScalar("SELECT access_mode FROM hr_iam_access_bindings WHERE user_id = " + userId))
                .isEqualTo("HR_MANAGED");

        // Unknown access modes are rejected.
        assertThatThrownBy(() -> executeUpdate(
                "INSERT INTO hr_iam_access_bindings (tenant_id, person_id, user_id, access_mode) VALUES (?,?,?, 'SOMETING_ELSE')",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, UUID.randomUUID());
                    ps.setObject(3, UUID.randomUUID());
                }))
                .isInstanceOf(SQLException.class);
    }

    // ==================== HELPERS ====================

    private void insertIdempotencyRecord(UUID ownerTenant, UUID principal, String operation, String key,
                                         String fingerprint, int responseStatus, String responseBody) throws Exception {
        executeUpdate("INSERT INTO hr_idempotency_records (tenant_id, principal_id, operation_code, idempotency_key, " +
                        "request_fingerprint, response_status, response_body) VALUES (?,?,?,?,?,?,?::jsonb)",
                ps -> {
                    ps.setObject(1, ownerTenant);
                    ps.setObject(2, principal);
                    ps.setString(3, operation);
                    ps.setString(4, key);
                    ps.setString(5, fingerprint);
                    ps.setInt(6, responseStatus);
                    ps.setString(7, responseBody);
                });
    }

    private void insertIdempotencyRecordWithExpiry(UUID ownerTenant, UUID principal, String operation, String key,
                                                   String fingerprint, java.time.OffsetDateTime expiresAt) throws Exception {
        executeUpdate("INSERT INTO hr_idempotency_records (tenant_id, principal_id, operation_code, idempotency_key, " +
                        "request_fingerprint, expires_at) VALUES (?,?,?,?,?,?)",
                ps -> {
                    ps.setObject(1, ownerTenant);
                    ps.setObject(2, principal);
                    ps.setString(3, operation);
                    ps.setString(4, key);
                    ps.setString(5, fingerprint);
                    ps.setObject(6, expiresAt);
                });
    }

    /**
     * The exact SQL primitive the future idempotency service will use:
     * an in-place replay when the fingerprint matches, and zero affected rows
     * (detectable conflict) when it does not.
     */
    private int conditionalUpsertIdempotency(UUID ownerTenant, UUID principal, String operation, String key,
                                             String fingerprint, int responseStatus) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_idempotency_records (tenant_id, principal_id, operation_code, idempotency_key, " +
                        "request_fingerprint, response_status) VALUES (?,?,?,?,?,?) " +
                        "ON CONFLICT (tenant_id, principal_id, operation_code, idempotency_key) DO UPDATE " +
                        "SET response_status = EXCLUDED.response_status " +
                        "WHERE hr_idempotency_records.request_fingerprint = EXCLUDED.request_fingerprint")) {
            ps.setObject(1, ownerTenant);
            ps.setObject(2, principal);
            ps.setString(3, operation);
            ps.setString(4, key);
            ps.setString(5, fingerprint);
            ps.setInt(6, responseStatus);
            return ps.executeUpdate();
        }
    }

    private String repeat(char c) {
        char[] chars = new char[64];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
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
}
