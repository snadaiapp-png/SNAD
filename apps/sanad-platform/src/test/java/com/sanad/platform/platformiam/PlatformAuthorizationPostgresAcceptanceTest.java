package com.sanad.platform.platformiam;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.security.scope.AccessScopeGrant;
import com.sanad.platform.security.scope.JdbcAccessScopeRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** PostgreSQL Direct acceptance for Task 4 Platform IAM authorization boundaries. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformAuthorizationPostgresAcceptanceTest {

    private static final UUID CONTROL_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GRANTED_BY =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String CAPABILITY = "PLATFORM.USER.READ";

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate namedJdbc;
    private TransactionTemplate transactions;
    private JdbcAccessScopeRepository scopeRepository;

    @BeforeAll
    void migrateAndCreateRepository() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "PlatformAuthorizationPostgresAcceptanceTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping PlatformAuthorizationPostgresAcceptanceTest.");

        String baseUrl = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        String username = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        MigrationTestSchemaSupport.ensureDatabase(baseUrl, username, password);
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(baseUrl);

        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl, username, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(isolatedUrl, username, password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        scopeRepository = new JdbcAccessScopeRepository(jdbc);
    }

    @Test
    void directTemporaryGrantAllowsBeforeExpiryButDeniesAtExactExpiryBoundary() {
        UUID userId = createUser("task4-expiry-boundary");
        Instant from = Instant.parse("2026-09-26T20:00:00Z");
        Instant expiry = Instant.parse("2026-09-26T21:00:00Z");
        insertDirectGrant(userId, from, expiry, "incident support");

        List<AccessScopeGrant> beforeExpiry = inTenant(() -> scopeRepository.findEffectiveGrants(
                CONTROL_TENANT_ID, userId, null, CAPABILITY, expiry.minusNanos(1)));
        List<AccessScopeGrant> atExpiry = inTenant(() -> scopeRepository.findEffectiveGrants(
                CONTROL_TENANT_ID, userId, null, CAPABILITY, expiry));

        assertThat(beforeExpiry)
                .singleElement()
                .satisfies(grant -> {
                    assertThat(grant.directException()).isTrue();
                    assertThat(grant.userId()).isEqualTo(userId);
                    assertThat(grant.effectiveTo()).isEqualTo(expiry);
                });
        assertThat(atExpiry).isEmpty();
    }

    @Test
    void revokedDirectTemporaryGrantNeverAuthorizes() {
        UUID userId = createUser("task4-revoked-grant");
        Instant from = Instant.parse("2026-09-26T20:00:00Z");
        Instant expiry = Instant.parse("2026-09-26T22:00:00Z");
        UUID grantId = insertDirectGrant(userId, from, expiry, "incident support");
        inTenantVoid(() -> jdbc.update(
                "UPDATE access_scope_grants SET status='REVOKED' WHERE id=?",
                grantId));

        List<AccessScopeGrant> effective = inTenant(() -> scopeRepository.findEffectiveGrants(
                CONTROL_TENANT_ID, userId, null, CAPABILITY, from.plusSeconds(60)));

        assertThat(effective).isEmpty();
    }

    private UUID createUser(String key) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        inTenantVoid(() -> jdbc.update(
                "INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) " +
                        "VALUES (?,?,?,?,'ACTIVE',?,?)",
                id, CONTROL_TENANT_ID, key + "+" + id + "@example.test", key,
                Timestamp.from(now), Timestamp.from(now)));
        return id;
    }

    private UUID insertDirectGrant(UUID userId, Instant from, Instant to, String reason) {
        UUID grantId = UUID.randomUUID();
        inTenantVoid(() -> jdbc.update("""
                INSERT INTO access_scope_grants (
                    id, tenant_id, user_id, capability_id, scope_type,
                    is_direct_exception, reason, granted_by,
                    effective_from, effective_to, status, created_at
                )
                SELECT ?, ?, ?, c.id, 'TENANT', TRUE, ?, ?, ?, ?, 'ACTIVE', NOW()
                FROM access_capabilities c
                WHERE c.code = ?
                """,
                grantId, CONTROL_TENANT_ID, userId, reason, GRANTED_BY,
                Timestamp.from(from), Timestamp.from(to), CAPABILITY));
        return grantId;
    }

    private <T> T inTenant(Supplier<T> work) {
        return transactions.execute(status -> {
            namedJdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', :tenantId, true)",
                    new MapSqlParameterSource("tenantId", CONTROL_TENANT_ID.toString()),
                    String.class);
            return work.get();
        });
    }

    private void inTenantVoid(Runnable work) {
        transactions.executeWithoutResult(status -> {
            namedJdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', :tenantId, true)",
                    new MapSqlParameterSource("tenantId", CONTROL_TENANT_ID.toString()),
                    String.class);
            work.run();
        });
    }
}
