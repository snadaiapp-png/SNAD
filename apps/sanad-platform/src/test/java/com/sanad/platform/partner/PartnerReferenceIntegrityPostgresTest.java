package com.sanad.platform.partner;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-T2 Partner Referential-Integrity Closure — PostgreSQL Direct contract.
 */
class PartnerReferenceIntegrityPostgresTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "PartnerReferenceIntegrityPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for PartnerReferenceIntegrityPostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);

        Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    @Test
    void partnerForeignKeyExistsAndTargetsCanonicalPartnersTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT confrelid::regclass::text AS target_table, confdeltype " +
                     "FROM pg_constraint " +
                     "WHERE conrelid = 'user_permission_overrides'::regclass " +
                     "AND conname = 'fk_user_permission_overrides_partner' " +
                     "AND contype = 'f'");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("target_table")).isEqualTo("partners");
            assertThat(rs.getString("confdeltype")).isEqualTo("r");
        }
    }

    @Test
    void nullPartnerIdRemainsValidForDirectTenantOverride() throws Exception {
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            UUID tenantId = seedTenant(connection);
            setTenant(connection, tenantId);
            UUID capabilityId = firstCapability(connection);

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user_permission_overrides " +
                    "(id, tenant_id, partner_id, user_id, capability_id, effect, reason, created_by) " +
                    "VALUES (?, ?, NULL, ?, ?, 'ALLOW', 'w2-t2 direct tenant control', ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, capabilityId);
                ps.setObject(5, UUID.randomUUID());
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            connection.rollback();
        }
    }

    @Test
    void validCanonicalPartnerIdIsAccepted() throws Exception {
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            UUID tenantId = seedTenant(connection);
            setTenant(connection, tenantId);
            UUID partnerId = seedPartner(connection);
            UUID capabilityId = firstCapability(connection);

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user_permission_overrides " +
                    "(id, tenant_id, partner_id, user_id, capability_id, effect, reason, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, 'ALLOW', 'w2-t2 valid partner control', ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, partnerId);
                ps.setObject(4, UUID.randomUUID());
                ps.setObject(5, capabilityId);
                ps.setObject(6, UUID.randomUUID());
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            connection.rollback();
        }
    }

    @Test
    void unknownPartnerIdFailsClosedWithForeignKeyViolation() throws Exception {
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            UUID tenantId = seedTenant(connection);
            setTenant(connection, tenantId);
            UUID capabilityId = firstCapability(connection);

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO user_permission_overrides " +
                        "(id, tenant_id, partner_id, user_id, capability_id, effect, reason, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, 'ALLOW', 'w2-t2 orphan rejection', ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, UUID.randomUUID());
                    ps.setObject(4, UUID.randomUUID());
                    ps.setObject(5, capabilityId);
                    ps.setObject(6, UUID.randomUUID());
                    ps.executeUpdate();
                }
            }).isInstanceOfSatisfying(SQLException.class,
                    ex -> assertThat(ex.getSQLState()).isEqualTo("23503"));
            connection.rollback();
        }
    }

    @Test
    void migratedDatabaseContainsNoPartnerReferenceOrphans() throws Exception {
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM user_permission_overrides upo " +
                     "LEFT JOIN partners p ON p.id = upo.partner_id " +
                     "WHERE upo.partner_id IS NOT NULL AND p.id IS NULL")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    private static UUID seedTenant(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, id);
            ps.setString(2, "w2-t2-" + id.toString().substring(0, 8));
            ps.setString(3, "w2t2-" + id.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID seedPartner(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO partners (id, partner_type, status, created_by) " +
                "VALUES (?, 'PARTNER', 'ACTIVE', ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, UUID.randomUUID());
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID firstCapability(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id FROM access_capabilities ORDER BY code LIMIT 1")) {
            if (!rs.next()) {
                throw new SQLException("No access_capabilities seed available for W2-T2 test");
            }
            return (UUID) rs.getObject(1);
        }
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            try (ResultSet ignored = ps.executeQuery()) { }
        }
    }
}
