package com.sanad.platform.hr.payroll;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G4 / T1 RED contract.
 *
 * <p>First canonical payroll invariant on real PostgreSQL Direct:
 * canonical employment + effective compensation + APPROVED G2 timesheet
 * must be snapshot-able into tenant-bound payroll rows, and a foreign tenant
 * must see zero payroll items through FORCE RLS.</p>
 *
 * <p>G2 design mentioned a LOCKED terminal state, but the migrated schema
 * currently constrains hr_timesheets to DRAFT/SUBMITTED/APPROVED/REJECTED.
 * G4 therefore consumes APPROVED as the authoritative implemented source
 * state and does not invent a non-existent LOCKED state.</p>
 *
 * <p>Expected initial RED: hr_payroll_runs / hr_payroll_items do not exist.
 * No production implementation is allowed in T1.</p>
 */
class HrG4PayrollSnapshotPostgresTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private static String isolatedUrl;

    private Connection connection;

    private UUID tenantId;
    private UUID foreignTenantId;
    private UUID legalEntityId;
    private UUID personId;
    private UUID employmentId;
    private UUID compensationPackageId;
    private UUID timesheetId;

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
    void migrateFreshPostgreSqlAndSeedAuthoritativeInputs() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();

        connection = ds.getConnection();
        connection.setAutoCommit(true);

        tenantId = UUID.randomUUID();
        foreignTenantId = UUID.randomUUID();
        legalEntityId = UUID.randomUUID();
        personId = UUID.randomUUID();
        employmentId = UUID.randomUUID();
        compensationPackageId = UUID.randomUUID();
        timesheetId = UUID.randomUUID();

        seedTenant(tenantId, "g4-payroll");
        seedTenant(foreignTenantId, "g4-foreign");
        setTenant(tenantId);
        seedLegalEntity();
        seedPerson();
        seedEmployment();
        seedCompensation();
        seedApprovedTimesheet();
    }

    @AfterEach
    void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void canonicalEmploymentCompensationAndApprovedTimesheetProduceTenantBoundPayrollSnapshot() throws Exception {
        // Prove the authoritative pre-G4 inputs are real and queryable before
        // failing on the intentionally missing G4 persistence.
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT c.amount, t.total_worked_minutes
                  FROM hr_compensation_packages p
                  JOIN hr_compensation_components c
                    ON c.tenant_id = p.tenant_id
                   AND c.package_id = p.id
                   AND c.component_type = 'BASE_SALARY'
                  JOIN hr_timesheets t
                    ON t.tenant_id = p.tenant_id
                   AND t.employment_id = p.employment_id
                   AND t.state = 'APPROVED'
                 WHERE p.tenant_id = ?
                   AND p.employment_id = ?
                   AND p.id = ?
                   AND t.id = ?
                   AND DATE '2026-10-31' BETWEEN p.effective_from
                       AND COALESCE(p.effective_to, DATE '9999-12-31')
                """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employmentId);
            ps.setObject(3, compensationPackageId);
            ps.setObject(4, timesheetId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("authoritative G0/G2 payroll inputs must exist").isTrue();
                assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("10000.0000");
                assertThat(rs.getInt(2)).isEqualTo(9600);
            }
        }

        // RED checkpoint: these tables intentionally do not exist on the G4
        // branch baseline. The first implementation task must make this
        // contract GREEN with additive Flyway + FORCE RLS.
        assertThat(regclass("hr_payroll_runs"))
                .as("G4 payroll-run persistence must exist")
                .isEqualTo("hr_payroll_runs");
        assertThat(regclass("hr_payroll_items"))
                .as("G4 payroll-item snapshot persistence must exist")
                .isEqualTo("hr_payroll_items");

        UUID runId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        execute("""
                INSERT INTO hr_payroll_runs (
                    id, tenant_id, legal_entity_id, period_start, period_end,
                    currency_code, status, source_cutoff_at, created_at, updated_at
                ) VALUES (?, ?, ?, DATE '2026-10-01', DATE '2026-10-31',
                          'SAR', 'DRAFT', ?, NOW(), NOW())
                """, ps -> {
            ps.setObject(1, runId);
            ps.setObject(2, tenantId);
            ps.setObject(3, legalEntityId);
            ps.setObject(4, OffsetDateTime.parse("2026-11-01T00:00:00Z"));
        });

        execute("""
                INSERT INTO hr_payroll_items (
                    id, tenant_id, payroll_run_id, employment_id,
                    compensation_package_id, timesheet_id,
                    base_amount, gross_amount, deduction_total, net_amount,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?,
                          10000.0000, 10000.0000, 0.0000, 10000.0000,
                          'CALCULATED', NOW(), NOW())
                """, ps -> {
            ps.setObject(1, itemId);
            ps.setObject(2, tenantId);
            ps.setObject(3, runId);
            ps.setObject(4, employmentId);
            ps.setObject(5, compensationPackageId);
            ps.setObject(6, timesheetId);
        });

        assertThat(forceRls("hr_payroll_runs")).isTrue();
        assertThat(forceRls("hr_payroll_items")).isTrue();

        setTenant(foreignTenantId);
        assertThat(queryCount("SELECT COUNT(*) FROM hr_payroll_runs"))
                .as("foreign tenant must not see another tenant payroll run")
                .isZero();
        assertThat(queryCount("SELECT COUNT(*) FROM hr_payroll_items"))
                .as("foreign tenant must not see another tenant payroll item")
                .isZero();

        setTenant(tenantId);
        assertThat(queryCount("SELECT COUNT(*) FROM hr_payroll_items WHERE employment_id = '" + employmentId + "'"))
                .as("own tenant can read the snapshotted payroll item")
                .isEqualTo(1);
    }

    private void seedTenant(UUID id, String prefix) throws Exception {
        execute("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, ps -> {
            ps.setObject(1, id);
            ps.setString(2, "G4 Payroll " + id);
            ps.setString(3, prefix + "-" + id.toString().substring(0, 8));
        });
    }

    private void seedLegalEntity() throws Exception {
        execute("""
                INSERT INTO legal_entities (
                    id, tenant_id, code, name, registered_country_code,
                    statutory_country_code, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'G4 Legal Entity', 'SA', 'SA', 'ACTIVE', NOW(), NOW())
                """, ps -> {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, "G4-" + legalEntityId.toString().substring(0, 8));
        });
    }

    private void seedPerson() throws Exception {
        execute("""
                INSERT INTO hr_people (
                    id, tenant_id, first_name, last_name, display_name,
                    version, created_at, updated_at
                ) VALUES (?, ?, 'Payroll', 'Subject', 'Payroll Subject', 0, NOW(), NOW())
                """, ps -> {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
        });
    }

    private void seedEmployment() throws Exception {
        execute("""
                INSERT INTO hr_employees (
                    id, tenant_id, person_id, legal_entity_id,
                    worker_classification_code, employee_number,
                    first_name, last_name, display_name, email,
                    employment_type, status, hire_date, version,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'FULL_TIME', ?,
                          'Payroll', 'Subject', 'Payroll Subject', ?,
                          'FULL_TIME', 'ACTIVE', DATE '2026-01-01', 0,
                          NOW(), NOW())
                """, ps -> {
            ps.setObject(1, employmentId);
            ps.setObject(2, tenantId);
            ps.setObject(3, personId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, "G4-EMP-" + employmentId.toString().substring(0, 8));
            ps.setString(6, "g4-" + employmentId.toString().substring(0, 8) + "@example.test");
        });
    }

    private void seedCompensation() throws Exception {
        execute("""
                INSERT INTO hr_compensation_packages (
                    id, tenant_id, employment_id, currency_code, pay_frequency,
                    effective_from, effective_to, status, created_at
                ) VALUES (?, ?, ?, 'SAR', 'MONTHLY',
                          DATE '2026-01-01', NULL, 'ACTIVE', NOW())
                """, ps -> {
            ps.setObject(1, compensationPackageId);
            ps.setObject(2, tenantId);
            ps.setObject(3, employmentId);
        });

        execute("""
                INSERT INTO hr_compensation_components (
                    id, tenant_id, package_id, component_type, code,
                    amount, percentage, created_at
                ) VALUES (?, ?, ?, 'BASE_SALARY', 'BASE',
                          10000.0000, NULL, NOW())
                """, ps -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, compensationPackageId);
        });
    }

    private void seedApprovedTimesheet() throws Exception {
        execute("""
                INSERT INTO hr_timesheets (
                    id, tenant_id, employment_id, period_start, period_end,
                    total_worked_minutes, total_break_minutes, state,
                    submitted_at, approved_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, DATE '2026-10-01', DATE '2026-10-31',
                          9600, 0, 'APPROVED', NOW(), NOW(), 0, NOW(), NOW())
                """, ps -> {
            ps.setObject(1, timesheetId);
            ps.setObject(2, tenantId);
            ps.setObject(3, employmentId);
        });
    }

    private String regclass(String table) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT to_regclass('public.' || ?)::text")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private boolean forceRls(String table) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT relrowsecurity AND relforcerowsecurity
                  FROM pg_class
                 WHERE relname = ?
                   AND relkind = 'r'
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("table %s must exist", table).isTrue();
                return rs.getBoolean(1);
            }
        }
    }

    private int queryCount(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void setTenant(UUID tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private void execute(String sql, SqlBinder binder) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }
}
