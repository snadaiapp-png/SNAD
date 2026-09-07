package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.infrastructure.JdbcTimelineEventAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Task 4 — Structured Timeline Events backward-compatible SAM extension.
 *
 * <p>PostgreSQL Direct test that verifies the {@link TimelineEventPort}
 * remains a functional interface (lambda-compatible) while adding a
 * structured default method that persists summary_key, metadata_json,
 * correlation_id, causation_id, and schema_version to crm_timeline_events.
 *
 * <p>The legacy {@code record(...)} signature is preserved byte-for-byte
 * so existing callers (lambdas, method references, anonymous classes)
 * continue to compile and write legacy rows.
 *
 * <p>Uses the Crm009TestEnvironment PostgreSQL-Direct gate.
 */
@DisplayName("Task 4 — Structured Timeline Events (PostgreSQL Direct)")
class JdbcStructuredTimelineEventPostgresTest {

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcTimelineEventAdapter adapter;
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final UUID TENANT_A = UUID.fromString("11111111-0000-4000-8000-00000000a001");
    private static final UUID TENANT_B = UUID.fromString("22222222-0000-4000-8000-00000000b001");
    private static final UUID ACTOR = UUID.fromString("33333333-0000-4000-8000-00000000c001");
    private static final UUID ACTOR_B = UUID.fromString("44444444-0000-4000-8000-00000000c002");

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "JdbcStructuredTimelineEventPostgresTest");
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
        adapter = new JdbcTimelineEventAdapter(jdbc, mapper);
    }

    @BeforeEach
    void seed() {
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            jdbc.update("DELETE FROM crm_timeline_events WHERE tenant_id IN (:a,:b)",
                    new MapSqlParameterSource()
                            .addValue("a", TENANT_A)
                            .addValue("b", TENANT_B));
            jdbc.update("DELETE FROM crm_contacts WHERE tenant_id IN (:a,:b)",
                    new MapSqlParameterSource()
                            .addValue("a", TENANT_A)
                            .addValue("b", TENANT_B));
        });
        jdbc.update("DELETE FROM users WHERE tenant_id IN (:a,:b)",
                new MapSqlParameterSource()
                        .addValue("a", TENANT_A)
                        .addValue("b", TENANT_B));
        jdbc.update("DELETE FROM tenants WHERE id IN (:a,:b)",
                new MapSqlParameterSource()
                        .addValue("a", TENANT_A)
                        .addValue("b", TENANT_B));
        ensureTenant(TENANT_A);
        ensureTenant(TENANT_B);
        ensureUser(ACTOR, TENANT_A);
        ensureUser(ACTOR_B, TENANT_B);
    }

    @Test
    @DisplayName("TimelineEventPort remains a functional interface (lambda-compatible)")
    void timelinePortRemainsLambdaCompatible() {
        // The 9-arg legacy record(...) method is the single abstract method.
        // A lambda assignment must compile and execute with no upcast.
        TimelineEventPort port =
                (tenant, type, id, event, summary,
                 source, sourceId, actor, at) -> { };
        assertThat(port).isNotNull();
        // Sanity: the SAM must accept the full 9-arg call shape.
        port.record(TENANT_A, "CONTACT", UUID.randomUUID(),
                "crm.contact.created", "ignored",
                "CRM_CONTACT", UUID.randomUUID(),
                ACTOR, Instant.now());
    }

    @Test
    @DisplayName("StructuredTimelineEvent validates required fields")
    void structuredEventValidatesRequiredFields() {
        UUID tenantId = TENANT_A;
        UUID subjectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Instant at = Instant.parse("2026-08-22T10:00:00Z");
        ObjectNode metadata = mapper.createObjectNode().put("k", "v");

        // Baseline valid record — must not throw.
        TimelineEventPort.StructuredTimelineEvent ok =
                new TimelineEventPort.StructuredTimelineEvent(
                        tenantId, "CONTACT", subjectId,
                        "crm.contact.created", "summary.key", "summary",
                        "CRM_CONTACT", sourceId, ACTOR, at,
                        "corr-1", null, 1, metadata);
        assertThat(ok.eventType()).isEqualTo("crm.contact.created");
        assertThat(ok.correlationId()).isEqualTo("corr-1");
        assertThat(ok.schemaVersion()).isEqualTo(1);

        // Each required field rejection — one assertion per null/invalid.
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                null, "CONTACT", subjectId,
                "evt", null, "summary",
                "CRM_CONTACT", sourceId, ACTOR, at,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, null, subjectId,
                "evt", null, "summary",
                "CRM_CONTACT", sourceId, ACTOR, at,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", null,
                "evt", null, "summary",
                "CRM_CONTACT", sourceId, ACTOR, at,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", subjectId,
                null, null, "summary",
                "CRM_CONTACT", sourceId, ACTOR, at,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", subjectId,
                "evt", null, null,
                "CRM_CONTACT", sourceId, ACTOR, at,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", subjectId,
                "evt", null, "summary",
                null, sourceId, ACTOR, at,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", subjectId,
                "evt", null, "summary",
                "CRM_CONTACT", null, ACTOR, at,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", subjectId,
                "evt", null, "summary",
                "CRM_CONTACT", sourceId, null, at,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", subjectId,
                "evt", null, "summary",
                "CRM_CONTACT", sourceId, ACTOR, null,
                "corr", null, 1, metadata))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", subjectId,
                "evt", null, "summary",
                "CRM_CONTACT", sourceId, ACTOR, at,
                null, null, 1, metadata))
                .isInstanceOf(NullPointerException.class);

        // schemaVersion must be >= 1
        assertThatThrownBy(() -> new TimelineEventPort.StructuredTimelineEvent(
                tenantId, "CONTACT", subjectId,
                "evt", null, "summary",
                "CRM_CONTACT", sourceId, ACTOR, at,
                "corr", null, 0, metadata))
                .isInstanceOf(IllegalArgumentException.class);

        // causationId may be null — not rejected
        TimelineEventPort.StructuredTimelineEvent withNullCausation =
                new TimelineEventPort.StructuredTimelineEvent(
                        tenantId, "CONTACT", subjectId,
                        "evt", null, "summary",
                        "CRM_CONTACT", sourceId, ACTOR, at,
                        "corr", null, 1, metadata);
        assertThat(withNullCausation.causationId()).isNull();

        // metadata may be null — not rejected
        TimelineEventPort.StructuredTimelineEvent withNullMetadata =
                new TimelineEventPort.StructuredTimelineEvent(
                        tenantId, "CONTACT", subjectId,
                        "evt", null, "summary",
                        "CRM_CONTACT", sourceId, ACTOR, at,
                        "corr", null, 1, null);
        assertThat(withNullMetadata.metadata()).isNull();
    }

    @Test
    @DisplayName("Default record(StructuredTimelineEvent) delegates legacy-compatible fields to abstract method")
    void defaultStructuredFallbackCallsLegacyMethod() {
        // A custom port that captures only the legacy 9-arg SAM call must
        // receive exactly the fields that map from StructuredTimelineEvent:
        // tenantId, subjectType, subjectId, eventType, summary,
        // sourceType, sourceId, actorId, occurredAt. summaryKey / metadata /
        // correlationId / causationId / schemaVersion are dropped.
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> capturedSummary = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> capturedEventType = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Instant> capturedAt = new java.util.concurrent.atomic.AtomicReference<>();

        TimelineEventPort port = new TimelineEventPort() {
            @Override
            public void record(UUID tenantId, String subjectType, UUID subjectId,
                               String eventType, String summary, String sourceType, UUID sourceId,
                               UUID actorId, Instant occurredAt) {
                calls.incrementAndGet();
                capturedSummary.set(summary);
                capturedEventType.set(eventType);
                capturedAt.set(occurredAt);
            }
        };

        Instant at = Instant.parse("2026-08-22T11:30:00Z");
        UUID subjectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ObjectNode metadata = mapper.createObjectNode().put("alpha", 1);
        TimelineEventPort.StructuredTimelineEvent ev =
                new TimelineEventPort.StructuredTimelineEvent(
                        TENANT_A, "CONTACT", subjectId,
                        "crm.contact.created", "summary.key", "structured summary",
                        "CRM_CONTACT", sourceId, ACTOR, at,
                        "corr-2", "causation-2", 3, metadata);

        port.record(ev);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(capturedSummary.get()).isEqualTo("structured summary");
        assertThat(capturedEventType.get()).isEqualTo("crm.contact.created");
        assertThat(capturedAt.get()).isEqualTo(at);
    }

    @Test
    @DisplayName("JdbcTimelineEventAdapter persists structured metadata + correlation + causation + schemaVersion")
    void jdbcAdapterPersistsStructuredMetadataAndCorrelation() {
        UUID subjectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Instant at = Instant.parse("2026-08-22T12:00:00Z");
        ObjectNode metadata = mapper.createObjectNode()
                .put("userId", "u-1")
                .put("count", 42);
        TimelineEventPort.StructuredTimelineEvent ev =
                new TimelineEventPort.StructuredTimelineEvent(
                        TENANT_A, "CONTACT", subjectId,
                        "crm.contact.created", "summary.key.alpha", "alpha summary",
                        "CRM_CONTACT", sourceId, ACTOR, at,
                        "corr-3", "causation-3", 2, metadata);

        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.record(ev);
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT summary_key, metadata_json, correlation_id, causation_id, schema_version " +
                            "FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :s",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("s", subjectId));
            assertThat(row.get("summary_key")).isEqualTo("summary.key.alpha");
            assertThat(row.get("correlation_id")).isEqualTo("corr-3");
            assertThat(row.get("causation_id")).isEqualTo("causation-3");
            assertThat(((Number) row.get("schema_version")).intValue()).isEqualTo(2);
            String storedMetadata = (String) row.get("metadata_json");
            assertThat(storedMetadata).isNotNull();
            try {
                JsonNode parsed = mapper.readTree(storedMetadata);
                assertThat(parsed.path("userId").asText()).isEqualTo("u-1");
                assertThat(parsed.path("count").asInt()).isEqualTo(42);
            } catch (Exception e) {
                throw new AssertionError("metadata_json must be valid JSON: " + storedMetadata, e);
            }
        });
    }

    @Test
    @DisplayName("Structured event with null metadata persists SQL NULL metadata_json")
    void structuredNullMetadataPersistsSqlNull() {
        UUID subjectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Instant at = Instant.parse("2026-08-22T12:30:00Z");
        TimelineEventPort.StructuredTimelineEvent ev =
                new TimelineEventPort.StructuredTimelineEvent(
                        TENANT_A, "CONTACT", subjectId,
                        "crm.contact.created", null, "null-meta summary",
                        "CRM_CONTACT", sourceId, ACTOR, at,
                        "corr-4", null, 1, null);

        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.record(ev);
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT metadata_json, summary_key, causation_id, schema_version " +
                            "FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :s",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("s", subjectId));
            assertThat(row.get("metadata_json")).isNull();
            assertThat(row.get("summary_key")).isNull();
            assertThat(row.get("causation_id")).isNull();
            assertThat(((Number) row.get("schema_version")).intValue()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Legacy record(...) persists a row with NO structured columns populated (legacy-compatible)")
    void legacyRecordStillPersistsLegacyRow() {
        UUID subjectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Instant at = Instant.parse("2026-08-22T13:00:00Z");

        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            adapter.record(TENANT_A, "CONTACT", subjectId,
                    "crm.contact.created", "legacy summary",
                    "CRM_CONTACT", sourceId, ACTOR, at);
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT summary, summary_key, metadata_json, correlation_id, causation_id, schema_version " +
                            "FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :s",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("s", subjectId));
            assertThat(row.get("summary")).isEqualTo("legacy summary");
            assertThat(row.get("summary_key")).isNull();
            assertThat(row.get("metadata_json")).isNull();
            assertThat(row.get("correlation_id")).isNull();
            assertThat(row.get("causation_id")).isNull();
            // The legacy INSERT must not explicitly set schema_version — the column
            // has NOT NULL DEFAULT 1 from V20260822_1.
            assertThat(((Number) row.get("schema_version")).intValue()).isEqualTo(1);
        });
    }

    // ---------- helpers ----------

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
                .addValue("sub", "t-" + id));
    }

    private void ensureUser(UUID id, UUID t) {
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :t, :email, :name, 'ACTIVE', 'dummy', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("t", t)
                .addValue("email", "tl-" + id + "@snad.test")
                .addValue("name", "Timeline User"));
    }
}
