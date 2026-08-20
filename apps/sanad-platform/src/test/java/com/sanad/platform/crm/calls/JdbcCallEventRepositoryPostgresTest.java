package com.sanad.platform.crm.calls;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.calls.domain.CallDirection;
import com.sanad.platform.crm.calls.domain.CallDisposition;
import com.sanad.platform.crm.calls.domain.CallEvent;
import com.sanad.platform.crm.calls.domain.CallEventRepository;
import com.sanad.platform.crm.calls.domain.CallStatus;
import com.sanad.platform.crm.calls.infrastructure.JdbcCallEventRepository;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * crm_call_events PostgreSQL Direct tests (G8-03 §63): migration chain,
 * RLS + FORCE, tenant isolation, unique idempotency, indexes, transitions.
 */
class JdbcCallEventRepositoryPostgresTest {

    private JdbcTemplate jdbc;
    private CallEventRepository repository;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping JdbcCallEventRepositoryPostgresTest.");
    }

    @BeforeEach
    void migrateAndSeed() {
        Flyway flyway = Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        repository = new JdbcCallEventRepository(new NamedParameterJdbcTemplate(ds));
    }

    private CallEvent event(UUID tenant, String callId, CallStatus status) {
        return new CallEvent(UUID.randomUUID(), tenant, 0L, "NATIVE", callId,
                CallDirection.INBOUND, CallEvent.CallerSourceOfRecord.ANDROID_CALL,
                "+966541234567", null, CallEvent.MATCH_UNKNOWN, null, null, null, null,
                null, null, null, status, Instant.parse("2026-08-20T10:00:00Z"),
                status == CallStatus.ANSWERED ? Instant.parse("2026-08-20T10:00:05Z") : null,
                null, null, null, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-08-20T10:00:00Z"), Instant.parse("2026-08-20T10:00:00Z"));
    }

    @Test
    void migrationChainIncludesCallEventsWithRlsForced() {
        Map<String, Object> rls = jdbc.queryForMap(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'crm_call_events'");
        assertThat(rls.get("relrowsecurity")).isEqualTo(true);
        assertThat(rls.get("relforcerowsecurity")).isEqualTo(true);
        List<Map<String, Object>> policies = jdbc.queryForList(
                "SELECT policyname FROM pg_policies WHERE tablename = 'crm_call_events'");
        assertThat(policies).extracting(p -> p.get("policyname"))
                .contains("call_events_tenant_isolation");
        List<Map<String, Object>> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'crm_call_events'");
        assertThat(indexes).extracting(i -> i.get("indexname"))
                .contains("uq_crm_call_events_provider_call",
                        "idx_crm_call_events_matched_entity",
                        "idx_crm_call_events_agent",
                        "idx_crm_call_events_status",
                        "idx_crm_call_events_from_number");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_capabilities " +
                "WHERE code IN ('CRM.CALL_EVENT.READ','CRM.CALL_EVENT.WRITE') AND status='ACTIVE'", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void createGetAndFindByProviderCallId() {
        UUID tenant = tenant("ce-1");
        CallEvent created = repository.create(tenant, UUID.randomUUID(), event(tenant, "call-1", CallStatus.RINGING),
                Instant.parse("2026-08-20T10:00:00Z"));

        assertThat(repository.get(tenant, created.id())).isPresent();
        assertThat(repository.findByProviderCallId(tenant, "NATIVE", "call-1")).isPresent();
        // Same provider call id in ANOTHER tenant is a DIFFERENT aggregate.
        UUID otherTenant = tenant("ce-other");
        assertThat(repository.findByProviderCallId(otherTenant, "NATIVE", "call-1")).isEmpty();
    }

    @Test
    void duplicateProviderCallIdIsRejectedByUniqueConstraint() {
        UUID tenant = tenant("ce-2");
        repository.create(tenant, UUID.randomUUID(), event(tenant, "dup-1", CallStatus.RINGING),
                Instant.parse("2026-08-20T10:00:00Z"));

        assertThatThrownBy(() -> repository.create(tenant, UUID.randomUUID(),
                event(tenant, "dup-1", CallStatus.RINGING), Instant.parse("2026-08-20T10:00:01Z")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transitionBumpsVersionAndCompleteSetsDuration() {
        UUID tenant = tenant("ce-3");
        CallEvent created = repository.create(tenant, UUID.randomUUID(), event(tenant, "t-1", CallStatus.RINGING),
                Instant.parse("2026-08-20T10:00:00Z"));

        CallEvent answered = repository.transition(tenant, created.id(), created.version(), UUID.randomUUID(),
                CallStatus.ANSWERED, Instant.parse("2026-08-20T10:00:05Z"), Instant.parse("2026-08-20T10:00:05Z"));
        assertThat(answered.version()).isEqualTo(1);
        assertThat(answered.answeredAt()).isEqualTo(Instant.parse("2026-08-20T10:00:05Z"));

        CallEvent completed = repository.complete(tenant, created.id(), answered.version(), UUID.randomUUID(),
                CallStatus.COMPLETED, Instant.parse("2026-08-20T10:00:30Z"), 25,
                CallDisposition.CONNECTED, Instant.parse("2026-08-20T10:00:30Z"));
        assertThat(completed.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(completed.durationSeconds()).isEqualTo(25);
        assertThat(completed.disposition()).isEqualTo(CallDisposition.CONNECTED);
    }

    @Test
    void listIsTenantScopedAndBounded() {
        UUID tenantA = tenant("ce-4a");
        UUID tenantB = tenant("ce-4b");
        for (int i = 0; i < 3; i++) {
            repository.create(tenantA, UUID.randomUUID(), event(tenantA, "l-a-" + i, CallStatus.RINGING),
                    Instant.parse("2026-08-20T10:00:0" + i + "Z"));
        }
        repository.create(tenantB, UUID.randomUUID(), event(tenantB, "l-b-1", CallStatus.RINGING),
                Instant.parse("2026-08-20T10:00:00Z"));

        List<CallEvent> onlyA = repository.list(tenantA, null, 0, null, 10);
        assertThat(onlyA).hasSize(3);
        assertThat(repository.list(tenantB, null, 0, null, 10)).hasSize(1);
    }

    private UUID tenant(String key) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (?,?,?,'ACTIVE',?,?)",
                id, key, key + "-" + id.toString().substring(0, 8),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return id;
    }
}
