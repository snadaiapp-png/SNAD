package com.sanad.platform.platformiam;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform IAM Task 2 — PostgreSQL Direct Acceptance Test.
 *
 * <p>Follows the {@code CrmPostgresMigrationTest} pattern: creates a
 * separate {@code test_migration} database via {@link MigrationTestSchemaSupport},
 * applies all Flyway migrations from scratch, then verifies the Platform IAM
 * bootstrap state using raw JDBC with per-connection RLS context.
 *
 * <p>PostgreSQL Direct only. No Docker/Testcontainers/H2.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformIamBootstrapPostgresAcceptanceTest {

    private static final UUID CONTROL_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");

    private DataSource dataSource;

    @BeforeAll
    void setupDatabase() {
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"));
        String username = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl, username, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .outOfOrder(true)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        dataSource = new DriverManagerDataSource(isolatedUrl, username, password);
    }

    /** Get a connection with the control tenant RLS context set. */
    private Connection getTenantConnection() throws Exception {
        Connection conn = dataSource.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET app.tenant_id = '" + CONTROL_TENANT_ID + "'");
        }
        return conn;
    }

    @Test
    void canonicalOwnerRemainsActive() throws Exception {
        try (Connection conn = getTenantConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM users WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, OWNER_USER_ID);
            ps.setObject(2, CONTROL_TENANT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("Canonical owner user must exist").isTrue();
                assertThat(rs.getString("status")).as("Canonical owner must remain ACTIVE").isEqualTo("ACTIVE");
            }
        }
    }

    @Test
    void canonicalOwnerHasPlatformAdminTrueForCompatibility() throws Exception {
        try (Connection conn = getTenantConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT platform_admin FROM users WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, OWNER_USER_ID);
            ps.setObject(2, CONTROL_TENANT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("platform_admin"))
                        .as("platform_admin must remain true for compatibility").isTrue();
            }
        }
    }

    @Test
    void exactlyOneActivePlatformMembershipForCanonicalOwner() throws Exception {
        try (Connection conn = getTenantConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM platform_memberships " +
                "WHERE control_tenant_id = ? AND user_id = ? AND status = 'ACTIVE'")) {
            ps.setObject(1, CONTROL_TENANT_ID);
            ps.setObject(2, OWNER_USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("Canonical owner must have exactly 1 ACTIVE platform_membership").isEqualTo(1);
            }
        }
    }

    @Test
    void platformOwnerRoleExistsAndIsProtected() throws Exception {
        try (Connection conn = getTenantConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT r.code, r.status, prm.role_type, prm.protected, prm.owner_role " +
                "FROM roles r " +
                "JOIN platform_role_metadata prm ON prm.role_id = r.id AND prm.control_tenant_id = r.tenant_id " +
                "WHERE r.tenant_id = ? AND r.code = 'PLATFORM_OWNER' AND r.status = 'ACTIVE'")) {
            ps.setObject(1, CONTROL_TENANT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("PLATFORM_OWNER role must exist in control tenant").isTrue();
                assertThat(rs.getString("role_type")).as("PLATFORM_OWNER must be SYSTEM type").isEqualTo("SYSTEM");
                assertThat(rs.getBoolean("protected")).as("PLATFORM_OWNER must be protected").isTrue();
                assertThat(rs.getBoolean("owner_role")).as("PLATFORM_OWNER must have owner_role=true").isTrue();
            }
        }
    }

    @Test
    void canonicalOwnerHasActiveAssignmentToPlatformOwner() throws Exception {
        try (Connection conn = getTenantConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM user_role_assignments ura " +
                "JOIN roles r ON r.id = ura.role_id AND r.tenant_id = ura.tenant_id " +
                "WHERE ura.tenant_id = ? AND ura.user_id = ? AND r.code = 'PLATFORM_OWNER' " +
                "AND ura.status = 'ACTIVE'")) {
            ps.setObject(1, CONTROL_TENANT_ID);
            ps.setObject(2, OWNER_USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("Canonical owner must have ACTIVE assignment to PLATFORM_OWNER").isGreaterThan(0);
            }
        }
    }

    @Test
    void platformOwnerRoleHasAllPlatformCapabilities() throws Exception {
        try (Connection conn = getTenantConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(DISTINCT ac.code) FROM access_capabilities ac " +
                "WHERE ac.code LIKE 'PLATFORM.%' AND ac.status = 'ACTIVE' " +
                "AND (" +
                "  EXISTS (SELECT 1 FROM role_capabilities rc " +
                "          WHERE rc.capability_id = ac.id AND rc.tenant_id = ? " +
                "          AND rc.role_id = (SELECT id FROM roles WHERE tenant_id = ? AND code = 'PLATFORM_OWNER')) " +
                "  OR " +
                "  EXISTS (SELECT 1 FROM access_scope_grants asg " +
                "          WHERE asg.capability_id = ac.id AND asg.tenant_id = ? " +
                "          AND asg.role_id = (SELECT id FROM roles WHERE tenant_id = ? AND code = 'PLATFORM_OWNER') " +
                "          AND asg.status = 'ACTIVE') " +
                ")")) {
            ps.setObject(1, CONTROL_TENANT_ID);
            ps.setObject(2, CONTROL_TENANT_ID);
            ps.setObject(3, CONTROL_TENANT_ID);
            ps.setObject(4, CONTROL_TENANT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int count = rs.getInt(1);
                assertThat(count)
                        .as("PLATFORM_OWNER must have all 17 PLATFORM.* capabilities (found %d)", count)
                        .isGreaterThanOrEqualTo(17);
            }
        }
    }

    @Test
    void noDuplicateOwnerMembership() throws Exception {
        try (Connection conn = getTenantConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM platform_memberships WHERE control_tenant_id = ? AND user_id = ?")) {
            ps.setObject(1, CONTROL_TENANT_ID);
            ps.setObject(2, OWNER_USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("Canonical owner must have at most 1 platform_membership").isLessThanOrEqualTo(1);
            }
        }
    }

    @Test
    void noCrossTenantPlatformOwnerAssignment() throws Exception {
        // No tenant context needed — queries all tenants
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM user_role_assignments ura " +
                "JOIN roles r ON r.id = ura.role_id AND r.tenant_id = ura.tenant_id " +
                "WHERE r.code = 'PLATFORM_OWNER' AND ura.tenant_id != ?")) {
            ps.setObject(1, CONTROL_TENANT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("PLATFORM_OWNER must not exist outside control tenant").isZero();
            }
        }
    }

    @Test
    void allSystemRolesExistInControlTenant() throws Exception {
        String[] expectedRoles = {
            "PLATFORM_OWNER", "PLATFORM_ADMIN", "SECURITY_ADMIN",
            "BILLING_ADMIN", "SUPPORT_OPERATOR", "READ_ONLY_AUDITOR"
        };
        try (Connection conn = getTenantConnection()) {
            for (String roleCode : expectedRoles) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND code = ? AND status = 'ACTIVE'")) {
                    ps.setObject(1, CONTROL_TENANT_ID);
                    ps.setString(2, roleCode);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getInt(1))
                                .as("Role %s must exist exactly once in control tenant", roleCode)
                                .isEqualTo(1);
                    }
                }
            }
        }
    }
}
