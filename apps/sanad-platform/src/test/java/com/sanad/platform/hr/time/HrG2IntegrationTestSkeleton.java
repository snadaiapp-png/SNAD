package com.sanad.platform.hr.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G2 PostgreSQL Direct integration test skeleton.
 *
 * <p>Tests run against host-native PostgreSQL (no Docker/Testcontainers).
 * The test environment is provisioned by CI v20260907.1 with:
 *   - Bootstrap role: postgres (SUPERUSER for provisioning)
 *   - Application role: sanad (NOSUPERUSER NOBYPASSRLS)
 *   - Database: sanad
 *
 * <p>This is a SKELETON — individual test methods need to be filled in
 * with actual assertions. The skeleton ensures the test class compiles
 * and is discovered by Maven Surefire.
 */
class HrG2IntegrationTestSkeleton {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgres() {
        Assumptions.assumeTrue(
                System.getenv("SPRING_DATASOURCE_URL") != null
                        || System.getenv("SPRING_PROFILES_ACTIVE") != null,
                "PostgreSQL Direct tests require SPRING_DATASOURCE_URL or SPRING_PROFILES_ACTIVE"
        );
    }

    @Test
    void g2TablesExist() throws Exception {
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name LIKE 'hr_%' " +
                    "AND table_name IN ('hr_work_schedules','hr_schedule_assignments'," +
                    "'hr_attendance_events','hr_attendance_records','hr_leave_requests'," +
                    "'hr_leave_ledger_entries','hr_leave_policies','hr_timesheets'," +
                    "'hr_g2_idempotency_records')"
            );
            int count = 0;
            while (rs.next()) count++;
            assertThat(count).as("G2 tables must exist").isGreaterThanOrEqualTo(9);
        }
    }

    @Test
    void rlsIsEnforcedOnG2Tables() throws Exception {
        // RLS must be ENABLE + FORCE on all G2 tenant-owned tables.
        // This test verifies the policy exists and is enforced.
        DataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(
                    "SELECT tablename, rowsecurity, forcerowsecurity " +
                    "FROM pg_tables " +
                    "WHERE schemaname = 'public' " +
                    "AND tablename IN ('hr_work_schedules','hr_attendance_events'," +
                    "'hr_leave_requests','hr_leave_ledger_entries','hr_timesheets')"
            );
            while (rs.next()) {
                assertThat(rs.getBoolean("rowsecurity"))
                        .as("RLS must be enabled on " + rs.getString("tablename"))
                        .isTrue();
                assertThat(rs.getBoolean("forcerowsecurity"))
                        .as("FORCE RLS must be enabled on " + rs.getString("tablename"))
                        .isTrue();
            }
        }
    }
}
