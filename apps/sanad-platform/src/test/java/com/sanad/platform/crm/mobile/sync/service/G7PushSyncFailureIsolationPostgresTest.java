package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.security.rls.TenantRlsDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL-direct acceptance test for G7 SYNC-009 / ISO-004 failure isolation.
 *
 * <p>The middle mutation deliberately violates the physical crm_accounts
 * account_type CHECK constraint. PostgreSQL marks the current transaction as
 * aborted after such an error unless the mutation is protected by an explicit
 * savepoint / nested transaction. The first and third mutations therefore
 * prove that one bad mutation cannot poison the whole batch.</p>
 */
class G7PushSyncFailureIsolationPostgresTest {

    private DriverManagerDataSource rawDataSource;
    private JdbcTemplate rawJdbc;
    private PushSyncService service;
    private UUID tenantId;
    private UUID userId;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping G7PushSyncFailureIsolationPostgresTest.");
    }

    @BeforeEach
    void migrateAndConfigureTenantContext() {
        rawDataSource = postgresDataSource();
        // Do NOT call flyway.clean() — the Spring Boot managed Flyway has already
        // applied all migrations during context startup. Calling flyway.clean()
        // would DROP ALL TABLES in the shared CI PostgreSQL database, breaking
        // every other @SpringBootTest that shares the same database.
        //
        // Other tests in this suite unfortunately still call flyway.clean(), which
        // can leave the shared schema in an empty or partial state by the time
        // this test runs. To be resilient to that without reintroducing
        // destructive behavior, this test calls flyway.migrate() (NOT clean())
        // with cleanDisabled(true) so any pending migrations are applied
        // idempotently. Already-applied migrations are no-ops; missing ones are
        // applied. Nothing is destroyed.
        Flyway flyway = Flyway.configure()
                .dataSource(rawDataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();
        flyway.migrate();

        rawJdbc = new JdbcTemplate(rawDataSource);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Instant now = Instant.now();
        rawJdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",
                tenantId,
                "g7-failure-isolation",
                "g7-fi-" + tenantId.toString().substring(0, 8),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TenantRlsDataSource tenantDataSource = new TenantRlsDataSource(rawDataSource);
        JdbcTemplate tenantJdbc = new JdbcTemplate(tenantDataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(tenantDataSource);
        ObjectMapper mapper = new ObjectMapper();
        ConflictService conflictService = new ConflictService(tenantJdbc, mapper);
        service = new PushSyncService(tenantJdbc, mapper, conflictService, transactionManager);
    }

    @AfterEach
    void clearSecurityContextAndTestData() {
        SecurityContextHolder.clearContext();
        // Clean up test data to avoid polluting other tests
        if (rawJdbc != null && tenantId != null) {
            try {
                rawJdbc.update("DELETE FROM crm_idempotency_records WHERE tenant_id = ?", tenantId);
                rawJdbc.update("DELETE FROM crm_accounts WHERE tenant_id = ?", tenantId);
                rawJdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }
    }

    @Test
    void oneConstraintFailureDoesNotAbortTheOtherMutations() {
        UUID firstId = UUID.randomUUID();
        UUID invalidId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();

        PushSyncRequest request = new PushSyncRequest(List.of(
                createMutation("g7-fi-1", firstId, "First valid account", "BUSINESS"),
                createMutation("g7-fi-2", invalidId, "Invalid account", "NOT_A_VALID_ACCOUNT_TYPE"),
                createMutation("g7-fi-3", thirdId, "Third valid account", "PROSPECT")
        ));

        PushSyncResponse response = service.push(
                tenantId, UUID.randomUUID(), userId, request);

        assertThat(response.totalMutations()).isEqualTo(3);
        assertThat(response.applied()).isEqualTo(2);
        assertThat(response.rejected()).isEqualTo(1);
        assertThat(response.duplicates()).isZero();
        assertThat(response.results()).extracting(PushSyncResponse.MutationResult::status)
                .containsExactly("APPLIED", "REJECTED", "APPLIED");
        assertThat(response.results().get(1).httpStatus()).isEqualTo("500");

        assertThat(rawJdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = ?", Integer.class, tenantId))
                .isEqualTo(2);
        assertThat(rawJdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = ? AND id IN (?, ?)",
                Integer.class, tenantId, firstId, thirdId))
                .isEqualTo(2);
        assertThat(rawJdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, invalidId))
                .isZero();

        // The failed mutation's idempotency claim is rolled back with its
        // savepoint; only successfully committed mutations retain replay keys.
        assertThat(rawJdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_idempotency_records WHERE tenant_id = ? AND principal_id = ? "
                        + "AND endpoint = '/api/v2/mobile/sync/push'",
                Integer.class, tenantId, userId))
                .isEqualTo(2);
    }

    private PushSyncRequest.MutationEnvelope createMutation(
            String key, UUID entityId, String displayName, String accountType) {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("display_name", displayName);
        payload.put("account_type", accountType);
        return new PushSyncRequest.MutationEnvelope(
                key,
                "account",
                entityId.toString(),
                "CREATE",
                null,
                payload,
                "2026-08-20T17:00:00Z");
    }

    private DriverManagerDataSource postgresDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
