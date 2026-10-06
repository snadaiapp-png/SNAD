package com.sanad.platform.access.evaluation;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.override.UserPermissionOverrideJdbcRepository;
import com.sanad.platform.access.relationship.SubjectRelationshipResolver;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 7 — effective access explanation read model (PostgreSQL Direct).
 *
 * <p>The projection is an EXPLANATION surface only: it must express
 * {@code effect} (ALLOW | DENY) and a canonical machine-readable {@code reason}
 * alongside the existing provenance columns, and it must never project an
 * effective ALLOW that the authoritative {@link CapabilityEvaluationService}
 * denies (deny dominance, directive §6).</p>
 *
 * <p>RED-first: assertions on the new {@code effect}/{@code reason} row fields
 * use reflection so this test compiles against the pre-Phase-7 row model and
 * fails because the Phase 7 behavior is missing (never because of an
 * environment problem).</p>
 */
class EffectiveAccessExplanationPhase7PostgresTest {
    private static final String SOURCE_URL=System.getenv().getOrDefault("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/sanad");
    private static final String USER=System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME","sanad");
    private static final String PASSWORD=System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD","");

    @BeforeAll static void requirePostgres(){
        boolean available;try{available=Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("EffectiveAccessExplanationPhase7PostgresTest");}catch(Throwable ignored){available=false;}
        Assumptions.assumeTrue(available,"PostgreSQL Direct is required");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL,USER,PASSWORD);
    }

    @Test
    void roleDerivedAllowRowCarriesEffectAndCanonicalReason() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            List<EffectivePermissionProjectionService.EffectivePermissionRow> rows =
                    service.rebuild(f.tenantId(), f.userId());

            Object roleRow = rows.stream()
                    .filter(r -> "ROLE".equals(field(r, "source")))
                    .filter(r -> f.capabilityId().equals(field(r, "capabilityId")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("role-derived ALLOW row missing"));
            assertThat(field(roleRow, "effect")).as("effect on role-derived row").isEqualTo("ALLOW");
            assertThat((String) field(roleRow, "reason")).as("canonical reason on role-derived row")
                    .isNotBlank().isEqualTo("ROLE_CAPABILITY_MATCH");
        }
    }

    @Test
    void allowOverrideRowCarriesEffectAndCanonicalReason() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            insertOverride(connection, f.tenantId(), f.userId(), f.capabilityId(), "ALLOW", "TEAM", UUID.randomUUID(), "projection override proof");
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            List<EffectivePermissionProjectionService.EffectivePermissionRow> rows =
                    service.rebuild(f.tenantId(), f.userId());

            Object overrideRow = rows.stream()
                    .filter(r -> "OVERRIDE".equals(field(r, "source")))
                    .filter(r -> f.capabilityId().equals(field(r, "capabilityId")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("ALLOW override row missing"));
            assertThat(field(overrideRow, "effect")).as("effect on override row").isEqualTo("ALLOW");
            assertThat((String) field(overrideRow, "reason")).as("canonical reason on override row")
                    .isNotBlank().isEqualTo("EXPLICIT_ALLOW_MATCH");
        }
    }

    @Test
    void denyOverrideIsRepresentedAsEffectiveDenyRow() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            insertOverride(connection, f.tenantId(), f.userId(), f.capabilityId(), "DENY", null, null, "hold capability during investigation");
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            EffectivePermissionProjectionService service = service(jdbc);

            service.rebuild(f.tenantId(), f.userId());

            Integer denyRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM effective_permission_projection "
                    + "WHERE tenant_id=? AND user_id=? AND capability_id=? AND effect='DENY'",
                    Integer.class, f.tenantId(), f.userId(), f.capabilityId());
            assertThat(denyRows).as("active DENY override must be represented as an effective DENY row")
                    .isNotNull().isEqualTo(1);
        }
    }

    @Test
    void denyDominanceSuppressesRoleDerivedAllowConsistentlyWithAuthoritativeEvaluator() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            assignRole(connection, f.tenantId(), f.userId(), f.roleId());
            insertOverride(connection, f.tenantId(), f.userId(), f.capabilityId(), "DENY", null, null, "hold capability during investigation");

            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));

            // Canonical evaluator (authoritative) must DENY for the same decision context.
            CapabilityEvaluationService evaluator = evaluator(jdbc, f);
            AuthorizationDecision decision = evaluator.evaluateDetailed(
                    f.tenantId(), f.userId(), f.capabilityCode(), null);
            assertThat(decision.decision()).as("canonical evaluator decision").isEqualTo("DENY");
            assertThat(decision.reason()).as("canonical evaluator reason").isEqualTo("EXPLICIT_DIRECT_DENY");

            // The explanation projection must not contradict the evaluator.
            EffectivePermissionProjectionService service = service(jdbc);
            service.rebuild(f.tenantId(), f.userId());
            Integer effectiveAllowRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM effective_permission_projection "
                    + "WHERE tenant_id=? AND user_id=? AND capability_id=? AND effect='ALLOW'",
                    Integer.class, f.tenantId(), f.userId(), f.capabilityId());
            assertThat(effectiveAllowRows)
                    .as("projection must not show effective ALLOW where the canonical evaluator denies")
                    .isNotNull().isZero();
        }
    }

    @Test
    void projectionSchemaCanRepresentDenyEffectAndReasonProvenance() throws Exception {
        try (Connection connection = openMigratedConnection()) {
            Fixture f = findFixture(connection);
            setTenant(connection, f.tenantId());
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));

            Integer reasonColumns = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_name='effective_permission_projection' AND column_name='reason'",
                    Integer.class);
            assertThat(reasonColumns).as("reason column must exist on the projection").isNotNull().isEqualTo(1);

            Integer reasonNullable = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_name='effective_permission_projection' AND column_name='reason' AND is_nullable='NO'",
                    Integer.class);
            assertThat(reasonNullable).as("reason column must be NOT NULL (canonical backfill enforced)")
                    .isNotNull().isEqualTo(1);

            // effect CHECK must accept DENY (allow-side of the dominance model).
            UUID denyRowId = UUID.randomUUID();
            java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
            RuntimeException rejection = null;
            try {
                jdbc.update("INSERT INTO effective_permission_projection "
                                + "(id, tenant_id, user_id, capability_id, effect, scope_type, scope_reference, "
                                + "source, matched_role_id, authorization_version, computed_at, reason) "
                                + "VALUES (?, ?, ?, ?, 'DENY', 'TENANT_ALL', NULL, 'OVERRIDE', NULL, 0, ?, 'EXPLICIT_DIRECT_DENY')",
                        denyRowId, f.tenantId(), f.userId(), f.capabilityId(), now);
            } catch (RuntimeException schemaRejection) {
                rejection = schemaRejection;
            } finally {
                jdbc.update("DELETE FROM effective_permission_projection WHERE id = ?", denyRowId);
            }
            assertThat(rejection)
                    .as("effect CHECK must accept DENY rows (schema cannot yet represent the Phase 7 model): %s",
                            rejection == null ? "" : String.valueOf(rejection.getMessage()))
                    .isNull();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static EffectivePermissionProjectionService service(JdbcTemplate jdbc) {
        return new EffectivePermissionProjectionService(jdbc, new AuthorizationVersionService(jdbc));
    }

    /** Canonical evaluator wired to the real override/relationship repositories over the migrated schema. */
    private static CapabilityEvaluationService evaluator(JdbcTemplate jdbc, Fixture f) {
        UserPermissionOverrideJdbcRepository overrides = new UserPermissionOverrideJdbcRepository(jdbc);
        SubjectRelationshipResolver relationships = new SubjectRelationshipResolver(jdbc);

        AccessCapability capability = mock(AccessCapability.class);
        when(capability.getId()).thenReturn(f.capabilityId());
        when(capability.getCode()).thenReturn(f.capabilityCode());
        when(capability.getStatus()).thenReturn(CapabilityStatus.ACTIVE);
        AccessCapabilityService capabilityService = mock(AccessCapabilityService.class);
        when(capabilityService.loadByCode(f.capabilityCode())).thenReturn(capability);

        UserRoleGrant grant = mock(UserRoleGrant.class);
        when(grant.isTenantWide()).thenReturn(true);
        when(grant.getRoleId()).thenReturn(f.roleId());
        UserRoleGrantService grants = mock(UserRoleGrantService.class);
        when(grants.activeGrants(f.tenantId(), f.userId())).thenReturn(List.of(grant));

        Role role = mock(Role.class);
        when(role.getId()).thenReturn(f.roleId());
        when(role.getCode()).thenReturn("PHASE7_ROLE");
        when(role.getStatus()).thenReturn(RoleStatus.ACTIVE);
        RoleService roles = mock(RoleService.class);
        when(roles.load(f.tenantId(), f.roleId())).thenReturn(role);

        RoleCapabilityService roleCaps = mock(RoleCapabilityService.class);
        when(roleCaps.roleHasCapability(f.tenantId(), f.roleId(), f.capabilityId())).thenReturn(true);

        return new CapabilityEvaluationService(grants, roles, roleCaps, capabilityService,
                mock(OrganizationRepository.class), overrides, relationships, mock(Environment.class));
    }

    /** Reads a named record component without referencing it at compile time (RED-safe). */
    private static Object field(Object row, String name) {
        try {
            java.lang.reflect.Field f = row.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(row);
        } catch (NoSuchFieldException absent) {
            return null; // Phase 7 contract field missing
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
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
                        "SELECT u.id, r.id, rc.capability_id, ac.code FROM users u "
                        + "JOIN roles r ON r.tenant_id = u.tenant_id AND r.status='ACTIVE' "
                        + "JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id "
                        + "JOIN access_capabilities ac ON ac.id = rc.capability_id AND ac.status='ACTIVE' "
                        + "WHERE u.tenant_id = ? AND u.status='ACTIVE' "
                        + "ORDER BY u.id, r.id, rc.capability_id LIMIT 1")) {
                    p.setObject(1, tenant);
                    try (ResultSet r = p.executeQuery()) {
                        if (r.next()) {
                            return new Fixture(tenant, r.getObject(1, UUID.class), r.getObject(2, UUID.class),
                                    r.getObject(3, UUID.class), r.getString(4));
                        }
                    }
                }
            }
        }
        throw new AssertionError("No tenant/user/role-capability fixture after migrations");
    }

    private static void assignRole(Connection c, UUID tenantId, UUID userId, UUID roleId) throws Exception {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                + "SELECT ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP "
                + "WHERE NOT EXISTS (SELECT 1 FROM user_role_assignments "
                + "WHERE tenant_id = ? AND user_id = ? AND role_id = ? AND status = 'ACTIVE')")) {
            p.setObject(1, UUID.randomUUID());
            p.setObject(2, tenantId);
            p.setObject(3, userId);
            p.setObject(4, roleId);
            p.setObject(5, tenantId);
            p.setObject(6, userId);
            p.setObject(7, roleId);
            p.executeUpdate();
        }
    }

    private static void insertOverride(Connection c, UUID tenantId, UUID userId, UUID capabilityId,
                                       String effect, String scopeType, UUID scopeReference, String reason) throws Exception {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO user_permission_overrides "
                + "(id, tenant_id, user_id, capability_id, effect, scope_type, scope_reference, reason, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            p.setObject(1, UUID.randomUUID());
            p.setObject(2, tenantId);
            p.setObject(3, userId);
            p.setObject(4, capabilityId);
            p.setString(5, effect);
            p.setString(6, scopeType);
            p.setObject(7, scopeReference);
            p.setString(8, reason);
            p.setObject(9, userId);
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

    private record Fixture(UUID tenantId, UUID userId, UUID roleId, UUID capabilityId, String capabilityCode) {}
}
