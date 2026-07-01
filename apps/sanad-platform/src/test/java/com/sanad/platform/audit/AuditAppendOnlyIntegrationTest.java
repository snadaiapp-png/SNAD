package com.sanad.platform.audit;

import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantMigrationOwnerDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 05A.1 §3-5 — Verifies the PostgreSQL append-only immutability
 * triggers on {@code audit_events} (V26 migration — no fixture bypass).
 *
 * <p>Stage 05A.1 §3 — V26 restored strict immutability: the trigger
 * functions NO LONGER exempt {@code sanad_fixture_ci}. UPDATE, DELETE,
 * and TRUNCATE are ALWAYS rejected, regardless of the connecting role.</p>
 *
 * <p>This test verifies immutability from THREE roles:</p>
 * <ul>
 *   <li>The fixture CI role (BYPASSRLS) — INSERT succeeds, UPDATE/DELETE/
 *       TRUNCATE fail.</li>
 *   <li>The migration owner role (table owner, subject to FORCE RLS) —
 *       INSERT succeeds, UPDATE/DELETE/TRUNCATE fail.</li>
 * </ul>
 *
 * <p>Stage 05A.1 §5 — Runtime role is verified separately in
 * {@link AuditDatabasePrivilegeIntegrationTest} (UPDATE/DELETE are revoked
 * at the GRANT level).</p>
 *
 * <p>All SQL uses {@link PreparedStatement} (no string concatenation) and
 * {@link Timestamp#from(java.time.Instant)} for JDBC timestamp parameters.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class,
        TenantMigrationOwnerDataSourceConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditAppendOnlyIntegrationTest {

    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    @Autowired
    @Qualifier("tenantMigrationOwnerDataSource")
    private DataSource migrationOwnerDataSource;

    private TenantTestFixture fixture;
    private JdbcTemplate fixtureJdbc;
    private JdbcTemplate migrationOwnerJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
        migrationOwnerJdbc = new JdbcTemplate(migrationOwnerDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    /**
     * Inserts a minimal audit_events row directly via the given DataSource.
     * Returns the generated UUID so individual tests can target it.
     */
    private UUID insertAuditRow(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO audit_events (id, tenant_id, actor_type, action, "
                + "resource_type, operation, outcome, occurred_at, recorded_at, "
                + "created_at, event_hash, previous_hash, hash_algorithm, "
                + "schema_version, sequence_number) "
                + "VALUES (?, ?, 'USER', 'TEST.APPEND.ONLY', 'TestResource', 'TEST', "
                + "'SUCCESS', ?, ?, ?, ?, "
                + "'0000000000000000000000000000000000000000000000000000000000000000', "
                + "'SHA-256', 1, ?)";
        Timestamp now = Timestamp.from(java.time.Instant.now());
        // Use a high sequence number (e.g. 999_999) to avoid colliding with
        // any sequence numbers already in use by the AuditService for this
        // tenant during the test run. The trigger does not check sequence
        // uniqueness — only the unique constraint does, and this row is
        // immediately deleted by the trigger test if UPDATE/DELETE worked
        // (they don't, so the row remains).
        jdbc.update(sql, id, fixture.tenantAId(), now, now, now,
                "a".repeat(64), 999_999L);
        return id;
    }

    // ============================================================
    // Fixture role (sanad_fixture_ci, BYPASSRLS) — V26 strict triggers
    // ============================================================

    @Test
    @DisplayName("fixtureRole_insertSucceeds: fixture DS can INSERT a new audit_events row")
    void fixtureRole_insertSucceeds() {
        UUID id = insertAuditRow(fixtureJdbc);
        Integer count = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE id = ?",
                Integer.class, id);
        assertThat(count).as("inserted row must be visible").isEqualTo(1);
    }

    @Test
    @DisplayName("fixtureRole_updateRejected: fixture DS UPDATE on audit_events → exception containing 'append-only' (V26 no bypass)")
    void fixtureRole_updateRejected() {
        UUID id = insertAuditRow(fixtureJdbc);
        assertThatThrownBy(() ->
                fixtureJdbc.update(
                        "UPDATE audit_events SET action = 'TAMPERED' WHERE id = ?",
                        id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("fixtureRole_deleteRejected: fixture DS DELETE on audit_events → exception containing 'append-only' (V26 no bypass)")
    void fixtureRole_deleteRejected() {
        UUID id = insertAuditRow(fixtureJdbc);
        assertThatThrownBy(() ->
                fixtureJdbc.update("DELETE FROM audit_events WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("fixtureRole_truncateRejected: fixture DS TRUNCATE on audit_events → exception containing 'append-only' (V26 no bypass)")
    void fixtureRole_truncateRejected() {
        insertAuditRow(fixtureJdbc);
        assertThatThrownBy(() ->
                fixtureJdbc.execute("TRUNCATE TABLE audit_events"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    // ============================================================
    // Migration owner role (table owner, FORCE RLS) — V26 strict triggers
    // ============================================================

    @Test
    @DisplayName("migrationOwner_insertSucceeds: migration owner can INSERT a new audit_events row")
    void migrationOwner_insertSucceeds() {
        UUID id = insertAuditRow(migrationOwnerJdbc);
        Integer count = migrationOwnerJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE id = ?",
                Integer.class, id);
        assertThat(count).as("inserted row must be visible").isEqualTo(1);
    }

    @Test
    @DisplayName("migrationOwner_updateRejected: migration owner UPDATE on audit_events → exception containing 'append-only'")
    void migrationOwner_updateRejected() {
        UUID id = insertAuditRow(migrationOwnerJdbc);
        assertThatThrownBy(() ->
                migrationOwnerJdbc.update(
                        "UPDATE audit_events SET action = 'TAMPERED' WHERE id = ?",
                        id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("migrationOwner_deleteRejected: migration owner DELETE on audit_events → exception containing 'append-only'")
    void migrationOwner_deleteRejected() {
        UUID id = insertAuditRow(migrationOwnerJdbc);
        assertThatThrownBy(() ->
                migrationOwnerJdbc.update("DELETE FROM audit_events WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("migrationOwner_truncateRejected: migration owner TRUNCATE on audit_events → exception containing 'append-only'")
    void migrationOwner_truncateRejected() {
        insertAuditRow(migrationOwnerJdbc);
        assertThatThrownBy(() ->
                migrationOwnerJdbc.execute("TRUNCATE TABLE audit_events"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
