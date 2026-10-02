package com.sanad.platform.partner.membership;

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
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerMembershipBoundaryPostgresTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "PartnerMembershipBoundaryPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for PartnerMembershipBoundaryPostgresTest");
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
    void canonicalUserForeignKeyRejectsUnknownUser() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture f = fixture(c, 1);
            setPartnerContext(c, f.partnerId());

            assertThatThrownBy(() -> insertMembership(
                    c, f.partnerId(), UUID.randomUUID(), "PARTNER_USER"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23503"));
            c.rollback();
        }
    }

    @Test
    void membershipRoleCannotEncodePlatformPrivilege() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture f = fixture(c, 1);
            setPartnerContext(c, f.partnerId());

            assertThatThrownBy(() -> insertMembership(
                    c, f.partnerId(), f.userIds()[0], "PLATFORM_ADMIN"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23514"));
            c.rollback();
        }
    }

    @Test
    void missingPartnerContextFailsClosed() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture f = fixture(c, 1);
            setPartnerContext(c, f.partnerId());
            insertMembership(c, f.partnerId(), f.userIds()[0], "PARTNER_USER");

            clearPartnerContext(c);
            assertThat(count(c, "SELECT COUNT(*) FROM partner_memberships")).isZero();
            c.rollback();
        }
    }

    @Test
    void partnerASeesOnlyPartnerA() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture a = fixture(c, 1);
            Fixture b = fixture(c, 1);

            setPartnerContext(c, a.partnerId());
            insertMembership(c, a.partnerId(), a.userIds()[0], "PARTNER_USER");

            setPartnerContext(c, b.partnerId());
            insertMembership(c, b.partnerId(), b.userIds()[0], "PARTNER_USER");

            setPartnerContext(c, a.partnerId());
            assertThat(count(c, "SELECT COUNT(*) FROM partner_memberships")).isEqualTo(1);
            assertThat(count(c,
                    "SELECT COUNT(*) FROM partner_memberships WHERE partner_id = '" + b.partnerId() + "'::uuid"))
                    .isZero();
            c.rollback();
        }
    }

    @Test
    void forgedForeignPartnerInsertIsDeniedByRls() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture a = fixture(c, 1);
            Fixture b = fixture(c, 1);
            setPartnerContext(c, a.partnerId());

            assertThatThrownBy(() -> insertMembership(
                    c, b.partnerId(), b.userIds()[0], "PARTNER_USER"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("42501"));
            c.rollback();
        }
    }

    @Test
    void duplicateActiveMembershipIsRejected() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture f = fixture(c, 1);
            setPartnerContext(c, f.partnerId());
            insertMembership(c, f.partnerId(), f.userIds()[0], "PARTNER_USER");

            assertThatThrownBy(() -> insertMembership(
                    c, f.partnerId(), f.userIds()[0], "PARTNER_ADMIN"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23505"));
            c.rollback();
        }
    }

    @Test
    void cannotDemoteLastActivePartnerAdmin() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture f = fixture(c, 1);
            setPartnerContext(c, f.partnerId());
            UUID membershipId = insertMembership(
                    c, f.partnerId(), f.userIds()[0], "PARTNER_ADMIN");

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_memberships SET membership_role='PARTNER_USER', updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.userIds()[0]);
                    ps.setObject(2, membershipId);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> {
                  SQLException sql = (SQLException) ex;
                  assertThat(sql.getSQLState()).isEqualTo("23001");
                  assertThat(sql.getMessage()).contains("LAST_PARTNER_ADMIN");
              });
            c.rollback();
        }
    }

    @Test
    void cannotSuspendLastActivePartnerAdmin() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture f = fixture(c, 1);
            setPartnerContext(c, f.partnerId());
            UUID membershipId = insertMembership(
                    c, f.partnerId(), f.userIds()[0], "PARTNER_ADMIN");

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_memberships " +
                        "SET status='SUSPENDED', suspended_at=NOW(), suspended_reason='test', updated_by=? " +
                        "WHERE id=?")) {
                    ps.setObject(1, f.userIds()[0]);
                    ps.setObject(2, membershipId);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23001"));
            c.rollback();
        }
    }

    @Test
    void cannotDeleteLastActivePartnerAdmin() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture f = fixture(c, 1);
            setPartnerContext(c, f.partnerId());
            UUID membershipId = insertMembership(
                    c, f.partnerId(), f.userIds()[0], "PARTNER_ADMIN");

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM partner_memberships WHERE id=?")) {
                    ps.setObject(1, membershipId);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23001"));
            c.rollback();
        }
    }

    @Test
    void partnerScopedAuditIsVisibleOnlyToSamePartner() throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            Fixture a = fixture(c, 1);
            Fixture b = fixture(c, 1);

            setPartnerContext(c, a.partnerId());
            UUID membershipId = insertMembership(
                    c, a.partnerId(), a.userIds()[0], "PARTNER_USER");

            assertThat(count(c,
                    "SELECT COUNT(*) FROM authorization_change_events " +
                    "WHERE target_type='PARTNER_MEMBERSHIP' AND target_id='" + membershipId + "'::uuid"))
                    .isEqualTo(1);

            setPartnerContext(c, b.partnerId());
            assertThat(count(c,
                    "SELECT COUNT(*) FROM authorization_change_events " +
                    "WHERE target_type='PARTNER_MEMBERSHIP' AND target_id='" + membershipId + "'::uuid"))
                    .isZero();
            c.rollback();
        }
    }

    @Test
    void concurrentAdminRemovalCannotLeaveZeroAdmins() throws Exception {
        Fixture f;
        UUID admin1;
        UUID admin2;

        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            f = fixture(setup, 2);
            setPartnerContext(setup, f.partnerId());
            admin1 = insertMembership(setup, f.partnerId(), f.userIds()[0], "PARTNER_ADMIN");
            admin2 = insertMembership(setup, f.partnerId(), f.userIds()[1], "PARTNER_ADMIN");
            setup.commit();
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> deactivate1 = () -> deactivateAdmin(f.partnerId(), admin1, f.userIds()[0], start);
            Callable<Boolean> deactivate2 = () -> deactivateAdmin(f.partnerId(), admin2, f.userIds()[1], start);

            Future<Boolean> r1 = pool.submit(deactivate1);
            Future<Boolean> r2 = pool.submit(deactivate2);
            start.countDown();

            boolean first = r1.get();
            boolean second = r2.get();

            assertThat(first ^ second)
                    .as("exactly one concurrent admin removal must succeed")
                    .isTrue();

            try (Connection verify = connection()) {
                verify.setAutoCommit(false);
                setPartnerContext(verify, f.partnerId());
                assertThat(count(verify,
                        "SELECT COUNT(*) FROM partner_memberships " +
                        "WHERE membership_role='PARTNER_ADMIN' AND status='ACTIVE'"))
                        .isEqualTo(1);
                verify.rollback();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean deactivateAdmin(
            UUID partnerId,
            UUID membershipId,
            UUID actorId,
            CountDownLatch start) throws Exception {
        start.await();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPartnerContext(c, partnerId);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE partner_memberships SET status='INACTIVE', updated_by=? WHERE id=?")) {
                ps.setObject(1, actorId);
                ps.setObject(2, membershipId);
                ps.executeUpdate();
                c.commit();
                return true;
            } catch (SQLException ex) {
                c.rollback();
                assertThat(ex.getSQLState()).isEqualTo("23001");
                return false;
            }
        }
    }

    private Fixture fixture(Connection c, int userCount) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                "VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "W2-T3 " + tenantId);
            ps.setString(3, "w2t3-" + tenantId.toString().substring(0, 12));
            ps.executeUpdate();
        }

        UUID[] userIds = new UUID[userCount];
        for (int i = 0; i < userCount; i++) {
            userIds[i] = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) " +
                    "VALUES (?,?,?,?,'ACTIVE',NOW(),NOW())")) {
                ps.setObject(1, userIds[i]);
                ps.setObject(2, tenantId);
                ps.setString(3, "w2t3-" + userIds[i] + "@example.invalid");
                ps.setString(4, "W2-T3 User " + i);
                ps.executeUpdate();
            }
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO partners (id,partner_type,status,created_by) VALUES (?,'PARTNER','ACTIVE',?)")) {
            ps.setObject(1, partnerId);
            ps.setObject(2, userIds[0]);
            ps.executeUpdate();
        }

        return new Fixture(tenantId, partnerId, userIds);
    }

    private UUID insertMembership(
            Connection c,
            UUID partnerId,
            UUID userId,
            String role) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO partner_memberships " +
                "(id,partner_id,user_id,membership_role,created_by,updated_by) " +
                "VALUES (?,?,?,?,?,?)")) {
            ps.setObject(1, id);
            ps.setObject(2, partnerId);
            ps.setObject(3, userId);
            ps.setString(4, role);
            ps.setObject(5, userId);
            ps.setObject(6, userId);
            ps.executeUpdate();
        }
        return id;
    }

    private void setPartnerContext(Connection c, UUID partnerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT set_config('app.partner_id', ?, true)")) {
            ps.setString(1, partnerId.toString());
            ps.execute();
        }
    }

    private void clearPartnerContext(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.partner_id', '', true)");
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

    private record Fixture(UUID tenantId, UUID partnerId, UUID[] userIds) {}
}
