package com.sanad.platform.hr.time;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G2 PostgreSQL Direct integration test.
 *
 * <p>Tests run against host-native PostgreSQL (no Docker/Testcontainers).
 * Tests: G2 table existence, RLS enforcement, schema constraints.
 *
 * <p>Full lifecycle tests (attendance, leave, timesheets) require
 * application context + service injection — those are covered by
 * {@code HrG2LeavePostgresIntegrationTest} (Spring Boot test).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG2PostgresIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    void requirePostgres() {
        Assumptions.assumeTrue(
                System.getenv("SPRING_DATASOURCE_URL") != null
                        || "sanad".equals(System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "")),
                "PostgreSQL Direct tests require SPRING_DATASOURCE_URL"
        );
    }

    @Test
    void g2TablesExist() throws Exception {
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' " +
                    "AND table_name IN (" +
                    "'hr_work_schedules','hr_schedule_versions','hr_schedule_assignments'," +
                    "'hr_attendance_events','hr_attendance_records'," +
                    "'hr_leave_types','hr_leave_balances','hr_leave_requests'," +
                    "'hr_leave_policies','hr_leave_ledger_entries'," +
                    "'hr_timesheets','hr_timesheet_entries'," +
                    "'hr_g2_idempotency_records'" +
                    ")"
            );
            List<String> tables = new ArrayList<>();
            while (rs.next()) tables.add(rs.getString("table_name"));
            assertThat(tables).as("All G2 tables must exist").hasSizeGreaterThanOrEqualTo(13);
        }
    }

    @Test
    void rlsIsEnabledAndForcedOnAllG2Tables() throws Exception {
        String[] g2Tables = {
            "hr_work_schedules", "hr_schedule_versions", "hr_schedule_assignments",
            "hr_attendance_events", "hr_attendance_records",
            "hr_leave_types", "hr_leave_balances", "hr_leave_requests",
            "hr_leave_policies", "hr_leave_ledger_entries",
            "hr_timesheets", "hr_timesheet_entries",
            "hr_g2_idempotency_records"
        };
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String table : g2Tables) {
                var rs = stmt.executeQuery(
                        "SELECT relrowsecurity, relforcerowsecurity FROM pg_class " +
                        "WHERE relname = '" + table + "' AND relkind = 'r'"
                );
                assertThat(rs.next()).as("Table %s must exist", table).isTrue();
                assertThat(rs.getBoolean("relrowsecurity"))
                        .as("RLS must be ENABLE on %s", table).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity"))
                        .as("FORCE RLS must be on %s", table).isTrue();
            }
        }
    }

    @Test
    void tenantIsolationPolicyExistsOnAllG2Tables() throws Exception {
        String[] g2Tables = {
            "hr_work_schedules", "hr_attendance_events", "hr_leave_requests",
            "hr_timesheets", "hr_g2_idempotency_records"
        };
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String table : g2Tables) {
                var rs = stmt.executeQuery(
                        "SELECT policyname FROM pg_policies " +
                        "WHERE schemaname = 'public' AND tablename = '" + table + "' " +
                        "AND policyname = 'tenant_isolation'"
                );
                assertThat(rs.next())
                        .as("tenant_isolation policy must exist on %s", table).isTrue();
            }
        }
    }

    @Test
    void leaveRequestStateConstraintsExist() throws Exception {
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(
                    "SELECT con.conname, pg_get_constraintdef(con.oid) " +
                    "FROM pg_constraint con " +
                    "JOIN pg_class rel ON rel.oid = con.conrelid " +
                    "WHERE rel.relname = 'hr_leave_requests' " +
                    "AND con.conname = 'ck_hr_leave_requests_state'"
            );
            assertThat(rs.next()).as("State CHECK constraint must exist").isTrue();
            String constraintDef = rs.getString(2);
            assertThat(constraintDef).contains("DRAFT");
            assertThat(constraintDef).contains("SUBMITTED");
            assertThat(constraintDef).contains("PENDING_MANAGER");
            assertThat(constraintDef).contains("PENDING_HR");
            assertThat(constraintDef).contains("APPROVED");
            assertThat(constraintDef).contains("REJECTED");
            assertThat(constraintDef).contains("WITHDRAWN");
            assertThat(constraintDef).contains("CANCELLED");
        }
    }

    @Test
    void attendanceEventTypesAreConstrained() throws Exception {
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(
                    "SELECT pg_get_constraintdef(con.oid) " +
                    "FROM pg_constraint con " +
                    "JOIN pg_class rel ON rel.oid = con.conrelid " +
                    "WHERE rel.relname = 'hr_attendance_events' " +
                    "AND con.conname = 'ck_hr_attendance_events_type'"
            );
            assertThat(rs.next()).as("Event type CHECK must exist").isTrue();
            String def = rs.getString(1);
            assertThat(def).contains("CLOCK_IN");
            assertThat(def).contains("CLOCK_OUT");
            assertThat(def).contains("BREAK_START");
            assertThat(def).contains("BREAK_END");
            assertThat(def).contains("MANUAL_CORRECTION");
        }
    }

    @Test
    void leaveLedgerEntryTypesAreConstrained() throws Exception {
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(
                    "SELECT pg_get_constraintdef(con.oid) " +
                    "FROM pg_constraint con " +
                    "JOIN pg_class rel ON rel.oid = con.conrelid " +
                    "WHERE rel.relname = 'hr_leave_ledger_entries' " +
                    "AND con.conname = 'ck_hr_leave_ledger_entries_type'"
            );
            assertThat(rs.next()).as("Ledger entry type CHECK must exist").isTrue();
            String def = rs.getString(1);
            assertThat(def).contains("OPENING");
            assertThat(def).contains("ACCRUAL");
            assertThat(def).contains("RESERVATION");
            assertThat(def).contains("RELEASE");
            assertThat(def).contains("CONSUMPTION");
            assertThat(def).contains("CARRYOVER");
            assertThat(def).contains("EXPIRY");
            assertThat(def).contains("ADJUSTMENT");
        }
    }

    @Test
    void crossTenantAccessDeniedWithoutTenantContext() throws Exception {
        // After V20260924_6 (NULLIF fail-closed RLS), RESET app.tenant_id no longer
        // throws a UUID cast exception. Instead, current_setting('app.tenant_id', true)
        // returns NULL via NULLIF, the comparison tenant_id = NULL yields NULL (not true),
        // and RLS filters out all rows → query succeeds with zero rows.
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("RESET app.tenant_id");
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM hr_leave_requests");
            rs.next();
            assertThat(rs.getInt(1))
                    .as("Without tenant context, no leave requests should be visible")
                    .isZero();

            rs = stmt.executeQuery("SELECT COUNT(*) FROM hr_attendance_records");
            rs.next();
            assertThat(rs.getInt(1))
                    .as("Without tenant context, no attendance records should be visible")
                    .isZero();
        }
    }
}
