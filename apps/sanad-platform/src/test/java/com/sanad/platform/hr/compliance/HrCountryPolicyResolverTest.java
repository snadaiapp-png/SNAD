package com.sanad.platform.hr.compliance;

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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HRM-G0 / Master Task 4 / WS3 Task 2 behavioral RED contract. */
class HrCountryPolicyResolverTest {

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
    void canonicalJurisdictionHistoryTableExistsAndIsForcedRls() {
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.hr_employment_jurisdiction_periods')::text", String.class))
                .isEqualTo("hr_employment_jurisdiction_periods");
        assertThat(jdbc.queryForObject(
                "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE relname='hr_employment_jurisdiction_periods'",
                Boolean.class)).isTrue();
    }

    @Test
    void resolvesActivePackFromEmploymentJurisdictionAndNotEmployerCountry() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId, "CANONICAL_EMPLOYEE", "SA", "SA");
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "AE", date.minusMonths(1), null);
        seedActivePack("AE", "AE-HR-FOUNDATION", "2", date.minusDays(1), null);

        Object result = invokeResolve(tenantId, employmentId, date);
        assertThat(invokeAccessor(result, "mode").toString()).isEqualTo("LOCALIZED");
        assertThat(invokeAccessor(result, "laborJurisdiction")).isEqualTo("AE");
        assertThat(invokeAccessor(result, "packCode")).isEqualTo("AE-HR-FOUNDATION");
        assertThat(invokeAccessor(result, "packVersion")).isEqualTo("2");
        assertThat(invokeAccessor(result, "workerClassification")).isEqualTo("CANONICAL_EMPLOYEE");
    }

    @Test
    void uncertifiedCountryFallsBackToGlobalModeWithoutBorrowingAnotherPack() throws Exception {
        UUID tenantId = UUID.randomUUID();
        registerCountry("EG");
        UUID employmentId = seedEmployment(tenantId, "LOCAL_CLASSIFICATION", "SA", "SA");
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "EG", date.minusMonths(1), null);
        seedActivePack("SA", "SA-RUNTIME", "99", date.minusDays(1), null);

        Object result = invokeResolve(tenantId, employmentId, date);
        assertThat(invokeAccessor(result, "mode").toString()).isEqualTo("GLOBAL");
        assertThat(invokeAccessor(result, "laborJurisdiction")).isEqualTo("EG");
        assertThat(invokeAccessor(result, "packCode")).isNull();
        assertThat(invokeAccessor(result, "workerClassification")).isEqualTo("GENERIC_EMPLOYEE");
    }

    @Test
    void missingEmploymentJurisdictionFailsInsteadOfInferringEmployerCountry() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId, "CANONICAL_EMPLOYEE", "SA", "SA");

        assertThatThrownBy(() -> invokeResolve(tenantId, employmentId, LocalDate.of(2026, 9, 3)))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("HRM_LEGAL_REVIEW_REQUIRED");
    }

    private Object invokeResolve(UUID tenantId, UUID employmentId, LocalDate date) throws Exception {
        Class<?> workerType = Class.forName(
                "com.sanad.platform.hr.compliance.application.WorkerClassificationResolver");
        Object worker = workerType.getConstructor(JdbcTemplate.class).newInstance(jdbc);
        Class<?> resolverType = Class.forName(
                "com.sanad.platform.hr.compliance.application.CountryPolicyResolver");
        Object resolver = resolverType.getConstructor(JdbcTemplate.class, workerType).newInstance(jdbc, worker);
        Method resolve = resolverType.getMethod("resolve", UUID.class, UUID.class, LocalDate.class);
        return resolve.invoke(resolver, tenantId, employmentId, date);
    }

    private static Object invokeAccessor(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private UUID seedEmployment(UUID tenantId, String classification, String registeredCountry, String statutoryCountry)
            throws Exception {
        UUID legalEntityId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        resetTenant();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS3-T2-" + tenantId);
            ps.setString(3, "ws3t2-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO legal_entities (id,tenant_id,code,name,registered_country_code,statutory_country_code,status) " +
                        "VALUES (?,?,?,?,?,?,'ACTIVE')")) {
            ps.setObject(1, legalEntityId); ps.setObject(2, tenantId); ps.setString(3, "LE-" + legalEntityId.toString().substring(0,8));
            ps.setString(4, "Task2 Employer"); ps.setString(5, registeredCountry); ps.setString(6, statutoryCountry); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_people (id,tenant_id,first_name,last_name,display_name) VALUES (?,?, 'Task','Two','Task Two')")) {
            ps.setObject(1, personId); ps.setObject(2, tenantId); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_employees (id,tenant_id,person_id,legal_entity_id,employee_number,first_name,last_name,display_name," +
                        "worker_classification_code,status,hire_date) VALUES (?,?,?,?,?,'Task','Two','Task Two',?,'ACTIVE',DATE '2026-01-01')")) {
            ps.setObject(1, employmentId); ps.setObject(2, tenantId); ps.setObject(3, personId); ps.setObject(4, legalEntityId);
            ps.setString(5, "E-" + employmentId.toString().substring(0,8)); ps.setString(6, classification); ps.executeUpdate();
        }
        return employmentId;
    }

    private void seedJurisdiction(UUID tenantId, UUID employmentId, String countryCode, LocalDate from, LocalDate to)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_employment_jurisdiction_periods " +
                        "(tenant_id,employment_id,labor_jurisdiction,effective_from,effective_to,approval_status,approval_reference) " +
                        "VALUES (?,?,?,?,?,'APPROVED','TEST-APPROVAL')")) {
            ps.setObject(1, tenantId); ps.setObject(2, employmentId); ps.setString(3, countryCode); ps.setObject(4, from); ps.setObject(5, to);
            ps.executeUpdate();
        }
    }

    private void seedActivePack(String country, String code, String version, LocalDate from, LocalDate to) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_country_packs (country_code,pack_code,pack_version,status,effective_from,effective_to," +
                        "legal_reviewed_at,legal_reviewed_by,certification_reference) VALUES (?,?,?,'ACTIVE',?,?,NOW(),'legal-review','TEST-CERT')")) {
            ps.setString(1, country); ps.setString(2, code); ps.setString(3, version); ps.setObject(4, from); ps.setObject(5, to); ps.executeUpdate();
        }
    }

    private void registerCountry(String code) {
        jdbc.update("INSERT INTO platform_countries(country_code,name_en,name_ar,status) VALUES (?, ?, ?, 'ACTIVE') ON CONFLICT DO NOTHING",
                code, "Test " + code, "Test " + code);
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString()); ps.execute();
        }
    }

    private void resetTenant() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
    }
}
