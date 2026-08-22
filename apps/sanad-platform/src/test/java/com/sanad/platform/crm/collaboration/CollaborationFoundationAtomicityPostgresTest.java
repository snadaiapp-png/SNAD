package com.sanad.platform.crm.collaboration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.AddParticipantCommand;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.EligibilityPolicy;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.infrastructure.JdbcEntityParticipantRepository;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.CorrelationContextPort;
import com.sanad.platform.crm.integration.domain.CrmEventOutboxPort;
import com.sanad.platform.crm.integration.domain.CrmEventOutboxPort.CrmEventEnvelope;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.infrastructure.JdbcAuditAdapter;
import com.sanad.platform.crm.integration.infrastructure.JdbcCrmEventOutboxAdapter;
import com.sanad.platform.crm.integration.infrastructure.JdbcTimelineEventAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Task 8 — Collaboration foundation atomicity proof (PostgreSQL Direct).
 *
 * <p>Proves that the four persistence adapters
 * ({@link JdbcEntityParticipantRepository},
 *  {@link JdbcTimelineEventAdapter},
 *  {@link JdbcAuditAdapter} via {@link PlatformAuditWriter},
 *  {@link JdbcCrmEventOutboxAdapter})
 * participate in ONE Spring-managed transaction driven by a single
 * {@link TransactionTemplate} over a shared {@code DataSource}.
 *
 * <p>Two independent scenarios:
 * <ol>
 *   <li><b>SUCCESS</b> — every adapter writes a row in one transaction;
 *       on commit, all four rows are visible in a fresh tenant-scoped
 *       verification transaction, with the correlation_id propagated
 *       from the {@link CorrelationContextPort} into the audit row.</li>
 *   <li><b>ROLLBACK</b> — same composition but the outbox adapter is
 *       replaced by a failpoint that throws {@code IllegalStateException}.
 *       The exception propagates out of the transaction; the
 *       transaction rolls back; all four rows (participant, timeline,
 *       audit, outbox) are ABSENT in the verification transaction.</li>
 * </ol>
 *
 * <p>The atomicity test uses a TEST-LOCAL {@link RecipientEligibilityPort}
 * that always returns {@code ELIGIBLE} — Task 6 eligibility behaviour is
 * already independently covered by {@code PlatformRecipientEligibilityAdapterTest}.
 *
 * <p>Anti-false-positive rule: every fixture ID is unique per scenario
 * (taskId / eventId / correlationId / requestId), so assertions can never
 * accidentally match a row from the other scenario.
 */
@DisplayName("Task 8 — Collaboration foundation atomicity (PostgreSQL Direct)")
class CollaborationFoundationAtomicityPostgresTest {

    private static NamedParameterJdbcTemplate namedJdbc;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static final ObjectMapper mapper = new ObjectMapper();

    private static EntityParticipantRepository participants;
    private static JdbcTimelineEventAdapter timeline;
    private static JdbcAuditAdapter audit;
    private static JdbcCrmEventOutboxAdapter outbox;
    private static CollaborationMembershipService membership;

