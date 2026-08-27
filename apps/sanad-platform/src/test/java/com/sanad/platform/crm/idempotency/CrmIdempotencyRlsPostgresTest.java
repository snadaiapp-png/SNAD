package com.sanad.platform.crm.idempotency;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G6 R1 RED contract for crm_idempotency_records tenant isolation.
 *
 * <p>The canonical migration chain must make this table fail closed under
 * FORCE ROW LEVEL SECURITY. This test intentionally describes the desired
 * security contract before the remediation migration exists.</p>
 */
class CrmIdempotencyRlsPostgresTest {

    private static final UUID TENANT_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID PRINCIPAL = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final String POLICY_NAME = "crm_idempotency_records_tenant_isolation";
    private static final String FINGERPRINT = "0".repeat(64);

    private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "CrmIdempotencyRlsPostgresTest");
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for CrmIdempotencyRlsPostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(
                datasourceUrl(), datasourceUsername(), datasourcePassword());
    }

    @BeforeEach
    void migrateFreshIsolatedDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl(), datasourceUsername(), datasourcePassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        jdbc = jdbc();
    }

    @Test
    void a_rlsIsEnabledAndForced() {
        Boolean enabled = jdbc.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE relname = 'crm_idempotency_records'",
                Boolean.class);
        Boolean forced = jdbc.queryForObject(
                "SELECT relforcerowsecurity FROM pg_class WHERE relname = 'crm_idempotency_records'",
                Boolean.class);

        assertThat(enabled)
                .as("RLS must be ENABLED on crm_idempotency_records")
                .isTrue();
        assertThat(forced)
                .as("FORCE RLS must protect even the table owner")
                .isTrue();
    }

    @Test
    void b_strictFailClosedTenantPolicyExists() {
        Long policyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_policies "
                        + "WHERE tablename = 'crm_idempotency_records' AND policyname = ? "
                        + "AND qual IS NOT NULL AND with_check IS NOT NULL "
                        + "AND qual LIKE '%app.tenant_id%' AND with_check LIKE '%app.tenant_id%' "
                        + "AND qual NOT LIKE '%IS NULL%' AND with_check NOT LIKE '%IS NULL%'",
                Long.class,
                POLICY_NAME);

        assertThat(policyCount)
                .as("crm_idempotency_records must have one strict fail-closed tenant policy")
                .isEqualTo(1L);

        Long legacyPermissive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_policies "
                        + "WHERE tablename = 'crm_idempotency_records' "
                        + "AND ((qual LIKE '%IS NULL%') OR (with_check LIKE '%IS NULL%'))",
                Long.class);
        assertThat(legacyPermissive)
                .as("No permissive-when-GUC-missing policy may remain")
                .isZero();
    }

    @Test
    void c_sameTenantInsertSucceeds() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            setTenant(conn, TENANT_A);
            insertRecord(conn, id, TENANT_A, "same-tenant");
            conn.commit();
        }

        assertThat(countRecordAsTenant(id, TENANT_A)).isEqualTo(1L);
    }

    @Test
    void d_crossTenantInsertIsDenied() throws SQLException {
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            setTenant(conn, TENANT_A);
            assertThatThrownBy(() -> insertRecord(
                    conn, UUID.randomUUID(), TENANT_B, "cross-insert"))
                    .isInstanceOf(SQLException.class);
            conn.rollback();
        }
    }

    @Test
    void e_missingTenantGucInsertIsDenied() throws SQLException {
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            assertThatThrownBy(() -> insertRecord(
                    conn, UUID.randomUUID(), TENANT_A, "missing-guc"))
                    .isInstanceOf(SQLException.class);
            conn.rollback();
        }
    }

    @Test
    void f_crossTenantReadReturnsZeroRows() throws SQLException {
        UUID id = seedRecord(TENANT_B, "cross-read");
        assertThat(countRecordAsTenant(id, TENANT_A))
                .as("Tenant A must not see Tenant B idempotency records")
                .isZero();
    }

    @Test
    void g_crossTenantUpdateAffectsZeroRows() throws SQLException {
        UUID id = seedRecord(TENANT_B, "cross-update");
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            setTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE crm_idempotency_records SET response_status = 201 WHERE id = ?")) {
                ps.setObject(1, id);
                assertThat(ps.executeUpdate())
                        .as("Tenant A must not update Tenant B idempotency records")
                        .isZero();
            }
            conn.commit();
        }
    }

    @Test
    void h_crossTenantDeleteAffectsZeroRows() throws SQLException {
        UUID id = seedRecord(TENANT_B, "cross-delete");
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            setTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM crm_idempotency_records WHERE id = ?")) {
                ps.setObject(1, id);
                assertThat(ps.executeUpdate())
                        .as("Tenant A must not delete Tenant B idempotency records")
                        .isZero();
            }
            conn.commit();
        }
    }

    @Test
    void i_applicationRoleIsLeastPrivilege() {
        Boolean superuser = jdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user",
                Boolean.class);
        Boolean bypassRls = jdbc.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user",
                Boolean.class);
        Boolean createRole = jdbc.queryForObject(
                "SELECT rolcreaterole FROM pg_roles WHERE rolname = current_user",
                Boolean.class);

        assertThat(superuser).as("application test role must not be SUPERUSER").isFalse();
        assertThat(bypassRls).as("application test role must not BYPASSRLS").isFalse();
        assertThat(createRole).as("application test role must not CREATEROLE").isFalse();
    }

    private UUID seedRecord(UUID tenantId, String key) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            setTenant(conn, tenantId);
            insertRecord(conn, id, tenantId, key);
            conn.commit();
        }
        return id;
    }

    private long countRecordAsTenant(UUID id, UUID tenantId) throws SQLException {
        try (Connection conn = ownerConnection()) {
            conn.setAutoCommit(false);
            setTenant(conn, tenantId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM crm_idempotency_records WHERE id = ?")) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long count = rs.getLong(1);
                    conn.commit();
                    return count;
                }
            }
        }
    }

    private static void insertRecord(
            Connection conn,
            UUID id,
            UUID tenantId,
            String key
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO crm_idempotency_records "
                        + "(id, tenant_id, principal_id, endpoint, idempotency_key, "
                        + "request_fingerprint_sha256, response_status, created_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour')")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, PRINCIPAL);
            ps.setString(4, "/api/v1/crm/r1-rls-test");
            ps.setString(5, key);
            ps.setString(6, FINGERPRINT);
            ps.executeUpdate();
        }
    }

    private static void setTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery().close();
        }
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(isolatedUrl(), datasourceUsername(), datasourcePassword());
    }

    private static JdbcTemplate jdbc() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                isolatedUrl(), datasourceUsername(), datasourcePassword());
        ds.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(ds);
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(datasourceUrl());
    }

    private static String datasourceUrl() {
        return System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    }

    private static String datasourceUsername() {
        return System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    }

    private static String datasourcePassword() {
        return System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
    }
}
