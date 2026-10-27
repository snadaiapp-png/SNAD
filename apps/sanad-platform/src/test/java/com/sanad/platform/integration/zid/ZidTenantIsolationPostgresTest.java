package com.sanad.platform.integration.zid;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL-Direct tenant-isolation matrix for the Zid integration
 * schema (mirrors ContactRlsPostgresTest conventions). Exercises the
 * REAL migration chain and the REAL FORCE RLS policies:
 *
 * <ul>
 *   <li>A1: same-tenant SELECT is visible</li>
 *   <li>A2: cross-tenant SELECT returns 0 rows</li>
 *   <li>A3: UNSET GUC returns 0 rows (fail closed)</li>
 *   <li>A4: WITH CHECK rejects writes with a foreign tenant_id</li>
 *   <li>B:  a Zid store bound to tenant A cannot be re-bound to
 *           tenant B (UNIQUE constraint)</li>
 * </ul>
 *
 * <p><b>Graceful skip:</b> the suite requires PostgreSQL Direct via
 * SPRING_DATASOURCE_URL (CI provides a disposable database). When no
 * PostgreSQL is reachable the tests are SKIPPED — never faked. The
 * connection is a local disposable database; the test never touches
 * production credentials or hosts (guarded below).</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZidTenantIsolationPostgresTest {

    private static final String JDBC_URL =
            System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                    "jdbc:postgresql://localhost:5432/sanad");
    private static final String USERNAME =
            System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD =
            System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static javax.sql.DataSource sharedDataSource;

    private static UUID tenantA;
    private static UUID tenantB;
    private static UUID connectionA;

    @BeforeAll
    static void requirePostgresAndMigrate() {
        boolean reachable = false;
        try (Connection ignored = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }
        Assumptions.assumeTrue(reachable,
                "PostgreSQL Direct not available — Zid RLS isolation tests SKIPPED (CI provides the DB)");

        String host = JDBC_URL;
        Assumptions.assumeTrue(
                !(host.contains("render.com") || host.contains("supabase.co")
                        || host.contains("amazonaws.com") || host.contains("azure.com")),
                "Refusing to run destructive migration/RLS matrix against a production-looking host");

        Flyway.configure()
                .dataSource(JDBC_URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(JDBC_URL, USERNAME, PASSWORD);
        sharedDataSource = dataSource;
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        tx = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        tenantA = ensureTenant("zid-iso-a");
        tenantB = ensureTenant("zid-iso-b");
    }

    private static UUID ensureTenant(String label) {
        UUID id = jdbc.queryForObject(
                "SELECT id FROM tenants WHERE subdomain = :sub",
                new MapSqlParameterSource("sub", label), UUID.class);
        if (id != null) {
            return id;
        }
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :sub, 'ACTIVE', NOW(), NOW())
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", "Zid ISO " + label)
                .addValue("sub", label));
        return id;
    }

    private static void inTenant(UUID tenantId, Runnable work) {
        tx.execute(status -> {
            setTenantGuc(tenantId);
            work.run();
            return null;
        });
    }

    private static void setTenantGuc(UUID tenantId) {
        JdbcTemplate plain = new JdbcTemplate(sharedDataSource);
        plain.update("SELECT set_config('app.tenant_id', ?, true)", tenantId.toString());
    }

    private static UUID insertConnection(UUID tenantId, String storeId) {
        UUID id = UUID.randomUUID();
        inTenant(tenantId, () -> jdbc.update("""
                INSERT INTO zid_store_connections
                    (id, tenant_id, zid_store_id, zid_store_name, status, created_at, updated_at)
                VALUES (:id, :tenantId, :storeId, 'store', 'CONNECTED', NOW(), NOW())
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("storeId", storeId)));
        return id;
    }

    @Test
    @Order(1)
    void a1_sameTenantRowsAreVisible() {
        connectionA = insertConnection(tenantA, "9001");
        Integer visible = inTenantWithResult(tenantA, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM zid_store_connections WHERE zid_store_id = '9001'",
                new MapSqlParameterSource(), Integer.class));
        assertThat(visible).isEqualTo(1);
    }

    @Test
    @Order(2)
    void a2_crossTenantRowsAreInvisible() {
        Integer visibleFromB = inTenantWithResult(tenantB, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM zid_store_connections WHERE zid_store_id = '9001'",
                new MapSqlParameterSource(), Integer.class));
        assertThat(visibleFromB).isZero();
    }

    @Test
    @Order(3)
    void a3_unsetGucFailsClosed() {
        Integer visible = tx.execute(status -> {
            // No GUC set — the fail-closed policy must yield zero rows.
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM zid_store_connections WHERE zid_store_id = '9001'",
                    new MapSqlParameterSource(), Integer.class);
        });
        assertThat(visible).isZero();
    }

    @Test
    @Order(4)
    void a4_withCheckRejectsForeignTenantWrite() {
        assertThatThrownBy(() -> tx.execute(status -> {
            setTenantGuc(tenantA);
            jdbc.update("""
                    INSERT INTO zid_store_connections
                        (id, tenant_id, zid_store_id, zid_store_name, status, created_at, updated_at)
                    VALUES (:id, :tenantId, :storeId, 'store', 'CONNECTED', NOW(), NOW())
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("tenantId", tenantB) // foreign tenant under tenant A's GUC
                    .addValue("storeId", "9002"));
            return null;
        })).isInstanceOf(Exception.class);
    }

    @Test
    @Order(5)
    void b_storeBoundToTenantACannotBeReboundToTenantB() {
        // The store 9001 belongs to tenant A's connection. A second connection
        // for the same store must be rejected by uk_zid_connections_store
        // regardless of which tenant attempts it.
        assertThatThrownBy(() -> inTenant(tenantB, () ->
                jdbc.update("""
                        INSERT INTO zid_store_connections
                            (id, tenant_id, zid_store_id, zid_store_name, status, created_at, updated_at)
                        VALUES (:id, :tenantId, :storeId, 'store', 'CONNECTED', NOW(), NOW())
                        """, new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", tenantB)
                        .addValue("storeId", "9001"))
        )).isInstanceOf(Exception.class);
    }

    @Test
    @Order(6)
    void c_syncDataRespectsTenantIsolation() {
        UUID productA = UUID.randomUUID();
        inTenant(tenantA, () -> jdbc.update("""
                INSERT INTO zid_products
                    (id, tenant_id, connection_id, zid_product_id, name_en, raw_payload,
                     created_at, updated_at)
                VALUES (:id, :tenantId, :cid, 'prod-1', 'Widget', '{}'::jsonb, NOW(), NOW())
                """, new MapSqlParameterSource()
                .addValue("id", productA)
                .addValue("tenantId", tenantA)
                .addValue("cid", connectionA)));
        Integer fromB = inTenantWithResult(tenantB, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM zid_products WHERE zid_product_id = 'prod-1'",
                new MapSqlParameterSource(), Integer.class));
        assertThat(fromB).isZero();
        Integer fromA = inTenantWithResult(tenantA, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM zid_products WHERE zid_product_id = 'prod-1'",
                new MapSqlParameterSource(), Integer.class));
        assertThat(fromA).isEqualTo(1);
    }

    @Test
    @Order(7)
    void d_rlsIsForcedOnEveryZidTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity
                     FROM pg_class c
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE n.nspname = 'public' AND c.relname LIKE 'zid_%' AND c.relkind = 'r'
                     """);
             ResultSet rs = ps.executeQuery()) {
            int checked = 0;
            while (rs.next()) {
                checked++;
                assertThat(rs.getBoolean("relrowsecurity")).as(rs.getString("relname")).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity")).as(rs.getString("relname")).isTrue();
            }
            assertThat(checked).isGreaterThanOrEqualTo(9);
        }
    }

    private static <T> T inTenantWithResult(UUID tenantId, java.util.function.Supplier<T> work) {
        return tx.execute(status -> {
            setTenantGuc(tenantId);
            return work.get();
        });
    }
}
