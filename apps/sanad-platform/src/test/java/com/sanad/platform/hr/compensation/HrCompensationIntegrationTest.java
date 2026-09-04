package com.sanad.platform.hr.compensation;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 5 / WS6 Task 1 RED contract — effective-dated
 * compensation package/component persistence with structural constraints.
 *
 * <p>Temporal invariants enforced AT THE DATABASE: at most one overlapping
 * ACTIVE compensation package per Employment (btree_gist exclusion), one
 * BASE_SALARY component maximum, amount/percentage exclusivity, structural
 * CHECK constraints, and FORCE fail-closed RLS on every table.</p>
 *
 * <p>RED fails because the WS6 Task 1 schema (hr_compensation_packages /
 * hr_compensation_components) does not exist yet — a clean schema-missing RED.</p>
 */
class HrCompensationIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private UUID tenantId;
    private UUID legalEntityId;
    private UUID personId;
    private UUID employmentId;

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

        tenantId = UUID.randomUUID();
        legalEntityId = UUID.randomUUID();
        personId = UUID.randomUUID();
        employmentId = UUID.randomUUID();
        insertTenant();
        setTenant(tenantId);
        insertLegalEntity();
        insertPerson();
        insertEmployment();
    }

    // ==================== temporal exclusivity ====================

    @Test
    void employmentCannotHaveOverlappingActiveCompensationPackages() throws Exception {
        UUID packageA = UUID.randomUUID();
        UUID packageB = UUID.randomUUID();
        insertPackage(packageA, "SAR", "MONTHLY", "2026-01-01", null, "ACTIVE");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_compensation_packages")).isEqualTo("1");

        assertThatThrownBy(() -> insertPackage(packageB, "SAR", "MONTHLY", "2026-06-01", null, "ACTIVE"))
                .as("two overlapping ACTIVE compensation packages for one employment must be impossible")
                .isInstanceOf(SQLException.class);

        // Successor window is allowed.
        insertPackage(packageB, "SAR", "MONTHLY", "2030-01-01", null, "ACTIVE");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_compensation_packages")).isEqualTo("2");
    }

    @Test
    void packageStatusAndDatesAreConstrained() throws Exception {
        assertThatThrownBy(() -> insertPackage(UUID.randomUUID(), "SAR", "MONTHLY", "2026-01-01", null, "SOMETHING"))
                .as("package status must be constrained to the canonical lifecycle")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertPackage(UUID.randomUUID(), "SAR", "MONTHLY", "2026-12-31", "2026-01-01", "ACTIVE"))
                .as("effective_to before effective_from must be impossible")
                .isInstanceOf(SQLException.class);
    }

    // ==================== structural component rules ====================

    @Test
    void componentTypeIsConstrained() throws Exception {
        UUID pkg = UUID.randomUUID();
        insertPackage(pkg, "SAR", "MONTHLY", "2026-01-01", null, "ACTIVE");
        assertThatThrownBy(() -> insertComponent(UUID.randomUUID(), pkg, "STOCK_OPTION", "EOQ", "5000.0000", null))
                .as("component type must be constrained to the canonical set")
                .isInstanceOf(SQLException.class);
    }

    @Test
    void componentMustCarryAmountOrPercentageExclusively() throws Exception {
        UUID pkg = UUID.randomUUID();
        insertPackage(pkg, "SAR", "MONTHLY", "2026-01-01", null, "ACTIVE");
        assertThatThrownBy(() -> insertComponent(UUID.randomUUID(), pkg, "BASE_SALARY", "BASE", null, null))
                .as("a component without amount and without percentage is invalid")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertComponent(UUID.randomUUID(), pkg, "ALLOWANCE", "HOU", "1500.0000", "5.0000"))
                .as("a component with both amount and percentage is invalid")
                .isInstanceOf(SQLException.class);
        insertComponent(UUID.randomUUID(), pkg, "BASE_SALARY", "BASE", "9000.0000", null);
        insertComponent(UUID.randomUUID(), pkg, "ALLOWANCE", "HOU", null, "25.0000");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_compensation_components")).isEqualTo("2");
    }

    @Test
    void componentAmountsMustBePositive() throws Exception {
        UUID pkg = UUID.randomUUID();
        insertPackage(pkg, "SAR", "MONTHLY", "2026-01-01", null, "ACTIVE");
        assertThatThrownBy(() -> insertComponent(UUID.randomUUID(), pkg, "BASE_SALARY", "BASE", "-1.0000", null))
                .as("amount must be positive when present")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertComponent(UUID.randomUUID(), pkg, "ALLOWANCE", "HOU", null, "0.0000"))
                .as("percentage must be positive when present")
                .isInstanceOf(SQLException.class);
    }

    // ==================== fail-closed tenant isolation ====================

    @Test
    void compensationTablesHaveForcedRls() throws Exception {
        for (String table : new String[]{"hr_compensation_packages", "hr_compensation_components"}) {
            assertThat(queryScalar("SELECT (relrowsecurity AND relforcerowsecurity)::text FROM pg_class WHERE relname = '" + table + "'"))
                    .isEqualTo("t");
        }
    }

    @Test
    void contextlessSessionSeesNoCompensationRows() throws Exception {
        insertPackage(UUID.randomUUID(), "SAR", "MONTHLY", "2026-01-01", null, "ACTIVE");
        try (Connection raw = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD).getConnection();
             PreparedStatement ps = raw.prepareStatement("SELECT COUNT(*) FROM hr_compensation_packages");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getString(1))
                    .as("a session without app.tenant_id must see nothing (FORCE RLS fail closed)")
                    .isEqualTo("0");
        }
    }

    // ==================== fixtures ====================

    private void insertTenant() throws Exception {
        executeUpdate("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "WS6-C1-" + tenantId);
                    ps.setString(3, "ws6c1-" + tenantId.toString().substring(0, 8));
                });
    }

    private void insertLegalEntity() throws Exception {
        executeUpdate("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status) "
                        + "VALUES (?,?,?,?,?,?,?)",
                ps -> {
                    ps.setObject(1, legalEntityId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "LE-" + legalEntityId.toString().substring(0, 8));
                    ps.setString(4, "WS6 Legal Entity");
                    ps.setString(5, "SA");
                    ps.setString(6, "SA");
                    ps.setString(7, "ACTIVE");
                });
    }

    private void insertPerson() throws Exception {
        executeUpdate("INSERT INTO hr_people (id, tenant_id, first_name, last_name, display_name) VALUES (?,?,?,?,?)",
                ps -> {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "Test");
                    ps.setString(4, "Person");
                    ps.setString(5, "Test Person");
                });
    }

    private void insertEmployment() throws Exception {
        executeUpdate("INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, "
                        + "first_name, last_name, display_name, employment_type, status, version, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?, 'Test','Employee','Test Employee','FULL_TIME','ACTIVE',1,NOW(),NOW())",
                ps -> {
                    ps.setObject(1, employmentId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, personId);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "EMP-" + employmentId.toString().substring(0, 8));
                });
    }

    private void insertPackage(UUID id, String currency, String payFrequency,
                               String effectiveFrom, String effectiveTo, String status) throws Exception {
        executeUpdate("INSERT INTO hr_compensation_packages (id, tenant_id, employment_id, currency_code, "
                        + "pay_frequency, effective_from, effective_to, status) VALUES (?,?,?,?,?,?::date,?::date,?)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, employmentId);
                    ps.setString(4, currency);
                    ps.setString(5, payFrequency);
                    ps.setString(6, effectiveFrom);
                    ps.setString(7, effectiveTo);
                    ps.setString(8, status);
                });
    }

    private void insertComponent(UUID id, UUID packageId, String type, String code,
                                 String amount, String percentage) throws Exception {
        executeUpdate("INSERT INTO hr_compensation_components (id, tenant_id, package_id, component_type, code, amount, percentage) "
                        + "VALUES (?,?,?,?,?,?::numeric,?::numeric)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, packageId);
                    ps.setString(4, type);
                    ps.setString(5, code);
                    ps.setString(6, amount);
                    ps.setString(7, percentage);
                });
    }

    private void setTenant(UUID tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private String queryScalar(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void executeUpdate(String sql, SqlBinder binder) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (binder != null) binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }
}
