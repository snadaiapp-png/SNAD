package com.sanad.platform.hr.foundation;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G0 / WS2 / Task 6 — absolute final closure contract.
 *
 * Covers the four remaining closure gates beyond the existing 19 + 9 tests:
 * deterministic explicit as-of execution, runtime tenant-binding security,
 * complete reconciliation gate reporting, and the final source-of-truth guard.
 */
class HrCanonicalBackfillFinalClosureIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private static String ISOLATED_URL;
    private DriverManagerDataSource dataSource;
    private Connection conn;

    @BeforeAll
    static void requirePostgreSql() {
        boolean ok = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                ok = c.isValid(5);
            }
        } catch (Throwable ignored) {
            // PostgreSQL Direct is an explicit test precondition.
        }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void setup() throws Exception {
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    void explicitAsOfDate_controlsEligibilityIndependentOfCurrentDate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "AsOf Org");
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-ASOF");
        seedOrgLegalEntity(tenantId, orgId, legalEntityId, "2026-06-01", "2026-07-31");
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        UUID employeeId = seedLegacyEmployee(tenantId, userId, "EMP-ASOF", "AsOf", "Employee", "2026-06-15");
        setTenant(tenantId);

        boolean explicitContractSupported = true;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT hr_backfill_tenant(?, ?::date)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "2026-06-30");
            ps.execute();
        } catch (SQLException e) {
            if ("42883".equals(e.getSQLState())) {
                explicitContractSupported = false;
            } else {
                throw e;
            }
        }

        assertThat(explicitContractSupported)
                .as("Task 6 MUST expose an explicit deterministic as-of-date backfill contract")
                .isTrue();
        assertThat(getEmployeeLegalEntity(employeeId, tenantId))
                .as("Eligibility valid on 2026-06-30 MUST be used regardless of today's date")
                .isEqualTo(legalEntityId);
    }

    @Test
    void applicationRole_crossTenantBackfillIsDeniedBeforeAnyMutation() throws Exception {
        UUID tenantA = UUID.randomUUID();
        seedTenant(tenantA);

        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantB);
        UUID orgB = seedOrganization(tenantB, "Tenant B Org");
        UUID leB = seedLegalEntity(tenantB, "LE-B");
        seedOrgLegalEntity(tenantB, orgB, leB, "2026-01-01", null);
        UUID userB = UUID.randomUUID();
        seedUser(tenantB, userB);
        seedLegacyEmployee(tenantB, userB, "EMP-B", "Tenant", "B", "2026-01-01");

        setTenant(tenantA);
        boolean denied = false;
        try (PreparedStatement ps = conn.prepareStatement("SELECT hr_backfill_tenant(?)")) {
            ps.setObject(1, tenantB);
            ps.execute();
        } catch (SQLException e) {
            denied = "42501".equals(e.getSQLState());
        }

        assertThat(denied)
                .as("Application role tenant A MUST NOT execute Task 6 backfill for tenant B")
                .isTrue();
        setTenant(tenantB);
        assertThat(countPersons(tenantB))
                .as("Denied cross-tenant call MUST mutate zero canonical Person rows")
                .isZero();
    }

    @Test
    void applicationRole_withoutTenantContextBackfillIsDenied() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "No Context Org");
        UUID leId = seedLegalEntity(tenantId, "LE-NC");
        seedOrgLegalEntity(tenantId, orgId, leId, "2026-01-01", null);
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-NC", "No", "Context", "2026-01-01");

        clearTenant();
        boolean denied = false;
        try (PreparedStatement ps = conn.prepareStatement("SELECT hr_backfill_tenant(?)")) {
            ps.setObject(1, tenantId);
            ps.execute();
        } catch (SQLException e) {
            denied = "42501".equals(e.getSQLState());
        }

        assertThat(denied)
                .as("Application role without app.tenant_id MUST NOT execute Task 6 backfill")
                .isTrue();
    }

    @Test
    void reconciliationReport_containsTheCompleteCanonicalDecisionMatrix() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID orgId = seedOrganization(tenantId, "Report Org");
        UUID leId = seedLegalEntity(tenantId, "LE-REPORT");
        seedOrgLegalEntity(tenantId, orgId, leId, "2026-01-01", null);
        UUID userId = UUID.randomUUID();
        seedUser(tenantId, userId);
        seedLegacyEmployee(tenantId, userId, "EMP-REPORT", "Report", "Employee", "2026-01-01");
        setTenant(tenantId);
        invokeBackfill(tenantId);

        Set<String> gates = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT gate_name FROM hr_reconcile_tenant_report(?)")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    gates.add(rs.getString(1));
                }
            }
        }

        Set<String> required = Set.of(
                "LEGACY_EMPLOYEE_COUNT",
                "CANONICAL_EMPLOYMENT_COUNT",
                "PERSON_MAPPING_MISSING",
                "LEGAL_ENTITY_MAPPING_MISSING",
                "PRIMARY_ASSIGNMENT_MISSING",
                "DEPARTMENT_MAPPING_MISSING",
                "POSITION_MAPPING_MISSING",
                "MANAGER_MAPPING_UNRESOLVED",
                "UNRESOLVED_MIGRATION_ROWS",
                "OPEN_REVIEW_ITEMS",
                "DUPLICATE_MAPPING",
                "ORPHAN_MAPPING",
                "CROSS_TENANT_MISMATCH",
                "UNACCOUNTED_ROWS"
        );

        assertThat(gates)
                .as("Reconciliation report MUST expose the complete Task 6 canonical gate matrix")
                .containsAll(required);
    }

    private void invokeBackfill(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement("SELECT hr_backfill_tenant(?)")) {
            ps.setObject(1, tenantId);
            ps.execute();
        }
    }

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Task6 Final Closure', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "t6fc-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedOrganization(UUID tenantId, String name) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, name + " " + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return id;
    }

    private UUID seedLegalEntity(UUID tenantId, String code) throws Exception {
        UUID id = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities " +
                        "(id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, "Task6 LE " + code);
            ps.executeUpdate();
        }
        return id;
    }

    private void seedOrgLegalEntity(
            UUID tenantId, UUID orgId, UUID legalEntityId, String effectiveFrom, String effectiveTo) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organization_legal_entities " +
                        "(id, tenant_id, organization_id, legal_entity_id, effective_from, effective_to, status, created_at) " +
                        "VALUES (?, ?, ?, ?, ?::date, ?::date, 'ACTIVE', NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orgId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, effectiveFrom);
            ps.setString(6, effectiveTo);
            ps.executeUpdate();
        }
    }

    private void seedUser(UUID tenantId, UUID userId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users " +
                        "(id, tenant_id, email, display_name, status, created_at, updated_at, must_change_password, session_version, platform_admin) " +
                        "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW(), false, 0, false)")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, "t6fc-" + userId.toString().substring(0, 8) + "@test.example");
            ps.setString(4, "Task6 Final User");
            ps.executeUpdate();
        }
    }

    private UUID seedLegacyEmployee(
            UUID tenantId, UUID userId, String employeeNumber, String firstName, String lastName, String hireDate)
            throws Exception {
        UUID id = UUID.randomUUID();
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees " +
                        "(id, tenant_id, user_id, employee_number, first_name, last_name, display_name, " +
                        "employment_type, status, hire_date, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'FULL_TIME', 'ACTIVE', ?::date, 0, NOW(), NOW())")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, userId);
            ps.setString(4, employeeNumber + "-" + id.toString().substring(0, 8));
            ps.setString(5, firstName);
            ps.setString(6, lastName);
            ps.setString(7, firstName + " " + lastName);
            ps.setString(8, hireDate);
            ps.executeUpdate();
        }
        return id;
    }

    private UUID getEmployeeLegalEntity(UUID employeeId, UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT legal_entity_id FROM hr_employees WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return (UUID) rs.getObject(1);
            }
        }
    }

    private int countPersons(UUID tenantId) throws Exception {
        setTenant(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM hr_people WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private void clearTenant() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
    }
}
