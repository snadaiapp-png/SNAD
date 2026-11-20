package com.sanad.platform.internal.bootstrap;

import com.sanad.platform.internal.bootstrap.service.ControlPlaneBootstrapResult;
import com.sanad.platform.internal.bootstrap.service.ControlPlaneBootstrapService;
import com.sanad.platform.security.config.CredentialBootstrapService;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rebuilt F-BOOT-01 / F-BOOT-02 PostgreSQL regression (PostgreSQL Direct,
 * real FORCE-RLS semantics — no H2 substitute).
 *
 * <h3>Finding provenance</h3>
 * <ul>
 *   <li><b>F-BOOT-01</b> — the control-plane bootstrap wrote {@code public.users}
 *       outside the trusted tenant GUC and was rejected with SQLSTATE 42501 by
 *       the fail-closed users RLS policy (V20261011_3). The service must
 *       explicitly classify its privileged pre-auth path by applying the
 *       trusted tenant GUC (TenantRlsTransactionContext) before any users
 *       access.</li>
 *   <li><b>F-BOOT-02</b> — the bootstrapped control-plane admin was left
 *       login-incapable (forced-rotation flag armed by the generic credential
 *       bootstrap, immediately locking the operator out of /api/** with 403).
 *       The control-plane wrapper must clear {@code must_change_password} for
 *       this protected server-managed flow only.</li>
 * </ul>
 *
 * <p>The pre-auth credential bootstrap test runs with an EMPTY
 * SecurityContextHolder on purpose: it proves the service establishes the
 * trusted GUC itself, rather than piggy-backing on an authenticated actor
 * context. Companion driver-level guarantee: unclassified pre-auth writes to
 * FORCE-RLS tables fail closed at the connection layer with
 * {@code RLS_PRE_AUTH_UNCLASSIFIED} (TenantRlsConnectionHandler).</p>
 */
@SpringBootTest(properties = {
        "sanad.control-plane.tenant-id=c0000000-0000-4000-8000-00000000c001",
        "sanad.control-plane.bootstrap.admin-email=cp-rls-probe@sanad.test",
        "sanad.control-plane.bootstrap.admin-password=Probe-Passphrase-2026!"
})
@ActiveProfiles("local")
@Transactional
class ControlPlaneBootstrapRlsPostgresTest {

    /** Fixed control-plane tenant UUID bound to the test properties above. */
    static final UUID CONTROL_PLANE_TENANT_ID =
            UUID.fromString("c0000000-0000-4000-8000-00000000c001");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CredentialBootstrapService credentialBootstrapService;
    @Autowired private ControlPlaneBootstrapService controlPlaneBootstrapService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder encoder;

    // ── F-BOOT-01 — RED state: unscoped users writes are fail-closed ─────

    @Test
    @DisplayName("F-BOOT-01 RED: raw users INSERT without the trusted tenant GUC is rejected (42501), never stored")
    void rawUsersWriteWithoutTrustedGucIsRejectedFailClosed() throws Exception {
        String probeEmail = "cp-rls-raw-probe-" + UUID.randomUUID() + "@sanad.test";
        String probeTenantId = UUID.randomUUID().toString();
        String probeSubdomain = "cp-rls-raw-probe-" + UUID.randomUUID().toString().substring(0, 8);

        // Borrow a pooled connection through the RLS proxy, unwrap to the
        // physical connection for a DB-layer-only probe (no driver guard).
        Connection proxied = jdbc.getDataSource().getConnection();
        Connection raw = proxied.unwrap(Connection.class);
        try {
            boolean previousAutoCommit = raw.getAutoCommit();
            // Fixture tenant committed on the physical connection (autocommit);
            // session GUC applied first so any tenant-directory policy accepts it.
            raw.setAutoCommit(true);
            executeRaw(raw, "SELECT set_config('app.tenant_id', ?, false)", probeTenantId);
            executeRaw(raw, "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                    + "VALUES (?::uuid, 'CP RLS Raw Probe', ?, 'ACTIVE', now(), now())",
                    probeTenantId, probeSubdomain);
            // Reset the GUC: the probe must run with NO tenant context at all.
            executeRaw(raw, "SELECT set_config('app.tenant_id', '', false)");

            SQLException rejected = null;
            try (PreparedStatement insert = raw.prepareStatement(
                    "INSERT INTO public.users (id, tenant_id, email, display_name, status, created_at, updated_at) "
                            + "VALUES (gen_random_uuid(), ?::uuid, ?, 'CP RLS Raw Probe', 'ACTIVE', now(), now())")) {
                insert.setString(1, probeTenantId);
                insert.setString(2, probeEmail);
                insert.executeUpdate();
            } catch (SQLException exception) {
                rejected = exception;
            }

            // Fail-closed contract: the write must be rejected with 42501 —
            // either the FORCE-RLS WITH CHECK violation or (on a topology where
            // grants are default-deny) the permission denial — and NEVER a
            // constraint violation that would imply the row was accepted.
            // SQLException implements Iterable<Throwable>, so the raw generic
            // assertThat would be ambiguous — pin the Throwable overload.
            org.assertj.core.api.Assertions.<java.sql.SQLException>assertThat(rejected)
                    .as("unscoped users write must be rejected by the database")
                    .isNotNull();
            assertThat(rejected.getSQLState())
                    .as("fail-closed SQLSTATE (42501 = insufficient_privilege family)")
                    .isEqualTo("42501");
            assertThat(rejected.getMessage()).satisfiesAnyOf(
                    message -> assertThat(message).contains("row-level security policy"),
                    message -> assertThat(message).contains("permission denied")
            );

            // Nothing may have been stored, even on partially-permissive topologies.
            long stored = countRawUsers(raw, probeEmail);
            assertThat(stored).as("no unscoped users row may persist").isZero();
            raw.setAutoCommit(previousAutoCommit);
        } finally {
            // Cleanup fixture rows, reset the session GUC, return the pooled
            // connection via the proxy (never close the raw physical delegate).
            try {
                executeRaw(raw, "SELECT set_config('app.tenant_id', ?, false)", probeTenantId);
                executeRaw(raw, "DELETE FROM public.users WHERE tenant_id = ?::uuid", probeTenantId);
                executeRaw(raw, "DELETE FROM tenants WHERE id = ?::uuid", probeTenantId);
                executeRaw(raw, "SELECT set_config('app.tenant_id', '', false)");
            } catch (SQLException cleanupFailure) {
                // surface but do not mask the primary assertion result
                System.err.println("[ControlPlaneBootstrapRlsPostgresTest] cleanup warning: "
                        + cleanupFailure.getMessage());
            }
            proxied.close();
        }
    }

    @Test
    @DisplayName("Driver layer: proxied pre-auth users write without classification fails closed (RLS_PRE_AUTH_UNCLASSIFIED)")
    void proxiedPreAuthUsersWriteWithoutClassificationFailsClosed() {
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        UUID absentTenantId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO public.users (id, tenant_id, email, display_name, status, created_at, updated_at) "
                        + "VALUES (gen_random_uuid(), ?, 'cp-rls-proxied-probe@sanad.test', 'CP Probe', 'ACTIVE', now(), now())",
                absentTenantId))
                .as("unclassified pre-auth writes to FORCE-RLS tables must fail closed at the driver")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("RLS_PRE_AUTH_UNCLASSIFIED");
    }

    // ── F-BOOT-01 — GREEN: the real service writes inside the trusted GUC ─

    @Test
    @DisplayName("F-BOOT-01 GREEN: pre-auth credential bootstrap persists the admin row inside the trusted tenant GUC")
    void preAuthCredentialBootstrapWritesAdminInsideTrustedGuc() {
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("the probe must run pre-auth (no actor tenant context)")
                .isNull();

        Tenant tenant = tenantRepository.save(new Tenant(
                "CP RLS Green Probe", "cp-rls-green-" + UUID.randomUUID().toString().substring(0, 8),
                TenantStatus.ACTIVE));

        String email = "cp-admin-" + UUID.randomUUID() + "@sanad.test";
        String password = "Probe-Passphrase-2026!";

        // Pre-fix this call died with SQLSTATE 42501 (F-BOOT-01). Post-fix the
        // service classifies its privileged path and applies the trusted GUC.
        User admin = credentialBootstrapService.bootstrap(
                /* enabled */ true, /* forceReset */ true,
                /* tenantId */ tenant.getId(),
                /* tenantName */ null, /* tenantSubdomain */ null,
                /* adminEmail */ email, /* adminPassword */ password,
                /* displayName */ "CP RLS Green Admin",
                /* auditActor */ "control-plane-bootstrap-rls-test");

        assertThat(admin).isNotNull();
        assertThat(admin.getId()).isNotNull();

        // Reload through the governed scoped lookup: only possible because the
        // service placed the transaction inside the trusted tenant GUC.
        User reloaded = userRepository.findByTenantIdAndEmail(tenant.getId(), email)
                .orElseThrow(() -> new AssertionError(
                        "bootstrapped admin row must be visible under the trusted tenant GUC"));
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getPasswordHash()).isNotBlank();
        assertThat(encoder.matches(password, reloaded.getPasswordHash())).isTrue();
    }

    // ── F-BOOT-02 — the bootstrapped control-plane admin is login-capable ─

    @Test
    @DisplayName("F-BOOT-02: control-plane bootstrap admin is ACTIVE, hash-armed, rotation cleared, tenant-scoped")
    void controlPlaneBootstrapAdminIsLoginCapableAndTenantScoped() {
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, 'Control Plane RLS Probe', 'cp-rls-probe-tenant', 'ACTIVE', now(), now()) "
                        + "ON CONFLICT (id) DO NOTHING",
                CONTROL_PLANE_TENANT_ID);

        ControlPlaneBootstrapResult result = controlPlaneBootstrapService.bootstrapControlPlaneAdmin();

        assertThat(result.tenantId()).isEqualTo(CONTROL_PLANE_TENANT_ID);
        assertThat(result.userId()).isNotNull();

        // Re-scope to the control-plane tenant (the service already applied it;
        // re-applying is idempotent) and assert the persisted login-capability
        // contract of the bootstrapped admin row.
        jdbc.execute("SELECT set_config('app.tenant_id', '" + CONTROL_PLANE_TENANT_ID + "', true)");
        User admin = userRepository.findByTenantIdAndEmail(CONTROL_PLANE_TENANT_ID, "cp-rls-probe@sanad.test")
                .orElseThrow(() -> new AssertionError(
                        "control-plane admin row must be visible under the trusted tenant GUC"));

        assertThat(admin.getStatus()).as("F-BOOT-02: account must be ACTIVE").isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).as("credential must be armed").isNotBlank();
        assertThat(encoder.matches("Probe-Passphrase-2026!", admin.getPasswordHash()))
                .as("password hash must match the configured control-plane credential")
                .isTrue();
        assertThat(admin.isMustChangePassword())
                .as("F-BOOT-02: forced-rotation must be cleared for the server-managed "
                        + "control-plane credential, otherwise /api/** locks the operator out (403)")
                .isFalse();
        assertThat(admin.isPlatformAdmin())
                .as("control-plane admin must carry the platform operator marker")
                .isTrue();

        // Tenant scoping: from a different tenant the admin row is invisible.
        jdbc.execute("SELECT set_config('app.tenant_id', '" + UUID.randomUUID() + "', true)");
        assertThat(userRepository.findByTenantIdAndEmail(CONTROL_PLANE_TENANT_ID, "cp-rls-probe@sanad.test"))
                .as("the control-plane admin must not leak across tenants")
                .isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void executeRaw(Connection raw, String sql, Object... args) throws SQLException {
        if (sql.startsWith("SELECT set_config")) {
            try (PreparedStatement ps = raw.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setString(i + 1, (String) args[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    // consume the GUC result
                }
            }
            return;
        }
        try (PreparedStatement ps = raw.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    private static long countRawUsers(Connection raw, String email) throws SQLException {
        try (PreparedStatement ps = raw.prepareStatement(
                "SELECT count(*) FROM public.users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }
}
