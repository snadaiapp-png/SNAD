package com.sanad.platform.security.service;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 1 lifecycle-gap contract (owner continuation order section 4):
 * a tenant created AFTER the V20261001_9 backfill must still receive the
 * canonical TENANT_ADMIN role when RoleTemplateProvisioner.provision runs.
 *
 * <p>Proven contract:</p>
 * <ol>
 *   <li>exactly one ACTIVE TENANT_ADMIN role per provisioned tenant;</li>
 *   <li>role_origin = SNAD_TEMPLATE and template_key = TENANT_ADMIN;</li>
 *   <li>an authoritative role_template_bindings row exists;</li>
 *   <li>the tenant's admin authority (ADMIN capability set) is carried over;</li>
 *   <li>customer-created roles are never silently hijacked (an unbound
 *       customer role with the template code aborts provisioning).</li>
 * </ol>
 */
class RoleTemplateProvisionerTenantAdminProvisioningTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private static final List<String> ADMIN_AUTHORITY = List.of(
            "CRM.ACCOUNT.READ", "HR.EMPLOYEE.READ");

    private Connection connection;
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "RoleTemplateProvisionerTenantAdminProvisioningTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for RoleTemplateProvisionerTenantAdminProvisioningTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
    }

    @BeforeEach
    void migrateAndOpenTransaction() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
        connection.setAutoCommit(false);
        dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(dataSource);
        // G1 REV3: fixture signer for the signed binding protocol (disposable
        // fixture topology only) — matches the V20261124_2 chain provisioned key.
        com.sanad.platform.security.rls.TenantRlsRuntimePolicy.install(
                new com.sanad.platform.security.rls.TenantRlsRuntimePolicy.Policy(
                        new com.sanad.platform.security.rls.TenantContextSigner(
                                java.util.HexFormat.of().parseHex(FIXTURE_KEY_HEX), true),
                        true));
    }

    @AfterEach
    void closeTransaction() {
        if (dataSource != null) {
            dataSource.destroy();
        }
        dataSource = null;
        jdbc = null;
        connection = null;
    }

    @Test
    void provisionsExactlyOneCanonicalTenantAdminForNewTenantsAndNeverHijacksCustomerRoles()
            throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // ---- Fixture: a new tenant in the exact state RegistrationProvisioner
        // ---- leaves it in before calling RoleTemplateProvisioner.provision:
        // ---- tenant row + customer-managed ADMIN role holding the tenant's
        // ---- admin authority + an unrelated customer role.
        UUID tenantA = UUID.randomUUID();
        insertTenant(tenantA, "TenantAdmin Gap A", "uac-gap-a-" + suffix);
        // V20261029_1: roles / role_capabilities are FORCE RLS. Session-level
        // tenant GUC (is_local=false) so the raw-JDBC fixture writes AND the
        // provisioner's own inserts (this test drives it without a Spring
        // transaction) both satisfy the fail-closed policies.
        setTenant(tenantA);
        UUID adminRole = insertRole(tenantA, "ADMIN", "Administrator", false);
        grantCapability(adminRole, tenantA, "CRM.ACCOUNT.READ");
        grantCapability(adminRole, tenantA, "HR.EMPLOYEE.READ");
        UUID customerRole = insertRole(tenantA, "SUPPORT", "Customer support", false);

        // ---- Act: canonical runtime provisioning.
        // G1 REV3 — the provisioner binds through the signed protocol, which
        // requires a Spring-managed transaction: run it inside a template that
        // JOINS the already-open transaction on the single fixture connection.
        org.springframework.transaction.support.TransactionTemplate provisionTx =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        provisionTx.executeWithoutResult(status ->
                new RoleTemplateProvisioner(jdbc, new TenantRlsTransactionContext(jdbc)).provision(tenantA));

        // G1 REV3 — provisionTx committed the shared transaction (the signed
        // binding is transaction-local): re-open the signed scope for the
        // assertion reads.
        setTenant(tenantA);

        // ---- Assert: exactly one ACTIVE TENANT_ADMIN with canonical template
        // ---- provenance.
        List<TenantAdminRow> rows = jdbc.query(
                "SELECT id, status, role_origin, template_key FROM roles "
                        + "WHERE tenant_id = ? AND code = 'TENANT_ADMIN'",
                (rs, n) -> new TenantAdminRow(
                        rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getString("role_origin"), rs.getString("template_key")),
                tenantA);
        assertThat(rows).as("exactly one TENANT_ADMIN role").hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo("ACTIVE");
        assertThat(rows.get(0).roleOrigin()).isEqualTo("SNAD_TEMPLATE");
        assertThat(rows.get(0).templateKey()).isEqualTo("TENANT_ADMIN");
        UUID tenantAdminRole = rows.get(0).id();

        // ---- Assert: authoritative binding provenance exists.
        Long bindings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_template_bindings "
                        + "WHERE tenant_id = ? AND template_key = 'TENANT_ADMIN' AND role_id = ?",
                Long.class, tenantA, tenantAdminRole);
        assertThat(bindings).as("role_template_bindings row").isEqualTo(1L);

        // ---- Assert: the tenant's admin authority is carried over exactly.
        assertThat(capabilityCodes(tenantA, tenantAdminRole))
                .containsExactlyElementsOf(ADMIN_AUTHORITY);

        // ---- Assert: customer roles are never silently hijacked.
        assertThat(jdbc.queryForObject(
                "SELECT role_origin FROM roles WHERE id = ?", String.class, adminRole)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT is_system_managed FROM roles WHERE id = ?", Boolean.class, adminRole)).isFalse();
        assertThat(capabilityCodes(tenantA, adminRole)).containsExactlyElementsOf(ADMIN_AUTHORITY);
        assertThat(bindingCount(tenantA, "ADMIN")).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT is_system_managed FROM roles WHERE id = ?", Boolean.class, customerRole)).isFalse();
        assertThat(capabilityCodes(tenantA, customerRole)).isEmpty();
        assertThat(bindingCount(tenantA, "SUPPORT")).isZero();

        // ---- Assert: a customer-owned role with the template code is never
        // ---- silently taken over — provisioning aborts loudly instead.
        UUID tenantB = UUID.randomUUID();
        insertTenant(tenantB, "TenantAdmin Gap B", "uac-gap-b-" + suffix);
        // V20261029_1: roles is FORCE RLS — bind tenant B before its fixture writes.
        setTenant(tenantB);
        UUID hijackTarget = insertRole(tenantB, "TENANT_ADMIN", "Customer owned admin", false);
        // G1 REV3 — commit the tenantB fixture independently so the expected
        // provisioner abort (which rolls back its joining transaction) cannot
        // erase the fixture; then re-establish the signed scope.
        connection.commit();
        setTenant(tenantB);

        org.springframework.transaction.support.TransactionTemplate negativeTx =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() -> negativeTx.executeWithoutResult(status ->
                        new RoleTemplateProvisioner(jdbc, new TenantRlsTransactionContext(jdbc)).provision(tenantB)))
                .as("customer-owned TENANT_ADMIN code must never be hijacked")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unbound customer-managed role");

        // G1 REV3 — the aborted transaction rolled back the shared tx; re-open
        // the signed scope for the negative assertions.
        setTenant(tenantB);

        assertThat(jdbc.queryForObject(
                "SELECT is_system_managed FROM roles WHERE id = ?",
                Boolean.class, hijackTarget)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT role_origin FROM roles WHERE id = ?",
                String.class, hijackTarget)).isNull();
        assertThat(bindingCount(tenantB, "TENANT_ADMIN")).isZero();
    }

    // ---- fixtures ----

    private void insertTenant(UUID tenantId, String name, String subdomain) {
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId, name, subdomain);
    }

    private UUID insertRole(UUID tenantId, String code, String name, boolean systemManaged) {
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, code, name, description, status, "
                        + "is_system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                roleId, tenantId, code, name, "test fixture role", systemManaged);
        return roleId;
    }

    private void grantCapability(UUID roleId, UUID tenantId, String capabilityCode) {
        jdbc.update("INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                        + "SELECT ?, ?, ?, ac.id, CURRENT_TIMESTAMP FROM access_capabilities ac "
                        + "WHERE ac.code = ? AND ac.status = 'ACTIVE'",
                UUID.randomUUID(), tenantId, roleId, capabilityCode);
    }

    /**
     * G1 REV3 — opens ONE explicit transaction on the single fixture
     * connection and binds the HMAC-SIGNED tenant context (raw session GUC is
     * untrusted after V20261124_2; roles/role_capabilities are FORCE RLS with
     * the signed fail-closed policies). The transaction stays open so the
     * fixture writes, the provisioner run and the assertions share the same
     * signed scope; callers commit at the end of the scenario.
     */
    private void setTenant(UUID tenantId) {
        try {
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            try (java.sql.PreparedStatement ps = connection.prepareStatement(
                    "SELECT public.app_bind_tenant_ctx(?::uuid, encode(hmac((?::text||':'||txid_current()::text"
                            + "||':'||pg_backend_pid()::text)::bytea, decode(?,'hex'),'sha256'),'hex'))")) {
                ps.setString(1, tenantId.toString());
                ps.setString(2, tenantId.toString());
                ps.setString(3, FIXTURE_KEY_HEX);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !rs.getBoolean(1)) {
                        throw new AssertionError("signed fixture binding rejected");
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("signed fixture binding failed", e);
        }
    }

    private static final String FIXTURE_KEY_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    // ---- assertions helpers ----

    private List<String> capabilityCodes(UUID tenantId, UUID roleId) {
        return jdbc.query(
                "SELECT ac.code FROM role_capabilities rc "
                        + "JOIN access_capabilities ac ON ac.id = rc.capability_id "
                        + "WHERE rc.tenant_id = ? AND rc.role_id = ? ORDER BY ac.code",
                (rs, n) -> rs.getString(1), tenantId, roleId);
    }

    private long bindingCount(UUID tenantId, String templateKey) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_template_bindings "
                        + "WHERE tenant_id = ? AND template_key = ?",
                Long.class, tenantId, templateKey);
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private record TenantAdminRow(UUID id, String status, String roleOrigin, String templateKey) {}
}
