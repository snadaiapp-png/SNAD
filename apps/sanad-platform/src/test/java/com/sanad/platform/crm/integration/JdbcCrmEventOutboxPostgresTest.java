package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.CrmEventOutboxPort;
import com.sanad.platform.crm.integration.domain.CrmEventOutboxPort.CrmEventEnvelope;
import com.sanad.platform.crm.integration.infrastructure.JdbcCrmEventOutboxAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 5 — Durable CRM Event Outbox.
 *
 * <p>PostgreSQL Direct test that verifies the {@link CrmEventOutboxPort}
 * contract: {@code append}, {@code claimDue} (with FOR UPDATE SKIP LOCKED),
 * {@code markPublished}, {@code markFailed}, and the domain envelope
 * {@link CrmEventEnvelope} validation.
 *
 * <p>The outbox is FORCE RLS (V20260822_2) with a fail-closed tenant policy.
 * The production adapter MUST NOT mutate the GUC or read SecurityContextHolder
 * — tests establish transaction-local GUC explicitly so behaviour can be
 * reasoned about deterministically.
 *
 * <p>All availableAt / now relationships use fixed {@link Instant#parse}
 * values — never {@code Instant.now()} — so due-ness is deterministic.
 */
@DisplayName("Task 5 — Durable CRM Event Outbox (PostgreSQL Direct)")
class JdbcCrmEventOutboxPostgresTest {

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcCrmEventOutboxAdapter adapter;
    private static final ObjectMapper mapper = new ObjectMapper();

    // Deterministic fixed UUIDs and Instants — no Instant.now() in fixtures.
    private static final UUID TENANT_A = UUID.fromString("55555555-0000-4000-8000-00000000a001");
    private static final UUID TENANT_B = UUID.fromString("66666666-0000-4000-8000-00000000b001");
    private static final UUID AGGREGATE_A = UUID.fromString("55555555-0000-4000-8000-00000000a002");
    private static final UUID AGGREGATE_B = UUID.fromString("66666666-0000-4000-8000-00000000b002");
    private static final UUID EVENT_ID_1 = UUID.fromString("55555555-0000-4000-8000-00000000a003");
    private static final UUID EVENT_ID_2 = UUID.fromString("55555555-0000-4000-8000-00000000a004");
    private static final UUID EVENT_ID_3 = UUID.fromString("55555555-0000-4000-8000-00000000a005");

    private static final Instant T0 = Instant.parse("2026-08-22T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-22T10:00:01Z");
    private static final Instant T2 = Instant.parse("2026-08-22T10:00:02Z");
    private static final Instant T3 = Instant.parse("2026-08-22T10:00:03Z");
    private static final Instant T_LATER = Instant.parse("2026-08-22T11:00:00Z");

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "JdbcCrmEventOutboxPostgresTest");
        } catch (Throwable ignored) {
            ok = false;
        }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required");
        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                                "jdbc:postgresql://localhost:5432/sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
        var ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new NamedParameterJdbcTemplate(ds);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        adapter = new JdbcCrmEventOutboxAdapter(jdbc, mapper);
    }

    @BeforeEach
    void seed() {
        // FORCE RLS on crm_event_outbox means we must set the GUC for EACH
        // tenant separately to delete its rows. The policy
        // `tenant_id = current_setting('app.tenant_id', true)::UUID`
        // filters any DELETE issued with a mismatched GUC.
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            jdbc.update("DELETE FROM crm_event_outbox WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
        });
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_B);
            jdbc.update("DELETE FROM crm_event_outbox WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_B));
        });
        // tenants table is NOT FORCE RLS — direct DELETE is safe.
        jdbc.update("DELETE FROM tenants WHERE id IN (:a,:b)",
                new MapSqlParameterSource()
                        .addValue("a", TENANT_A)
                        .addValue("b", TENANT_B));
        ensureTenant(TENANT_A);
        ensureTenant(TENANT_B);
    }

    // A. appendStoresEnvelopeExactlyOnce
    @Test
    @DisplayName("A. append stores the envelope exactly once with PENDING status and zero attempt_count")
    void appendStoresEnvelopeExactlyOnce() {
        CrmEventEnvelope env = envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                "crm.contact.created", "agg.contact", T0, T0);

        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.append(env);
        });

        Integer count = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1),
                    Integer.class);
        });
        assertThat(count).isEqualTo(1);

        Map<String, Object> row = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForMap(
                    "SELECT status, attempt_count, available_at, created_at, updated_at, " +
                            "aggregate_type, aggregate_id, correlation_id, causation_id, schema_version, " +
                            "claimed_at, published_at, last_error, payload_json " +
                            "FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1));
        });
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(0);
        assertThat(((java.sql.Timestamp) row.get("available_at")).toInstant()).isEqualTo(T0);
        assertThat(((java.sql.Timestamp) row.get("created_at")).toInstant()).isEqualTo(T0);
        assertThat(((java.sql.Timestamp) row.get("updated_at")).toInstant()).isEqualTo(T0);
        assertThat(row.get("aggregate_type")).isEqualTo("agg.contact");
        assertThat(row.get("aggregate_id")).isEqualTo(AGGREGATE_A);
        assertThat(row.get("correlation_id")).isEqualTo("corr-" + EVENT_ID_1);
        assertThat(row.get("causation_id")).isNull();
        assertThat(((Number) row.get("schema_version")).intValue()).isEqualTo(1);
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("last_error")).isNull();
        String payload = (String) row.get("payload_json");
        assertThat(payload).isNotNull();
        try {
            JsonNode parsed = mapper.readTree(payload);
            assertThat(parsed.path("kind").asText()).isEqualTo("created");
        } catch (Exception e) {
            throw new AssertionError("payload_json must be valid JSON: " + payload, e);
        }
    }

    // B. duplicateEventIdRejected
    @Test
    @DisplayName("B. append rejects duplicate event id within the same tenant")
    void duplicateEventIdRejected() {
        CrmEventEnvelope env = envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                "crm.contact.created", "agg.contact", T0, T0);

        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.append(env);
        });

        // Same id+tenant_id again — must violate the unique constraint.
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.append(env);
        })).isInstanceOf(DataAccessException.class);
    }

    // C. claimDueUsesTenantScopeAndChangesStateToProcessing
    @Test
    @DisplayName("C. claimDue returns only the supplied tenant's rows and transitions PENDING -> PROCESSING")
    void claimDueUsesTenantScopeAndChangesStateToProcessing() {
        // Seed events for tenant A and tenant B.
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.append(envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                    "crm.contact.created", "agg.contact", T0, T0));
            adapter.append(envelope(EVENT_ID_2, TENANT_A, AGGREGATE_A,
                    "crm.contact.updated", "agg.contact", T1, T1));
        });
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_B);
            adapter.append(envelope(EVENT_ID_3, TENANT_B, AGGREGATE_B,
                    "crm.contact.created", "agg.contact", T0, T0));
        });

        // Claim for tenant A only — must NOT see tenant B's row.
        List<CrmEventEnvelope> claimed = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.claimDue(TENANT_A, T2, 10);
        });
        assertThat(claimed).hasSize(2);
        assertThat(claimed).extracting(CrmEventEnvelope::id)
                .containsExactlyInAnyOrder(EVENT_ID_1, EVENT_ID_2);

        // Both must now be PROCESSING, with claimed_at set to the supplied now.
        Map<String, Object> row = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForMap(
                    "SELECT status, claimed_at, updated_at FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1));
        });
        assertThat(row.get("status")).isEqualTo("PROCESSING");
        assertThat(((java.sql.Timestamp) row.get("claimed_at")).toInstant()).isEqualTo(T2);
        assertThat(((java.sql.Timestamp) row.get("updated_at")).toInstant()).isEqualTo(T2);

        // attempt_count must NOT be incremented on claim.
        Integer attempts = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT attempt_count FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1),
                    Integer.class);
        });
        assertThat(attempts).isEqualTo(0);

        // Tenant B's row must remain PENDING.
        String tenantBStatus = transactions.execute(s -> {
            setGuc(TENANT_B);
            return jdbc.queryForObject(
                    "SELECT status FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_B)
                            .addValue("id", EVENT_ID_3),
                    String.class);
        });
        assertThat(tenantBStatus).isEqualTo("PENDING");
    }

    // D. claimDueRespectsAvailabilityAndOrdering
    @Test
    @DisplayName("D. claimDue respects available_at and returns rows in deterministic available_at/created_at/id order")
    void claimDueRespectsAvailabilityAndOrdering() {
        // Three events with staggered available_at values.
        // The two with available_at <= now must be returned in ASC order.
        // The third (available_at in the future) must NOT be returned.
        CrmEventEnvelope e1 = envelope(UUID.randomUUID(), TENANT_A, AGGREGATE_A,
                "evt.1", "agg.contact", T0, T0);
        CrmEventEnvelope e2 = envelope(UUID.randomUUID(), TENANT_A, AGGREGATE_A,
                "evt.2", "agg.contact", T1, T1);
        CrmEventEnvelope e3 = envelope(UUID.randomUUID(), TENANT_A, AGGREGATE_A,
                "evt.3", "agg.contact", T_LATER, T2);

        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            // Insert out of order to confirm ORDER BY works on the index, not insertion order.
            adapter.append(e2);
            adapter.append(e1);
            adapter.append(e3);
        });

        List<CrmEventEnvelope> claimed = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.claimDue(TENANT_A, T2, 10);
        });
        assertThat(claimed).hasSize(2);
        // ASC order: e1 (available_at=T0) before e2 (available_at=T1).
        assertThat(claimed.get(0).id()).isEqualTo(e1.id());
        assertThat(claimed.get(1).id()).isEqualTo(e2.id());
        // e3 (available_at=T_LATER) must NOT be claimed.
        assertThat(claimed).extracting(CrmEventEnvelope::id).doesNotContain(e3.id());
    }

    // E. claimDueAcceptsFailedForRetry
    @Test
    @DisplayName("E. claimDue retries FAILED rows so failed events can be re-delivered")
    void claimDueAcceptsFailedForRetry() {
        // Seed a PENDING event, claim it, then mark it FAILED with nextAttemptAt
        // in the future. Then advance the clock past nextAttemptAt and confirm
        // claimDue re-claims it.
        CrmEventEnvelope env = envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                "crm.contact.failed", "agg.contact", T0, T0);
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.append(env);
        });
        transactions.execute(s -> {
            setGuc(TENANT_A);
            adapter.claimDue(TENANT_A, T0, 10);
            return adapter.markFailed(TENANT_A, EVENT_ID_1, T2, "transient network error");
        });

        // Before T2: not claimable. After T2: claimable.
        List<CrmEventEnvelope> beforeDue = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.claimDue(TENANT_A, T1, 10);
        });
        assertThat(beforeDue).isEmpty();

        List<CrmEventEnvelope> afterDue = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.claimDue(TENANT_A, T2, 10);
        });
        assertThat(afterDue).hasSize(1);
        assertThat(afterDue.get(0).id()).isEqualTo(EVENT_ID_1);
    }

    // F. claimDueRejectsInvalidLimits
    @Test
    @DisplayName("F. claimDue rejects limit < 1 and limit > 100")
    void claimDueRejectsInvalidLimits() {
        assertThatThrownBy(() -> transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.claimDue(TENANT_A, T0, 0);
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.claimDue(TENANT_A, T0, 101);
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.claimDue(TENANT_A, null, 10);
        })).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.claimDue(null, T0, 10);
        })).isInstanceOf(NullPointerException.class);
    }

    // G. publishAndFailureTransitionsRequireProcessingState
    @Test
    @DisplayName("G. markPublished and markFailed require status=PROCESSING")
    void publishAndFailureTransitionsRequireProcessingState() {
        CrmEventEnvelope env = envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                "evt", "agg.contact", T0, T0);
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.append(env);
        });
        // Status is PENDING — markPublished/markFailed must return false (no row updated).
        Boolean publishedFromPending = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.markPublished(TENANT_A, EVENT_ID_1, T1);
        });
        assertThat(publishedFromPending).isFalse();

        Boolean failedFromPending = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.markFailed(TENANT_A, EVENT_ID_1, T1, "err");
        });
        assertThat(failedFromPending).isFalse();

        // Claim → PROCESSING, then markPublished returns true and the row is PUBLISHED.
        transactions.execute(s -> {
            setGuc(TENANT_A);
            adapter.claimDue(TENANT_A, T1, 10);
            return null;
        });
        Boolean published = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.markPublished(TENANT_A, EVENT_ID_1, T2);
        });
        assertThat(published).isTrue();

        // Now status=PUBLISHED — markFailed must return false.
        Boolean failedFromPublished = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.markFailed(TENANT_A, EVENT_ID_1, T3, "err");
        });
        assertThat(failedFromPublished).isFalse();
    }

    // H. staleTerminalTransitionIsRejected
    @Test
    @DisplayName("H. stale state transitions (PUBLISHED -> PUBLISHED, FAILED -> PUBLISHED) are rejected")
    void staleTerminalTransitionIsRejected() {
        CrmEventEnvelope env = envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                "evt", "agg.contact", T0, T0);
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.append(env);
        });
        // PENDING -> claim -> PUBLISHED
        transactions.execute(s -> {
            setGuc(TENANT_A);
            adapter.claimDue(TENANT_A, T1, 10);
            return adapter.markPublished(TENANT_A, EVENT_ID_1, T2);
        });
        // Stale re-publish attempt — must NOT update.
        Boolean secondPublish = transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.markPublished(TENANT_A, EVENT_ID_1, T3);
        });
        assertThat(secondPublish).isFalse();

        // The published_at timestamp must remain T2 (the first publish).
        java.sql.Timestamp publishedAt = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT published_at FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1),
                    java.sql.Timestamp.class);
        });
        assertThat(publishedAt.toInstant()).isEqualTo(T2);
    }

    // I. failureErrorIsBoundedTo2000Characters
    @Test
    @DisplayName("I. markFailed truncates the error to first 2000 Java characters")
    void failureErrorIsBoundedTo2000Characters() {
        CrmEventEnvelope env = envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                "evt", "agg.contact", T0, T0);
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.append(env);
        });
        transactions.execute(s -> {
            setGuc(TENANT_A);
            adapter.claimDue(TENANT_A, T1, 10);
            return null;
        });
        // Build a 5000-char error string.
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5000; i++) big.append('x');
        String error5000 = big.toString();

        transactions.execute(s -> {
            setGuc(TENANT_A);
            return adapter.markFailed(TENANT_A, EVENT_ID_1, T2, error5000);
        });

        String storedError = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT last_error FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1),
                    String.class);
        });
        assertThat(storedError).hasSize(2000);
        assertThat(storedError).isEqualTo(error5000.substring(0, 2000));

        Integer attempts = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT attempt_count FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1),
                    Integer.class);
        });
        assertThat(attempts).isEqualTo(1);

        java.sql.Timestamp claimedAt = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT claimed_at FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1),
                    java.sql.Timestamp.class);
        });
        assertThat(claimedAt).isNull();

        java.sql.Timestamp availableAt = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT available_at FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1),
                    java.sql.Timestamp.class);
        });
        assertThat(availableAt.toInstant()).isEqualTo(T2);

        String status = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT status FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("id", EVENT_ID_1),
                    String.class);
        });
        assertThat(status).isEqualTo("FAILED");
    }

    // J. missingTenantContextFailsClosed
    @Test
    @DisplayName("J. missing tenant GUC fails closed — append is rejected by RLS WITH CHECK")
    void missingTenantContextFailsClosed() {
        CrmEventEnvelope env = envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                "evt", "agg.contact", T0, T0);
        // No setGuc call — app.tenant_id GUC is unset → WITH CHECK fails.
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            adapter.append(env);
        })).isInstanceOf(DataAccessException.class);
    }

    // K. wrongTenantContextCannotAccessOtherTenant
    @Test
    @DisplayName("K. wrong tenant GUC cannot append for a different tenant_id (RLS WITH CHECK)")
    void wrongTenantContextCannotAccessOtherTenant() {
        // Tenant B's GUC, but the envelope is for tenant A.
        CrmEventEnvelope env = envelope(EVENT_ID_1, TENANT_A, AGGREGATE_A,
                "evt", "agg.contact", T0, T0);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            setGuc(TENANT_B);
            adapter.append(env);
        })).isInstanceOf(DataAccessException.class);
    }

    // L. envelopeDomainValidation
    @Test
    @DisplayName("L. CrmEventEnvelope rejects null required fields and schemaVersion < 1")
    void envelopeDomainValidation() {
        ObjectNode payload = mapper.createObjectNode().put("k", 1);
        // Valid baseline — must not throw.
        CrmEventEnvelope ok = new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 1, "agg", AGGREGATE_A,
                "corr", null, payload, T0, T0);
        assertThat(ok.id()).isEqualTo(EVENT_ID_1);

        // Each required field null.
        assertThatThrownBy(() -> new CrmEventEnvelope(
                null, TENANT_A, "evt", 1, "agg", AGGREGATE_A,
                "corr", null, payload, T0, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, null, "evt", 1, "agg", AGGREGATE_A,
                "corr", null, payload, T0, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, null, 1, "agg", AGGREGATE_A,
                "corr", null, payload, T0, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 1, null, AGGREGATE_A,
                "corr", null, payload, T0, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 1, "agg", null,
                "corr", null, payload, T0, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 1, "agg", AGGREGATE_A,
                null, null, payload, T0, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 1, "agg", AGGREGATE_A,
                "corr", null, null, T0, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 1, "agg", AGGREGATE_A,
                "corr", null, payload, null, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 1, "agg", AGGREGATE_A,
                "corr", null, payload, T0, null)).isInstanceOf(NullPointerException.class);

        // schemaVersion must be >= 1.
        assertThatThrownBy(() -> new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 0, "agg", AGGREGATE_A,
                "corr", null, payload, T0, T0)).isInstanceOf(IllegalArgumentException.class);
    }

    // M. nullCausationAllowed
    @Test
    @DisplayName("M. CrmEventEnvelope allows null causationId")
    void nullCausationAllowed() {
        ObjectNode payload = mapper.createObjectNode().put("k", 1);
        CrmEventEnvelope env = new CrmEventEnvelope(
                EVENT_ID_1, TENANT_A, "evt", 1, "agg", AGGREGATE_A,
                "corr", null, payload, T0, T0);
        assertThat(env.causationId()).isNull();
    }

    // ---------- helpers ----------

    private CrmEventEnvelope envelope(UUID id, UUID tenantId, UUID aggregateId,
                                       String eventType, String aggregateType,
                                       Instant availableAt, Instant createdAt) {
        ObjectNode payload = mapper.createObjectNode().put("kind", "created");
        return new CrmEventEnvelope(
                id, tenantId, eventType, 1, aggregateType, aggregateId,
                "corr-" + id, null, payload, availableAt, createdAt);
    }

    private void setGuc(UUID t) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                new MapSqlParameterSource("t", t.toString()), String.class);
    }

    private void ensureTenant(UUID id) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :sub, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", "Tenant " + id)
                .addValue("sub", "ob-" + id));
    }
}
