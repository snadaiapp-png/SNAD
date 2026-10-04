package com.sanad.platform.partner.recovery;

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
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerRecoverySafetyPostgresTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final UUID CONTROL_TENANT =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "PartnerRecoverySafetyPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for PartnerRecoverySafetyPostgresTest");

        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
        Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load()
                .migrate();
    }

    @Test
    void tableIsForceRlsAndOnlyCanonicalRecoverCapabilityIsAccepted() throws Exception {
        Fixture f;
        UUID membership;
        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            f = fixture(setup, 1);
            membership = activeMembership(
                    setup, f.partnerId(), f.userIds()[0], "PARTNER_USER");

            try (Statement statement = setup.createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT relrowsecurity, relforcerowsecurity
                           FROM pg_class
                          WHERE relname='partner_membership_capabilities'
                         """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("relrowsecurity")).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity")).isTrue();
            }
            setup.commit();
        }

        try (Connection platform = connection()) {
            platform.setAutoCommit(false);
            setPlatformContext(platform);
            UUID wrongCapability = capabilityId(platform, "AUTHORIZATION.BREAK_GLASS");
            assertThatThrownBy(() -> insertRecovery(
                    platform, f.partnerId(), membership,
                    wrongCapability, f.userIds()[0], null, null))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> {
                        SQLException sql = (SQLException) ex;
                        assertThat(sql.getSQLState()).isEqualTo("23001");
                        assertThat(sql.getMessage()).contains("PARTNER_RECOVERY_CAPABILITY_INVALID");
                    });
            platform.rollback();
        }
    }

    @Test
    void inactiveMembershipCannotReceiveActiveRecoveryGrant() throws Exception {
        Fixture f;
        UUID membership;
        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            f = fixture(setup, 1);
            membership = activeMembership(
                    setup, f.partnerId(), f.userIds()[0], "PARTNER_USER");
            setPartnerContext(setup, f.partnerId());
            try (PreparedStatement ps = setup.prepareStatement(
                    "UPDATE partner_memberships SET status='INACTIVE', updated_by=? WHERE id=?")) {
                ps.setObject(1, f.userIds()[0]);
                ps.setObject(2, membership);
                ps.executeUpdate();
            }
            setup.commit();
        }

        try (Connection platform = connection()) {
            platform.setAutoCommit(false);
            setPlatformContext(platform);
            UUID recover = capabilityId(platform, "AUTHORIZATION.RECOVER");
            assertThatThrownBy(() -> insertRecovery(
                    platform, f.partnerId(), membership,
                    recover, f.userIds()[0], null, null))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getMessage())
                            .contains("PARTNER_RECOVERY_MEMBERSHIP_INACTIVE"));
            platform.rollback();
        }
    }

    @Test
    void cannotRevokeFinalRecoveryPathAndRejectedMutationWritesNoAudit() throws Exception {
        Fixture f;
        UUID grant;
        UUID membership;
        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            f = fixture(setup, 1);
            membership = activeMembership(
                    setup, f.partnerId(), f.userIds()[0], "PARTNER_USER");
            setup.commit();
        }
        try (Connection platform = connection()) {
            platform.setAutoCommit(false);
            setPlatformContext(platform);
            grant = insertRecovery(platform, f.partnerId(), membership,
                    capabilityId(platform, "AUTHORIZATION.RECOVER"),
                    f.userIds()[0], null, null);
            platform.commit();
        }

        long auditBefore = auditCount(f.partnerId(), grant);

        try (Connection mutate = connection()) {
            mutate.setAutoCommit(false);
            setPlatformContext(mutate);
            assertThatThrownBy(() -> revokeRecovery(
                    mutate, grant, f.userIds()[0], "attempt final recovery removal"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> {
                        SQLException sql = (SQLException) ex;
                        assertThat(sql.getSQLState()).isEqualTo("23001");
                        assertThat(sql.getMessage())
                                .contains("FINAL_PARTNER_RECOVERY_CAPABILITY_REQUIRED");
                    });
            mutate.rollback();
        }

        assertThat(activeRecoveryCount(f.partnerId())).isEqualTo(1);
        assertThat(auditCount(f.partnerId(), grant)).isEqualTo(auditBefore);
    }

    @Test
    void oneOfTwoRecoveryPathsMayBeRevoked() throws Exception {
        Fixture f;
        UUID grant1;
        UUID grant2;
        UUID m1;
        UUID m2;
        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            f = fixture(setup, 2);
            m1 = activeMembership(setup, f.partnerId(), f.userIds()[0], "PARTNER_USER");
            m2 = activeMembership(setup, f.partnerId(), f.userIds()[1], "PARTNER_USER");
            setup.commit();
        }
        try (Connection platform = connection()) {
            platform.setAutoCommit(false);
            setPlatformContext(platform);
            UUID recover = capabilityId(platform, "AUTHORIZATION.RECOVER");
            grant1 = insertRecovery(platform, f.partnerId(), m1, recover, f.userIds()[0], null, null);
            grant2 = insertRecovery(platform, f.partnerId(), m2, recover, f.userIds()[1], null, null);
            platform.commit();
        }

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            revokeRecovery(c, grant1, f.userIds()[0], "redundant path removal");
            c.commit();
        }

        assertThat(activeRecoveryCount(f.partnerId())).isEqualTo(1);
        assertThat(recoveryStatus(grant1)).isEqualTo("REVOKED");
        assertThat(recoveryStatus(grant2)).isEqualTo("ACTIVE");
    }

    @Test
    void expiredRecoveryPathDoesNotSatisfySurvivalInvariant() throws Exception {
        Fixture f;
        UUID currentGrant;
        UUID m1;
        UUID m2;
        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            f = fixture(setup, 2);
            m1 = activeMembership(setup, f.partnerId(), f.userIds()[0], "PARTNER_USER");
            m2 = activeMembership(setup, f.partnerId(), f.userIds()[1], "PARTNER_USER");
            setup.commit();
        }
        try (Connection platform = connection()) {
            platform.setAutoCommit(false);
            setPlatformContext(platform);
            UUID recover = capabilityId(platform, "AUTHORIZATION.RECOVER");
            currentGrant = insertRecovery(platform, f.partnerId(), m1, recover,
                    f.userIds()[0], null, null);
            insertRecovery(platform, f.partnerId(), m2, recover, f.userIds()[1],
                    OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusHours(1));
            platform.commit();
        }

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            assertThatThrownBy(() -> revokeRecovery(
                    c, currentGrant, f.userIds()[0], "expired backup must not count"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getMessage())
                            .contains("FINAL_PARTNER_RECOVERY_CAPABILITY_REQUIRED"));
            c.rollback();
        }

        assertThat(activeRecoveryCount(f.partnerId())).isEqualTo(1);
    }

    @Test
    void membershipLifecycleCannotInvalidateFinalRecoveryPath() throws Exception {
        Fixture f;
        UUID membership;
        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            f = fixture(setup, 2);
            // Separate admin ensures LAST_PARTNER_ADMIN is not the blocker.
            activeMembership(setup, f.partnerId(), f.userIds()[0], "PARTNER_ADMIN");
            membership = activeMembership(
                    setup, f.partnerId(), f.userIds()[1], "PARTNER_USER");
            setup.commit();
        }
        try (Connection platform = connection()) {
            platform.setAutoCommit(false);
            setPlatformContext(platform);
            insertRecovery(platform, f.partnerId(), membership,
                    capabilityId(platform, "AUTHORIZATION.RECOVER"),
                    f.userIds()[1], null, null);
            platform.commit();
        }

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPartnerContext(c, f.partnerId());
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE partner_memberships SET status='INACTIVE', updated_by=? WHERE id=?")) {
                    ps.setObject(1, f.userIds()[0]);
                    ps.setObject(2, membership);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(ex -> {
                  SQLException sql = (SQLException) ex;
                  assertThat(sql.getSQLState()).isEqualTo("23001");
                  assertThat(sql.getMessage())
                          .contains("FINAL_PARTNER_RECOVERY_CAPABILITY_REQUIRED");
              });
            c.rollback();
        }

        assertThat(activeRecoveryCount(f.partnerId())).isEqualTo(1);
    }

    @Test
    void partnerAReadsOnlyOwnRecoveryAndCannotWrite() throws Exception {
        Fixture a;
        Fixture b;
        UUID grantA;
        UUID ma;
        UUID mb;
        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            a = fixture(setup, 1);
            b = fixture(setup, 1);
            ma = activeMembership(setup, a.partnerId(), a.userIds()[0], "PARTNER_USER");
            mb = activeMembership(setup, b.partnerId(), b.userIds()[0], "PARTNER_USER");
            setup.commit();
        }
        try (Connection platform = connection()) {
            platform.setAutoCommit(false);
            setPlatformContext(platform);
            UUID recover = capabilityId(platform, "AUTHORIZATION.RECOVER");
            grantA = insertRecovery(platform, a.partnerId(), ma, recover, a.userIds()[0], null, null);
            insertRecovery(platform, b.partnerId(), mb, recover, b.userIds()[0], null, null);
            platform.commit();
        }

        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPartnerContext(c, a.partnerId());
            assertThat(count(c, "SELECT COUNT(*) FROM partner_membership_capabilities"))
                    .isEqualTo(1);
            assertThat(count(c, "SELECT COUNT(*) FROM partner_membership_capabilities " +
                    "WHERE partner_id='" + b.partnerId() + "'::uuid")).isZero();

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE partner_membership_capabilities SET updated_by=? WHERE id=?")) {
                ps.setObject(1, a.userIds()[0]);
                ps.setObject(2, grantA);
                assertThat(ps.executeUpdate()).isZero();
            }
            c.rollback();
        }
    }

    @Test
    void concurrentRecoveryRemovalPreservesOnePath() throws Exception {
        Fixture f;
        UUID grant1;
        UUID grant2;
        UUID m1;
        UUID m2;
        try (Connection setup = connection()) {
            setup.setAutoCommit(false);
            f = fixture(setup, 2);
            m1 = activeMembership(setup, f.partnerId(), f.userIds()[0], "PARTNER_USER");
            m2 = activeMembership(setup, f.partnerId(), f.userIds()[1], "PARTNER_USER");
            setup.commit();
        }
        try (Connection platform = connection()) {
            platform.setAutoCommit(false);
            setPlatformContext(platform);
            UUID recover = capabilityId(platform, "AUTHORIZATION.RECOVER");
            grant1 = insertRecovery(platform, f.partnerId(), m1, recover,
                    f.userIds()[0], null, null);
            grant2 = insertRecovery(platform, f.partnerId(), m2, recover,
                    f.userIds()[1], null, null);
            platform.commit();
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first = () -> concurrentRevoke(grant1, f.userIds()[0], start);
            Callable<Boolean> second = () -> concurrentRevoke(grant2, f.userIds()[1], start);

            Future<Boolean> r1 = pool.submit(first);
            Future<Boolean> r2 = pool.submit(second);
            start.countDown();

            assertThat(r1.get() ^ r2.get())
                    .as("exactly one concurrent final-path removal may succeed")
                    .isTrue();
            assertThat(activeRecoveryCount(f.partnerId())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean concurrentRevoke(UUID grantId, UUID actor, CountDownLatch start) throws Exception {
        start.await();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            try {
                revokeRecovery(c, grantId, actor, "concurrency safety");
                c.commit();
                return true;
            } catch (SQLException ex) {
                c.rollback();
                assertThat(ex.getSQLState()).isEqualTo("23001");
                assertThat(ex.getMessage()).contains("FINAL_PARTNER_RECOVERY_CAPABILITY_REQUIRED");
                return false;
            }
        }
    }

    private long auditCount(UUID partnerId, UUID grantId) throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT COUNT(*)
                      FROM authorization_change_events
                     WHERE target_type='PARTNER_RECOVERY_GRANT'
                       AND target_id=?
                       AND payload ->> 'partner_id'=?
                    """)) {
                ps.setObject(1, grantId);
                ps.setString(2, partnerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long value = rs.getLong(1);
                    c.rollback();
                    return value;
                }
            }
        }
    }

    private long activeRecoveryCount(UUID partnerId) throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT COUNT(*)
                      FROM partner_membership_capabilities pmc
                      JOIN partner_memberships pm
                        ON pm.id=pmc.membership_id AND pm.partner_id=pmc.partner_id
                      JOIN access_capabilities ac ON ac.id=pmc.capability_id
                     WHERE pmc.partner_id=?
                       AND pmc.status='ACTIVE'
                       AND pmc.valid_from <= NOW()
                       AND (pmc.valid_until IS NULL OR pmc.valid_until > NOW())
                       AND pm.status='ACTIVE'
                       AND pm.valid_from <= NOW()
                       AND (pm.valid_until IS NULL OR pm.valid_until > NOW())
                       AND ac.code='AUTHORIZATION.RECOVER'
                       AND ac.status='ACTIVE'
                    """)) {
                ps.setObject(1, partnerId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long value = rs.getLong(1);
                    c.rollback();
                    return value;
                }
            }
        }
    }

    private String recoveryStatus(UUID grantId) throws Exception {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            setPlatformContext(c);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT status FROM partner_membership_capabilities WHERE id=?")) {
                ps.setObject(1, grantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    String value = rs.getString(1);
                    c.rollback();
                    return value;
                }
            }
        }
    }

    private void revokeRecovery(Connection c, UUID grantId, UUID actor, String reason)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE partner_membership_capabilities
                   SET status='REVOKED',
                       revoked_at=NOW(),
                       revoked_reason=?,
                       updated_by=?
                 WHERE id=?
                """)) {
            ps.setString(1, reason);
            ps.setObject(2, actor);
            ps.setObject(3, grantId);
            ps.executeUpdate();
        }
    }

    private UUID insertRecovery(
            Connection c,
            UUID partnerId,
            UUID membershipId,
            UUID capabilityId,
            UUID actor,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil) throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO partner_membership_capabilities
                    (id,partner_id,membership_id,capability_id,status,
                     valid_from,valid_until,created_by,updated_by)
                VALUES (?,?,?,?,'ACTIVE',COALESCE(?,NOW()),?,?,?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, partnerId);
            ps.setObject(3, membershipId);
            ps.setObject(4, capabilityId);
            if (validFrom == null) {
                ps.setTimestamp(5, null);
            } else {
                ps.setTimestamp(5, Timestamp.from(validFrom.toInstant()));
            }
            if (validUntil == null) {
                ps.setTimestamp(6, null);
            } else {
                ps.setTimestamp(6, Timestamp.from(validUntil.toInstant()));
            }
            ps.setObject(7, actor);
            ps.setObject(8, actor);
            ps.executeUpdate();
        }
        return id;
    }

    private UUID activeMembership(Connection c, UUID partnerId, UUID userId, String role)
            throws SQLException {
        setPartnerContext(c, partnerId);
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO partner_memberships
                    (id,partner_id,user_id,membership_role,status,created_by,updated_by)
                VALUES (?,?,?,?,'ACTIVE',?,?)
                """)) {
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

    private UUID capabilityId(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM access_capabilities WHERE code=? AND status='ACTIVE'")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private Fixture fixture(Connection c, int userCount) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();

        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at)
                VALUES (?,?,?,'ACTIVE',NOW(),NOW())
                """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, "W2-T9 " + tenantId);
            ps.setString(3, "w2t9-" + tenantId.toString().substring(0, 12));
            ps.executeUpdate();
        }

        UUID[] users = new UUID[userCount];
        for (int i = 0; i < userCount; i++) {
            users[i] = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO users
                        (id,tenant_id,email,display_name,status,created_at,updated_at)
                    VALUES (?,?,?,?,'ACTIVE',NOW(),NOW())
                    """)) {
                ps.setObject(1, users[i]);
                ps.setObject(2, tenantId);
                ps.setString(3, "w2t9-" + users[i] + "@example.invalid");
                ps.setString(4, "W2-T9 User " + i);
                ps.executeUpdate();
            }
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO partners (id,partner_type,status,created_by) VALUES (?,'PARTNER','ACTIVE',?)")) {
            ps.setObject(1, partnerId);
            ps.setObject(2, users[0]);
            ps.executeUpdate();
        }

        return new Fixture(tenantId, partnerId, users);
    }

    private void setPlatformContext(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, CONTROL_TENANT.toString());
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
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private record Fixture(UUID tenantId, UUID partnerId, UUID[] userIds) {}
}
