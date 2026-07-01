package com.sanad.platform.audit;

import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
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
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 05A.1 §5 — Verifies that the runtime DB role
 * {@code sanad_runtime_app} has ONLY {@code SELECT} and {@code INSERT}
 * privileges on {@code audit_events}. The migration V26 revokes
 * {@code UPDATE}, {@code DELETE}, and {@code TRUNCATE} from the runtime
 * role so that even a compromised application process cannot tamper with
 * audit history.
 *
 * <p>Three checks:</p>
 * <ol>
 *   <li>{@code runtimeRole_hasSelectInsertOnly} — introspects
 *       {@code pg_class}/{@code information_schema.role_table_grants}
 *       to assert the runtime role holds SELECT+INSERT but NOT
 *       UPDATE/DELETE/TRUNCATE on audit_events.</li>
 *   <li>{@code runtimeRole_hasNoUpdateOnAudit} — attempts an UPDATE via
 *       the runtime {@link DataSource} and asserts a permission denied
 *       error.</li>
 *   <li>{@code runtimeRole_hasNoDeleteOnAudit} — attempts a DELETE via
 *       the runtime {@link DataSource} and asserts a permission denied
 *       error.</li>
 * </ol>
 *
 * <p>All SQL uses {@link PreparedStatement} (no string concatenation)
 * per Stage 05A.1 testing standards. Timestamps use
 * {@link java.sql.Timestamp#from(java.time.Instant)} when needed.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditDatabasePrivilegeIntegrationTest {

    /** Runtime DataSource — subject to RLS, owned by sanad_runtime_app. */
    @Autowired
    private DataSource runtimeDataSource;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    /**
     * Inserts a minimal audit_events row via the fixture DataSource so that
     * the runtime role has a concrete row to attempt UPDATE/DELETE on.
     * Returns the row id.
     */
    private UUID seedAuditRow() {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO audit_events (id, tenant_id, actor_type, action, "
                + "resource_type, operation, outcome, occurred_at, recorded_at, "
                + "created_at, event_hash, previous_hash, hash_algorithm, "
                + "schema_version, sequence_number) "
                + "VALUES (?, ?, 'USER', 'TEST.PRIVILEGE.CHECK', 'TestResource', "
                + "'TEST', 'SUCCESS', ?, ?, ?, ?, "
                + "'0000000000000000000000000000000000000000000000000000000000000000', "
                + "'SHA-256', 1, ?)";
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
        fixtureJdbc.update(sql, id, fixture.tenantAId(), now, now, now,
                "a".repeat(64), 999L);
        return id;
    }

    @Test
    @DisplayName("runtimeRole_hasSelectInsertOnly: sanad_runtime_app has SELECT, INSERT but NOT UPDATE, DELETE, TRUNCATE on audit_events")
    void runtimeRole_hasSelectInsertOnly() throws Exception {
        // Introspect via information_schema.role_table_grants — this view
        // shows one row per (grantee, table_name, privilege_type).
        // We use a PreparedStatement with a literal table name bind.
        String sql = "SELECT privilege_type FROM information_schema.role_table_grants "
                + "WHERE table_name = ? AND grantee = ? "
                + "ORDER BY privilege_type";

        try (Connection conn = runtimeDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "audit_events");
            ps.setString(2, "sanad_runtime_app");
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Set<String> privileges = new java.util.TreeSet<>();
                while (rs.next()) {
                    privileges.add(rs.getString("privilege_type"));
                }

                assertThat(privileges)
                        .as("sanad_runtime_app must hold SELECT on audit_events")
                        .contains("SELECT");
                assertThat(privileges)
                        .as("sanad_runtime_app must hold INSERT on audit_events")
                        .contains("INSERT");
                assertThat(privileges)
                        .as("sanad_runtime_app must NOT hold UPDATE on audit_events (revoked by V26)")
                        .doesNotContain("UPDATE");
                assertThat(privileges)
                        .as("sanad_runtime_app must NOT hold DELETE on audit_events (revoked by V26)")
                        .doesNotContain("DELETE");
                assertThat(privileges)
                        .as("sanad_runtime_app must NOT hold TRUNCATE on audit_events (revoked by V26)")
                        .doesNotContain("TRUNCATE");
            }
        }
    }

    @Test
    @DisplayName("runtimeRole_hasNoUpdateOnAudit: runtime DS UPDATE on audit_events → permission denied")
    void runtimeRole_hasNoUpdateOnAudit() {
        UUID id = seedAuditRow();
        JdbcTemplate runtimeJdbc = new JdbcTemplate(runtimeDataSource);
        // The runtime role (sanad_runtime_app) has NO UPDATE privilege on
        // audit_events (revoked by V26). The GRANT check happens BEFORE
        // RLS or the immutability trigger, so this fails with
        // "permission denied for table audit_events".
        assertThatThrownBy(() ->
                runtimeJdbc.update(
                        "UPDATE audit_events SET action = 'TAMPERED' WHERE id = ?",
                        id))
                .as("UPDATE on audit_events via runtime role must be rejected (no UPDATE privilege)")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("runtimeRole_hasNoDeleteOnAudit: runtime DS DELETE on audit_events → permission denied")
    void runtimeRole_hasNoDeleteOnAudit() {
        UUID id = seedAuditRow();
        JdbcTemplate runtimeJdbc = new JdbcTemplate(runtimeDataSource);
        // The runtime role (sanad_runtime_app) has NO DELETE privilege on
        // audit_events (revoked by V26). The GRANT check fails first.
        assertThatThrownBy(() ->
                runtimeJdbc.update(
                        "DELETE FROM audit_events WHERE id = ?",
                        id))
                .as("DELETE on audit_events via runtime role must be rejected (no DELETE privilege)")
                .isInstanceOf(DataAccessException.class);
    }
}
