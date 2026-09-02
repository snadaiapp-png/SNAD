package com.sanad.platform.hrmfoundation.platform;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalEntityOrganizationEligibilityIntegrationTest {
    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSql() {
        boolean ok = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) { ok = c.isValid(5); }
        } catch (Throwable ignored) {}
        Assumptions.assumeTrue(ok, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void setup() throws Exception {
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
                .baselineOnMigrate(true).cleanDisabled(false).validateOnMigrate(false).load();
        flyway.clean();
        flyway.migrate();
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (var s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, 'Test Tenant', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedOrg(UUID tenantId) throws Exception {
        UUID orgId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) VALUES (?, ?, 'Test Org', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, orgId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
        return orgId;
    }

    private UUID seedLE(UUID tenantId) throws Exception {
        UUID leId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, leId);
            ps.setObject(2, tenantId);
            ps.setString(3, "LE-" + leId.toString().substring(0, 8));
            ps.setString(4, "Test LE");
            ps.executeUpdate();
        }
        return leId;
    }

    private void insertElig(UUID tenantId, UUID orgId, UUID leId, String from, String to, String status) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organization_legal_entities (id, tenant_id, organization_id, legal_entity_id, effective_from, effective_to, status, created_at) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, orgId);
            ps.setObject(3, leId);
            ps.setDate(4, java.sql.Date.valueOf(from));
            if (to != null) ps.setDate(5, java.sql.Date.valueOf(to)); else ps.setNull(5, java.sql.Types.DATE);
            ps.setString(6, status);
            ps.executeUpdate();
        }
    }

    @Test
    void overlappingActiveIntervalIsRejectedByDatabase() throws Exception {
        UUID tid = UUID.randomUUID();
        seedTenant(tid);
        setTenant(tid);
        UUID orgId = seedOrg(tid);
        UUID leId = seedLE(tid);
        insertElig(tid, orgId, leId, "2026-01-01", null, "ACTIVE");
        assertThatThrownBy(() -> insertElig(tid, orgId, leId, "2026-06-01", null, "ACTIVE"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void overlappingClosedIntervalIsRejectedByDatabase() throws Exception {
        UUID tid = UUID.randomUUID();
        seedTenant(tid);
        setTenant(tid);
        UUID orgId = seedOrg(tid);
        UUID leId = seedLE(tid);
        insertElig(tid, orgId, leId, "2026-01-01", "2026-06-30", "ACTIVE");
        assertThatThrownBy(() -> insertElig(tid, orgId, leId, "2026-06-01", null, "ACTIVE"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void nonOverlappingIntervalIsAccepted() throws Exception {
        UUID tid = UUID.randomUUID();
        seedTenant(tid);
        setTenant(tid);
        UUID orgId = seedOrg(tid);
        UUID leId = seedLE(tid);
        insertElig(tid, orgId, leId, "2026-01-01", "2026-05-31", "ACTIVE");
        insertElig(tid, orgId, leId, "2026-06-01", null, "ACTIVE");
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM organization_legal_entities WHERE organization_id = ? AND legal_entity_id = ?")) {
            ps.setObject(1, orgId);
            ps.setObject(2, leId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void inactiveIntervalDoesNotBlockNewActiveInterval() throws Exception {
        UUID tid = UUID.randomUUID();
        seedTenant(tid);
        setTenant(tid);
        UUID orgId = seedOrg(tid);
        UUID leId = seedLE(tid);
        insertElig(tid, orgId, leId, "2026-01-01", null, "INACTIVE");
        insertElig(tid, orgId, leId, "2026-06-01", null, "ACTIVE");
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM organization_legal_entities WHERE organization_id = ? AND status = 'ACTIVE'")) {
            ps.setObject(1, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }
}
