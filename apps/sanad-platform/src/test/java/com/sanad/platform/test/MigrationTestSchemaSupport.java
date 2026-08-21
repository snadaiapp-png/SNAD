package com.sanad.platform.test;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared test utility for isolating migration-upgrade tests from the shared
 * CI {@code public} schema.
 *
 * <p>Several integration tests bring up their own {@link Flyway} instance
 * pointing at the default {@code public} schema and call {@code .clean()}
 * between runs. In a CI environment where the same database backs both these
 * tests and {@code @SpringBootTest} contexts, a {@code clean()} on
 * {@code public} destroys every table the surrounding Spring Boot context
 * depends on, breaking subsequent tests in unpredictable ways.</p>
 *
 * <p>This utility routes every migration-upgrade Flyway instance through a
 * disposable, per-test {@code test_migration} schema. Calling
 * {@code .clean()} on a Flyway configured via
 * {@link #configureIsolatedFlyway(String, String, String, MigrationVersion, JavaMigration...)}
 * only affects {@code test_migration} — the shared {@code public} schema is
 * untouched.</p>
 *
 * <p>Tests that bring up their own {@link DriverManagerDataSource} for
 * JdbcTemplate queries SHOULD use {@link #isolatedDataSource(String, String, String)}
 * instead of a raw {@code DriverManagerDataSource}. {@code DriverManagerDataSource}
 * returns a fresh JDBC connection per JdbcTemplate operation, so a one-shot
 * {@code SET search_path} would not persist across calls. The
 * {@code isolatedDataSource} wrapper re-issues {@code SET search_path TO
 * test_migration} on every fresh connection, ensuring unqualified table
 * references (e.g. {@code SELECT * FROM tenants}) resolve to the schema
 * where the isolated Flyway instance created them.</p>
 */
public final class MigrationTestSchemaSupport {

    /** Name of the disposable schema used by migration-upgrade tests. */
    public static final String TEST_SCHEMA = "test_migration";

    private MigrationTestSchemaSupport() {}

    /**
     * Ensures the {@code test_migration} schema exists in the target
     * database. Idempotent — safe to call from {@code @BeforeAll} or at the
     * start of every individual test method.
     *
     * <p>The supplied {@link JdbcTemplate} may use a raw
     * {@link DriverManagerDataSource}; this call only needs a single
     * connection.</p>
     */
    public static void ensureSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + TEST_SCHEMA);
    }

    /**
     * Sets {@code search_path} to {@code test_migration} for the connection
     * used by the next JdbcTemplate operation on this {@link JdbcTemplate}.
     *
     * <p><b>Warning:</b> when the {@link JdbcTemplate}'s
     * {@link DataSource} is a plain {@link DriverManagerDataSource}, each
     * operation borrows a fresh connection. A one-shot {@code SET search_path}
     * will therefore NOT persist to subsequent operations. Prefer wrapping the
     * data source with {@link #isolatedDataSource(String, String, String)} so
     * that every fresh connection is initialised with the correct
     * {@code search_path}.</p>
     */
    public static void setSearchPath(JdbcTemplate jdbc) {
        jdbc.execute("SET search_path TO " + TEST_SCHEMA);
    }

    /**
     * Builds and loads a {@link Flyway} instance whose DDL, history table,
     * and {@code .clean()} scope are all confined to the {@code test_migration}
     * schema. The returned Flyway's {@code .clean()} is safe — it cannot
     * affect {@code public}.
     *
     * <p>The returned Flyway is pre-configured with
     * {@code .cleanDisabled(false)} so isolated schema resets remain possible.
     * The default migration locations {@code classpath:db/migration} and
     * {@code classpath:db/vendor/postgresql} are applied so that tests get the
     * full migration chain by default.</p>
     *
     * <p>Tests that need finer-grained control (custom locations, custom
     * {@code validateOnMigrate}, etc.) should inline the standard pattern
     * in their own helper:</p>
     * <pre>{@code
     * var configuration = Flyway.configure()
     *         .dataSource(url, user, pwd)
     *         .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
     *         .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
     *         .schemas(MigrationTestSchemaSupport.TEST_SCHEMA)
     *         .defaultSchema(MigrationTestSchemaSupport.TEST_SCHEMA)
     *         .cleanDisabled(false)
     *         .validateOnMigrate(true);
     * if (target != null) configuration.target(target);
     * return configuration.load();
     * }</pre>
     *
     * @param url           JDBC URL of the target PostgreSQL database
     * @param user          DB user
     * @param password      DB password
     * @param target        optional migration target version; {@code null} for full migration
     * @param javaMigrations zero or more {@link JavaMigration} instances (e.g. {@code V15__seed_rbac_roles_and_capabilities})
     * @return a loaded {@link Flyway} instance whose {@code .clean()} only affects {@code test_migration}
     */
    public static Flyway configureIsolatedFlyway(String url, String user, String password,
                                                  MigrationVersion target,
                                                  JavaMigration... javaMigrations) {
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .schemas(TEST_SCHEMA)
                .defaultSchema(TEST_SCHEMA)
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (javaMigrations != null && javaMigrations.length > 0) {
            configuration.javaMigrations(javaMigrations);
        }
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    /**
     * Returns a {@link DriverManagerDataSource} whose every fresh connection
     * has its {@code search_path} set to {@code test_migration}.
     *
     * <p>This is required for tests whose JdbcTemplate operations span
     * multiple SQL statements: {@code DriverManagerDataSource} returns a new
     * connection per operation, so a single {@code SET search_path} does not
     * persist. By re-issuing {@code SET search_path TO test_migration} on each
     * {@code getConnection()}, every unqualified table reference (e.g.
     * {@code SELECT * FROM tenants}) resolves to the schema created by the
     * isolated Flyway instance.</p>
     */
    public static DriverManagerDataSource isolatedDataSource(String url, String user, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password) {
            @Override
            public Connection getConnection() throws SQLException {
                return withTestSchema(super.getConnection());
            }

            @Override
            public Connection getConnection(String username, String pwd) throws SQLException {
                return withTestSchema(super.getConnection(username, pwd));
            }

            private Connection withTestSchema(Connection conn) throws SQLException {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET search_path TO " + TEST_SCHEMA);
                }
                return conn;
            }
        };
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
