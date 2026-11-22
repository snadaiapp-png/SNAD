package com.sanad.platform.security.rls;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes tenant-lifecycle and capability-registry DML on a DEDICATED
 * control-plane database connection (ORDER 11, 2026-11-21: Control Plane
 * Security &amp; PostgreSQL Root-Cause Remediation).
 *
 * <h3>Contract</h3>
 *
 * <p>The control plane connects to PostgreSQL with its own limited-privilege
 * identity {@code sanad_control_plane} (LOGIN, NOSUPERUSER NOCREATEDB
 * NOCREATEROLE NOBYPASSRLS, holding exactly SELECT/INSERT/UPDATE/DELETE on
 * {@code tenants} and {@code access_capabilities} — V20261118_1/_2). The
 * runtime identity {@code sanad} is NOT a member of that role in any
 * direction, so no runtime session can {@code SET ROLE} into the control
 * plane. The previous SET ROLE compensating control is forbidden and was
 * removed (its live failure chain — 42501 {@code permission denied for
 * table tenants} on a WHERE-keyed UPDATE, surfaced as 25P02 on the RESET
 * ROLE compensation — is preserved in the ORDER 11 diagnosis record).
 *
 * <h3>Credential isolation</h3>
 *
 * <p>The control-plane credentials are provisioned exclusively by the
 * deployment environment ({@code SANAD_CONTROL_PLANE_DB_URL} /
 * {@code _USERNAME} / {@code _PASSWORD}, wired through
 * {@code sanad.control-plane.datasource.*}) and are never readable from the
 * runtime datasource configuration. When the properties are absent the
 * executor fails closed on first use: control-plane DML silently falling
 * back to the runtime pool would recreate the forbidden shared-identity
 * design.
 *
 * <h3>Transaction semantics</h3>
 *
 * <p>Each {@link #inControlPlane(Function)} block runs in its own
 * transaction on the control-plane pool. The runtime transaction (if any)
 * is a DIFFERENT connection: the two cannot be atomically committed
 * together without XA, which this platform does not deploy. Callers that
 * create durable control-plane rows inside a wider runtime flow must
 * register their own compensation (see
 * {@code RegistrationProvisioner#provision} for the rollback-synchronized
 * tenant deletion).
 *
 * <h3>THIS CLASS IS NOT AN AUTHORIZATION MECHANISM</h3>
 *
 * <p>Reaching the control-plane pool merely unlocks the database-level DML
 * path; it does NOT verify that the caller is allowed to manage tenants or
 * capabilities. That decision belongs to the upstream control-plane guards
 * (executive/admin authentication, capability evaluation). Only
 * control-plane call sites may use this executor.
 */
@Component
public class ControlPlaneDataAccess {

    /** Cluster role holding the control-plane DML (dedicated LOGIN identity). */
    static final String CONTROL_PLANE_ROLE = "sanad_control_plane";

    private static final String URL_PROPERTY = "sanad.control-plane.datasource.url";
    private static final String USERNAME_PROPERTY = "sanad.control-plane.datasource.username";
    private static final String PASSWORD_PROPERTY = "sanad.control-plane.datasource.password";

    private final Environment environment;

    private final AtomicReference<ControlPlanePool> pool = new AtomicReference<>();

    public ControlPlaneDataAccess(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /**
     * Runs {@code action} with a {@link JdbcTemplate} bound to the dedicated
     * control-plane connection pool, inside a transaction on that pool.
     *
     * @param <T>    result type of the action
     * @param action control-plane DML block (must not commit or roll back the
     *               control-plane transaction itself)
     * @return the action's result
     * @throws IllegalStateException when the control-plane datasource is not
     *                               provisioned (fail closed — never falls
     *                               back to the runtime pool)
     */
    public <T> T inControlPlane(Function<JdbcTemplate, T> action) {
        Objects.requireNonNull(action, "action");
        ControlPlanePool cp = pool();
        return cp.transactionTemplate.execute(status -> action.apply(cp.jdbc));
    }

    /**
     * Identity probe for certification tests: the value of
     * {@code current_user} on the dedicated control-plane connection.
     */
    String controlPlaneCurrentUser() {
        return inControlPlane(jdbc -> jdbc.queryForObject(
                "SELECT current_user", String.class));
    }

    private ControlPlanePool pool() {
        ControlPlanePool current = pool.get();
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = pool.get();
            if (current != null) {
                return current;
            }
            String url = environment.getProperty(URL_PROPERTY);
            String username = environment.getProperty(USERNAME_PROPERTY);
            String password = environment.getProperty(PASSWORD_PROPERTY);
            if (url == null || url.isBlank() || username == null || username.isBlank()) {
                throw new IllegalStateException(
                        "Control-plane datasource is not provisioned: set SANAD_CONTROL_PLANE_DB_URL / "
                                + "SANAD_CONTROL_PLANE_DB_USERNAME / SANAD_CONTROL_PLANE_DB_PASSWORD "
                                + "(sanad.control-plane.datasource.*). Control-plane DML must NEVER fall "
                                + "back to the runtime pool (ORDER 11 contract).");
            }
            ControlPlanePool created = new ControlPlanePool(url, username, password);
            pool.set(created);
            return created;
        }
    }

    /** Lazily-created dedicated pool with its own transaction template. */
    private static final class ControlPlanePool {

        final JdbcTemplate jdbc;
        final TransactionTemplate transactionTemplate;

        ControlPlanePool(String url, String username, String password) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
            config.setPoolName("SanadControlPlaneHikariCP");
            config.setMaximumPoolSize(4);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(30_000);
            config.setIdleTimeout(600_000);
            config.setMaxLifetime(1_800_000);
            DataSource dataSource = new HikariDataSource(config);
            this.jdbc = new JdbcTemplate(dataSource);
            org.springframework.jdbc.datasource.DataSourceTransactionManager txManager =
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
            this.transactionTemplate = new TransactionTemplate(txManager);
            this.transactionTemplate.setPropagationBehavior(
                    TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        }
    }
}
