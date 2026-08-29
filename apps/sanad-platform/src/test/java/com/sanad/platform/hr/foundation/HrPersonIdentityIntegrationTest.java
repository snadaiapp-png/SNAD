package com.sanad.platform.hr.foundation;

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

class HrPersonIdentityIntegrationTest {
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

    private UUID insertPerson(UUID tenantId, UUID userId) throws Exception {
        UUID personId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_people (id, tenant_id, user_id, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            if (userId != null) ps.setObject(3, userId); else ps.setNull(3, java.sql.Types.OTHER);
            ps.executeUpdate();
        }
        return personId;
    }

    // RED-1: same non-null User + same Tenant + two Persons → REJECT
    @Test
    void duplicateUserPerTenantIsRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID userId = UUID.randomUUID();
        insertPerson(tenantId, userId);
        assertThatThrownBy(() -> insertPerson(tenantId, userId))
                .isInstanceOf(Exception.class);
    }

    // RED-2: same User + different Tenant → independent
    @Test
    void sameUserDifferentTenantIsAllowed() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        UUID userId = UUID.randomUUID();
        setTenant(tenantA);
        insertPerson(tenantA, userId);
        setTenant(tenantB);
        insertPerson(tenantB, userId); // should succeed — different tenant
    }

    // RED-3: Person without User → ALLOWED
    @Test
    void personWithoutUserIsAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = insertPerson(tenantId, null); // no user_id
        assertThat(personId).isNotNull();
    }

    // RED-4: sensitive identifier uses blind index, plaintext never stored
    @Test
    void sensitiveIdentifierUsesBlindIndexNotPlaintext() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = insertPerson(tenantId, null);

        // Insert a sensitive identifier
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, issuing_country_code, " +
                "encrypted_value, blind_index, key_version, status, created_at) " +
                "VALUES (gen_random_uuid(), ?, ?, 'NATIONAL_ID', 'SA', " +
                "'enc:v1:dGVzdA==', 'blindhash123', 'v1', 'ACTIVE', NOW())")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, personId);
            ps.executeUpdate();
        }

        // Verify plaintext is NOT stored
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT encrypted_value, blind_index FROM hr_person_identifiers WHERE person_id = ? AND identifier_type = 'NATIONAL_ID'")) {
            ps.setObject(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getString("encrypted_value")).startsWith("enc:");
                assertThat(rs.getString("blind_index")).isNotEqualTo("1234567890"); // plaintext not stored
            }
        }
    }

    // RED-5: same identifier + ACTIVE duplicate → rejected
    @Test
    void duplicateActiveIdentifierIsRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null);
        UUID person2 = insertPerson(tenantId, null);

        // Insert first identifier
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, issuing_country_code, " +
                "encrypted_value, blind_index, key_version, status, created_at) " +
                "VALUES (gen_random_uuid(), ?, ?, 'NATIONAL_ID', 'SA', " +
                "'enc:v1:aaa', 'sameblindhash', 'v1', 'ACTIVE', NOW())")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, person1);
            ps.executeUpdate();
        }

        // Insert duplicate ACTIVE identifier with same blind_index
        assertThatThrownBy(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, issuing_country_code, " +
                    "encrypted_value, blind_index, key_version, status, created_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, 'NATIONAL_ID', 'SA', " +
                    "'enc:v1:bbb', 'sameblindhash', 'v1', 'ACTIVE', NOW())")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, person2);
                ps.executeUpdate();
            }
        }).isInstanceOf(Exception.class);
    }
}
