package com.sanad.platform.hr.security;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** HRM-G0 / WS4 Task 1 persistence + Task 2 scoped-authorization contract. */
class HrScopedAuthorizationIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) { available = c.isValid(5); }
        } catch (Throwable ignored) { }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateFreshDatabase() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        connection = ds.getConnection();
        connection.setAutoCommit(true);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @Test
    void accessScopeGrantTableUsesForcedFailClosedRls() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'access_scope_grants'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("access_scope_grants must exist").isTrue();
                assertThat(rs.getBoolean("relrowsecurity")).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity")).isTrue();
            }
        }
    }

    @Test
    void principalMustBeExactlyOneRoleOrUser() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        seedTenantAndCapability(tenantId, capabilityId, "TEST.WS4.PRINCIPAL." + capabilityId);
        setTenant(tenantId);
        assertThatThrownBy(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO access_scope_grants (tenant_id, capability_id, scope_type, status) " +
                            "VALUES (?, ?, 'TENANT', 'ACTIVE')")) {
                ps.setObject(1, tenantId); ps.setObject(2, capabilityId); ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
          .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void directExceptionRequiresGovernanceMetadata() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        seedTenantAndCapability(tenantId, capabilityId, "TEST.WS4.DIRECT." + capabilityId);
        setTenant(tenantId);
        assertThatThrownBy(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO access_scope_grants " +
                            "(tenant_id, user_id, capability_id, scope_type, is_direct_exception, status) " +
                            "VALUES (?, ?, ?, 'SELF', TRUE, 'ACTIVE')")) {
                ps.setObject(1, tenantId); ps.setObject(2, userId); ps.setObject(3, capabilityId); ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
          .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void legacyCapabilitiesAreNotBackfilledIntoCanonicalScopeGrants() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM access_scope_grants g JOIN access_capabilities c ON c.id = g.capability_id " +
                        "WHERE c.code LIKE 'HR.%' OR c.code LIKE 'HRM.%'")) {
            try (ResultSet rs = ps.executeQuery()) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
        }
    }

    @Test
    void capabilityWithoutCanonicalScopeIsDenied() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        String capability = "TEST.WS4.NOSCOPE." + capabilityId;
        seedTenantAndCapability(tenantId, capabilityId, capability);
        setTenant(tenantId);

        Object service = newScopedService(coarseAllow(tenantId, userId, organizationId, capability, roleId));
        Object request = newRequest(tenantId, userId, capability,
                newResourceContext(tenantId, UUID.randomUUID(), null, organizationId, null, null, LocalDate.of(2025, 1, 1)),
                Instant.parse("2026-09-03T00:00:00Z"));
        Object decision = authorize(service, request);
        assertThat(accessor(decision, "allowed")).isEqualTo(false);
        assertThat(accessor(decision, "reason").toString()).contains("SCOPE");
    }

    @Test
    void organizationScopeDoesNotCrossOrganizationBoundary() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        String capability = "TEST.WS4.ORG." + capabilityId;
        seedTenantAndCapability(tenantId, capabilityId, capability);
        setTenant(tenantId);
        jdbc.update("INSERT INTO access_scope_grants " +
                        "(tenant_id,role_id,capability_id,scope_type,organization_id,status,effective_from) " +
                        "VALUES (?,?,?,'ORGANIZATION',?,'ACTIVE',TIMESTAMPTZ '2026-01-01 00:00:00+00')",
                tenantId, roleId, capabilityId, orgA);

        Object service = newScopedService(coarseAllow(tenantId, userId, orgA, capability, roleId));
        Instant authTime = Instant.parse("2026-09-03T00:00:00Z");
        Object allowed = authorize(service, newRequest(tenantId, userId, capability,
                newResourceContext(tenantId, UUID.randomUUID(), null, orgA, null, null, LocalDate.now()), authTime));
        assertThat(accessor(allowed, "allowed")).isEqualTo(true);

        Object denied = authorize(service, newRequest(tenantId, userId, capability,
                newResourceContext(tenantId, UUID.randomUUID(), null, orgB, null, null, LocalDate.now()), authTime));
        assertThat(accessor(denied, "allowed")).isEqualTo(false);
    }

    private Object newScopedService(CapabilityEvaluationService coarse) throws Exception {
        Class<?> repoType = Class.forName("com.sanad.platform.security.scope.JdbcAccessScopeRepository");
        Object repo = repoType.getConstructor(JdbcTemplate.class).newInstance(jdbc);
        Class<?> contextResolverType = Class.forName("com.sanad.platform.hr.security.HrResourceContextResolver");
        Object contextResolver = contextResolverType.getConstructor(JdbcTemplate.class).newInstance(jdbc);
        Class<?> serviceType = Class.forName("com.sanad.platform.security.scope.ScopedAuthorizationService");
        return serviceType.getConstructor(CapabilityEvaluationService.class, repoType, contextResolverType)
                .newInstance(coarse, repo, contextResolver);
    }

    private CapabilityEvaluationService coarseAllow(UUID tenantId, UUID userId, UUID orgId, String capability, UUID roleId) {
        CapabilityEvaluationService coarse = mock(CapabilityEvaluationService.class);
        when(coarse.evaluate(any(UUID.class), any(UUID.class), anyString(), any(UUID.class)))
                .thenReturn(new AccessDecisionResponse(tenantId, userId, orgId, capability, true,
                        "ROLE_CAPABILITY_MATCH", roleId, "TASK2_ROLE"));
        return coarse;
    }

    private Object newResourceContext(UUID tenantId, UUID employmentId, UUID personId, UUID organizationId,
                                      UUID orgUnitId, UUID legalEntityId, LocalDate resourceAsOf) throws Exception {
        Class<?> type = Class.forName("com.sanad.platform.hr.security.HrAuthorizationResourceContext");
        return type.getConstructor(UUID.class, String.class, UUID.class, UUID.class, UUID.class, UUID.class,
                        UUID.class, UUID.class, UUID.class, String.class, LocalDate.class)
                .newInstance(tenantId, "EMPLOYMENT", employmentId, personId, employmentId, null,
                        organizationId, orgUnitId, legalEntityId, "INTERNAL", resourceAsOf);
    }

    private Object newRequest(UUID tenantId, UUID userId, String capability, Object context, Instant authTime) throws Exception {
        Class<?> contextType = Class.forName("com.sanad.platform.hr.security.HrAuthorizationResourceContext");
        Class<?> requestType = Class.forName("com.sanad.platform.security.scope.ScopedAuthorizationRequest");
        return requestType.getConstructor(UUID.class, UUID.class, String.class, contextType, Instant.class)
                .newInstance(tenantId, userId, capability, context, authTime);
    }

    private Object authorize(Object service, Object request) throws Exception {
        Class<?> requestType = Class.forName("com.sanad.platform.security.scope.ScopedAuthorizationRequest");
        Method method = service.getClass().getMethod("authorize", requestType);
        return method.invoke(service, request);
    }

    private static Object accessor(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private void seedTenantAndCapability(UUID tenantId, UUID capabilityId, String capabilityCode) throws Exception {
        resetTenant();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId); ps.setString(2, "WS4-T2-" + tenantId);
            ps.setString(3, "ws4t2-" + tenantId.toString().substring(0, 8)); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO access_capabilities (id,code,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, capabilityId); ps.setString(2, capabilityCode); ps.setString(3, "Task2 Capability"); ps.executeUpdate();
        }
    }

    private void setTenant(UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString()); ps.execute();
        }
    }

    private void resetTenant() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', '', false)")) { ps.execute(); }
    }
}
