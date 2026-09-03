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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** HRM-G0 / Master Task 4 / WS4 Task 2 complete scope matrix RED contract. */
class HrScopedAuthorizationScopeMatrixIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final Instant AUTH_TIME = Instant.parse("2026-09-03T00:00:00Z");
    private static final LocalDate AUTH_DATE = LocalDate.of(2026, 9, 3);
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
    void selfScopeAllowsOnlyPersonLinkedToCurrentUser() throws Exception {
        Fixture f = seedBase();
        grant(f, "SELF", null);
        Object service = newScopedService(coarseAllow(f));

        Object own = authorize(service, request(f, context(f, f.actorPersonId, f.actorEmploymentId, null)));
        assertThat(accessor(own, "allowed")).isEqualTo(true);

        Object other = authorize(service, request(f, context(f, f.targetPersonId, f.targetEmploymentId, null)));
        assertThat(accessor(other, "allowed")).isEqualTo(false);
    }

    @Test
    void directReportsScopeUsesCurrentPrimaryAssignmentGraph() throws Exception {
        Fixture f = seedBase();
        UUID managerAssignment = insertAssignment(f, f.actorEmploymentId, null, null);
        insertAssignment(f, f.targetEmploymentId, managerAssignment, null);
        grant(f, "DIRECT_REPORTS", null);

        Object decision = authorize(newScopedService(coarseAllow(f)),
                request(f, context(f, f.targetPersonId, f.targetEmploymentId, null)));
        assertThat(accessor(decision, "allowed")).isEqualTo(true);
    }

    @Test
    void reportingTreeScopeTraversesCurrentHierarchy() throws Exception {
        Fixture f = seedBase();
        UUID middleUser = UUID.randomUUID();
        UUID middlePerson = UUID.randomUUID();
        UUID middleEmployment = UUID.randomUUID();
        exec("INSERT INTO users(id,tenant_id,email,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                middleUser, f.tenantId, middleUser + "@example.invalid");
        exec("INSERT INTO hr_people(id,tenant_id,user_id,first_name,last_name,display_name) VALUES (?,?,?,'Middle','Manager','Middle Manager')",
                middlePerson, f.tenantId, middleUser);
        insertEmployment(middleEmployment, f.tenantId, middlePerson, f.legalEntityId, "MID");

        UUID managerAssignment = insertAssignment(f, f.actorEmploymentId, null, null);
        UUID middleAssignment = insertAssignment(f, middleEmployment, managerAssignment, null);
        insertAssignment(f, f.targetEmploymentId, middleAssignment, null);
        grant(f, "REPORTING_TREE", null);

        Object decision = authorize(newScopedService(coarseAllow(f)),
                request(f, context(f, f.targetPersonId, f.targetEmploymentId, null)));
        assertThat(accessor(decision, "allowed")).isEqualTo(true);
    }

    @Test
    void orgUnitScopeIncludesCurrentDescendantsButNotUnrelatedUnits() throws Exception {
        Fixture f = seedBase();
        UUID root = insertOrgUnit(f, "ROOT", null);
        UUID child = insertOrgUnit(f, "CHILD", root);
        UUID unrelated = insertOrgUnit(f, "OTHER", null);
        grant(f, "ORG_UNIT", root);
        Object service = newScopedService(coarseAllow(f));

        Object childDecision = authorize(service,
                request(f, context(f, f.targetPersonId, f.targetEmploymentId, child)));
        assertThat(accessor(childDecision, "allowed")).isEqualTo(true);

        Object unrelatedDecision = authorize(service,
                request(f, context(f, f.targetPersonId, f.targetEmploymentId, unrelated)));
        assertThat(accessor(unrelatedDecision, "allowed")).isEqualTo(false);
    }

    @Test
    void tenantScopeAllowsAnyResourceInSameTenantAfterCoarseCapabilityGate() throws Exception {
        Fixture f = seedBase();
        grant(f, "TENANT", null);
        Object decision = authorize(newScopedService(coarseAllow(f)),
                request(f, context(f, f.targetPersonId, f.targetEmploymentId, null)));
        assertThat(accessor(decision, "allowed")).isEqualTo(true);
    }

    private Fixture seedBase() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID actorUser = UUID.randomUUID();
        UUID targetUser = UUID.randomUUID();
        UUID actorPerson = UUID.randomUUID();
        UUID targetPerson = UUID.randomUUID();
        UUID legalEntity = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID actorEmployment = UUID.randomUUID();
        UUID targetEmployment = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        String capabilityCode = "TEST.WS4.MATRIX." + capabilityId;

        resetTenant();
        exec("INSERT INTO tenants(id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                tenant, "WS4 Matrix " + tenant, "ws4matrix-" + tenant.toString().substring(0, 8));
        setTenant(tenant);
        exec("INSERT INTO users(id,tenant_id,email,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                actorUser, tenant, actorUser + "@example.invalid");
        exec("INSERT INTO users(id,tenant_id,email,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                targetUser, tenant, targetUser + "@example.invalid");
        exec("INSERT INTO organizations(id,tenant_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                organization, tenant, "Task2 Matrix Org " + organization);
        exec("INSERT INTO legal_entities(id,tenant_id,code,name,registered_country_code,statutory_country_code,status) " +
                        "VALUES (?,?,?,?,'SA','SA','ACTIVE')",
                legalEntity, tenant, "LE-" + legalEntity.toString().substring(0, 8), "Task2 Matrix Employer");
        exec("INSERT INTO hr_people(id,tenant_id,user_id,first_name,last_name,display_name) VALUES (?,?,?,'Actor','User','Actor User')",
                actorPerson, tenant, actorUser);
        exec("INSERT INTO hr_people(id,tenant_id,user_id,first_name,last_name,display_name) VALUES (?,?,?,'Target','User','Target User')",
                targetPerson, tenant, targetUser);
        insertEmployment(actorEmployment, tenant, actorPerson, legalEntity, "ACT");
        insertEmployment(targetEmployment, tenant, targetPerson, legalEntity, "TGT");
        exec("INSERT INTO access_capabilities(id,code,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                capabilityId, capabilityCode, "Task2 Matrix Capability");
        return new Fixture(tenant, actorUser, actorPerson, actorEmployment, targetPerson, targetEmployment,
                legalEntity, organization, capabilityId, roleId, capabilityCode);
    }

    private void insertEmployment(UUID id, UUID tenant, UUID person, UUID legalEntity, String prefix) {
        exec("INSERT INTO hr_employees(id,tenant_id,person_id,legal_entity_id,employee_number,first_name,last_name,display_name," +
                        "worker_classification_code,status,hire_date) VALUES (?,?,?,?,?,'Task','Two','Task Two','GENERIC_EMPLOYEE','ACTIVE',DATE '2024-01-01')",
                id, tenant, person, legalEntity, prefix + "-" + id.toString().substring(0, 8));
    }

    private UUID insertAssignment(Fixture f, UUID employmentId, UUID reportsTo, UUID orgUnitId) {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO hr_employee_assignments(id,tenant_id,employment_id,organization_id,org_unit_id,reports_to_assignment_id," +
                        "assignment_type,occupancy_mode,allocation_percent,effective_from,status) " +
                        "VALUES (?,?,?,?,?,?,'PRIMARY','NON_OCCUPYING',100,DATE '2026-01-01','ACTIVE')",
                id, f.tenantId, employmentId, f.organizationId, orgUnitId, reportsTo);
        return id;
    }

    private UUID insertOrgUnit(Fixture f, String code, UUID parentId) {
        UUID unit = UUID.randomUUID();
        exec("INSERT INTO hr_org_units(id,tenant_id,organization_id,stable_code) VALUES (?,?,?,?)",
                unit, f.tenantId, f.organizationId, code + "-" + unit.toString().substring(0, 8));
        exec("INSERT INTO hr_org_unit_versions(tenant_id,org_unit_id,name,code,unit_type,parent_org_unit_id,effective_from,status) " +
                        "VALUES (?,?,?,?,'DEPARTMENT',?,DATE '2026-01-01','ACTIVE')",
                f.tenantId, unit, code, code + "-" + unit.toString().substring(0, 8), parentId);
        return unit;
    }

    private void grant(Fixture f, String scopeType, UUID orgUnitId) {
        exec("INSERT INTO access_scope_grants(tenant_id,role_id,capability_id,scope_type,organization_id,org_unit_id,status,effective_from) " +
                        "VALUES (?,?,?,?,?,?,'ACTIVE',TIMESTAMPTZ '2026-01-01 00:00:00+00')",
                f.tenantId, f.roleId, f.capabilityId, scopeType, f.organizationId, orgUnitId);
    }

    private CapabilityEvaluationService coarseAllow(Fixture f) {
        CapabilityEvaluationService coarse = mock(CapabilityEvaluationService.class);
        when(coarse.evaluate(any(UUID.class), any(UUID.class), anyString(), any(UUID.class)))
                .thenReturn(new AccessDecisionResponse(f.tenantId, f.actorUserId, f.organizationId, f.capabilityCode,
                        true, "ROLE_CAPABILITY_MATCH", f.roleId, "TASK2_MATRIX_ROLE"));
        return coarse;
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

    private Object context(Fixture f, UUID personId, UUID employmentId, UUID orgUnitId) throws Exception {
        Class<?> type = Class.forName("com.sanad.platform.hr.security.HrAuthorizationResourceContext");
        return type.getConstructor(UUID.class, String.class, UUID.class, UUID.class, UUID.class, UUID.class,
                        UUID.class, UUID.class, UUID.class, String.class, LocalDate.class)
                .newInstance(f.tenantId, "EMPLOYMENT", employmentId, personId, employmentId, null,
                        f.organizationId, orgUnitId, f.legalEntityId, "INTERNAL", LocalDate.of(2025, 6, 1));
    }

    private Object request(Fixture f, Object context) throws Exception {
        Class<?> contextType = Class.forName("com.sanad.platform.hr.security.HrAuthorizationResourceContext");
        Class<?> requestType = Class.forName("com.sanad.platform.security.scope.ScopedAuthorizationRequest");
        return requestType.getConstructor(UUID.class, UUID.class, String.class, contextType, Instant.class)
                .newInstance(f.tenantId, f.actorUserId, f.capabilityCode, context, AUTH_TIME);
    }

    private Object authorize(Object service, Object request) throws Exception {
        Class<?> requestType = Class.forName("com.sanad.platform.security.scope.ScopedAuthorizationRequest");
        Method method = service.getClass().getMethod("authorize", requestType);
        return method.invoke(service, request);
    }

    private static Object accessor(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private void exec(String sql, Object... args) { jdbc.update(sql, args); }

    private void setTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString()); ps.execute();
        }
    }

    private void resetTenant() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', '', false)")) { ps.execute(); }
    }

    private record Fixture(
            UUID tenantId,
            UUID actorUserId,
            UUID actorPersonId,
            UUID actorEmploymentId,
            UUID targetPersonId,
            UUID targetEmploymentId,
            UUID legalEntityId,
            UUID organizationId,
            UUID capabilityId,
            UUID roleId,
            String capabilityCode) { }
}
