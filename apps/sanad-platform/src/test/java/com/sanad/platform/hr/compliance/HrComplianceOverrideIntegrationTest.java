package com.sanad.platform.hr.compliance;

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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / WS3 / Task 1 RED contract for tenant-owned compliance evidence
 * and database-level four-eyes invariants. Application-level hard-rule
 * override behavior is added in WS3 Task 4 with ComplianceOverrideService.
 */
class HrComplianceOverrideIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;

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
    }

    @Test
    void tenantOwnedComplianceTablesUseForcedFailClosedRls() throws Exception {
        for (String table : new String[]{"hr_compliance_decisions", "hr_compliance_override_requests"}) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = ?")) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("%s must exist", table).isTrue();
                    assertThat(rs.getBoolean("relrowsecurity")).as("%s ENABLE RLS", table).isTrue();
                    assertThat(rs.getBoolean("relforcerowsecurity")).as("%s FORCE RLS", table).isTrue();
                }
            }
        }
    }

    @Test
    void overrideFourEyesConstraintRejectsRequesterAsApprover() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID ruleId = seedTenantUserAndRule(tenantId, requester);
        setTenant(tenantId);

        assertThatThrownBy(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_compliance_override_requests " +
                            "(tenant_id, compliance_rule_id, resource_type, resource_id, " +
                            "requested_value_redacted, compliant_value_redacted, requester_user_id, " +
                            "justification, approved_by, valid_from, status) " +
                            "VALUES (?, ?, 'EMPLOYMENT', ?, '{}'::jsonb, '{}'::jsonb, ?, 'test', ?, ?, 'APPROVED')")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ruleId);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, requester);
                ps.setObject(5, requester);
                ps.setObject(6, LocalDate.of(2026, 1, 1));
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
          .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    private UUID seedTenantUserAndRule(UUID tenantId, UUID userId) throws Exception {
        resetTenant();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS3-" + tenantId);
            ps.setString(3, "ws3-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, userId + "@example.invalid");
            ps.executeUpdate();
        }
        UUID packId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_country_packs (id, country_code, pack_code, pack_version, status, effective_from) " +
                        "VALUES (?, 'SA', 'OVERRIDE_TEST', '1', 'ACTIVE', DATE '2026-01-01')")) {
            ps.setObject(1, packId);
            ps.executeUpdate();
        }
        UUID ruleId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_compliance_rules " +
                        "(id, country_pack_id, rule_code, rule_version, operation_code, enforcement_level, " +
                        "exception_allowed, official_source_uri, legal_citation, source_snapshot_sha256, " +
                        "effective_from, last_legal_review_at, reviewed_by, status) " +
                        "VALUES (?, ?, 'TEST_RULE', '1', 'TEST_OPERATION', 'MANDATORY_WITH_EXCEPTION', TRUE, " +
                        "'https://example.invalid/source', 'TEST', ?, DATE '2026-01-01', NOW(), 'test-reviewer', 'ACTIVE')")) {
            ps.setObject(1, ruleId);
            ps.setObject(2, packId);
            ps.setString(3, "0".repeat(64));
            ps.executeUpdate();
        }
        return ruleId;
    }

    private void setTenant(UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private void resetTenant() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
    }
}
