package com.sanad.platform.partner.binding;

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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerTenantBindingPostgresTest {

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
                    "PartnerTenantBindingPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for PartnerTenantBindingPostgresTest");

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
    void tableIsForceRlsAndContainsNoAuthorizationGrantColumns() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "SELECT relrowsecurity, relforcerowsecurity " +
                    "FROM pg_class WHERE relname='partner_tenant_bindings'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("relrowsecurity")).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity")).isTrue();
            }

            for (String forbidden : new String[]{"user_id", "role_id", "capability_id", "delegation_id"}) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_name='partner_tenant_bindings' AND column_name=?")) {
                    ps.setString(1, forbidden);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getLong(1))
                                .as("binding must not imply authorization via %s", forbidden)
                                .isZero();
                    }
                }
            }
        }
    }

    @Test
    void platformControlPlaneCanCreateBinding() throws Exception {
        Fixture f = fixture();
        UUID binding;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            binding = insertBinding(c, f.partnerA(), f.tenantId(), f.actor());
            assertThat(count(c,
                    "SELECT COUNT(*) FROM partner_tenant_bindings WHERE id='" + binding + "'::uuid"))
                    .isEqualTo(1);
            c.commit();
        }
    }

    @Test
    void partnerCanReadOnlyItsOwnBinding() throws Exception {
        Fixture f = fixture();
        UUID binding;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            binding = insertBinding(c, f.partnerA(), f.tenantId(), f.actor());
            c.commit();
        }

        try (Connection a = connection()) {
            a.setAutoCommit(false);
            setPartnerContext(a, f.partnerA());
            assertThat(count(a,
                    "SELECT COUNT(*) FROM partner_tenant_bindings WHERE id='" + binding + "'::uuid"))
                    .isEqualTo(1);
            a.rollback();
        }

        try (Connection b = connection()) {
            b.setAutoCommit(false);
            setPartnerContext(b, f.partnerB());
            assertThat(count(b,
                    "SELECT COUNT(*) FROM partner_tenant_bindings WHERE id='" + binding + "'::uuid"))
                    .isZero();
            b.rollback();
        }
    }

    @Test
    void missingContextFailsClosed() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            insertBinding(c, f.partnerA(), f.tenantId(), f.actor());
            c.commit();
        }

        try (Connection noContext = connection()) {
            assertThat(count(noContext, "SELECT COUNT(*) FROM partner_tenant_bindings")).isZero();
        }
    }

    @Test
    void partnerCannotSelfBindArbitraryTenant() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPartnerContext(c, f.partnerA());
            assertThatThrownBy(() ->
                    insertBinding(c, f.partnerA(), f.tenantId(), f.actor()))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                            .isEqualTo("42501"));
            c.rollback();
        }
    }

    @Test
    void canonicalForeignKeysRejectUnknownPartnerAndTenant() throws Exception {
        Fixture f = fixture();

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            assertThatThrownBy(() ->
                    insertBinding(c, UUID.randomUUID(), f.tenantId(), f.actor()))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                            .isEqualTo("23503"));
            c.rollback();
        }

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            assertThatThrownBy(() ->
                    insertBinding(c, f.partnerA(), UUID.randomUUID(), f.actor()))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                            .isEqualTo("23503"));
            c.rollback();
        }
    }

    @Test
    void tenantCannotHaveTwoActivePartnerBindings() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            insertBinding(c, f.partnerA(), f.tenantId(), f.actor());

            assertThatThrownBy(() ->
                    insertBinding(c, f.partnerB(), f.tenantId(), f.actor()))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                            .isEqualTo("23505"));
            c.rollback();
        }
    }

    @Test
    void bindingIdentityCannotBeReassignedInPlace() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID binding = insertBinding(c, f.partnerA(), f.tenantId(), f.actor());

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_tenant_bindings SET partner_id=?, updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.partnerB());
                    ps.setObject(2, f.actor());
                    ps.setObject(3, binding);
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
    }

    @Test
    void terminatedBindingIsTerminalAndAllowsGovernedRebind() throws Exception {
        Fixture f = fixture();
        UUID first;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            first = insertBinding(c, f.partnerA(), f.tenantId(), f.actor());
            terminate(c, first, f.actor());
            UUID second = insertBinding(c, f.partnerB(), f.tenantId(), f.actor());
            assertThat(second).isNotEqualTo(first);

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_tenant_bindings SET status='ACTIVE', " +
                        "terminated_at=NULL, terminated_reason=NULL, updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.actor());
                    ps.setObject(2, first);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                      .isEqualTo("23001"));
            c.rollback();
        }
    }

    @Test
    void suspendedLifecycleRequiresTimestampAndReason() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID binding = insertBinding(c, f.partnerA(), f.tenantId(), f.actor());

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_tenant_bindings SET status='SUSPENDED', updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.actor());
                    ps.setObject(2, binding);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                      .isEqualTo("23514"));
            c.rollback();
        }
    }

    @Test
    void physicalDeleteIsDenied() throws Exception {
        Fixture f = fixture();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            UUID binding = insertBinding(c, f.partnerA(), f.tenantId(), f.actor());

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM partner_tenant_bindings WHERE id=?")) {
                    ps.setObject(1, binding);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class);
            c.rollback();
        }
    }

    @Test
    void bindingAuditIsVisibleOnlyToBoundPartner() throws Exception {
        Fixture f = fixture();
        UUID binding;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            binding = insertBinding(c, f.partnerA(), f.tenantId(), f.actor());
            c.commit();
        }

        String auditQuery =
                "SELECT COUNT(*) FROM authorization_change_events " +
                "WHERE target_type='PARTNER_TENANT_BINDING' " +
                "AND target_id='" + binding + "'::uuid";

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
    }

    @Test
    void concurrentCompetingBindsCannotCreateTwoActiveOwners() throws Exception {
        Fixture f = fixture();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> a = () -> tryBind(f.partnerA(), f.tenantId(), f.actor(), start);
            Callable<Boolean> b = () -> tryBind(f.partnerB(), f.tenantId(), f.actor(), start);

            Future<Boolean> ra = pool.submit(a);
            Future<Boolean> rb = pool.submit(b);
            start.countDown();

            boolean first = ra.get();
            boolean second = rb.get();
            assertThat(first ^ second)
                    .as("exactly one competing ACTIVE partner binding must commit")
                    .isTrue();

            try (Connection verify = connection()) {
                verify.setAutoCommit(false);
                setPlatformContext(verify);
                assertThat(count(verify,
                        "SELECT COUNT(*) FROM partner_tenant_bindings " +
                        "WHERE tenant_id='" + f.tenantId() + "'::uuid AND status='ACTIVE'"))
                        .isEqualTo(1);
                verify.rollback();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean tryBind(
            UUID partnerId, UUID tenantId, UUID actor, CountDownLatch start) throws Exception {
        start.await();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            try {
                insertBinding(c, partnerId, tenantId, actor);
                c.commit();
                return true;
            } catch (SQLException ex) {
                c.rollback();
                assertThat(ex.getSQLState()).isEqualTo("23505");
                return false;
            }
        }
    }

    private Fixture fixture() throws SQLException {
        UUID tenant = UUID.randomUUID();
        UUID partnerA = UUID.randomUUID();
        UUID partnerB = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                    "VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
                ps.setObject(1, tenant);
                ps.setString(2, "W2-T4 " + tenant);
                ps.setString(3, "w2t4-" + tenant.toString().substring(0, 12));
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
            c.commit();
        }
        return new Fixture(tenant, partnerA, partnerB, actor);
    }

    private UUID insertBinding(
            Connection c, UUID partnerId, UUID tenantId, UUID actor) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO partner_tenant_bindings " +
                "(id,partner_id,tenant_id,created_by,updated_by) VALUES (?,?,?,?,?)")) {
            ps.setObject(1, id);
            ps.setObject(2, partnerId);
            ps.setObject(3, tenantId);
            ps.setObject(4, actor);
            ps.setObject(5, actor);
            ps.executeUpdate();
        }
        return id;
    }

    private void terminate(Connection c, UUID bindingId, UUID actor) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE partner_tenant_bindings SET status='TERMINATED', " +
                "terminated_at=NOW(), terminated_reason='governed rebind', updated_by=? WHERE id=?")) {
            ps.setObject(1, actor);
            ps.setObject(2, bindingId);
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
