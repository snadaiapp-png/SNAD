package com.sanad.platform.platformiam;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformOwnerExecutiveAdminPostgresAcceptanceTest {
    private static final UUID CONTROL_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String OWNER_EMAIL = "snad.ai.app@gmail.com";
    private DataSource dataSource;

    @BeforeAll void setupDatabase() {
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"));
        String username = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
        Flyway flyway = Flyway.configure().dataSource(isolatedUrl, username, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true).validateOnMigrate(false).outOfOrder(true).cleanDisabled(false).load();
        flyway.clean(); flyway.migrate();
        dataSource = new DriverManagerDataSource(isolatedUrl, username, password);
    }

    private Connection controlTenantConnection() throws Exception {
        Connection conn = dataSource.getConnection();
        try (Statement stmt = conn.createStatement()) { stmt.execute("SET app.tenant_id = '" + CONTROL_TENANT_ID + "'"); }
        return conn;
    }

    @Test void canonicalOwnerIdentityIsActiveAndPlatformAdmin() throws Exception {
        try (Connection conn = controlTenantConnection(); PreparedStatement ps = conn.prepareStatement("SELECT email,status,platform_admin FROM users WHERE tenant_id=? AND id=?")) {
            ps.setObject(1, CONTROL_TENANT_ID); ps.setObject(2, OWNER_USER_ID);
            try (ResultSet rs = ps.executeQuery()) { assertThat(rs.next()).isTrue(); assertThat(rs.getString("email")).isEqualToIgnoringCase(OWNER_EMAIL); assertThat(rs.getString("status")).isEqualTo("ACTIVE"); assertThat(rs.getBoolean("platform_admin")).isTrue(); }
        }
    }

    @Test void canonicalOwnerHasExactlyOneActivePlatformMembership() throws Exception {
        try (Connection conn = controlTenantConnection(); PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM platform_memberships WHERE control_tenant_id=? AND user_id=? AND status='ACTIVE'")) {
            ps.setObject(1, CONTROL_TENANT_ID); ps.setObject(2, OWNER_USER_ID); try (ResultSet rs = ps.executeQuery()) { rs.next(); assertThat(rs.getInt(1)).isEqualTo(1); }
        }
    }

    @Test void canonicalOwnerHasActivePlatformOwnerAndAdminAssignments() throws Exception {
        try (Connection conn = controlTenantConnection(); PreparedStatement ps = conn.prepareStatement("SELECT r.code,COUNT(*) FROM user_role_assignments ura JOIN roles r ON r.id=ura.role_id AND r.tenant_id=ura.tenant_id WHERE ura.tenant_id=? AND ura.user_id=? AND ura.status='ACTIVE' AND r.code IN ('PLATFORM_OWNER','ADMIN') GROUP BY r.code")) {
            ps.setObject(1, CONTROL_TENANT_ID); ps.setObject(2, OWNER_USER_ID); int po=0, admin=0; try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { if ("PLATFORM_OWNER".equals(rs.getString(1))) po=rs.getInt(2); if ("ADMIN".equals(rs.getString(1))) admin=rs.getInt(2); } } assertThat(po).isGreaterThanOrEqualTo(1); assertThat(admin).isGreaterThanOrEqualTo(1);
        }
    }

    @Test void platformOwnerRoleHasEveryActiveCapabilityThroughRoleCapabilities() throws Exception {
        try (Connection conn = controlTenantConnection(); PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM access_capabilities ac WHERE ac.status='ACTIVE' AND ac.code <> 'HRM.PAYROLL.VIEW' AND NOT EXISTS (SELECT 1 FROM role_capabilities rc JOIN roles r ON r.id=rc.role_id AND r.tenant_id=rc.tenant_id WHERE rc.tenant_id=? AND r.code='PLATFORM_OWNER' AND r.status='ACTIVE' AND rc.capability_id=ac.id)")) {
            ps.setObject(1, CONTROL_TENANT_ID); try (ResultSet rs = ps.executeQuery()) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
        }
    }

    @Test void platformOwnerRoleHasTenantScopeForEveryActiveCapability() throws Exception {
        try (Connection conn = controlTenantConnection(); PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM access_capabilities ac WHERE ac.status='ACTIVE' AND ac.code <> 'HRM.PAYROLL.VIEW' AND NOT EXISTS (SELECT 1 FROM access_scope_grants asg JOIN roles r ON r.id=asg.role_id AND r.tenant_id=asg.tenant_id WHERE asg.tenant_id=? AND r.code='PLATFORM_OWNER' AND r.status='ACTIVE' AND asg.capability_id=ac.id AND asg.user_id IS NULL AND asg.scope_type='TENANT' AND asg.status='ACTIVE')")) {
            ps.setObject(1, CONTROL_TENANT_ID); try (ResultSet rs = ps.executeQuery()) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
        }
    }

    @Test void platformOwnerNeverExistsOutsideTheControlTenant() throws Exception {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM user_role_assignments ura JOIN roles r ON r.id=ura.role_id AND r.tenant_id=ura.tenant_id WHERE r.code='PLATFORM_OWNER' AND ura.tenant_id<>?")) {
            ps.setObject(1, CONTROL_TENANT_ID); try (ResultSet rs = ps.executeQuery()) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
        }
    }

    @Test void platformMembershipRlsDoesNotLeakOwnerIntoAnotherTenantContext() throws Exception {
        UUID otherTenant = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("SET app.tenant_id = '" + otherTenant + "'");
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM platform_memberships WHERE user_id=?")) { ps.setObject(1, OWNER_USER_ID); try (ResultSet rs = ps.executeQuery()) { rs.next(); assertThat(rs.getInt(1)).isZero(); } }
        }
    }
}
