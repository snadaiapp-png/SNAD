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

/** HRM-G0 / WS4 Task 2: authorization time is current even for historical business reads. */
class HrHistoricalAuthorizationIntegrationTest {

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
    void formerManagerCannotReadHistoricalEmployeeAfterCurrentRelationshipEnds() throws Exception {
        Fixture f = seedHistoricalManagerRelationship();
        Object service = newScopedService(coarseAllow(f));
        Object context = newResourceContext(
                f.tenantId, f.employeeEmploymentId, f.employeePersonId, f.organizationId,
                LocalDate.of(2025, 6, 1));
        Object request = newRequest(f.tenantId, f.managerUserId, f.capabilityCode, context,
                Instant.parse("2026-09-03T00:00:00Z"));

        Object decision = authorize(service, request);
        assertThat(accessor(decision, "allowed")).isEqualTo(false);
        assertThat(accessor(decision, "reason").toString()).contains("SCOPE");
    }

    private Fixture seedHistoricalManagerRelationship() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID managerUser = UUID.randomUUID();
        UUID employeeUser = UUID.randomUUID();
        UUID managerPerson = UUID.randomUUID();
        UUID employeePerson = UUID.randomUUID();
        UUID legalEntity = UUID.randomUUID();
        UUID managerEmployment = UUID.randomUUID();
        UUID employeeEmployment = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID managerAssignment = UUID.randomUUID();
        UUID historicalEmployeeAssignment = UUID.randomUUID();
        UUID currentEmployeeAssignment = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        String capabilityCode = "TEST.WS4.HISTORICAL." + capabilityId;

        resetTenant();
        exec("INSERT INTO tenants(id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                tenant, "WS4 Historical " + tenant, "ws4hist-" + tenant.toString().substring(0,8));
        setTenant(tenant);
        exec("INSERT INTO users(id,tenant_id,email,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                managerUser, tenant, managerUser + "@example.invalid");
        exec("INSERT INTO users(id,tenant_id,email,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                employeeUser, tenant, employeeUser + "@example.invalid");
        exec("INSERT INTO organizations(id,tenant_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                organization, tenant, "Task2 Org " + organization);
        exec("INSERT INTO legal_entities(id,tenant_id,code,name,registered_country_code,statutory_country_code,status) " +
                        "VALUES (?,?,?,?,'SA','SA','ACTIVE')",
                legalEntity, tenant, "LE-" + legalEntity.toString().substring(0,8), "Task2 Employer");
        exec("INSERT INTO hr_people(id,tenant_id,user_id,first_name,last_name,display_name) VALUES (?,?,?,'Manager','User','Manager User')",
                managerPerson, tenant, managerUser);
        exec("INSERT INTO hr_people(id,tenant_id,user_id,first_name,last_name,display_name) VALUES (?,?,?,'Employee','User','Employee User')",
                employeePerson, tenant, employeeUser);
        insertEmployment(managerEmployment, tenant, managerPerson, legalEntity, "MGR");
        insertEmployment(employeeEmployment, tenant, employeePerson, legalEntity, "EMP");

        exec("INSERT INTO hr_employee_assignments(id,tenant_id,employment_id,organization_id,assignment_type,occupancy_mode," +
                        "allocation_percent,effective_from,status) VALUES (?,?,?,?,'PRIMARY','NON_OCCUPYING',100,DATE '2024-01-01','ACTIVE')",
                managerAssignment, tenant, managerEmployment, organization);
        exec("INSERT INTO hr_employee_assignments(id,tenant_id,employment_id,organization_id,reports_to_assignment_id,assignment_type," +
                        "occupancy_mode,allocation_percent,effective_from,effective_to,status) " +
                        "VALUES (?,?,?,?,?,'PRIMARY','NON_OCCUPYING',100,DATE '2025-01-01',DATE '2025-12-31','ENDED')",
                historicalEmployeeAssignment, tenant, employeeEmployment, organization, managerAssignment);
        exec("INSERT INTO hr_employee_assignments(id,tenant_id,employment_id,organization_id,assignment_type,occupancy_mode," +
                        "allocation_percent,effective_from,status) VALUES (?,?,?,?,'PRIMARY','NON_OCCUPYING',100,DATE '2026-01-01','ACTIVE')",
                currentEmployeeAssignment, tenant, employeeEmployment, organization);

        exec("INSERT INTO access_capabilities(id,code,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                capabilityId, capabilityCode, "Historical Task2 Capability");
        exec("INSERT INTO access_scope_grants(tenant_id,role_id,capability_id,scope_type,status,effective_from) " +
                        "VALUES (?,?,?,'DIRECT_REPORTS','ACTIVE',TIMESTAMPTZ '2026-01-01 00:00:00+00')",
                tenant, roleId, capabilityId);
        return new Fixture(tenant, managerUser, employeePerson, employeeEmployment, organization, capabilityCode, roleId);
    }

    private void insertEmployment(UUID id, UUID tenant, UUID person, UUID legalEntity, String prefix) throws Exception {
        exec("INSERT INTO hr_employees(id,tenant_id,person_id,legal_entity_id,employee_number,first_name,last_name,display_name," +
                        "worker_classification_code,status,hire_date) VALUES (?,?,?,?,?,'Task','Two','Task Two','GENERIC_EMPLOYEE','ACTIVE',DATE '2024-01-01')",
                id, tenant, person, legalEntity, prefix + "-" + id.toString().substring(0,8));
    }

    private CapabilityEvaluationService coarseAllow(Fixture f) {
        CapabilityEvaluationService coarse = mock(CapabilityEvaluationService.class);
        when(coarse.evaluate(any(UUID.class), any(UUID.class), anyString(), any(UUID.class)))
                .thenReturn(new AccessDecisionResponse(f.tenantId, f.managerUserId, f.organizationId, f.capabilityCode,
                        true, "ROLE_CAPABILITY_MATCH", f.roleId, "TASK2_MANAGER"));
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

    private Object newResourceContext(UUID tenant, UUID employment, UUID person, UUID organization, LocalDate asOf) throws Exception {
        Class<?> type = Class.forName("com.sanad.platform.hr.security.HrAuthorizationResourceContext");
        return type.getConstructor(UUID.class, String.class, UUID.class, UUID.class, UUID.class, UUID.class,
                        UUID.class, UUID.class, UUID.class, String.class, LocalDate.class)
                .newInstance(tenant, "EMPLOYMENT", employment, person, employment, null,
                        organization, null, null, "INTERNAL", asOf);
    }

    private Object newRequest(UUID tenant, UUID user, String capability, Object context, Instant authTime) throws Exception {
        Class<?> contextType = Class.forName("com.sanad.platform.hr.security.HrAuthorizationResourceContext");
        Class<?> requestType = Class.forName("com.sanad.platform.security.scope.ScopedAuthorizationRequest");
        return requestType.getConstructor(UUID.class, UUID.class, String.class, contextType, Instant.class)
                .newInstance(tenant, user, capability, context, authTime);
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

    private record Fixture(UUID tenantId, UUID managerUserId, UUID employeePersonId, UUID employeeEmploymentId,
                           UUID organizationId, String capabilityCode, UUID roleId) { }
}
