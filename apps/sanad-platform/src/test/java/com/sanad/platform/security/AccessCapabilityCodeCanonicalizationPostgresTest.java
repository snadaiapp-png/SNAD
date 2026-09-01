package com.sanad.platform.security;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration contract test for V20260901_1 (capability-code canonicalization).
 *
 * <p>ROOT CAUSE being pinned: V20260830_2 seeded granular control-plane
 * capability codes in LOWERCASE, while {@code AccessCapabilityService.requireCode()}
 * normalizes every {@code loadByCode()} lookup to UPPERCASE before the
 * exact-match query. Result in production: {@code @RequireCapability("audit.read")}
 * (GovernanceController /api/v1/executive/audit/v2) and
 * {@code @RequireCapability("usage.read")} (UsageController /api/v1/executive/usage)
 * denied every authenticated SCP admin with CAPABILITY_NOT_FOUND even though
 * the capabilities existed, were ACTIVE, and were granted to all
 * EXECUTIVE_VIEW-holding roles (correlationId
 * edfce834-25db-4fff-a023-a268b2ed6ede, 2026-09-01T19:16:14Z).</p>
 *
 * <p>This test baselines the isolated {@code test_migration} database with the
 * FULL forward migration chain (same configuration as the canonical
 * {@code CrmPostgresMigrationTest} harness: both SQL locations plus the V15
 * Java migration) and asserts the exact failure chain end-to-end at the data
 * layer:</p>
 * <ol>
 *   <li>every capability code is stored UPPERCASE (the lookup normalizer's
 *       convention — dotted codes are uppercase: CRM.ACCOUNT.READ, ...);</li>
 *   <li>the exact normalized forms {@code loadByCode} searches for
 *       ({@code UPPER("audit.read")} = {@code AUDIT.READ},
 *       {@code UPPER("usage.read")} = {@code USAGE.READ}) exist and are ACTIVE;</li>
 *   <li>every (tenant, role) pair holding EXECUTIVE_VIEW also holds
 *       AUDIT.READ and USAGE.READ — the V20260830_2 backward-compatibility
 *       grant — so the smoke identity (SCP platform admin) is ALLOWED.</li>
 * </ol>
 */
@DisplayName("V20260901_1 — capability codes canonicalize to the UPPERCASE lookup convention")
class AccessCapabilityCodeCanonicalizationPostgresTest {

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "AccessCapabilityCodeCanonicalizationPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available.");
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));

        // Baseline the isolated database with the full canonical forward chain:
        // the same locations + V15 Java migration configuration the sibling
        // migration harnesses use, so validation cannot report applied-but-
        // unresolved migrations regardless of shared-database ordering.
        Flyway flyway = Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    private static JdbcTemplate jdbc() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        ds.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(ds);
    }

    @Test
    @DisplayName("after full migration every capability code is UPPERCASE-conformant")
    void everyCapabilityCodeIsUppercase() {
        Integer nonUppercase = jdbc().queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code <> UPPER(code)", Integer.class);
        assertThat(nonUppercase).as("rows violating the UPPERCASE convention").isZero();
    }

    @Test
    @DisplayName("loadByCode-normalized forms AUDIT.READ and USAGE.READ exist and are ACTIVE")
    void normalizedControllerCapabilityCodesResolve() {
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT code, status FROM access_capabilities WHERE code IN ('AUDIT.READ', 'USAGE.READ')");
        assertThat(rows).as("AUDIT.READ + USAGE.READ must both resolve").hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("status")).isEqualTo("ACTIVE"));
    }

    @Test
    @DisplayName("every EXECUTIVE_VIEW (tenant, role) also holds AUDIT.READ and USAGE.READ")
    void executiveViewRolesKeepTheBackwardCompatibleReadGrants() {
        JdbcTemplate template = jdbc();

        Integer total = template.queryForObject(
                "SELECT COUNT(*) FROM role_capabilities rc "
                        + "JOIN access_capabilities c ON c.id = rc.capability_id AND c.code = 'EXECUTIVE_VIEW'",
                Integer.class);
        Integer withAudit = template.queryForObject(
                "SELECT COUNT(DISTINCT (rc.tenant_id::text || ':' || rc.role_id::text)) FROM role_capabilities rc "
                        + "JOIN access_capabilities c ON c.id = rc.capability_id AND c.code = 'EXECUTIVE_VIEW' "
                        + "WHERE EXISTS (SELECT 1 FROM role_capabilities g "
                        + "  JOIN access_capabilities gc ON gc.id = g.capability_id AND gc.code = 'AUDIT.READ' "
                        + "  WHERE g.tenant_id = rc.tenant_id AND g.role_id = rc.role_id)",
                Integer.class);
        Integer withUsage = template.queryForObject(
                "SELECT COUNT(DISTINCT (rc.tenant_id::text || ':' || rc.role_id::text)) FROM role_capabilities rc "
                        + "JOIN access_capabilities c ON c.id = rc.capability_id AND c.code = 'EXECUTIVE_VIEW' "
                        + "WHERE EXISTS (SELECT 1 FROM role_capabilities g "
                        + "  JOIN access_capabilities gc ON gc.id = g.capability_id AND gc.code = 'USAGE.READ' "
                        + "  WHERE g.tenant_id = rc.tenant_id AND g.role_id = rc.role_id)",
                Integer.class);

        // The V20260830_2 grant promises zero regression: EVERY EXECUTIVE_VIEW
        // holder keeps the granular *.read capabilities. The smoke SCP admin
        // depends on exactly this chain to reach /audit/v2 and /usage.
        assertThat(total).as("distinct EXECUTIVE_VIEW (tenant, role) grants").isGreaterThan(0);
        assertThat(withAudit).as("EXECUTIVE_VIEW pairs also holding AUDIT.READ").isEqualTo(total);
        assertThat(withUsage).as("EXECUTIVE_VIEW pairs also holding USAGE.READ").isEqualTo(total);
    }
}
