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
import java.sql.Statement;
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
 * by composite FK that enforces tenant congruence at the DB boundary.</p>
 *
 * <p>Expected RED: all schema/RLS behavior tests fail because hr_people /
 * hr_person_private / hr_person_identifiers tables do not exist yet.
 * Runtime-role assertion tests (rolsuper / rolbypassrls) PASS regardless of
 * schema state because they only query pg_roles.</p>
 *
 * <p>Constraint assertions are root-cause specific via SQLSTATE:
 * <ul>
 *   <li>unique_violation → SQLSTATE 23505</li>
 *   <li>foreign_key_violation → SQLSTATE 23503</li>
 * </ul>
 * Message-contains-table-name assertions are NOT used because RLS/FK/CHECK
 * /unique errors can all mention the same table.</p>
 *
 * <p>RLS behavior tests use a SEPARATE runtime-role connection
 * ({@link #runtimeConn}) distinct from the migration/admin connection
 * ({@link #conn}). Both connections use the same least-privilege role
 * (sanad — NOSUPERUSER, NOBYPASSRLS, NOCREATEDB). The separation is at the
 * Connection level so tenant context can be controlled independently per
 * connection without polluting the admin connection's session state.</p>
 */
class HrPersonIdentityIntegrationTest {

    /** Migration/admin connection — used for fixture setup (seedTenant, seedUser, insertPerson, insertPrivate, insertIdentifier). */
    private Connection conn;
    /** Runtime-role connection — used for RLS behavior tests. Separate from {@link #conn} so tenant context can be controlled independently. */
    private Connection runtimeConn;
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
        // Migration/admin connection — for fixture setup.
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);
        // Runtime-role connection — separate Connection for RLS behavior tests.
        // Same role (sanad) but separate session so SET app.tenant_id on runtimeConn
        // does not pollute conn's session state.
        runtimeConn = dataSource.getConnection();
        runtimeConn.setAutoCommit(true);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (runtimeConn != null && !runtimeConn.isClosed()) runtimeConn.close();
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // --- Fixture helpers (use `conn` admin connection) ---

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

    /** Set app.tenant_id on the admin connection. */
    private void setTenant(UUID tenantId) throws Exception {
        setTenant(conn, tenantId);
    }

    /** Set app.tenant_id on a specific connection (typically runtimeConn). */
    private void setTenant(Connection c, UUID tenantId) throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    /** Reset app.tenant_id on a specific connection (returns to NULL = fail-closed). */
    private void resetTenant(Connection c) throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute("RESET app.tenant_id");
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

    /** Insert a Person via a specific connection (used by RLS behavior tests on runtimeConn). */
    private void insertPerson(Connection c, UUID tenantId, UUID personId, UUID userId, String firstName, String lastName) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
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

    /** Insert a private row (admin connection). */
    private void insertPrivate(UUID personId, UUID tenantId, String dateOfBirth,
            String nationalityCountryCode, String maritalStatus) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_person_private (person_id, tenant_id, date_of_birth, nationality_country_code, marital_status, version, updated_at) " +
                "VALUES (?, ?, ?::date, ?, ?, 0, NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            ps.setString(3, dateOfBirth);
            if (nationalityCountryCode != null) ps.setString(4, nationalityCountryCode); else ps.setNull(4, java.sql.Types.VARCHAR);
            if (maritalStatus != null) ps.setString(5, maritalStatus); else ps.setNull(5, java.sql.Types.VARCHAR);
            ps.executeUpdate();
        }
    }

    // --- SQLSTATE Condition helper ---

    /**
     * AssertJ Condition that asserts the thrown Throwable is a SQLException
     * with the specified SQLSTATE. This is more precise than message-contains
     * assertions because RLS/FK/CHECK/unique errors can all mention the same
     * table name in their message text.
     */
    private static org.assertj.core.api.Condition<Throwable> sqlState(String code) {
        return new org.assertj.core.api.Condition<Throwable>(
                t -> t instanceof SQLException && code.equals(((SQLException) t).getSQLState()),
                "SQLSTATE %s", code);
    }

    // ==================== PERSON-01..07: schema constraint tests ====================

    // --- PERSON-01: duplicate user per tenant rejected (SQLSTATE 23505 = unique_violation) ---
    @Test
    void person01_duplicateUserPerTenantRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID userId = seedUser(tenantId, "user01@snad.test");
        setTenant(tenantId);

        insertPerson(tenantId, userId, "Alice", "Smith");
        // Second person with same user_id in same tenant must violate unique constraint.
        // SQLSTATE 23505 = unique_violation (root-cause specific, not message-based).
        assertThatThrownBy(() -> insertPerson(tenantId, userId, "Alice2", "Smith2"))
                .isInstanceOf(SQLException.class)
                .has(sqlState("23505"));
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

    // --- PERSON-03: cross-tenant user congruence enforced (SQLSTATE 23503 = foreign_key_violation) ---
    // User from Tenant A cannot be linked to Person in Tenant B.
    // Blocked by composite FK (tenant_id, user_id) REFERENCES users(tenant_id, id).
    // The user EXISTS (so a simple FK to users(id) would pass), but tenant
    // congruence (hr_people.tenant_id != users.tenant_id) must be rejected.
    @Test
    void person03_crossTenantUserCongruenceEnforced() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        // Create a valid user in Tenant A.
        UUID userIdA = seedUser(tenantA, "cross@snad.test");
        setTenant(tenantB);

        // Try to create a Person in Tenant B with user from Tenant A.
        // The user EXISTS (so FK to users(id) would pass), but composite FK
        // (tenant_id, user_id) REFERENCES users(tenant_id, id) rejects because
        // (tenantB, userIdA) does not exist in users.
        // SQLSTATE 23503 = foreign_key_violation (root-cause specific).
        assertThatThrownBy(() -> insertPerson(tenantB, userIdA, "Cross", "Tenant"))
                .isInstanceOf(SQLException.class)
                .has(sqlState("23503"));
    }

    // --- PERSON-04: identifier unique per (tenant, type, issuer, blind_index, ACTIVE) ---
    // SQLSTATE 23505 = unique_violation on partial unique index
    // (tenant_id, identifier_type, issuing_country_code, blind_index) WHERE status = 'ACTIVE'
    // with NULLS NOT DISTINCT.
    @Test
    void person04_duplicateActiveIdentifierRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null, "Dup", "One");
        UUID person2 = insertPerson(tenantId, null, "Dup", "Two");

        insertIdentifier(tenantId, person1, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "blindhashX", "v1", "v1", "ACTIVE");

        // Duplicate ACTIVE identifier with same blind_index for different person → REJECT.
        assertThatThrownBy(() -> insertIdentifier(tenantId, person2, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdB==", "blindhashX", "v1", "v1", "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .has(sqlState("23505"));
    }

    // --- PERSON-05: NULL issuing_country duplicate must NOT bypass uniqueness ---
    // Requires NULLS NOT DISTINCT on the partial unique index so two NULL
    // issuing_country values are treated as equal for uniqueness purposes.
    @Test
    void person05_nullIssuerDuplicateRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null, "Null", "Issuer1");
        UUID person2 = insertPerson(tenantId, null, "Null", "Issuer2");

        insertIdentifier(tenantId, person1, "PASSPORT", null,
                "enc:v1:dGVzdA==", "nullblindX", "v1", "v1", "ACTIVE");

        // Duplicate with NULL issuing_country — must be rejected (NULLS NOT DISTINCT).
        assertThatThrownBy(() -> insertIdentifier(tenantId, person2, "PASSPORT", null,
                "enc:v1:dGVzdB==", "nullblindX", "v1", "v1", "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .has(sqlState("23505"));
    }

    // --- PERSON-06: different identifier_type is independent ---
    @Test
    void person06_differentIdentifierTypeIsIndependent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null, "Type", "One");
        UUID person2 = insertPerson(tenantId, null, "Type", "Two");

        // Same blind_index but different identifier_type → should be ALLOWED.
        insertIdentifier(tenantId, person1, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "sameblind", "v1", "v1", "ACTIVE");
        insertIdentifier(tenantId, person2, "PASSPORT", "SA",
                "enc:v1:dGVzdB==", "sameblind", "v1", "v1", "ACTIVE");
        // Both should succeed — no exception.
    }

    // --- PERSON-07: different issuing_country is independent ---
    @Test
    void person07_differentIssuingCountryIsIndependent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID person1 = insertPerson(tenantId, null, "Country", "One");
        UUID person2 = insertPerson(tenantId, null, "Country", "Two");

        // Same blind_index but different issuing_country → should be ALLOWED.
        insertIdentifier(tenantId, person1, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "sameblind", "v1", "v1", "ACTIVE");
        insertIdentifier(tenantId, person2, "NATIONAL_ID", "AE",
                "enc:v1:dGVzdB==", "sameblind", "v1", "v1", "ACTIVE");
        // Both should succeed — no exception.
    }

    // ==================== PRIVATE-01..05: hr_person_private schema tests ====================

    // --- PRIVATE-01: hr_person_private table exists with required columns ---
    @Test
    void private01_privateTableHasRequiredColumns() throws Exception {
        // Query information_schema for column presence.
        // Expected columns: person_id, tenant_id, date_of_birth,
        // nationality_country_code, marital_status, version, updated_at.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'hr_person_private' ORDER BY ordinal_position")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Set<String> cols = new java.util.HashSet<>();
                while (rs.next()) {
                    cols.add(rs.getString("column_name"));
                }
                assertThat(cols).as("hr_person_private must define all required columns")
                        .contains("person_id", "tenant_id", "date_of_birth",
                                "nationality_country_code", "marital_status",
                                "version", "updated_at");
            }
        }
    }

    // --- PRIVATE-02: person_id FK to hr_people(id) (SQLSTATE 23503) ---
    @Test
    void private02_personIdFkToHrPeople() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        // Insert a private row pointing to a non-existent person_id.
        UUID fakePersonId = UUID.randomUUID();
        // SQLSTATE 23503 = foreign_key_violation (person_id FK to hr_people.id).
        assertThatThrownBy(() -> insertPrivate(fakePersonId, tenantId,
                "1990-01-01", "SA", "SINGLE"))
                .isInstanceOf(SQLException.class)
                .has(sqlState("23503"));
    }

    // --- PRIVATE-03: tenant_id FK to tenants(id) (SQLSTATE 23503) ---
    // Test design: the row's tenant_id must MATCH the session's app.tenant_id
    // (otherwise RLS WITH CHECK fires first — SQLSTATE 42501 — masking the FK).
    // We set app.tenant_id = fakeTenantId (so RLS WITH CHECK passes) and verify
    // the FK to tenants(id) then rejects because fakeTenantId doesn't exist.
    @Test
    void private03_tenantIdFkToTenants() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = insertPerson(tenantId, null, "Private", "Tenant");

        // Insert a private row with valid person_id but a non-existent tenant_id.
        // IMPORTANT: set app.tenant_id to MATCH the row's intended tenant_id so
        // RLS WITH CHECK passes and lets the FK to tenants(id) be evaluated.
        // Otherwise RLS WITH CHECK fires first (42501) and masks the FK violation.
        UUID fakeTenantId = UUID.randomUUID();
        setTenant(fakeTenantId);
        assertThatThrownBy(() -> insertPrivate(personId, fakeTenantId,
                "1990-01-01", "SA", "SINGLE"))
                .isInstanceOf(SQLException.class)
                .has(sqlState("23503"));
    }

    // --- PRIVATE-04: nationality_country_code FK to platform_countries(country_code) (SQLSTATE 23503) ---
    @Test
    void private04_nationalityCountryFk() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = insertPerson(tenantId, null, "Private", "Country");

        // Insert a private row with a non-existent country code (ZZ is not seeded).
        assertThatThrownBy(() -> insertPrivate(personId, tenantId,
                "1990-01-01", "ZZ", "SINGLE"))
                .isInstanceOf(SQLException.class)
                .has(sqlState("23503"));
    }

    // --- PRIVATE-05: cross-tenant private tenant congruence ---
    // Person in Tenant A cannot have a private row in Tenant B.
    // Enforced by composite FK (tenant_id, person_id) REFERENCES hr_people(tenant_id, id)
    // — requires UNIQUE (tenant_id, id) constraint on hr_people to permit composite FK.
    //
    // Test design: set app.tenant_id = tenantB (so RLS WITH CHECK passes for the
    // row with tenant_id = tenantB). The person_in_A exists (so a simple FK to
    // hr_people(id) would pass), but the composite FK (tenant_id, person_id) →
    // hr_people(tenant_id, id) rejects because (tenantB, person_in_A) does not
    // exist in hr_people. SQLSTATE 23503 = foreign_key_violation.
    @Test
    void private05_crossTenantPrivateTenantCongruence() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID personInA = insertPerson(tenantA, null, "In", "TenantA");

        // Try to insert a private row in Tenant B pointing to person in Tenant A.
        // IMPORTANT: set app.tenant_id = tenantB (NOT tenantA) so the row's
        // tenant_id (tenantB) matches the session's app.tenant_id. RLS WITH CHECK
        // passes; the composite FK (tenant_id, person_id) → hr_people(tenant_id, id)
        // then rejects because (tenantB, personInA) does not exist in hr_people.
        setTenant(tenantB);
        assertThatThrownBy(() -> insertPrivate(personInA, tenantB,
                "1990-01-01", "SA", "SINGLE"))
                .isInstanceOf(SQLException.class)
                .has(sqlState("23503"));
    }

    // ==================== RLS runtime-role assertions ====================

    // --- RLS precondition: runtime role is NOT superuser ---
    @Test
    void rls_runtimeRoleIsNotSuperuser() throws Exception {
        try (PreparedStatement ps = runtimeConn.prepareStatement(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean("rolsuper"))
                        .as("RLS tests must run as non-superuser role")
                        .isFalse();
            }
        }
    }

    // --- RLS precondition: runtime role does NOT have BYPASSRLS ---
    @Test
    void rls_runtimeRoleDoesNotHaveBypassrls() throws Exception {
        try (PreparedStatement ps = runtimeConn.prepareStatement(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean("rolbypassrls"))
                        .as("RLS tests must run as role without BYPASSRLS")
                        .isFalse();
            }
        }
    }

    // ==================== RLS behavior: hr_people ====================

    // --- RLS hr_people NO TENANT CONTEXT: SELECT returns 0 rows + INSERT rejected ---
    @Test
    void rls_hrPeople_noTenantContext_failsClosed() throws Exception {
        // Seed a row via admin conn with tenant context.
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(conn, tenantId);
        insertPerson(tenantId, null, "Rls", "NoContext");

        // Reset runtime conn tenant context to NULL (fail-closed).
        resetTenant(runtimeConn);

        // SELECT on runtime conn → must return 0 rows.
        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_people")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_people RLS must be fail-closed when app.tenant_id is unset")
                    .isZero();
        }

        // INSERT on runtime conn without tenant context → must be rejected.
        // RLS WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true))
        // evaluates to NULL (not true) when app.tenant_id is unset → insert rejected.
        assertThatThrownBy(() -> {
            UUID newPerson = UUID.randomUUID();
            insertPerson(runtimeConn, tenantId, newPerson, null, "Rls", "InsertNoCtx");
        }).isInstanceOf(SQLException.class);
    }

    // --- RLS hr_people WRONG TENANT: SELECT returns 0 rows + INSERT rejected ---
    @Test
    void rls_hrPeople_wrongTenant_failsClosed() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        // Seed a row in Tenant A via admin conn with tenant A context.
        setTenant(conn, tenantA);
        insertPerson(tenantA, null, "Rls", "TenantA");

        // Set runtime conn to Tenant B (wrong tenant).
        setTenant(runtimeConn, tenantB);

        // SELECT on runtime conn (tenant=B) → must return 0 rows
        // (only Tenant A rows exist; RLS USING filters them out).
        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_people")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_people RLS must hide rows when tenant context mismatches")
                    .isZero();
        }

        // INSERT on runtime conn with tenant_id=A but app.tenant_id=B → must be rejected
        // (RLS WITH CHECK fails because tenant_id (A) != app.tenant_id (B)).
        assertThatThrownBy(() -> {
            UUID newPerson = UUID.randomUUID();
            insertPerson(runtimeConn, tenantA, newPerson, null, "Rls", "InsertWrong");
        }).isInstanceOf(SQLException.class);
    }

    // --- RLS hr_people CORRECT TENANT: SELECT/INSERT allowed ---
    @Test
    void rls_hrPeople_correctTenant_allowsAccess() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        // Seed via admin conn with tenant context.
        setTenant(conn, tenantId);
        insertPerson(tenantId, null, "Rls", "Correct");

        // Set runtime conn to the correct tenant.
        setTenant(runtimeConn, tenantId);

        // SELECT on runtime conn → must return at least 1 row (the seeded one).
        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_people")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_people RLS must allow SELECT with matching tenant context")
                    .isGreaterThanOrEqualTo(1);
        }

        // INSERT on runtime conn with tenant_id=tenantId and app.tenant_id=tenantId → must succeed.
        UUID newPerson = UUID.randomUUID();
        insertPerson(runtimeConn, tenantId, newPerson, null, "Rls", "InsertCorrect");
        // No exception expected — implicitly asserted by reaching the end of the test.
    }

    // ==================== RLS behavior: hr_person_private ====================

    // --- RLS hr_person_private NO TENANT CONTEXT: SELECT returns 0 + INSERT rejected ---
    @Test
    void rls_hrPersonPrivate_noTenantContext_failsClosed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(conn, tenantId);
        UUID personId = insertPerson(tenantId, null, "Rls", "PrivateNoCtx");
        insertPrivate(personId, tenantId, "1990-01-01", "SA", "SINGLE");

        resetTenant(runtimeConn);

        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_person_private")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_person_private RLS must be fail-closed when app.tenant_id is unset")
                    .isZero();
        }

        // INSERT without tenant context → must be rejected.
        assertThatThrownBy(() -> {
            UUID fakePerson = UUID.randomUUID();
            try (PreparedStatement ps = runtimeConn.prepareStatement(
                    "INSERT INTO hr_person_private (person_id, tenant_id, date_of_birth, " +
                    "nationality_country_code, marital_status, version, updated_at) " +
                    "VALUES (?, ?, ?::date, ?, ?, 0, NOW())")) {
                ps.setObject(1, fakePerson);
                ps.setObject(2, tenantId);
                ps.setString(3, "1990-01-01");
                ps.setString(4, "SA");
                ps.setString(5, "SINGLE");
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
    }

    // --- RLS hr_person_private WRONG TENANT: SELECT returns 0 + INSERT rejected ---
    @Test
    void rls_hrPersonPrivate_wrongTenant_failsClosed() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(conn, tenantA);
        UUID personInA = insertPerson(tenantA, null, "Rls", "PrivateA");
        insertPrivate(personInA, tenantA, "1990-01-01", "SA", "SINGLE");

        setTenant(runtimeConn, tenantB);

        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_person_private")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_person_private RLS must hide rows when tenant context mismatches")
                    .isZero();
        }

        // INSERT with tenant_id=A but app.tenant_id=B → must be rejected.
        assertThatThrownBy(() -> {
            UUID fakePerson = UUID.randomUUID();
            try (PreparedStatement ps = runtimeConn.prepareStatement(
                    "INSERT INTO hr_person_private (person_id, tenant_id, date_of_birth, " +
                    "nationality_country_code, marital_status, version, updated_at) " +
                    "VALUES (?, ?, ?::date, ?, ?, 0, NOW())")) {
                ps.setObject(1, fakePerson);
                ps.setObject(2, tenantA);  // tenant_id = A
                ps.setString(3, "1990-01-01");
                ps.setString(4, "SA");
                ps.setString(5, "SINGLE");
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
    }

    // --- RLS hr_person_private CORRECT TENANT: SELECT/INSERT allowed ---
    @Test
    void rls_hrPersonPrivate_correctTenant_allowsAccess() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(conn, tenantId);
        UUID personId = insertPerson(tenantId, null, "Rls", "PrivateCorrect");
        insertPrivate(personId, tenantId, "1990-01-01", "SA", "SINGLE");

        setTenant(runtimeConn, tenantId);

        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_person_private")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_person_private RLS must allow SELECT with matching tenant context")
                    .isGreaterThanOrEqualTo(1);
        }

        // INSERT with matching tenant_id=tenantId and app.tenant_id=tenantId → must succeed.
        UUID newPerson = insertPerson(tenantId, null, "Rls", "PrivateCorrect2");
        try (PreparedStatement ps = runtimeConn.prepareStatement(
                "INSERT INTO hr_person_private (person_id, tenant_id, date_of_birth, " +
                "nationality_country_code, marital_status, version, updated_at) " +
                "VALUES (?, ?, ?::date, ?, ?, 0, NOW())")) {
            ps.setObject(1, newPerson);
            ps.setObject(2, tenantId);
            ps.setString(3, "1990-01-01");
            ps.setString(4, "SA");
            ps.setString(5, "SINGLE");
            ps.executeUpdate();
        }
        // No exception expected.
    }

    // ==================== RLS behavior: hr_person_identifiers ====================

    // --- RLS hr_person_identifiers NO TENANT CONTEXT: SELECT returns 0 + INSERT rejected ---
    @Test
    void rls_hrPersonIdentifiers_noTenantContext_failsClosed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(conn, tenantId);
        UUID personId = insertPerson(tenantId, null, "Rls", "IdNoCtx");
        insertIdentifier(tenantId, personId, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "blindnoctx", "v1", "v1", "ACTIVE");

        resetTenant(runtimeConn);

        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_person_identifiers")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_person_identifiers RLS must be fail-closed when app.tenant_id is unset")
                    .isZero();
        }

        // INSERT without tenant context → must be rejected.
        assertThatThrownBy(() -> {
            try (PreparedStatement ps = runtimeConn.prepareStatement(
                    "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, " +
                    "issuing_country_code, identifier_ciphertext, blind_index, " +
                    "encryption_key_version, blind_index_key_version, status, created_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, 'NATIONAL_ID', 'SA', 'enc:v1:dGVzdA==', " +
                    "'blindnoctx2', 'v1', 'v1', 'ACTIVE', NOW())")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, personId);
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
    }

    // --- RLS hr_person_identifiers WRONG TENANT: SELECT returns 0 + INSERT rejected ---
    @Test
    void rls_hrPersonIdentifiers_wrongTenant_failsClosed() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(conn, tenantA);
        UUID personInA = insertPerson(tenantA, null, "Rls", "IdA");
        insertIdentifier(tenantA, personInA, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "blindwrong", "v1", "v1", "ACTIVE");

        setTenant(runtimeConn, tenantB);

        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_person_identifiers")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_person_identifiers RLS must hide rows when tenant context mismatches")
                    .isZero();
        }

        // INSERT with tenant_id=A but app.tenant_id=B → must be rejected.
        assertThatThrownBy(() -> {
            try (PreparedStatement ps = runtimeConn.prepareStatement(
                    "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, " +
                    "issuing_country_code, identifier_ciphertext, blind_index, " +
                    "encryption_key_version, blind_index_key_version, status, created_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, 'NATIONAL_ID', 'SA', 'enc:v1:dGVzdA==', " +
                    "'blindwrong2', 'v1', 'v1', 'ACTIVE', NOW())")) {
                ps.setObject(1, tenantA);  // tenant_id = A
                ps.setObject(2, personInA);
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
    }

    // --- RLS hr_person_identifiers CORRECT TENANT: SELECT/INSERT allowed ---
    @Test
    void rls_hrPersonIdentifiers_correctTenant_allowsAccess() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(conn, tenantId);
        UUID personId = insertPerson(tenantId, null, "Rls", "IdCorrect");
        insertIdentifier(tenantId, personId, "NATIONAL_ID", "SA",
                "enc:v1:dGVzdA==", "blindcorrect", "v1", "v1", "ACTIVE");

        setTenant(runtimeConn, tenantId);

        try (Statement s = runtimeConn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hr_person_identifiers")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("hr_person_identifiers RLS must allow SELECT with matching tenant context")
                    .isGreaterThanOrEqualTo(1);
        }

        // INSERT with matching tenant_id=tenantId and app.tenant_id=tenantId → must succeed.
        UUID newPerson = insertPerson(tenantId, null, "Rls", "IdCorrect2");
        try (PreparedStatement ps = runtimeConn.prepareStatement(
                "INSERT INTO hr_person_identifiers (id, tenant_id, person_id, identifier_type, " +
                "issuing_country_code, identifier_ciphertext, blind_index, " +
                "encryption_key_version, blind_index_key_version, status, created_at) " +
                "VALUES (gen_random_uuid(), ?, ?, 'PASSPORT', 'SA', 'enc:v1:dGVzdC==', " +
                "'blindcorrect2', 'v1', 'v1', 'ACTIVE', NOW())")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, newPerson);
            ps.executeUpdate();
        }
        // No exception expected.
    }
}
