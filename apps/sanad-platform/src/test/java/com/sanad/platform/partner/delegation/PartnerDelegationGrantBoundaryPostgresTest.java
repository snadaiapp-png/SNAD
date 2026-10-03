package com.sanad.platform.partner.delegation;

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
 * Database-level boundary evidence for Wave 2 Task 5
 * (partner_delegation_grants + partner_delegation_capabilities).
 *
 * Proves: FORCE RLS fail-closed isolation (platform writes, partner read-only
 * on own rows, tenant-plane and missing contexts see nothing), explicit
 * whitelist registry with no business-data capabilities, identity
 * immutability, terminal REVOKED, forbidden physical DELETE, uniqueness of the
 * ACTIVE delegation, binding/partner cross-table validation, deterministic
 * re-grant, and partner-scoped audit isolation.
 */
class PartnerDelegationGrantBoundaryPostgresTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String PLATFORM_TENANT_ID =
            "00000000-0000-0000-0000-000000000001";

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "PartnerDelegationGrantBoundaryPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for PartnerDelegationGrantBoundaryPostgresTest");

        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
        Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL), USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load()
                .migrate();
    }

    @Test
    void tablesAreForceRlsAndGrantsCarryNoBusinessDataColumns() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            for (String table : new String[]{
                    "partner_delegation_grants", "partner_delegation_capabilities"}) {
                try (ResultSet rs = s.executeQuery(
                        "SELECT relrowsecurity, relforcerowsecurity " +
                        "FROM pg_class WHERE relname='" + table + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("relrowsecurity")).isTrue();
                    assertThat(rs.getBoolean("relforcerowsecurity")).isTrue();
                }
            }

            for (String forbidden : new String[]{"user_id", "role_id", "capability_id"}) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_name='partner_delegation_grants' AND column_name=?")) {
                    ps.setString(1, forbidden);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getLong(1))
                                .as("delegation grant must not imply authorization via %s", forbidden)
                                .isZero();
                    }
                }
            }
        }
    }

    @Test
    void delegationRegistryIsAnExplicitWhitelistWithoutBusinessDataCapabilities()
            throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            try (Statement s = c.createStatement()) {
                try (ResultSet rs = s.executeQuery(
                        "SELECT COUNT(*) FROM partner_delegation_capabilities")) {
                    rs.next();
                    assertThat(rs.getLong(1))
                            .as("exactly the 11 delegated capabilities from the W2-T5 registry")
                            .isEqualTo(11);
                }

                try (ResultSet rs = s.executeQuery(
                        "SELECT code FROM partner_delegation_capabilities " +
                        "WHERE code LIKE 'CRM.%' OR code LIKE 'HRM.%' OR code LIKE 'PAYROLL.%' " +
                        "OR code LIKE 'ACCOUNTING.%' OR code LIKE 'ERP.%'")) {
                    assertThat(rs.next())
                            .as("business-data capabilities must never be delegatable")
                            .isFalse();
                }

                try (ResultSet rs = s.executeQuery(
                        "SELECT COUNT(*) FROM partner_delegation_capabilities " +
                        "WHERE status='ACTIVE' AND code='TENANT.CREATE'")) {
                    rs.next();
                    assertThat(rs.getLong(1)).isEqualTo(1);
                }
            }
            c.rollback();
        }
    }

    @Test
    void platformControlPlaneCanCreateDelegationGrantWithAudit() throws Exception {
        Fixture f = fixture();
        UUID grant;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            grant = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);
            assertThat(count(c,
                    "SELECT COUNT(*) FROM partner_delegation_grants WHERE id='" + grant + "'::uuid"))
                    .isEqualTo(1);
            c.commit();
        }

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            assertThat(count(c,
                    "SELECT COUNT(*) FROM authorization_change_events " +
                    "WHERE target_type='PARTNER_DELEGATION' AND target_id='" + grant + "'::uuid " +
                    "AND event_type='PARTNER_DELEGATION_CREATED'"))
                    .isEqualTo(1);
            c.rollback();
        }
    }

    @Test
    void grantWithoutActiveInWindowBindingIsRejected() throws Exception {
        Fixture f = fixture();

        // No binding at all for the tenant.
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            assertThatThrownBy(() ->
                    insertGrant(c, f.partnerB(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> {
                        SQLException sql = (SQLException) ex;
                        assertThat(sql.getSQLState()).isEqualTo("23001");
                        assertThat(sql.getMessage()).contains("BINDING_INACTIVE");
                    });
            c.rollback();
        }

        // Binding exists for partnerA but grant is requested for partnerB.
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            assertThatThrownBy(() ->
                    insertGrant(c, f.partnerB(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23001"));
            c.rollback();
        }
    }

    @Test
    void grantForInactivePartnerIsRejected() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            suspendPartner(c, f.partnerB(), f.actor());
            assertThatThrownBy(() ->
                    insertGrant(c, f.partnerB(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> {
                        SQLException sql = (SQLException) ex;
                        assertThat(sql.getSQLState()).isEqualTo("23001");
                        assertThat(sql.getMessage()).contains("PARTNER_INACTIVE");
                    });
            c.rollback();
        }
    }

    @Test
    void partnerContextIsReadOnlyAndIsolated() throws Exception {
        Fixture f = fixture();
        UUID grant;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            grant = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);
            c.commit();
        }

        // Partner A reads its own grant.
        try (Connection a = connection()) {
            a.setAutoCommit(false);
            setPartnerContext(a, f.partnerA());
            assertThat(count(a,
                    "SELECT COUNT(*) FROM partner_delegation_grants WHERE id='" + grant + "'::uuid"))
                    .isEqualTo(1);
            a.rollback();
        }

        // Partner B sees nothing.
        try (Connection b = connection()) {
            b.setAutoCommit(false);
            setPartnerContext(b, f.partnerB());
            assertThat(count(b,
                    "SELECT COUNT(*) FROM partner_delegation_grants WHERE id='" + grant + "'::uuid"))
                    .isZero();
            b.rollback();
        }

        // Partner INSERT is denied (no partner INSERT policy).
        try (Connection a = connection()) {
            a.setAutoCommit(false);
            setPartnerContext(a, f.partnerA());
            assertThatThrownBy(() ->
                    insertGrant(a, f.partnerA(), f.tenantId(), "TENANT.SUSPEND", f.actor(), null, null))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                            .isEqualTo("42501"));
            a.rollback();
        }

        // Partner UPDATE affects zero rows (RLS hides rows from the USING pass).
        try (Connection a = connection()) {
            a.setAutoCommit(false);
            setPartnerContext(a, f.partnerA());
            try (PreparedStatement ps = a.prepareStatement(
                    "UPDATE partner_delegation_grants SET status='INACTIVE' WHERE id=?")) {
                ps.setObject(1, grant);
                int updated = ps.executeUpdate();
                assertThat(updated).as("partner UPDATE must affect 0 rows").isZero();
            }
            a.rollback();
        }

        // Tenant-plane context has NO branch on delegation grants.
        try (Connection t = connection()) {
            t.setAutoCommit(false);
            setTenantContext(t, f.tenantId());
            assertThat(count(t, "SELECT COUNT(*) FROM partner_delegation_grants")).isZero();
            t.rollback();
        }
    }

    @Test
    void missingContextFailsClosed() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);
            c.commit();
        }

        try (Connection noContext = connection()) {
            assertThat(count(noContext,
                    "SELECT COUNT(*) FROM partner_delegation_grants")).isZero();
        }
    }

    @Test
    void grantIdentityCannotBeReassignedInPlace() throws Exception {
        Fixture f = fixture();

        // partner_id reassignment is rejected. Each rejected mutation runs on
        // its own connection: a raised SQL error aborts its transaction, and
        // PostgreSQL reports 25P02 for any later statement in that transaction.
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID grant = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_delegation_grants SET partner_id=?, updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.partnerB());
                    ps.setObject(2, f.actor());
                    ps.setObject(3, grant);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> {
                  SQLException sql = (SQLException) ex;
                  assertThat(sql.getSQLState()).isEqualTo("23001");
                  assertThat(sql.getMessage()).contains("IDENTITY_IMMUTABLE");
              });
            c.rollback();
        }

        // capability_code reassignment is rejected too.
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID grant = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_delegation_grants SET capability_code='TENANT.SUSPEND', " +
                        "updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.actor());
                    ps.setObject(2, grant);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23001"));
            c.rollback();
        }
    }

    @Test
    void revokedGrantIsTerminal() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID grant = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);
            revokeGrant(c, grant, f.actor());

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_delegation_grants SET status='ACTIVE', " +
                        "revoked_at=NULL, revoked_reason=NULL, updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.actor());
                    ps.setObject(2, grant);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> {
                  SQLException sql = (SQLException) ex;
                  assertThat(sql.getSQLState()).isEqualTo("23001");
                  assertThat(sql.getMessage()).contains("TERMINAL");
              });
            c.rollback();
        }
    }

    @Test
    void suspendedGrantRequiresTimestampAndReason() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID grant = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_delegation_grants SET status='SUSPENDED', updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.actor());
                    ps.setObject(2, grant);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23514"));
            c.rollback();
        }
    }

    @Test
    void onlyOneActiveGrantPerPartnerTenantCapability() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);

            assertThatThrownBy(() ->
                    insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23505"));
            c.rollback();
        }
    }

    @Test
    void regrantAfterRevocationIsDeterministic() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID first = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);
            revokeGrant(c, first, f.actor());

            UUID second = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);
            assertThat(second).isNotEqualTo(first);
            assertThat(count(c,
                    "SELECT COUNT(*) FROM partner_delegation_grants " +
                    "WHERE partner_id='" + f.partnerA() + "'::uuid " +
                    "AND tenant_id='" + f.tenantId() + "'::uuid " +
                    "AND capability_code='TENANT.ACTIVATE' AND status='ACTIVE'"))
                    .isEqualTo(1);
            c.rollback();
        }
    }

    @Test
    void physicalDeleteIsDenied() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID grant = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);

            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM partner_delegation_grants WHERE id=?")) {
                ps.setObject(1, grant);
                int deleted = ps.executeUpdate();
                assertThat(deleted).as("platform physical delete must affect 0 rows").isZero();
            }

            setPartnerContext(c, f.partnerA());
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM partner_delegation_grants WHERE id=?")) {
                ps.setObject(1, grant);
                int deleted = ps.executeUpdate();
                assertThat(deleted).as("partner physical delete must affect 0 rows").isZero();
            }

            setPlatformContext(c);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT count(*) FROM partner_delegation_grants WHERE id=?")) {
                ps.setObject(1, grant);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("delegation grant must survive physical delete attempts")
                            .isEqualTo(1);
                }
            }
            c.rollback();
        }
    }

    @Test
    void grantAuditIsVisibleOnlyToBoundPartnerAndNeverToTenantPlane() throws Exception {
        Fixture f = fixture();
        UUID grant;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            grant = insertGrant(c, f.partnerA(), f.tenantId(), "TENANT.ACTIVATE", f.actor(), null, null);
            c.commit();
        }

        String auditQuery =
                "SELECT COUNT(*) FROM authorization_change_events " +
                "WHERE target_type='PARTNER_DELEGATION' " +
                "AND target_id='" + grant + "'::uuid";

        try (Connection a = connection()) {
            a.setAutoCommit(false);
            setPartnerContext(a, f.partnerA());
            assertThat(count(a, auditQuery)).isEqualTo(1);
            a.rollback();
        }

        try (Connection b = connection()) {
            b.setAutoCommit(false);
            setPartnerContext(b, f.partnerB());
            assertThat(count(b, auditQuery)).isZero();
            b.rollback();
        }

        try (Connection t = connection()) {
            t.setAutoCommit(false);
            setTenantContext(t, f.tenantId());
            assertThat(count(t, auditQuery))
                    .as("tenant plane must not inspect partner control-plane audit")
                    .isZero();
            t.rollback();
        }
    }

    @Test
    void validityWindowMustBeSane() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO partner_delegation_grants " +
                        "(partner_id,tenant_id,capability_code,valid_from,valid_until,created_by) " +
                        "VALUES (?,?, 'TENANT.ACTIVATE', NOW(), NOW(), ?)")) {
                    ps.setObject(1, f.partnerA());
                    ps.setObject(2, f.tenantId());
                    ps.setObject(3, f.actor());
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23514"));
            c.rollback();
        }
    }

    @Test
    void businessDataCapabilityIsRejectedByRegistryForeignKey() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            assertThatThrownBy(() ->
                    insertGrant(c, f.partnerA(), f.tenantId(), "CRM.READ", f.actor(), null, null))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23503"));
            c.rollback();
        }
    }

    // ------------------------------------------------------------------
    // fixture helpers (mirror PartnerTenantBindingPostgresTest)
    // ------------------------------------------------------------------

    private Fixture fixture() throws SQLException {
        UUID tenant = UUID.randomUUID();
        UUID partnerA = UUID.randomUUID();
        UUID partnerB = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            // Binding inserts are platform-only under FORCE RLS.
            setPlatformContext(c);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                    "VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
                ps.setObject(1, tenant);
                ps.setString(2, "W2-T5 " + tenant);
                ps.setString(3, "w2t5-" + tenant.toString().substring(0, 12));
                ps.executeUpdate();
            }
            for (UUID partner : new UUID[]{partnerA, partnerB}) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO partners (id,partner_type,status,created_by) " +
                        "VALUES (?,'PARTNER','ACTIVE',?)")) {
                    ps.setObject(1, partner);
                    ps.setObject(2, actor);
                    ps.executeUpdate();
                }
            }
            // partnerA is bound to the tenant with an ACTIVE in-window binding.
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO partner_tenant_bindings " +
                    "(id,partner_id,tenant_id,created_by,updated_by) VALUES (?,?,?,?,?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, partnerA);
                ps.setObject(3, tenant);
                ps.setObject(4, actor);
                ps.setObject(5, actor);
                ps.executeUpdate();
            }
            c.commit();
        }
        return new Fixture(tenant, partnerA, partnerB, actor);
    }

    private UUID insertGrant(
            Connection c, UUID partnerId, UUID tenantId, String capabilityCode,
            UUID actor, java.time.Instant validFrom, java.time.Instant validUntil)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO partner_delegation_grants " +
                "(id,partner_id,tenant_id,capability_code,valid_from,valid_until,created_by,updated_by) " +
                "VALUES (?,?,?,?,COALESCE(?, NOW()),?,?,?)")) {
            ps.setObject(1, id);
            ps.setObject(2, partnerId);
            ps.setObject(3, tenantId);
            ps.setString(4, capabilityCode);
            ps.setTimestamp(5, validFrom == null ? null : java.sql.Timestamp.from(validFrom));
            ps.setTimestamp(6, validUntil == null ? null : java.sql.Timestamp.from(validUntil));
            ps.setObject(7, actor);
            ps.setObject(8, actor);
            ps.executeUpdate();
        }
        return id;
    }

    private void revokeGrant(Connection c, UUID grantId, UUID actor) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE partner_delegation_grants SET status='REVOKED', " +
                "revoked_at=NOW(), revoked_reason='test revocation', updated_by=? WHERE id=?")) {
            ps.setObject(1, actor);
            ps.setObject(2, grantId);
            ps.executeUpdate();
        }
    }

    private void suspendPartner(Connection c, UUID partnerId, UUID actor) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE partners SET status='SUSPENDED', suspended_at=NOW(), " +
                "suspended_reason='test suspension', updated_by=? WHERE id=?")) {
            ps.setObject(1, actor);
            ps.setObject(2, partnerId);
            ps.executeUpdate();
        }
    }

    private void setPlatformContext(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, PLATFORM_TENANT_ID);
            ps.execute();
        }
    }

    private void setPartnerContext(Connection c, UUID partnerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT set_config('app.partner_id', ?, true)")) {
            ps.setString(1, partnerId.toString());
            ps.execute();
        }
    }

    private void setTenantContext(Connection c, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private long count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL), USER, PASSWORD);
    }

    private record Fixture(UUID tenantId, UUID partnerA, UUID partnerB, UUID actor) {}
}
