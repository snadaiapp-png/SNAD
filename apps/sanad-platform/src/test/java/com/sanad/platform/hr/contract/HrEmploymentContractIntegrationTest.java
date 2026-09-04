package com.sanad.platform.hr.contract;

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
 * employment contract persistence with immutable temporal guarantees.
 *
 * <p>Temporal invariants enforced AT THE DATABASE: at most one overlapping
 * ACTIVE primary contract per Employment (btree_gist exclusion), no
 * overlapping effective versions inside one contract, structural CHECK
 * constraints, and FORCE fail-closed RLS on every table.</p>
 *
 * <p>RED fails because the WS6 Task 1 schema (hr_employment_contracts /
 * hr_employment_contract_versions) does not exist yet — a clean
 * schema-missing RED.</p>
 */
class HrEmploymentContractIntegrationTest {

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
    void employmentCannotHaveTwoOverlappingActivePrimaryContracts() throws Exception {
        UUID contractA = UUID.randomUUID();
        UUID contractB = UUID.randomUUID();
        insertContract(contractA, "CONTRACT-A");
        insertContract(contractB, "CONTRACT-B");
        insertContractVersion(UUID.randomUUID(), contractA, employmentId, 1, "ACTIVE", "2026-01-01", "2029-12-31", true);
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_employment_contracts")).isEqualTo("2");

        // Overlapping ACTIVE primary window for the same employment must be
        // rejected by the exclusion constraint.
        assertThatThrownBy(() -> insertContractVersion(UUID.randomUUID(), contractB, employmentId, 1,
                "ACTIVE", "2026-06-01", null, true))
                .as("two overlapping ACTIVE primary contracts for one employment must be impossible")
                .isInstanceOf(SQLException.class);

        // Non-overlapping successor window (after the first contract ends) is allowed.
        insertContractVersion(UUID.randomUUID(), contractB, employmentId, 1, "ACTIVE", "2030-01-01", null, true);
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_employment_contract_versions")).isEqualTo("2");
    }

    @Test
    void contractVersionsOfOneContractCannotOverlap() throws Exception {
        UUID contract = UUID.randomUUID();
        insertContract(contract, "CONTRACT-V");
        insertContractVersion(UUID.randomUUID(), contract, employmentId, 1, "ACTIVE", "2026-01-01", "2026-12-31", true);

        assertThatThrownBy(() -> insertContractVersion(UUID.randomUUID(), contract, employmentId, 2,
                "ACTIVE", "2026-07-01", "2027-06-30", true))
                .as("effective versions of one contract must never overlap")
                .isInstanceOf(SQLException.class);
    }

    // ==================== structural constraints ====================

    @Test
    void contractVersionStatusIsConstrained() throws Exception {
        UUID contract = UUID.randomUUID();
        insertContract(contract, "CONTRACT-C");
        assertThatThrownBy(() -> insertContractVersion(UUID.randomUUID(), contract, employmentId, 1,
                "SOMETHING_ELSE", "2026-01-01", null, true))
                .as("status must be constrained to the canonical lifecycle")
                .isInstanceOf(SQLException.class);
    }

    @Test
    void contractVersionDatesAreConstrained() throws Exception {
        UUID contract = UUID.randomUUID();
        insertContract(contract, "CONTRACT-D");
        assertThatThrownBy(() -> insertContractVersion(UUID.randomUUID(), contract, employmentId, 1,
                "ACTIVE", "2026-12-31", "2026-01-01", true))
                .as("end date before start date must be impossible")
                .isInstanceOf(SQLException.class);
    }

    // ==================== fail-closed tenant isolation ====================

    @Test
    void contractTablesHaveForcedRls() throws Exception {
        for (String table : new String[]{
                "hr_employment_contracts", "hr_employment_contract_versions",
                "hr_compensation_packages", "hr_compensation_components"}) {
            assertThat(queryScalar("SELECT (relrowsecurity AND relforcerowsecurity)::text FROM pg_class WHERE relname = '" + table + "'"))
                    .as(table + " must be ENABLE + FORCE row level security")
                    .isEqualTo("true");
        }
    }

    @Test
    void contextlessSessionSeesNoContractRows() throws Exception {
        UUID contract = UUID.randomUUID();
        insertContract(contract, "CONTRACT-E");
        insertContractVersion(UUID.randomUUID(), contract, employmentId, 1, "ACTIVE", "2026-01-01", null, true);

        try (Connection raw = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD).getConnection();
             PreparedStatement ps = raw.prepareStatement("SELECT COUNT(*) FROM hr_employment_contracts");
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
                    ps.setString(2, "WS6-T1-" + tenantId);
                    ps.setString(3, "ws6t1-" + tenantId.toString().substring(0, 8));
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

    private void insertContract(UUID id, String number) throws Exception {
        executeUpdate("INSERT INTO hr_employment_contracts (id, tenant_id, employment_id, contract_number, is_primary) "
                        + "VALUES (?,?,?,?,TRUE)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, employmentId);
                    ps.setString(4, number);
                });
    }

    private void insertContractVersion(UUID id, UUID contractId, UUID employment, int versionNumber,
                                       String status, String effectiveFrom, String effectiveTo,
                                       boolean isPrimary) throws Exception {
        executeUpdate("INSERT INTO hr_employment_contract_versions (id, tenant_id, contract_id, employment_id, "
                        + "version_number, status, contract_term_type, contract_start_date, contract_end_date, "
                        + "effective_from, effective_to, is_primary) "
                        + "VALUES (?,?,?,?,?,?,'FIXED_TERM',?::date,?::date,?::date,?::date,?)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, contractId);
                    ps.setObject(4, employment);
                    ps.setInt(5, versionNumber);
                    ps.setString(6, status);
                    ps.setString(7, effectiveFrom);
                    ps.setString(8, effectiveTo);
                    ps.setString(9, effectiveFrom);
                    ps.setString(10, effectiveTo);
                    ps.setBoolean(11, isPrimary);
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
