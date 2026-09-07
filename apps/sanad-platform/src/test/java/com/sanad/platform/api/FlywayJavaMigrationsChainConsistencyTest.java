package com.sanad.platform.api;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the 2026-09-04 production boot outage.
 *
 * <p>Production history deliberately removed the V15 Java migration from the
 * applied chain (flyway DELETE marker, 2026-09-01) while the class remained on
 * the application classpath. As soon as runtime Flyway was enabled
 * (FLYWAY_ENABLED=true) the boot-time validate resolved V15, found no applied
 * history row for it, and failed the whole container:
 * "Detected resolved migration not applied to database: 15".</p>
 *
 * <p>These tests pin the invariant that makes runtime Flyway safe:</p>
 * <ol>
 *   <li>no Java migration classes may remain on the classpath — the chain is
 *       SQL-only and any future Java migration requires an explicit
 *       production-chain reconciliation decision;</li>
 *   <li>a fresh full migrate resolves zero JDBC migrations and validates
 *       cleanly, so boot-time validate-on-migrate can never hit a
 *       resolved-but-unapplied migration again.</li>
 * </ol>
 */
class FlywayJavaMigrationsChainConsistencyTest {

    /**
     * Skip gracefully on machines without PostgreSQL (e.g. dev Windows boxes).
     * On CI runners with PostgreSQL, the test runs normally.
     */
    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable = false;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable t) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping FlywayJavaMigrationsChainConsistencyTest. "
                        + "Run with PostgreSQL Direct to exercise the migration chain invariant.");
        // Ensure the disposable isolated database exists so that flyway.clean()
        // below only affects this isolated database (not the shared sanad
        // database that other @SpringBootTest contexts depend on).
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    @Test
    void noJavaMigrationClassesRemainOnTheClasspath() {
        assertClassAbsent("com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities");
        assertClassAbsent("com.sanad.platform.config.FlywayJavaMigrationConfig");
    }

    @Test
    void freshMigrateResolvesNoJavaMigrationsAndValidatesClean() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        JdbcTemplate jdbc = jdbc();
        Long javaMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'JDBC'",
                Long.class);
        assertThat(javaMigrations)
                .as("The production chain is SQL-only; no JDBC (Java) migration may be applied")
                .isZero();
    }

    private void assertClassAbsent(String className) {
        try {
            Class.forName(className);
            throw new AssertionError(
                    "Class must not exist on the classpath (it was deleted from the production chain): "
                            + className);
        } catch (ClassNotFoundException expected) {
            // expected — the class was removed from the repository
        }
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }
}
