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
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WS2 Task 1A RED — Person Identity Schema.
 *
 * <p>Schema-only RED. No service/crypto tests here. Those belong to Task 1B.</p>
 *
 * <p>Platform identity model: users.tenant_id is NOT NULL with FK to tenants(id).
 * User identity is tenant-scoped. Cross-tenant user reference must be blocked
 * by composite FK or trigger that enforces tenant congruence.</p>
 *
 * <p>Expected RED: all tests fail because hr_people / hr_person_private /
 * hr_person_identifiers tables do not exist.</p>
 */
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

    // --- Fixture helpers ---

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, 'Test Tenant', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedUser(UUID tenantId, String email) throws Exception {
        UUID userId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, 'Test User', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, email);
            ps.executeUpdate();
        }
        return userId;
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (var s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    private UUID insertPerson(UUID tenantId, UUID userId, String firstName, String lastName) throws Exception {
        UUID personId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_people (id, tenant_id, user_id, first_name, middle_name, last_name, display_name, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NULL, ?, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            if (userId != null) ps.setObject(3, userId); else ps.setNull(3, java.sql.Types.OTHER);
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            ps.setString(6, firstName + " " + lastName);
            ps.executeUpdate();
        }
        return personId;
    }

    private void insertIdentifier(UUID tenantId, UUID personId, String idType,
            String issuingCountry, String ciphertext, String blindIndex, String encKeyVer,
            String blindKeyVer, String status) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, issuing_country_code, " +
                "identifier_ciphertext, blind_index, encryption_key_version, blind_index_key_version, status, created_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, personId);
            ps.setString(3, idType);
            if (issuingCountry != null) ps.setString(4, issuingCountry); else ps.setNull(4, java.sql.Types.VARCHAR);
            ps.setString(5, ciphertext);
            ps.setString(6, blindIndex);
            ps.setString(7, encKeyVer);
            ps.setString(8, blindKeyVer);
            ps.setString(9, status);
            ps.executeUpdate();
        }
    }

    // --- PERSON-01: duplicate user per tenant rejected (SQLSTATE 23505 = unique_violation) ---
    @Test
    void person01_duplicateUserPerTenantRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID userId = seedUser(tenantId, "user01@snad.test");
        setTenant(tenantId);

        insertPerson(tenantId, userId, "Alice", "Smith");
        // Second person with same user_id in same tenant must violate unique constraint
        assertThatThrownBy(() -> insertPerson(tenantId, userId, "Alice2", "Smith2"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("hr_people");
    }

    // --- PERSON-02: person without user_id (NULL) → ALLOWED ---
    @Test
    void person02_personWithoutUserIsAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);

        UUID personId = insertPerson(tenantId, null, "No", "User");
        assertThat(personId).isNotNull();

        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM hr_people WHERE id = ?")) {
            ps.setObject(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    // --- PERSON-03: cross-tenant user congruence enforced ---
    // User from Tenant A cannot be linked to Person in Tenant B.
    // Must be blocked specifically because tenant mismatch, not because user doesn't exist.
    @Test
    void person03_crossTenantUserCongruenceEnforced() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        // Create a valid user in Tenant A
        UUID userIdA = seedUser(tenantA, "cross@snad.test");
        setTenant(tenantB);

        // Try to create a Person in Tenant B with user from Tenant A.
        // The user EXISTS (so FK to users(id) would pass),
        // but tenant congruence (hr_people.tenant_id != users.tenant_id) must be rejected.
        assertThatThrownBy(() -> insertPerson(tenantB, userIdA, "Cross", "Tenant"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("hr_people");
    }

    // --- PERSON-04: identifier unique per (tenant, type, issuer, blind_index, ACTIVE) ---
    @Test
    void person04_duplicateActiveIdentifierRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null, "Dup", "One");
        UUID person2 = insertPerson(tenantId, null, "Dup", "Two");

        insertIdentifier(tenantId, person1, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "blindhashX", "v1", "v1", "ACTIVE");

        // Duplicate ACTIVE identifier with same blind_index for different person → REJECT
        assertThatThrownBy(() -> insertIdentifier(tenantId, person2, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdB==", "blindhashX", "v1", "v1", "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("hr_person_identifiers");
    }

    // --- PERSON-05: NULL issuing_country duplicate must NOT bypass uniqueness ---
    @Test
    void person05_nullIssuerDuplicateRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null, "Null", "Issuer1");
        UUID person2 = insertPerson(tenantId, null, "Null", "Issuer2");

        insertIdentifier(tenantId, person1, "PASSPORT", null,
                "enc:v1:dGVzdA==", "nullblindX", "v1", "v1", "ACTIVE");

        // Duplicate with NULL issuing_country — must be rejected (NULLS NOT DISTINCT or equivalent)
        assertThatThrownBy(() -> insertIdentifier(tenantId, person2, "PASSPORT", null,
                "enc:v1:dGVzdB==", "nullblindX", "v1", "v1", "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("hr_person_identifiers");
    }

    // --- PERSON-06: different identifier_type is independent ---
    @Test
    void person06_differentIdentifierTypeIsIndependent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null, "Type", "One");
        UUID person2 = insertPerson(tenantId, null, "Type", "Two");

        // Same blind_index but different identifier_type → should be ALLOWED
        insertIdentifier(tenantId, person1, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "sameblind", "v1", "v1", "ACTIVE");
        insertIdentifier(tenantId, person2, "PASSPORT", "SA",
                "enc:v1:dGVzdB==", "sameblind", "v1", "v1", "ACTIVE");
        // Both should succeed — no exception
    }

    // --- PERSON-07: different issuing_country is independent ---
    @Test
    void person07_differentIssuingCountryIsIndependent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null, "Country", "One");
        UUID person2 = insertPerson(tenantId, null, "Country", "Two");

        // Same blind_index but different issuing_country → should be ALLOWED
        insertIdentifier(tenantId, person1, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "sameblind", "v1", "v1", "ACTIVE");
        insertIdentifier(tenantId, person2, "NATIONAL_ID", "AE",
                "enc:v1:dGVzdB==", "sameblind", "v1", "v1", "ACTIVE");
        // Both should succeed — no exception
    }

    // --- RLS: runtime role is NOT superuser ---
    @Test
    void rls_runtimeRoleIsNotSuperuser() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean("rolsuper"))
                        .as("RLS tests must run as non-superuser role")
                        .isFalse();
            }
        }
    }

    // --- RLS: runtime role does NOT have BYPASSRLS ---
    @Test
    void rls_runtimeRoleDoesNotHaveBypassrls() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean("rolbypassrls"))
                        .as("RLS tests must run as role without BYPASSRLS")
                        .isFalse();
            }
        }
    }
}
