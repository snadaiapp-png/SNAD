package com.sanad.platform.crm.idempotency;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.security.rls.TenantRlsDataSource;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** G6 R2/R3 RED contracts. Production code is intentionally unchanged. */
class CrmIdempotencyTransactionConcurrencyPostgresTest {
    private static final UUID TENANT = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID PRINCIPAL = UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final String ENDPOINT = "POST:/api/v2/crm/g6-r2-r3";
    private static final String FINGERPRINT = "a".repeat(64);

    private static final String BASE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(BASE_URL);

    private AnnotationConfigApplicationContext context;
    private IdempotencyService service;

    @BeforeAll
    static void requirePostgres() {
        boolean available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "CrmIdempotencyTransactionConcurrencyPostgresTest");
        Assumptions.assumeTrue(available, "PostgreSQL Direct is required for R2/R3");
        MigrationTestSchemaSupport.ensureDatabase(BASE_URL, USER, PASSWORD);
    }

    @BeforeEach
    void setUp() {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        installStrictTestPolicy();

        DriverManagerDataSource raw = rawDataSource();
        DataSource rlsDataSource = new TenantRlsDataSource(raw);
        context = new AnnotationConfigApplicationContext();
        context.registerBean("dataSource", DataSource.class, () -> rlsDataSource);
        context.registerBean("transactionManager", PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(rlsDataSource));
        context.registerBean(NamedParameterJdbcTemplate.class,
                () -> new NamedParameterJdbcTemplate(rlsDataSource));
        context.register(TransactionConfig.class, IdempotencyConfig.class);
        context.refresh();
        service = context.getBean(IdempotencyService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) context.close();
    }

    @Test
    void r2_serviceOwnsTenantScopedTransactionForBeginCompleteAndFail() throws SQLException {
        authenticate();

        assertThatCode(() -> {
            IdempotencyService.Replay replay = service.begin(
                    TENANT, PRINCIPAL, ENDPOINT, "r2", FINGERPRINT);
            assertThat(replay).isInstanceOf(IdempotencyService.Replay.ReplayMiss.class);
            UUID operationId = ((IdempotencyService.Replay.ReplayMiss) replay).operationId();

            service.complete(operationId, 201, "{\"ok\":true}", "{}", "application/json");
            assertThat(readStatus(operationId)).isEqualTo(201);

            service.fail(operationId);
            assertThat(count(operationId)).isZero();
        }).doesNotThrowAnyException();
    }

    @Test
    void r3_concurrentSameKeyProducesOneMissAndOneContractConflict() throws Exception {
        installDelayTrigger();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    authenticate();
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        return Outcome.replay(service.begin(
                                TENANT, PRINCIPAL, ENDPOINT, "r3-concurrent", FINGERPRINT));
                    } catch (Throwable failure) {
                        return Outcome.failure(failure);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }

            List<Outcome> outcomes = List.of(
                    futures.get(0).get(20, TimeUnit.SECONDS),
                    futures.get(1).get(20, TimeUnit.SECONDS));
            long misses = outcomes.stream().filter(Outcome::isMiss).count();
            long conflicts = outcomes.stream().filter(Outcome::isGovernedConflict).count();
            List<String> unexpected = outcomes.stream()
                    .filter(o -> !o.isMiss() && !o.isGovernedConflict())
                    .map(Outcome::description)
                    .toList();

            assertThat(misses).as("outcomes=%s", outcomes).isEqualTo(1L);
            assertThat(conflicts).as("outcomes=%s", outcomes).isEqualTo(1L);
            assertThat(unexpected)
                    .as("RLS/duplicate/transaction-aborted exceptions must not leak")
                    .isEmpty();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void installStrictTestPolicy() {
        JdbcTemplate jdbc = new JdbcTemplate(rawDataSource());
        jdbc.execute("ALTER TABLE crm_idempotency_records ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE crm_idempotency_records FORCE ROW LEVEL SECURITY");
        jdbc.execute("DROP POLICY IF EXISTS tenant_isolation ON crm_idempotency_records");
        jdbc.execute("DROP POLICY IF EXISTS crm_idempotency_records_tenant_isolation ON crm_idempotency_records");
        jdbc.execute("DROP POLICY IF EXISTS g6_r2_r3_strict ON crm_idempotency_records");
        jdbc.execute("CREATE POLICY g6_r2_r3_strict ON crm_idempotency_records "
                + "USING (tenant_id = current_setting('app.tenant_id', true)::uuid) "
                + "WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid)");
    }

    private void installDelayTrigger() {
        JdbcTemplate jdbc = new JdbcTemplate(rawDataSource());
        jdbc.execute("CREATE OR REPLACE FUNCTION g6_r3_delay() RETURNS trigger AS $$ "
                + "BEGIN IF NEW.idempotency_key = 'r3-concurrent' THEN PERFORM pg_sleep(0.75); END IF; "
                + "RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER g6_r3_delay BEFORE INSERT ON crm_idempotency_records "
                + "FOR EACH ROW EXECUTE FUNCTION g6_r3_delay()");
    }

    private DriverManagerDataSource rawDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource(URL, USER, PASSWORD);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    private int readStatus(UUID operationId) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            setTenant(c);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT response_status FROM crm_idempotency_records WHERE id = ?")) {
                ps.setObject(1, operationId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    int value = rs.getInt(1);
                    c.commit();
                    return value;
                }
            }
        }
    }

    private long count(UUID operationId) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            setTenant(c);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM crm_idempotency_records WHERE id = ?")) {
                ps.setObject(1, operationId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long value = rs.getLong(1);
                    c.commit();
                    return value;
                }
            }
        }
    }

    private static void setTenant(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, TENANT.toString());
            ps.executeQuery();
        }
    }

    private static void authenticate() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("g6-r2-r3", "n/a", List.of());
        auth.setDetails(Map.of(
                "tenant_id", TENANT.toString(),
                "user_id", PRINCIPAL.toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfig { }

    private record Outcome(IdempotencyService.Replay replay, Throwable failure) {
        static Outcome replay(IdempotencyService.Replay replay) { return new Outcome(replay, null); }
        static Outcome failure(Throwable failure) { return new Outcome(null, failure); }
        boolean isMiss() { return replay instanceof IdempotencyService.Replay.ReplayMiss; }
        boolean isGovernedConflict() {
            return failure instanceof CrmContractException e
                    && e.code() == CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT;
        }
        String description() {
            if (replay != null) return replay.getClass().getSimpleName();
            return failure == null ? "<none>" : failure.getClass().getName() + ": " + failure.getMessage();
        }
        @Override public String toString() { return description(); }
    }
}
