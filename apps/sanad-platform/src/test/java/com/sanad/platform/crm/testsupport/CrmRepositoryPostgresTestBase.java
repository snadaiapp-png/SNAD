package com.sanad.platform.crm.testsupport;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared Testcontainers + Flyway harness for CRM repository integration tests (TD-003-S2).
 *
 * <p>Replicates the proven pattern from {@code OwnershipRepositoryBindingPostgresTest} and
 * {@code JdbcAddressCommunicationArchivePostgresTest}: a single static {@code postgres:16-alpine}
 * container, Flyway migration of the full CRM schema (including the {@code V15} Java migration
 * that seeds RBAC), and a {@link NamedParameterJdbcTemplate} built over the container's data
 * source. Subclasses obtain the {@code jdbc} template, a {@link TransactionTemplate}, and a
 * fresh per-class tenant via {@link #newTenant()}.
 *
 * <p>Tests are skipped (not failed) when Docker is unavailable, matching the established
 * convention and the Sprint-0 constraint that backend Testcontainers tests run in CI only.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
@Testcontainers
public abstract class CrmRepositoryPostgresTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void migrateSchema() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable,
                "Docker is required for CRM repository Testcontainers integration tests (TD-003-S2).");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    }

    /** The shared JDBC template, available after {@link #migrateSchema()} has run. */
    protected static NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    /** A transaction template for wrapping multi-statement repo writes. */
    protected static TransactionTemplate tx() {
        return transactions;
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
     */
    protected UUID seedContact(UUID tenantId, UUID actorId, String givenName, String displayName) {
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
    }
}
