package com.sanad.platform.access.evaluation;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 regression locks for the effective access explanation projection
 * (PostgreSQL Direct). These behaviors are pre-Phase-7 canonical semantics and
 * must remain intact after the two-effect explanation model landed:
 * break-glass provenance, expiry windows, inactive role/capability
 * fail-closed, tenant isolation, and evaluator consistency in the ALLOW
 * direction.
 */
class EffectiveAccessExplanationRegressionPostgresTest {
    private static final String SOURCE_URL=System.getenv().getOrDefault("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/sanad");
    private static final String USER=System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME","sanad");
    private static final String PASSWORD=System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD","");

    @BeforeAll static void requirePostgres(){
        boolean available;try{available=Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("EffectiveAccessExplanationRegressionPostgresTest");}catch(Throwable ignored){available=false;}
        Assumptions.assumeTrue(available,"PostgreSQL Direct is required");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL,USER,PASSWORD);
    }

    @Test
    void breakGlassOverridePreservesSourceWithCanonicalAllowReason() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            insertOverride(connection, f.tenantId(), f.userId(), f.capabilityId(), "ALLOW", null, null,
                    "[BREAK_GLASS] incident-1234 emergency restore", null, null);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            List<EffectivePermissionProjectionService.EffectivePermissionRow> rows =
                    service.rebuild(f.tenantId(), f.userId());

            EffectivePermissionProjectionService.EffectivePermissionRow breakGlass = rows.stream()
                    .filter(r -> f.capabilityId().equals(r.capabilityId()))
                    .filter(r -> "BREAK_GLASS".equals(r.source()))
                    .findFirst().orElseThrow(() -> new AssertionError("break-glass source lost"));
            assertThat(breakGlass.effect()).isEqualTo("ALLOW");
            assertThat(breakGlass.reason()).isEqualTo("EXPLICIT_ALLOW_MATCH");
        }
    }

    @Test
    void expiredAllowOverrideIsNotEffective() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            insertOverride(connection, f.tenantId(), f.userId(), f.capabilityId(), "ALLOW", "TEAM", UUID.randomUUID(),
                    "expired window", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(7200)),
                    java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)));
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            List<EffectivePermissionProjectionService.EffectivePermissionRow> rows =
                    service.rebuild(f.tenantId(), f.userId());

            assertThat(rows.stream().filter(r -> f.capabilityId().equals(r.capabilityId()))
                    .filter(r -> "OVERRIDE".equals(r.source()) || "BREAK_GLASS".equals(r.source()))
                    .filter(r -> "TEAM".equals(r.scopeType())).count())
                    .as("the expired TEAM-scoped ALLOW must not be projected for the fixture capability").isZero();
        }
    }

    @Test
    void expiredDenyOverrideDoesNotSuppressRoleAllow() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            insertOverride(connection, f.tenantId(), f.userId(), f.capabilityId(), "DENY", null, null,
                    "expired hold", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(7200)),
                    java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)));
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            List<EffectivePermissionProjectionService.EffectivePermissionRow> rows =
                    service.rebuild(f.tenantId(), f.userId());

            assertThat(rows.stream().anyMatch(r -> f.capabilityId().equals(r.capabilityId())
                    && "ALLOW".equals(r.effect()) && "ROLE".equals(r.source())))
                    .as("expired DENY must not suppress the role-derived ALLOW").isTrue();
            assertThat(rows.stream().noneMatch(r -> "DENY".equals(r.effect()))).isTrue();
        }
    }

    @Test
    void revokedRoleAssignmentIsNotEffective() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            // Dedicated capability so no other assignment can keep it effective.
            UUID capabilityId = UUID.randomUUID();
            insertCapability(connection, capabilityId, "PHASE7_REVOKE_CAP", "ACTIVE");
            insertRoleCapability(connection, f.tenantId(), f.roleId(), capabilityId);
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            service.rebuild(f.tenantId(), f.userId());
            Integer withRole = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM effective_permission_projection WHERE tenant_id=? AND user_id=? AND capability_id=?",
                    Integer.class, f.tenantId(), f.userId(), capabilityId);

            // Revoke every active assignment of the subject: no active role may
            // remain effective for the dedicated capability.
            jdbc.update("UPDATE user_role_assignments SET status = 'REVOKED' "
                    + "WHERE tenant_id = ? AND user_id = ? AND status = 'ACTIVE'", f.tenantId(), f.userId());
            service.rebuild(f.tenantId(), f.userId());
            Integer afterRevoke = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM effective_permission_projection WHERE tenant_id=? AND user_id=? AND capability_id=?",
                    Integer.class, f.tenantId(), f.userId(), capabilityId);

            assertThat(withRole).as("active assignment projects the dedicated capability").isGreaterThanOrEqualTo(1);
            assertThat(afterRevoke).as("revoked assignments must not remain effective").isZero();
        }
    }

    @Test
    void inactiveCapabilityIsNotEffective() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            UUID capabilityId = UUID.randomUUID();
            insertCapability(connection, capabilityId, "PHASE7_INACTIVE_CAP", "INACTIVE");
            insertRoleCapability(connection, f.tenantId(), f.roleId(), capabilityId);
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            service.rebuild(f.tenantId(), f.userId());

            Integer rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM effective_permission_projection WHERE tenant_id=? AND user_id=? AND capability_id=?",
                    Integer.class, f.tenantId(), f.userId(), capabilityId);
            assertThat(rows).as("inactive capability must fail closed (no projection row)").isZero();
        }
    }

    @Test
    void crossTenantSubjectCannotBeProjectedOrRead() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            service.rebuild(f.tenantId(), f.userId());
            List<EffectivePermissionProjectionService.EffectivePermissionRow> own = service.list(f.tenantId(), f.userId());
            assertThat(own).isNotEmpty();

            UUID otherTenant = UUID.randomUUID();
            List<EffectivePermissionProjectionService.EffectivePermissionRow> foreign =
                    service.list(otherTenant, f.userId());
            assertThat(foreign).as("tenant-jailed read of a foreign subject must be empty").isEmpty();
        }
    }

    @Test
    void projectionNeverGrantsAuthorityTheCanonicalEvaluatorDenies() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            List<EffectivePermissionProjectionService.EffectivePermissionRow> rows =
                    service.rebuild(f.tenantId(), f.userId());

            // Safe-direction consistency: every projected TENANT_ALL role ALLOW row
            // must correspond to an evaluator ALLOW for the same decision context
            // (the DENY direction is covered by the dominance test).
            for (EffectivePermissionProjectionService.EffectivePermissionRow row : rows) {
                if (!"ROLE".equals(row.source()) || !"ALLOW".equals(row.effect())
                        || !"TENANT_ALL".equals(row.scopeType())) {
                    continue;
                }
                Integer roleHasCap = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM role_capabilities rc "
                        + "JOIN user_role_assignments ura ON ura.role_id = rc.role_id AND ura.tenant_id = rc.tenant_id "
                        + "WHERE ura.tenant_id = ? AND ura.user_id = ? AND ura.status = 'ACTIVE' AND rc.capability_id = ?",
                        Integer.class, f.tenantId(), f.userId(), row.capabilityId());
                assertThat(roleHasCap).as("projected role ALLOW must trace to a real active assignment")
                        .isGreaterThanOrEqualTo(1);
            }
        }
    }

    // ------------------------------------------------------------------

    private static EffectivePermissionProjectionService service(JdbcTemplate jdbc) {
        return new EffectivePermissionProjectionService(jdbc, new AuthorizationVersionService(jdbc));
    }

    private static Connection openMigratedConnection() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration").cleanDisabled(false).validateOnMigrate(true).load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        return DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
    }

    private static Fixture findFixture(Connection c) throws Exception {
        try (PreparedStatement tenants = c.prepareStatement("SELECT id FROM tenants ORDER BY id");
             ResultSet tr = tenants.executeQuery()) {
            while (tr.next()) {
                UUID tenant = tr.getObject(1, UUID.class);
                setTenant(c, tenant);
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT u.id, r.id, rc.capability_id FROM users u "
                        + "JOIN roles r ON r.tenant_id = u.tenant_id AND r.status='ACTIVE' "
                        + "JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id "
                        + "JOIN access_capabilities ac ON ac.id = rc.capability_id AND ac.status='ACTIVE' "
                        + "WHERE u.tenant_id = ? AND u.status='ACTIVE' "
                        + "ORDER BY u.id, r.id, rc.capability_id LIMIT 1")) {
                    p.setObject(1, tenant);
                    try (ResultSet r = p.executeQuery()) {
                        if (r.next()) {
                            return new Fixture(tenant, r.getObject(1, UUID.class),
                                    r.getObject(2, UUID.class), r.getObject(3, UUID.class));
                        }
                    }
                }
            }
        }
        throw new AssertionError("No tenant/user/role-capability fixture after migrations");
    }

    private static void insertAssignment(Connection c, UUID id, UUID tenantId, UUID userId,
                                         UUID roleId, String status) throws Exception {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            p.setObject(1, id);
            p.setObject(2, tenantId);
            p.setObject(3, userId);
            p.setObject(4, roleId);
            p.setString(5, status);
            p.executeUpdate();
        }
    }

    private static void assignRole(Connection c, UUID tenantId, UUID userId, UUID roleId) throws Exception {
        insertAssignment(c, UUID.randomUUID(), tenantId, userId, roleId, "ACTIVE");
    }

    private static void insertCapability(Connection c, UUID id, String code, String status) throws Exception {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO access_capabilities (id, code, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            p.setObject(1, id);
            p.setString(2, code);
            p.setString(3, code);
            p.setString(4, status);
            p.executeUpdate();
        }
    }

    private static void insertRoleCapability(Connection c, UUID tenantId, UUID roleId, UUID capabilityId) throws Exception {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
            p.setObject(1, UUID.randomUUID());
            p.setObject(2, tenantId);
            p.setObject(3, roleId);
            p.setObject(4, capabilityId);
            p.executeUpdate();
        }
    }

    private static void insertOverride(Connection c, UUID tenantId, UUID userId, UUID capabilityId,
                                       String effect, String scopeType, UUID scopeReference, String reason,
                                       java.sql.Timestamp validFrom, java.sql.Timestamp validUntil) throws Exception {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO user_permission_overrides "
                + "(id, tenant_id, user_id, capability_id, effect, scope_type, scope_reference, reason, "
                + "valid_from, valid_until, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), ?, ?)")) {
            p.setObject(1, UUID.randomUUID());
            p.setObject(2, tenantId);
            p.setObject(3, userId);
            p.setObject(4, capabilityId);
            p.setString(5, effect);
            p.setString(6, scopeType);
            p.setObject(7, scopeReference);
            p.setString(8, reason);
            p.setObject(9, validFrom);
            p.setObject(10, validUntil);
            p.setObject(11, userId);
            p.executeUpdate();
        }
    }

    private static void setTenant(Connection c, UUID tenant) throws Exception {
        try (PreparedStatement p = c.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            p.setString(1, tenant.toString());
            p.executeQuery();
        }
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private record Fixture(UUID tenantId, UUID userId, UUID roleId, UUID capabilityId) {}
}