    private static RecipientEligibilityPort alwaysEligible =
            (tenantId, userId, organizationId, capability) ->
                    new RecipientEligibilityPort.EligibilityDecision(true, "ELIGIBLE");

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "CollaborationFoundationAtomicityPostgresTest");
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
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
        var ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new JdbcTemplate(ds);
        namedJdbc = new NamedParameterJdbcTemplate(ds);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        participants = new JdbcEntityParticipantRepository(namedJdbc);
        timeline = new JdbcTimelineEventAdapter(namedJdbc, mapper);
        outbox = new JdbcCrmEventOutboxAdapter(namedJdbc, mapper);
        // AuditAdapter depends on PlatformAuditWriter + CorrelationContextPort.
        // Use a single test correlation that the test sets per scenario via a
        // mutable CorrelationContextPort.
        PlatformAuditWriter auditWriter = new PlatformAuditWriter(jdbc, mapper);
        audit = new JdbcAuditAdapter(auditWriter, mutableCorrelation);
        membership = new CollaborationMembershipService(participants, alwaysEligible);
    }

    /** Mutable correlation so each scenario can set its own correlationId. */
    private static final CorrelationContextPort mutableCorrelation = new CorrelationContextPort() {
        private volatile String current = UUID.randomUUID().toString();
        @Override
        public String currentCorrelationId() {
            return current;
        }
    };

    /** Set the correlation id used by the audit adapter for the next writes. */
    private void setCorrelation(String id) {
        try {
            java.lang.reflect.Field f = mutableCorrelation.getClass().getDeclaredField("current");
            f.setAccessible(true);
            f.set(mutableCorrelation, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===========================================================
    //  SUCCESS — commit
    // ===========================================================

    @Test
    @DisplayName("SUCCESS: participant + timeline + audit + outbox committed in one transaction")
    void successTransactionCommitsAllFourAdapters() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String correlationId = "succ-corr-" + UUID.randomUUID();
        Instant now = Instant.parse("2026-08-22T14:00:00Z");

        seedTenantAndUsers(tenantId, actorId, recipientUserId);
        seedTask(tenantId, taskId, actorId);

        transactions.executeWithoutResult(s -> {
            setGuc(tenantId);
            setCorrelation(correlationId);

            EntityParticipant participant = membership.addParticipant(
                    new AddParticipantCommand(
                            tenantId, CollaborationEntityType.TASK, taskId,
                            recipientUserId, ParticipantRole.COLLABORATOR,
                            actorId, now),
                    new EligibilityPolicy(null, "CRM.TASK.READ"));

            timeline.record(new TimelineEventPort.StructuredTimelineEvent(
                    tenantId, "TASK", taskId,
                    "TASK_COLLABORATOR_ADDED",
                    "crm.task.collaborator_added",
                    "CRM participant added",
                    "COLLABORATION_PARTICIPANT",
                    participant.id(),
                    actorId, now,
                    correlationId,
                    requestId.toString(),
                    1,
                    mapper.createObjectNode()
                            .put("participantUserId", participant.userId().toString())));

            audit.record(tenantId, actorId, "ADD_COLLABORATOR", "TASK", taskId,
                    new AuditPort.AuditChange(null,
                            mapper.valueToTree(Map.of(
                                    "participantId", participant.id().toString(),
                                    "userId", participant.userId().toString()))),
                    now);

            outbox.append(new CrmEventEnvelope(
                    eventId, tenantId, "TASK_COLLABORATOR_ADDED", 1,
                    "TASK", taskId,
                    correlationId,
                    requestId.toString(),
                    mapper.createObjectNode()
                            .put("participantId", participant.id().toString())
                            .put("participantUserId", participant.userId().toString()),
                    now, now));
        });

        // Verification in fresh tenant-scoped transaction.
        Map<String, Object> participantRow = transactions.execute(s -> {
            setGuc(tenantId);
            return namedJdbc.queryForMap(
                    "SELECT tenant_id, entity_type, entity_id, user_id, role " +
                            "FROM crm_entity_participants WHERE tenant_id = :t AND entity_id = :e AND user_id = :u",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("e", taskId)
                            .addValue("u", recipientUserId));
        });
        assertThat(participantRow.get("tenant_id")).isEqualTo(tenantId);
        assertThat(participantRow.get("entity_type")).isEqualTo("TASK");
        assertThat(participantRow.get("entity_id")).isEqualTo(taskId);
        assertThat(participantRow.get("user_id")).isEqualTo(recipientUserId);
        assertThat(participantRow.get("role")).isEqualTo("COLLABORATOR");

        Map<String, Object> timelineRow = transactions.execute(s -> {
            setGuc(tenantId);
            return namedJdbc.queryForMap(
                    "SELECT subject_type, subject_id, event_type, summary_key, " +
                            "correlation_id, causation_id, schema_version " +
                            "FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :e AND event_type = :evt",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("e", taskId)
                            .addValue("evt", "TASK_COLLABORATOR_ADDED"));
        });
        assertThat(timelineRow.get("subject_type")).isEqualTo("TASK");
        assertThat(timelineRow.get("subject_id")).isEqualTo(taskId);
        assertThat(timelineRow.get("event_type")).isEqualTo("TASK_COLLABORATOR_ADDED");
        assertThat(timelineRow.get("summary_key")).isEqualTo("crm.task.collaborator_added");
        assertThat(timelineRow.get("correlation_id")).isEqualTo(correlationId);
        assertThat(timelineRow.get("causation_id")).isEqualTo(requestId.toString());
        assertThat(((Number) timelineRow.get("schema_version")).intValue()).isEqualTo(1);

        Map<String, Object> outboxRow = transactions.execute(s -> {
            setGuc(tenantId);
            return namedJdbc.queryForMap(
                    "SELECT event_type, aggregate_type, aggregate_id, correlation_id, causation_id, " +
                            "status, attempt_count, payload_json " +
                            "FROM crm_event_outbox WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("id", eventId));
        });
        assertThat(outboxRow.get("event_type")).isEqualTo("TASK_COLLABORATOR_ADDED");
        assertThat(outboxRow.get("aggregate_type")).isEqualTo("TASK");
        assertThat(outboxRow.get("aggregate_id")).isEqualTo(taskId);
        assertThat(outboxRow.get("correlation_id")).isEqualTo(correlationId);
        assertThat(outboxRow.get("causation_id")).isEqualTo(requestId.toString());
        assertThat(outboxRow.get("status")).isEqualTo("PENDING");
        assertThat(((Number) outboxRow.get("attempt_count")).intValue()).isZero();

        // Audit row verification — platform_audit_logs is NOT FORCE RLS, so
        // no GUC needed; but the row must still be uniquely identifiable by
        // the scenario's correlation_id / taskId / action.
        Map<String, Object> auditRow = namedJdbc.queryForMap(
                "SELECT action, resource_type, resource_id, actor_tenant_id, actor_user_id, " +
                        "target_tenant_id, result, correlation_id " +
                        "FROM platform_audit_logs WHERE correlation_id = :c AND resource_id = :r",
                new MapSqlParameterSource()
                        .addValue("c", correlationId)
                        .addValue("r", taskId.toString()));
        assertThat(auditRow.get("action")).isEqualTo("ADD_COLLABORATOR");
        assertThat(auditRow.get("resource_type")).isEqualTo("TASK");
        assertThat(auditRow.get("resource_id")).isEqualTo(taskId.toString());
        assertThat(auditRow.get("actor_tenant_id")).isEqualTo(tenantId);
        assertThat(auditRow.get("actor_user_id")).isEqualTo(actorId);
        assertThat(auditRow.get("target_tenant_id")).isEqualTo(tenantId);
        assertThat(auditRow.get("result")).isEqualTo("SUCCESS");
        assertThat(auditRow.get("correlation_id")).isEqualTo(correlationId);
    }

    // ===========================================================
    //  ROLLBACK — failpoint outbox throws IllegalStateException
    // ===========================================================

    @Test
    @DisplayName("ROLLBACK: outbox failpoint rolls back participant + timeline + audit + outbox")
    void rollbackTransactionAbortsAllFourAdapters() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String correlationId = "fail-corr-" + UUID.randomUUID();
        Instant now = Instant.parse("2026-08-22T14:30:00Z");

        seedTenantAndUsers(tenantId, actorId, recipientUserId);
        seedTask(tenantId, taskId, actorId);

        // Local failpoint outbox — only append() is exercised.
        CrmEventOutboxPort failingOutbox = new CrmEventOutboxPort() {
            @Override
            public void append(CrmEventEnvelope event) {
                throw new IllegalStateException("OUTBOX_FAILPOINT");
            }
            @Override
            public List<CrmEventEnvelope> claimDue(UUID tenantId, Instant now, int limit) {
                throw new UnsupportedOperationException();
            }
            @Override
            public boolean markPublished(UUID tenantId, UUID eventId, Instant publishedAt) {
                throw new UnsupportedOperationException();
            }
            @Override
            public boolean markFailed(UUID tenantId, UUID eventId, Instant nextAttemptAt, String error) {
                throw new UnsupportedOperationException();
            }
        };

        // Execute the transaction — the exception must propagate.
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            setGuc(tenantId);
            setCorrelation(correlationId);

            EntityParticipant participant = membership.addParticipant(
                    new AddParticipantCommand(
                            tenantId, CollaborationEntityType.TASK, taskId,
                            recipientUserId, ParticipantRole.COLLABORATOR,
                            actorId, now),
                    new EligibilityPolicy(null, "CRM.TASK.READ"));

            timeline.record(new TimelineEventPort.StructuredTimelineEvent(
                    tenantId, "TASK", taskId,
                    "TASK_COLLABORATOR_ADDED",
                    "crm.task.collaborator_added",
                    "CRM participant added (rollback scenario)",
                    "COLLABORATION_PARTICIPANT",
                    participant.id(),
                    actorId, now,
                    correlationId,
                    requestId.toString(),
                    1,
                    mapper.createObjectNode()
                            .put("participantUserId", participant.userId().toString())));

            audit.record(tenantId, actorId, "ADD_COLLABORATOR", "TASK", taskId,
                    new AuditPort.AuditChange(null,
                            mapper.valueToTree(Map.of(
                                    "participantId", participant.id().toString(),
                                    "userId", participant.userId().toString()))),
                    now);

            failingOutbox.append(new CrmEventEnvelope(
                    eventId, tenantId, "TASK_COLLABORATOR_ADDED", 1,
                    "TASK", taskId,
                    correlationId,
                    requestId.toString(),
                    mapper.createObjectNode()
                            .put("participantId", participant.id().toString())
                            .put("participantUserId", participant.userId().toString()),
                    now, now));
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OUTBOX_FAILPOINT");

        // Verification in fresh tenant-scoped transaction.
        Integer participantCount = transactions.execute(s -> {
            setGuc(tenantId);
            return namedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_entity_participants " +
                            "WHERE tenant_id = :t AND entity_id = :e AND user_id = :u",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("e", taskId)
                            .addValue("u", recipientUserId),
                    Integer.class);
        });
        assertThat(participantCount).as("participant must be ABSENT after rollback").isZero();

        Integer timelineCount = transactions.execute(s -> {
            setGuc(tenantId);
            return namedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_timeline_events " +
                            "WHERE tenant_id = :t AND subject_id = :e AND correlation_id = :c",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("e", taskId)
                            .addValue("c", correlationId),
                    Integer.class);
        });
        assertThat(timelineCount).as("timeline must be ABSENT after rollback").isZero();

        Integer outboxCount = transactions.execute(s -> {
            setGuc(tenantId);
            return namedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_event_outbox " +
                            "WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("id", eventId),
                    Integer.class);
        });
        assertThat(outboxCount).as("outbox must be ABSENT after rollback").isZero();

        // Audit — no GUC needed because platform_audit_logs is not FORCE RLS.
        Integer auditCount = namedJdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_logs " +
                        "WHERE correlation_id = :c AND resource_id = :r",
                new MapSqlParameterSource()
                        .addValue("c", correlationId)
                        .addValue("r", taskId.toString()),
                Integer.class);
        assertThat(auditCount).as("audit must be ABSENT after rollback").isZero();
    }

    // ---------- helpers ----------

    private void setGuc(UUID t) {
        namedJdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                new MapSqlParameterSource("t", t.toString()), String.class);
    }

    private void seedTenantAndUsers(UUID tenantId, UUID actorId, UUID recipientId) {
        jdbc.update("DELETE FROM platform_audit_logs WHERE correlation_id LIKE 'succ-corr-%' OR correlation_id LIKE 'fail-corr-%'");
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, tenantId, "Tenant " + tenantId, "atom-" + tenantId);
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'x', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, actorId, tenantId, "actor-" + actorId + "@snad.test", "Actor");
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'x', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, recipientId, tenantId, "rec-" + recipientId + "@snad.test", "Recipient");
    }

    private void seedTask(UUID tenantId, UUID taskId, UUID actorId) {
        transactions.executeWithoutResult(s -> {
            setGuc(tenantId);
            namedJdbc.update("""
                    INSERT INTO crm_tasks (id, tenant_id, version, title, status, priority,
                        created_by, updated_by, created_at, updated_at)
                    VALUES (:id, :t, 0, 'atomicity task', 'OPEN', 50,
                        :actor, :actor, NOW(), NOW())
                    ON CONFLICT (id) DO NOTHING
                    """, new MapSqlParameterSource()
                    .addValue("id", taskId)
                    .addValue("t", tenantId)
                    .addValue("actor", actorId));
        });
    }

    @BeforeEach
    void cleanup() {
        // Defensive — clear any leftover rows from prior runs.
        try {
            jdbc.update("DELETE FROM platform_audit_logs WHERE correlation_id LIKE 'succ-corr-%' OR correlation_id LIKE 'fail-corr-%'");
        } catch (Exception ignored) { }
    }
}
