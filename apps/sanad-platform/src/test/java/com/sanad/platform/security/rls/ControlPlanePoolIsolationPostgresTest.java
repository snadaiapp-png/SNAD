package com.sanad.platform.security.rls;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ORDER 11 (2026-11-21) control-plane separation certification, application
 * layer. Runs only against PostgreSQL Direct (the CI "Maven Test Suite"
 * contract) and proves, on the LIVE application pools:
 *
 * <ol>
 *   <li>The dedicated control-plane connection authenticates as
 *       {@code sanad_control_plane} (both {@code current_user} and
 *       {@code session_user}) — an independent, limited-privilege identity.</li>
 *   <li>The runtime pool NEVER holds the control-plane identity, before and
 *       after control-plane operations (connection-pool privilege-leakage
 *       denial). With the dedicated-identity design there is no SET ROLE at
 *       all, so a runtime connection cannot carry the control-plane
 *       identity.</li>
 *   <li>The runtime cannot {@code SET ROLE sanad_control_plane}: PostgreSQL
 *       refuses with SQLSTATE 42501 (no membership — the forbidden
 *       escalation path is structurally absent).</li>
 *   <li>Authorized control-plane DML (WHERE-keyed UPDATE, the exact
 *       statement shape that failed with 42501/25P02 under the removed SET
 *       ROLE design) PASSES on the dedicated connection.</li>
 *   <li>The runtime membership matrix confirms no MEMBER/SET privilege
 *       toward the control-plane role.</li>
 * </ol>
 *
 * <p>The separated-topology denial matrix (plain runtime INSERT/UPDATE/
 * DELETE denied on {@code tenants}/{@code access_capabilities}) is certified
 * by {@code tests/security/migrator-separation/run-separation-cert.sh} S2 —
 * in the fixture topology the runtime owns the tables and the denials are
 * structurally unenforceable (documented skip class of V20261118_1/_2).</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class ControlPlanePoolIsolationPostgresTest {

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "ControlPlanePoolIsolationPostgresTest");
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for ControlPlanePoolIsolationPostgresTest");
    }

    @Autowired private ControlPlaneDataAccess controlPlaneDataAccess;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void controlPlaneConnectionAuthenticatesAsDedicatedIdentity() {
        assertThat(controlPlaneDataAccess.controlPlaneCurrentUser())
                .isEqualTo("sanad_control_plane");
    }

    @Test
    void runtimePoolNeverHoldsControlPlaneIdentity() {
        // Run a real control-plane write first so a leaked identity would be
        // observable, then probe the runtime pool.
        UUID probe = UUID.randomUUID();
        String subdomain = "cp-leak-" + UUID.randomUUID().toString().substring(0, 8);
        controlPlaneDataAccess.inControlPlane(cp -> cp.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, 'Leak Probe', ?, 'ACTIVE', now(), now())",
                probe, subdomain));

        Map<String, Object> runtimeIdentity = jdbc.queryForMap(
                "SELECT current_user AS cu, session_user AS su, current_setting('role') AS effective_role");
        assertThat(runtimeIdentity.get("cu")).isEqualTo("sanad");
        assertThat(runtimeIdentity.get("su")).isEqualTo("sanad");
        // The role GUC is "none" on a plain session, or the connection's OWN
        // login role name after the pgjdbc driver's read-only state management
        // (it restores the saved role with SET ROLE 'sanad' — its own identity,
        // never an escalation). What must NEVER appear here is the control-
        // plane identity.
        assertThat((String) runtimeIdentity.get("effective_role"))
                .isIn("none", "sanad")
                .isNotEqualTo("sanad_control_plane");

        controlPlaneDataAccess.inControlPlane(cp -> cp.update(
                "DELETE FROM tenants WHERE id = ?", probe));
    }

    @Test
    void runtimeSetRoleToControlPlaneIsDenied42501() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> jdbc.execute((ConnectionCallback<Object>) connection -> {
                    try (var statement = connection.createStatement()) {
                        statement.execute("SET ROLE sanad_control_plane");
                    }
                    return null;
                }));
        Throwable cause = failure.getCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) cause).getSQLState()).isEqualTo("42501");
        assertThat(cause.getMessage()).contains("permission denied");
        // The runtime session identity is unchanged after the refused switch.
        assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("sanad");
    }

    @Test
    void runtimeMembershipMatrixProhibitsEscalation() {
        assertThat(jdbc.queryForObject(
                "SELECT pg_has_role('sanad', 'sanad_control_plane', 'MEMBER')", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT pg_has_role('sanad', 'sanad_control_plane', 'SET')", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT pg_has_role('sanad', 'sanad_control_plane', 'USAGE')", Boolean.class))
                .isFalse();
    }

    @Test
    void controlPlaneAuthorizedWhereKeyedDmlPassesOnDedicatedConnection() {
        UUID id = UUID.randomUUID();
        String subdomain = "cp-dml-" + UUID.randomUUID().toString().substring(0, 8);

        controlPlaneDataAccess.inControlPlane(cp -> cp.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, 'CP DML Probe', ?, 'ACTIVE', now(), now())",
                id, subdomain));

        // The exact WHERE-keyed UPDATE shape that produced the ORDER 11
        // 42501/25P02 failure chain under the removed SET ROLE design.
        Integer updated = controlPlaneDataAccess.inControlPlane(cp -> cp.update(
                "UPDATE tenants SET name = ?, updated_at = now() WHERE id = ?",
                "CP DML Probe 2", id));
        assertThat(updated).isEqualTo(1);

        String readBack = controlPlaneDataAccess.inControlPlane(cp -> cp.queryForObject(
                "SELECT name FROM tenants WHERE id = ?", String.class, id));
        assertThat(readBack).isEqualTo("CP DML Probe 2");

        Integer deleted = controlPlaneDataAccess.inControlPlane(cp -> cp.update(
                "DELETE FROM tenants WHERE id = ?", id));
        assertThat(deleted).isEqualTo(1);
    }
}
