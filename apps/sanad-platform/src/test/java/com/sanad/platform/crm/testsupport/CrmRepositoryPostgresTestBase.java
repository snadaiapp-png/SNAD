package com.sanad.platform.crm.testsupport;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared PostgreSQL Direct + Flyway harness for CRM repository integration tests (TD-003-S2).
 *
 * <p>Uses PostgreSQL Direct (localhost:5432) instead of Docker/Testcontainers.
 * Flyway migration of the full CRM schema (including the {@code V15} Java migration
 * that seeds RBAC), and a {@link NamedParameterJdbcTemplate} built over the direct data
 * source. Subclasses obtain the {@code jdbc} template, a {@link TransactionTemplate}, and a
 * fresh per-class tenant via {@link #newTenant()}.
 *
 * <p>Database credentials are read from environment variables:
 * {@code SPRING_DATASOURCE_URL}, {@code SPRING_DATASOURCE_USERNAME},
 * {@code SPRING_DATASOURCE_PASSWORD}.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
public abstract class CrmRepositoryPostgresTestBase {

    private static final String JDBC_URL =
            System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                    "jdbc:postgresql://localhost:5432/sanad");
    private static final String USERNAME =
            System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD =
            System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static TenantRlsTransactionContext tenantRlsContext;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(JDBC_URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(JDBC_URL, USERNAME, PASSWORD);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        // Reuse the production TenantRlsTransactionContext against the SAME
        // DataSource that backs `jdbc` and `transactions`. This lets
        // repository tests scope their transaction to a tenant without
        // re-implementing the GUC plumbing, and without mutating
        // SecurityContextHolder or adding GUC logic to production
        // JdbcContactRepository.
        tenantRlsContext = new TenantRlsTransactionContext(
                new JdbcTemplate(dataSource));
    }

    /** The shared JDBC template, available after {@link #migrateSchema()} has run. */
    protected static NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    /** A transaction template for wrapping multi-statement repo writes. */
    protected static TransactionTemplate tx() {
        return transactions;
    }

    /**
     * Execute a unit of work inside a Spring transaction whose
     * {@code app.tenant_id} GUC is scoped to {@code tenantId} for the
     * duration of the transaction. The GUC is set transaction-local via
     * the production {@link TenantRlsTransactionContext} and disappears
     * automatically at transaction end (commit or rollback).
     *
     * <p>Use this for repository writes against FORCE RLS tables
     * (e.g. {@code crm_contacts} after V20260823_1) so the WITH CHECK
     * predicate is satisfied without granting SUPERUSER/BYPASSRLS to
     * the application role.</p>
     *
     * @param tenantId the tenant id to scope the transaction to
     * @param work     the unit of work
     * @param <T>      result type
     * @return the result of {@code work}
     */
    protected static <T> T inTenantTransaction(UUID tenantId, Supplier<T> work) {
        return transactions.execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(tenantId);
            return work.get();
        });
    }

    /** void-work variant of {@link #inTenantTransaction(UUID, Supplier)}. */
    protected static void inTenantTransaction(UUID tenantId, Runnable work) {
        transactions.execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(tenantId);
            work.run();
            return null;
        });
    }

    /** Execute a unit of work inside a transaction and return its result. */
    protected static <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    /** Execute a void unit of work inside a transaction (for repo methods returning void). */
    protected static void inTransaction(Runnable work) {
        transactions.execute(status -> {
            work.run();
            return null;
        });
    }

    /**
     * Insert a fresh, unique tenant and return its id. Each test class (or test, when called in
     * a {@code @BeforeEach}) gets its own tenant so data never leaks across tests.
     */
    protected UUID newTenant() {
        UUID tenantId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc().update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', :now, :now)
                """, Map.of(
                "id", (Object) tenantId,
                "name", "TD-003-S2 Tenant " + tenantId.toString().substring(0, 8),
                "subdomain", "td003s2-" + tenantId.toString().substring(0, 8),
                "now", now));
        return tenantId;
    }

    /**
     * Insert a minimal {@code crm_accounts} row (BUSINESS/ACTIVE) and return its id, owned by
     * {@code actorId}. Seeds only the NOT NULL base columns so it satisfies every FK that points
     * at {@code crm_accounts(id)}.
     */
    protected UUID seedAccount(UUID tenantId, UUID actorId, String displayName, String normalizedName) {
        UUID accountId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc().update("""
                INSERT INTO crm_accounts (id, tenant_id, version, display_name, normalized_name,
                    account_type, lifecycle_status, created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, 0, :name, :normalized, 'BUSINESS', 'ACTIVE',
                    :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", accountId)
                .addValue("tenantId", tenantId)
                .addValue("name", displayName)
                .addValue("normalized", normalizedName)
                .addValue("actorId", actorId)
                .addValue("now", now));
        return accountId;
    }

    /**
     * Insert a minimal {@code crm_contacts} row (ACTIVE, no account link) and return its id.
     * Avoids the {@code V20260717_1} legacy-relationship backfill by leaving {@code account_id}
     * null, keeping relationship tests deterministic.
     *
     * <p>Executes the INSERT inside a tenant-scoped transaction so the
     * fail-closed WITH CHECK predicate on {@code crm_contacts} (enabled
     * by V20260823_1) accepts the row.</p>
     */
    protected UUID seedContact(UUID tenantId, UUID actorId, String givenName, String displayName) {
        return inTenantTransaction(tenantId, () -> {
            UUID contactId = UUID.randomUUID();
            Timestamp now = Timestamp.from(Instant.now());
            jdbc().update("""
                    INSERT INTO crm_contacts (id, tenant_id, version, given_name, display_name, normalized_name,
                        lifecycle_status, created_by, updated_by, created_at, updated_at)
                    VALUES (:id, :tenantId, 0, :givenName, :name, :normalized, 'ACTIVE',
                        :actorId, :actorId, :now, :now)
                    """, new MapSqlParameterSource()
                    .addValue("id", contactId)
                    .addValue("tenantId", tenantId)
                    .addValue("givenName", givenName)
                    .addValue("name", displayName)
                    .addValue("normalized", displayName.toLowerCase().replace(' ', '-'))
                    .addValue("actorId", actorId)
                    .addValue("now", now));
            return contactId;
        });
    }
}
