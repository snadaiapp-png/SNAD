package com.sanad.platform.crm.web;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

/**
 * Flyway migration history assertion test — CRM-028.
 *
 * <p>Asserts that the {@code flyway_schema_history} table contains exactly the
 * expected CRM migration versions, in the correct order.  The test fails if:
 * <ul>
 *   <li>Any expected CRM version is missing.</li>
 *   <li>Any CRM version is out of order relative to its neighbours.</li>
 *   <li>Any duplicate version exists.</li>
 *   <li>An unexpected CRM version appears that is not in the expected list.</li>
 * </ul>
 *
 * <p>This test is the canonical guard for production migration integrity.  It
 * runs against a Testcontainers PostgreSQL instance and exercises the full
 * Flyway migration path.
 */
class CrmFlywayHistoryAssertionTest {

    /**
     * Main schema baseline version — the last non-CRM Flyway version that
     * existed before CRM migrations began.
     */
    private static final String MAIN_SCHEMA_VERSION = "20260629.2";

    /**
     * Ordered list of every CRM migration version that must appear in the
     * {@code flyway_schema_history} table after a full migration.
     *
     * <p>The list is in Flyway execution order (ascending version).  If you
     * add a new CRM migration, append its version here so the history
     * assertion stays current.
     */
    private static final List<String> EXPECTED_CRM_VERSIONS = List.of(
            // CRM Core
            "20260702.1",   // create unified crm core
            "20260702.2",   // reconcile admin role and capabilities
            "20260702.3",   // complete crm imports custom fields
            // Platform extensions
            "20260706.1",   // create tenant quota
            "20260711.1",   // create subscription change events
            // CRM G2 & pipeline
            "20260713.1",   // create crm idempotency records
            "20260713.2",   // add pipeline version column
            // CRM Tasks, Notes, Tags, Customer Master
            "20260716.1",   // create crm tasks
            "20260716.2",   // create crm notes
            "20260716.3",   // create crm tags
            "20260716.4",   // crm enterprise account customer master
            // CRM Contact Relationship
            "20260717.1",   // crm contact relationship model
            "20260717.2",   // crm contact relationship capabilities
            "20260717.3",   // crm timeline tenant lifecycle
            // Business Process
            "20260717.4",   // create business process e2e backbone
            "20260717.5",   // grant business process capabilities
            // CRM G1 Extension
            "20260717.6",   // create crm g1 extension tables
            // CRM Address Communication
            "20260717.100", // crm addresses communication methods
            "20260717.101", // crm addresses communication capabilities
            // Vendor Reconcile
            "20260718.1",   // reconcile crm g1 after baseline gap
            "20260718.2",   // reconcile crm tags after baseline gap
            "20260721.1",   // reconcile crm contact relationship model after baseline gap
            "20260721.2",   // reconcile crm idempotency records after baseline gap
            // CRM 008B — Teams, Queues, Territories, Assignments
            "20260722.1",   // create crm sales teams
            "20260722.2",   // create crm queues
            "20260722.3",   // create crm territories
            "20260722.4",   // create crm assignment rules
            "20260722.5",   // upgrade crm assignments and create ownership history
            "20260722.6",   // create crm transfer requests
            "20260722.7",   // add owner team queue columns
            "20260722.8",   // seed crm ownership capabilities
            "20260722.9",   // create crm assignment rule counters
            // CRM 009 — Integration
            "20260723.1",   // create crm integration requests
            "20260724.1",   // create crm command executions ledger
            "20260724.2",   // create crm command artifacts
            // CRM 010 — Intelligence
            "20260728.1",   // seed crm 008 team management capabilities
            "20260729.1",   // create crm customer intelligence
            "20260729.2",   // seed default scoring models
            "20260730.1",   // enable crm row level security
            "20260802.1",   // re-enable crm row level security
            "20260804.1",   // reconcile crm custom field and pipeline audit columns
            "20260804.2",   // create crm shift templates
            "20260804.3",   // create crm shift assignments
            "20260804.4",   // create crm staff availability
            "20260804.5",   // create crm staff skills
            "20260804.6",   // create crm capacity plans
            "20260804.7",   // create crm workload assignments
            "20260804.8",   // create crm service assignments
            "20260804.9",    // create crm cases
            // MOD-002 — Email Integration
            "20260805.1",    // create crm email logs
            // Reporting & Portal capabilities
            "20260805.2",    // create crm reporting capabilities
            "20260805.3",    // create crm portal capabilities
            // Executive Health
            "20260806.1",    // seed executive health capabilities
            // CRM Capability Grant & Pipeline Seed
            "20260807.1",    // grant crm capabilities to non admin roles
            "20260807.2",    // seed default pipeline and accounts
            "20260807.3",    // add case insensitive tag unique index
            "20260807.4",    // add activity result column and related type check
            // G7 Mobile Offline Sync (V20260812.1/2/3)
            "20260812.1",    // create mobile sync tables
            "20260812.2",    // add sync columns to crm entities
            "20260812.3",    // force rls mobile sync tables
            // Mission 01: Control Plane Admin + Module Registry + Capabilities (V20260813.1, V20260814.1/2)
            "20260813.1",    // seed control plane admin and capabilities
            "20260814.1",    // create module registry
            "20260814.2"     // create module capabilities and plan module entitlements
    );


    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping CrmFlywayHistoryAssertionTest. " +
                        "Run with PostgreSQL Direct to exercise Flyway history assertions.");
    }

    /**
     * Asserts that the Flyway history table contains exactly the expected
     * CRM versions in the correct order, with no duplicates and no missing
     * versions.
     */
    @Test
    void flywayHistoryContainsExactlyExpectedCrmVersionsInOrder() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        JdbcTemplate jdbc = jdbc();

        // 1. Fetch all successful CRM versions from flyway_schema_history in
        //    installed_rank order.
        List<String> actualVersions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history " +
                        "WHERE version IS NOT NULL AND success = TRUE " +
                        "ORDER BY installed_rank ASC",
                String.class);

        // 2. Filter to only CRM versions (those >= the first CRM version).
        String firstCrmVersion = EXPECTED_CRM_VERSIONS.get(0);
        List<String> actualCrmVersions = actualVersions.stream()
                .filter(v -> compareVersions(v, firstCrmVersion) >= 0)
                .toList();

        // 3. Assert no missing versions.
        List<String> missingVersions = new ArrayList<>(EXPECTED_CRM_VERSIONS);
        missingVersions.removeAll(actualCrmVersions);
        assertThat(missingVersions)
                .as("No expected CRM versions should be missing from flyway_schema_history")
                .isEmpty();

        // 4. Assert no unexpected versions.
        List<String> unexpectedVersions = new ArrayList<>(actualCrmVersions);
        unexpectedVersions.removeAll(EXPECTED_CRM_VERSIONS);
        assertThat(unexpectedVersions)
                .as("No unexpected CRM versions should appear in flyway_schema_history")
                .isEmpty();

        // 5. Assert exact count match.
        assertThat(actualCrmVersions)
                .as("Number of CRM versions in history must match expected count (%d)",
                        EXPECTED_CRM_VERSIONS.size())
                .hasSize(EXPECTED_CRM_VERSIONS.size());

        // 6. Assert correct ordering — each version must appear in the same
        //    relative position as in the expected list.
        for (int i = 0; i < EXPECTED_CRM_VERSIONS.size(); i++) {
            assertThat(actualCrmVersions.get(i))
                    .as("CRM version at position %d must be %s", i, EXPECTED_CRM_VERSIONS.get(i))
                    .isEqualTo(EXPECTED_CRM_VERSIONS.get(i));
        }
    }

    /**
     * Asserts that no duplicate versions exist in the Flyway history table.
     */
    @Test
    void flywayHistoryContainsNoDuplicateVersions() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();

        JdbcTemplate jdbc = jdbc();

        Long duplicateCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" +
                        "  SELECT version FROM flyway_schema_history " +
                        "  WHERE version IS NOT NULL AND success = TRUE " +
                        "  GROUP BY version HAVING COUNT(*) > 1" +
                        ") duplicates",
                Long.class);

        assertThat(duplicateCount)
                .as("No duplicate versions should exist in flyway_schema_history")
                .isZero();
    }

    /**
     * Asserts that the latest version in the history matches the last entry
     * in the expected versions list.
     */
    @Test
    void flywayHistoryLatestVersionMatchesExpected() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();

        JdbcTemplate jdbc = jdbc();

        String latestVersion = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history " +
                        "WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class);

        assertThat(latestVersion)
                .as("Latest Flyway version must be %s",
                        EXPECTED_CRM_VERSIONS.get(EXPECTED_CRM_VERSIONS.size() - 1))
                .isEqualTo(EXPECTED_CRM_VERSIONS.get(EXPECTED_CRM_VERSIONS.size() - 1));
    }

    /**
     * Asserts that every migration in the history was successful.
     */
    @Test
    void allFlywayMigrationsSuccessful() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();

        JdbcTemplate jdbc = jdbc();

        Long failedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE",
                Long.class);

        assertThat(failedCount)
                .as("All Flyway migrations must have succeeded")
                .isZero();
    }

    /**
     * Asserts that the total number of migrations includes all expected CRM
     * versions plus at least the baseline and V15 Java migration.
     */
    @Test
    void flywayHistoryTotalMigrationCountIncludesAllCrmVersions() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();

        JdbcTemplate jdbc = jdbc();

        Long actualCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Long.class);

        // Total must be at least: CRM versions + baseline (1) + V15 Java migration (1)
        long minimumExpected = EXPECTED_CRM_VERSIONS.size() + 2;

        assertThat(actualCount)
                .as("Total successful migrations must be >= %d (at least %d CRM + baseline + V15)",
                        minimumExpected, EXPECTED_CRM_VERSIONS.size())
                .isGreaterThanOrEqualTo(minimumExpected);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }

    /**
     * Compares two Flyway version strings numerically.
     * Returns negative if v1 < v2, zero if equal, positive if v1 > v2.
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2) return Integer.compare(p1, p2);
        }
        return 0;
    }
}
