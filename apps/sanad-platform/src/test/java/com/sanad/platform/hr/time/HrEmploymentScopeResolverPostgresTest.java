package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.access.AccessDeniedException;

import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL Direct regression for the canonical HR identity contract used by
 * G2 authenticated acceptance.
 *
 * <p>This test intentionally exercises the real migrated PostgreSQL schema and
 * FORCE-RLS tables. It proves the historical G2 failure cannot be hidden by a
 * Mockito SQL-shape test: SELF must resolve through User -> Person -> Employment,
 * legacy-only hr_employees.user_id must not be accepted, and TEAM scope must
 * resolve through PRIMARY assignment reporting.</p>
 */
class HrEmploymentScopeResolverPostgresTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private static String isolatedUrl;

    private Connection connection;
    private JdbcTemplate jdbc;
    private HrEmploymentScopeResolver resolver;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                available = c.isValid(5);
            }
        } catch (Throwable ignored) {
        }

        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateFreshPostgreSql() throws Exception {
        DriverManagerDataSource migrationDataSource =
                new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(migrationDataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();

        connection = migrationDataSource.getConnection();
        connection.setAutoCommit(true);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        resolver = new HrEmploymentScopeResolver(jdbc);
    }

    @AfterEach
    void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void canonicalPersonEmploymentResolvesSelfOnRealPostgreSql() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();

        seedTenant(tenantId);
        setTenant(tenantId);
        seedUser(tenantId, userId, "canonical-self");
        seedLegalEntity(tenantId, legalEntityId, "SELF");
        seedPerson(tenantId, personId, userId, "Canonical", "Self");
        seedCanonicalEmployment(tenantId, employmentId, userId, personId, legalEntityId, "SELF-001");

        assertThat(resolver.requireSelfEmployment(tenantId, userId)).isEqualTo(employmentId);
    }

    @Test
    void legacyOnlyEmployeeUserLinkFailsClosedOnRealPostgreSql() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();

        seedTenant(tenantId);
        setTenant(tenantId);
        seedUser(tenantId, userId, "legacy-only");
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, email, employment_type, status, hire_date, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Legacy', 'Only', 'Legacy Only', ?, 'FULL_TIME', 'ACTIVE', CURRENT_DATE, NOW(), NOW())
                """,
                employmentId, tenantId, userId, "LEGACY-001", "legacy-only@example.test");

        assertThatThrownBy(() -> resolver.requireSelfEmployment(tenantId, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("active HR employment");
    }

    @Test
    void primaryAssignmentReportingAuthorizesOnlyCanonicalDirectReportOnRealPostgreSql() {
        UUID tenantId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID employeeUserId = UUID.randomUUID();
        UUID managerPersonId = UUID.randomUUID();
        UUID employeePersonId = UUID.randomUUID();
        UUID managerEmploymentId = UUID.randomUUID();
        UUID employeeEmploymentId = UUID.randomUUID();
        UUID managerAssignmentId = UUID.randomUUID();
        UUID employeeAssignmentId = UUID.randomUUID();

        seedTenant(tenantId);
        setTenant(tenantId);
        seedOrganization(tenantId, organizationId);
        seedLegalEntity(tenantId, legalEntityId, "TEAM");
        seedUser(tenantId, managerUserId, "manager");
        seedUser(tenantId, employeeUserId, "employee");
        seedPerson(tenantId, managerPersonId, managerUserId, "Canonical", "Manager");
        seedPerson(tenantId, employeePersonId, employeeUserId, "Canonical", "Employee");
        seedCanonicalEmployment(
                tenantId, managerEmploymentId, managerUserId, managerPersonId, legalEntityId, "MGR-001");
        seedCanonicalEmployment(
                tenantId, employeeEmploymentId, employeeUserId, employeePersonId, legalEntityId, "EMP-001");
        seedPrimaryAssignment(
                tenantId, managerAssignmentId, managerEmploymentId, organizationId, null);
        seedPrimaryAssignment(
                tenantId, employeeAssignmentId, employeeEmploymentId, organizationId, managerAssignmentId);

        assertThatCode(() -> resolver.requireManagedEmployment(
                tenantId, managerUserId, employeeEmploymentId)).doesNotThrowAnyException();

        assertThatThrownBy(() -> resolver.requireManagedEmployment(
                tenantId, employeeUserId, managerEmploymentId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("direct report");
    }

    private void seedTenant(UUID tenantId) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """,
                tenantId,
                "G2 scope test " + tenantId,
                "g2-scope-" + tenantId.toString().substring(0, 8));
    }

    private void setTenant(UUID tenantId) {
        jdbc.queryForObject(
                "SELECT set_config('app.tenant_id', ?, false)",
                String.class,
                tenantId.toString());
    }

    private void seedUser(UUID tenantId, UUID userId, String label) {
        jdbc.update("""
                INSERT INTO users (
                    id, tenant_id, email, display_name, status, password_hash, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, NOW(), NOW())
                """,
                userId,
                tenantId,
                label + "@example.test",
                label,
                "test-hash");
    }

    private void seedOrganization(UUID tenantId, UUID organizationId) {
        jdbc.update("""
                INSERT INTO organizations (
                    id, tenant_id, name, description, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'G2 canonical scope regression', 'ACTIVE', NOW(), NOW())
                """,
                organizationId,
                tenantId,
                "G2 scope org " + organizationId);
    }

    private void seedLegalEntity(UUID tenantId, UUID legalEntityId, String codeSuffix) {
        jdbc.update("""
                INSERT INTO legal_entities (
                    id, tenant_id, code, name, registered_country_code,
                    statutory_country_code, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())
                """,
                legalEntityId,
                tenantId,
                "G2-" + codeSuffix + "-" + legalEntityId.toString().substring(0, 8),
                "G2 legal entity " + codeSuffix);
    }

    private void seedPerson(
            UUID tenantId,
            UUID personId,
            UUID userId,
            String firstName,
            String lastName) {
        jdbc.update("""
                INSERT INTO hr_people (
                    id, tenant_id, user_id, first_name, last_name, display_name,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, NOW(), NOW())
                """,
                personId,
                tenantId,
                userId,
                firstName,
                lastName,
                firstName + " " + lastName);
    }

    private void seedCanonicalEmployment(
            UUID tenantId,
            UUID employmentId,
            UUID userId,
            UUID personId,
            UUID legalEntityId,
            String employeeNumber) {
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, person_id, legal_entity_id,
                    worker_classification_code, employee_number, first_name, last_name,
                    display_name, email, employment_type, status, hire_date, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'FULL_TIME', ?, 'Canonical', 'Employee',
                          'Canonical Employee', ?, 'FULL_TIME', 'ACTIVE', CURRENT_DATE, NOW(), NOW())
                """,
                employmentId,
                tenantId,
                userId,
                personId,
                legalEntityId,
                employeeNumber,
                employeeNumber.toLowerCase() + "@example.test");
    }

    private void seedPrimaryAssignment(
            UUID tenantId,
            UUID assignmentId,
            UUID employmentId,
            UUID organizationId,
            UUID reportsToAssignmentId) {
        jdbc.update("""
                INSERT INTO hr_employee_assignments (
                    id, tenant_id, employment_id, organization_id,
                    reports_to_assignment_id, assignment_type, occupancy_mode,
                    allocation_percent, effective_from, effective_to, status, version,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PRIMARY', 'NON_OCCUPYING',
                          100.00, CURRENT_DATE, NULL, 'ACTIVE', 0, NOW(), NOW())
                """,
                assignmentId,
                tenantId,
                employmentId,
                organizationId,
                reportsToAssignmentId);
    }
}
