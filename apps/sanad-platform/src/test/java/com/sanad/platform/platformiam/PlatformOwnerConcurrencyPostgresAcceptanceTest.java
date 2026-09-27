package com.sanad.platform.platformiam;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.exception.LastPlatformOwnerException;
import com.sanad.platform.platformiam.repository.JdbcPlatformMembershipRepository;
import com.sanad.platform.platformiam.repository.JdbcPlatformRoleMetadataRepository;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.platformiam.service.PlatformOwnerSafetyService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** PostgreSQL Direct concurrency acceptance for the protected Platform Owner invariant. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformOwnerConcurrencyPostgresAcceptanceTest {

    private static final UUID CONTROL_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CANONICAL_OWNER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate namedJdbc;
    private TransactionTemplate transactions;
    private PlatformMembershipRepository memberships;
    private PlatformOwnerSafetyService ownerSafety;

    @BeforeAll
    void migrateAndCreateService() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "PlatformOwnerConcurrencyPostgresAcceptanceTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping PlatformOwnerConcurrencyPostgresAcceptanceTest.");

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
        memberships = new JdbcPlatformMembershipRepository(namedJdbc);
        ownerSafety = new PlatformOwnerSafetyService(
                memberships,
                new JdbcPlatformRoleMetadataRepository(namedJdbc));
    }

    @Test
    void concurrentOwnerRoleRemovalsRejectOneMutationAndPreserveAnActiveOwner() throws Exception {
        UUID ownerRoleId = inTenant(() -> jdbc.queryForObject(
                "SELECT id FROM roles WHERE tenant_id=? AND code='PLATFORM_OWNER' AND status='ACTIVE'",
                UUID.class,
                CONTROL_TENANT_ID));
        UUID secondOwnerId = createSecondOwner(ownerRoleId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(
                    () -> revokeOwnerRole(CANONICAL_OWNER_ID, ownerRoleId, ready, start));
            Future<Boolean> second = pool.submit(
                    () -> revokeOwnerRole(secondOwnerId, ownerRoleId, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            assertThat(inTenant(this::effectiveActiveOwnerCount)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean revokeOwnerRole(
            UUID userId,
            UUID ownerRoleId,
            CountDownLatch ready,
            CountDownLatch start) {
        Boolean result = transactions.execute(status -> {
            setTenantContext();
            ready.countDown();
            await(start);
            try {
                ownerSafety.assertMayRemoveOwnerRole(CONTROL_TENANT_ID, userId, ownerRoleId);
                int updated = jdbc.update(
                        "UPDATE user_role_assignments SET status='REVOKED', updated_at=NOW() " +
                                "WHERE tenant_id=? AND user_id=? AND role_id=? AND status='ACTIVE'",
                        CONTROL_TENANT_ID, userId, ownerRoleId);
                assertThat(updated).isEqualTo(1);
                return true;
            } catch (LastPlatformOwnerException expected) {
                assertThat(expected.reasonCode()).isEqualTo("LAST_PLATFORM_OWNER");
                return false;
            }
        });
        return Boolean.TRUE.equals(result);
    }

    private UUID createSecondOwner(UUID ownerRoleId) {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        inTenantVoid(() -> {
            jdbc.update(
                    "INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) " +
                            "VALUES (?,?,?,?,'ACTIVE',?,?)",
                    userId, CONTROL_TENANT_ID,
                    "task5-second-owner+" + userId + "@example.test",
                    "Task 5 Second Owner", Timestamp.from(now), Timestamp.from(now));

            memberships.save(new PlatformMembership(
                    UUID.randomUUID(), CONTROL_TENANT_ID, userId, PlatformMembershipStatus.ACTIVE,
                    now, now, null, null, null,
                    CANONICAL_OWNER_ID, CANONICAL_OWNER_ID,
                    "Task 5 concurrency fixture", now, now));

            jdbc.update(
                    "INSERT INTO user_role_assignments " +
                            "(id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) " +
                            "VALUES (?,?,?,?,NULL,'ACTIVE',?,?)",
                    UUID.randomUUID(), CONTROL_TENANT_ID, userId, ownerRoleId,
                    Timestamp.from(now), Timestamp.from(now));
        });
        return userId;
    }

    private int effectiveActiveOwnerCount() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM platform_memberships pm
                WHERE pm.control_tenant_id = ?
                  AND pm.status = 'ACTIVE'
                  AND EXISTS (
                      SELECT 1
                      FROM user_role_assignments ura
                      JOIN roles r
                        ON r.tenant_id = ura.tenant_id
                       AND r.id = ura.role_id
                      WHERE ura.tenant_id = pm.control_tenant_id
                        AND ura.user_id = pm.user_id
                        AND ura.status = 'ACTIVE'
                        AND r.status = 'ACTIVE'
                        AND r.code = 'PLATFORM_OWNER'
                  )
                """, Integer.class, CONTROL_TENANT_ID);
        return count == null ? 0 : count;
    }

    private <T> T inTenant(Supplier<T> work) {
        return transactions.execute(status -> {
            setTenantContext();
            return work.get();
        });
    }

    private void inTenantVoid(Runnable work) {
        transactions.executeWithoutResult(status -> {
            setTenantContext();
            work.run();
        });
    }

    private void setTenantContext() {
        namedJdbc.queryForObject(
                "SELECT set_config('app.tenant_id', :tenantId, true)",
                new MapSqlParameterSource("tenantId", CONTROL_TENANT_ID.toString()),
                String.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent owner mutation start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating owner mutation", interrupted);
        }
    }
}
