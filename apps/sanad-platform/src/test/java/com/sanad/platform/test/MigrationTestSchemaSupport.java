package com.sanad.platform.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database-level isolation for migration-upgrade tests that call {@code .clean()}.
 *
 * <p>Several integration tests bring up their own {@link
 * org.flywaydb.core.Flyway Flyway} instance pointing at the shared {@code sanad}
 * database and call {@code .clean()} between runs. In a CI environment where
 * the same database backs both these tests and {@code @SpringBootTest}
 * contexts, a {@code clean()} on the {@code public} schema of {@code sanad}
 * destroys every table the surrounding Spring Boot context depends on,
 * breaking subsequent tests in unpredictable ways.</p>
 *
 * <p>An earlier attempt at <b>schema</b> isolation (confining Flyway's DDL
 * to a {@code test_migration} schema via {@code .schemas("test_migration")
 * .defaultSchema("test_migration")}) failed because some migration SQL files
 * contain postcondition checks that query {@code information_schema} and
 * {@code pg_catalog} across <b>all</b> schemas; when both {@code public} and
 * {@code test_migration} schemas had tables, the postconditions found
 * duplicate foreign keys and failed.</p>
 *
 * <p>This utility provides <b>database</b> isolation. Each migration-upgrade
 * test's Flyway instance connects to a separate {@code test_migration}
 * database (not schema). The migration postconditions only see tables within
 * the {@code test_migration} database (each PostgreSQL database has its own
 * isolated {@code information_schema}, {@code pg_catalog}, and default
 * {@code public} schema), so the duplicate-foreign-key failure cannot occur.
 * Calling {@code .clean()} on a Flyway instance pointed at
 * {@code test_migration} only affects that disposable database — the shared
 * {@code sanad} database used by {@code @SpringBootTest} is never touched.</p>
 *
 * <h2>Usage in a migration-upgrade test</h2>
 * <pre>{@code
 * import com.sanad.platform.test.MigrationTestSchemaSupport;
 *
 * @BeforeAll
 * static void requirePostgreSql() {
 *     boolean available;
 *     try {
 *         available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
 *     } catch (Throwable ignored) {
 *         available = false;
 *     }
 *     Assumptions.assumeTrue(available, "PostgreSQL Direct is not available.");
 *     // Ensure the disposable test_migration database exists so that
 *     // flyway.clean() only affects this isolated database (not the
 *     // shared sanad database other @SpringBootTest contexts depend on).
 *     MigrationTestSchemaSupport.ensureDatabase(
 *             System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
 *             System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
 *             System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
 * }
 *
 * private Flyway flyway(MigrationVersion target) {
 *     var configuration = Flyway.configure()
 *             .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(
 *                     System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
 *                     System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
 *                     System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
 *             .locations("classpath:db/migration")
 *             .cleanDisabled(false)
 *             .validateOnMigrate(true);
 *     if (target != null) configuration.target(target);
 *     return configuration.load();
 * }
 *
 * private JdbcTemplate jdbc() {
 *     DriverManagerDataSource ds = new DriverManagerDataSource(
 *             MigrationTestSchemaSupport.getIsolatedJdbcUrl(
 *                     System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
 *             System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
 *             System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
 *     ds.setDriverClassName("org.postgresql.Driver");
 *     return new JdbcTemplate(ds);
 * }
 * }</pre>
 */
public final class MigrationTestSchemaSupport {

    /** Name of the disposable database used by migration-upgrade tests. */
    public static final String ISOLATED_DB_NAME = "test_migration";

    private MigrationTestSchemaSupport() {}

    /**
     * Returns the JDBC URL pointing at the {@code test_migration} database
     * on the same host/port as the supplied URL. The database name is the
     * path segment between the last {@code /} and the first {@code ?} (or
     * end of string). For example:
     * <pre>
     * jdbc:postgresql://localhost:5432/sanad?params  ->  jdbc:postgresql://localhost:5432/test_migration?params
     * jdbc:postgresql://localhost:5432/sanad          ->  jdbc:postgresql://localhost:5432/test_migration
     * jdbc:postgresql://user:pass@host:5432/sanad     ->  jdbc:postgresql://user:pass@host:5432/test_migration
     * </pre>
     *
     * @param originalUrl the source JDBC URL whose database name should be replaced
     * @return a new JDBC URL pointing at {@code test_migration}
     */
    public static String getIsolatedJdbcUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isEmpty()) {
            throw new IllegalArgumentException("originalUrl must be non-empty");
        }
        // Split on the FIRST '?' to separate the query string (if any).
        int queryIdx = originalUrl.indexOf('?');
        String prefix;
        String suffix;
        if (queryIdx >= 0) {
            prefix = originalUrl.substring(0, queryIdx);
            suffix = originalUrl.substring(queryIdx);
        } else {
            prefix = originalUrl;
            suffix = "";
        }
        // Find the last '/' after the host:port. For JDBC URLs of the form
        // 'jdbc:postgresql://host:port/dbname', the last '/' delimits dbname.
        int slashIdx = prefix.lastIndexOf('/');
        if (slashIdx < 0) {
            throw new IllegalArgumentException(
                    "Cannot parse database name from URL (missing '/' path separator): " + originalUrl);
        }
        return prefix.substring(0, slashIdx + 1) + ISOLATED_DB_NAME + suffix;
    }

    /**
     * Returns the JDBC URL pointing at the {@code postgres} maintenance
     * database on the same host/port as the supplied URL. Used internally
     * by {@link #ensureDatabase(String, String, String)} to obtain an
     * admin connection capable of issuing {@code CREATE DATABASE}.
     */
    static String getAdminJdbcUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isEmpty()) {
            throw new IllegalArgumentException("originalUrl must be non-empty");
        }
        int queryIdx = originalUrl.indexOf('?');
        String prefix;
        String suffix;
        if (queryIdx >= 0) {
            prefix = originalUrl.substring(0, queryIdx);
            suffix = originalUrl.substring(queryIdx);
        } else {
            prefix = originalUrl;
            suffix = "";
        }
        int slashIdx = prefix.lastIndexOf('/');
        if (slashIdx < 0) {
            throw new IllegalArgumentException(
                    "Cannot parse database name from URL (missing '/' path separator): " + originalUrl);
        }
        return prefix.substring(0, slashIdx + 1) + "postgres" + suffix;
    }

    /**
     * Ensures the {@code test_migration} database exists on the PostgreSQL
     * server reachable via {@code originalUrl}. Idempotent — safe to call
     * from {@code @BeforeAll}.
     *
     * <p>Algorithm:</p>
     * <ol>
     *   <li>Connect to the {@code postgres} maintenance database on the same
     *       server (resolved from {@code originalUrl} by
     *       {@link #getAdminJdbcUrl(String)}).</li>
     *   <li>Query {@code pg_database} for the {@code test_migration} row.
     *       If the row exists, return successfully <em>without</em> issuing
     *       {@code CREATE DATABASE}. This avoids requiring the application
     *       role to have CREATEDB when the disposable database has been
     *       pre-provisioned.</li>
     *   <li>If the row does not exist, issue {@code CREATE DATABASE
     *       test_migration}. This path still requires CREATEDB; it is only
     *       needed the first time the database is bootstrapped on a fresh
     *       cluster.</li>
     *   <li>The legacy {@code duplicate_database} SQLSTATE {@code 42P04}
     *       handling is preserved for race safety: if a concurrent caller
     *       creates the database between our SELECT and our CREATE, we
     *       swallow the error and return success.</li>
     * </ol>
     *
     * <p><strong>Least-privilege contract:</strong> CREATEDB is needed on
     * this connection role only when this helper must actually create the
     * disposable database (first run on a fresh cluster, or after the
     * database has been dropped). A pre-provisioned {@code test_migration}
     * database — for example one created by an operator via the
     * {@code postgres} superuser and owned by the application role —
     * allows a least-privilege application role (no SUPERUSER, no
     * BYPASSRLS, no CREATEDB, no CREATEROLE) to run every migration-upgrade
     * test. The CI PostgreSQL Docker container continues to work because
     * its {@code POSTGRES_USER} has CREATEDB and the first call creates
     * the database; subsequent calls hit the {@code pg_database} existence
     * short-circuit and skip the CREATE.</p>
     *
     * <p>This call must NOT be issued from inside a transaction — PostgreSQL
     * forbids {@code CREATE DATABASE} in a transaction block. The driver's
     * default auto-commit mode (on) is used here, and the existence check
     * is also run in auto-commit so the helper never starts an explicit
     * transaction.</p>
     *
     * @param originalUrl the source JDBC URL (any database on the same server is fine)
     * @param user        DB user (needs CREATEDB only if the disposable database
     *                    does not already exist; otherwise no special privilege
     *                    beyond CONNECT on the {@code postgres} maintenance DB
     *                    and SELECT on {@code pg_database})
     * @param password    DB password
     */
    public static void ensureDatabase(String originalUrl, String user, String password) {
        String adminUrl = getAdminJdbcUrl(originalUrl);
        try (Connection conn = DriverManager.getConnection(adminUrl, user, password);
             Statement stmt = conn.createStatement()) {
            // Fast path: if the disposable database already exists, return
            // without attempting CREATE DATABASE. This lets a least-privilege
            // application role (no CREATEDB) run all migration-upgrade tests
            // once an operator has pre-provisioned `test_migration`.
            boolean exists = databaseExists(stmt, ISOLATED_DB_NAME);
            if (exists) {
                return;
            }
            // Slow path: database is absent. CREATE DATABASE requires
            // CREATEDB on the connection role. PostgreSQL does NOT support
            // `CREATE DATABASE IF NOT EXISTS`, so we attempt the CREATE and
            // swallow the duplicate_database error (SQLSTATE 42P04) for
            // race safety against concurrent callers.
            try {
                stmt.executeUpdate("CREATE DATABASE " + ISOLATED_DB_NAME);
            } catch (SQLException sqle) {
                if (!"42P04".equals(sqle.getSQLState())) {
                    throw sqle;
                }
                // Else: a concurrent caller created the database between our
                // SELECT and our CREATE — expected on parallel test runs.
            }
        } catch (SQLException sqle) {
            throw new IllegalStateException(
                    "Failed to ensure isolated test database '" + ISOLATED_DB_NAME
                            + "' exists on server reachable via " + adminUrl,
                    sqle);
        }
    }

    /**
     * Read-only existence check on {@code pg_database}. Runs in the
     * statement's current (auto-commit) mode so it never starts an
     * explicit transaction — required because the caller may subsequently
     * issue {@code CREATE DATABASE} on the same connection, which
     * PostgreSQL forbids inside a transaction block.
     *
     * @param stmt        an open statement on the {@code postgres}
     *                    maintenance database
     * @param databaseName the database name to look up
     * @return {@code true} iff a row with the given name exists in
     *         {@code pg_database}
     * @throws SQLException if the lookup query fails
     */
    private static boolean databaseExists(Statement stmt, String databaseName) throws SQLException {
        try (var ps = stmt.getConnection().prepareStatement(
                "SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, databaseName);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Convenience: reads {@code SPRING_DATASOURCE_URL} (defaulting to
     * {@code jdbc:postgresql://localhost:5432/sanad}) and the matching
     * {@code SPRING_DATASOURCE_USERNAME} / {@code SPRING_DATASOURCE_PASSWORD}
     * environment variables, ensures the {@code test_migration} database
     * exists, and returns the isolated JDBC URL pointing at it.
     *
     * <p>Useful for tests that do not need to inspect the original URL —
     * a single call replaces the inline {@code getOrDefault} boilerplate
     * and the {@code ensureDatabase} call.</p>
     */
    public static String getIsolatedJdbcUrlOrDefault() {
        String originalUrl = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
        ensureDatabase(originalUrl, user, password);
        return getIsolatedJdbcUrl(originalUrl);
    }
}
