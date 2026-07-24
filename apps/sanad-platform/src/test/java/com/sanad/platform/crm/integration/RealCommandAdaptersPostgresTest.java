package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.infrastructure.JdbcActivityRepository;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationCommandPort;
import com.sanad.platform.crm.integration.application.CreateFollowUpActivityCommandAdapter;
import com.sanad.platform.crm.integration.application.RequestOpportunityReviewCommandAdapter;
import com.sanad.platform.crm.integration.application.ScheduleContactCommandAdapter;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.task.application.TaskUseCases;
import com.sanad.platform.crm.task.infrastructure.JdbcTaskRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL integration tests for the three production command adapters.
 * Each test verifies the real persisted CRM artifact and atomic replay behavior.
 */
class RealCommandAdaptersPostgresTest {

    private static PostgreSQLContainer<?> POSTGRES;
    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private static CreateFollowUpActivityCommandAdapter followUpAdapter;
    private static ScheduleContactCommandAdapter scheduleContactAdapter;
    private static RequestOpportunityReviewCommandAdapter reviewAdapter;
    private static final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private UUID actorId;
    private UUID requestId;
    private UUID decisionId;
    private UUID contactId;
    private UUID opportunityId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("RealCommandAdaptersPostgresTest");
        Assumptions.assumeTrue(
                docker,
                "Docker unavailable in local development — skipping in non-CI environment");

        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        store = new CrmIntegrationStore(jdbc, mapper);

        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(ds);
        JdbcActivityRepository activityRepo = new JdbcActivityRepository(namedJdbc);
        TimelineEventPort timeline = (
                tenantId, subjectType, subjectId, eventType, summary,
                sourceType, sourceId, actorId, occurredAt) -> {
            // The adapter assertion is the persisted activity. Timeline behavior
            // is covered independently; this non-null port preserves use-case invariants.
        };
        ActivityUseCases activityUseCases = new ActivityUseCases(activityRepo, timeline);
        JdbcTaskRepository taskRepo = new JdbcTaskRepository(namedJdbc);
        TaskUseCases taskUseCases = new TaskUseCases(taskRepo);

        followUpAdapter = new CreateFollowUpActivityCommandAdapter(activityUseCases, jdbc, store);
        scheduleContactAdapter = new ScheduleContactCommandAdapter(activityUseCases, jdbc, store);
        reviewAdapter = new RequestOpportunityReviewCommandAdapter(taskUseCases, jdbc, store);
    }

    @BeforeEach
    void seedTenantAndEntities() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        decisionId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        opportunityId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Test Tenant', ?, 'ACTIVE', ?, ?)",
                tenantId,
                "test-" + tenantId.toString().substring(0, 8),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        jdbc.update(
                "INSERT INTO crm_integration_requests " +
                        "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                        "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, data_classification, requested_locale, " +
                        "payload, result_payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, ?, ?, 0, " +
                        "'CRM.AI.READ', 'INTERNAL', 'en-US', CAST('{}' AS jsonb), CAST('{}' AS jsonb), " +
                        "'CONFIRMED', ?, ?, ?, ?, 0)",
                requestId, tenantId, actorId, "corr", "caus", "idem", "ACCOUNT", UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    @Test
    void createFollowUpActivityCreatesRealActivity() {
        var rec = recommendation("CREATE_FOLLOW_UP_ACTIVITY", "ACCOUNT", UUID.randomUUID());
        var result = followUpAdapter.execute(rec);

        assertThat(result.success()).isTrue();
        assertThat(result.commandReference()).startsWith("activity:");
        UUID activityId = UUID.fromString(result.commandReference().substring("activity:".length()));
        Map<String, Object> activity = jdbc.queryForMap(
                "SELECT tenant_id, activity_type, subject, related_type, related_id, owner_user_id " +
                        "FROM crm_activities WHERE id = ?",
                activityId);
        assertThat(activity.get("tenant_id")).isEqualTo(tenantId);
        assertThat(activity.get("activity_type")).isEqualTo("TASK");
        assertThat(activity.get("subject").toString()).contains(decisionId.toString());
        assertThat(activity.get("owner_user_id")).isEqualTo(actorId);

        Map<String, Object> artifact = jdbc.queryForMap(
                "SELECT artifact_type, artifact_id FROM crm_integration_command_artifacts " +
                        "WHERE tenant_id = ? AND decision_id = ? AND action_code = ?",
                tenantId, decisionId, "CREATE_FOLLOW_UP_ACTIVITY");
        assertThat(artifact.get("artifact_type")).isEqualTo("ACTIVITY");
        assertThat(artifact.get("artifact_id")).isEqualTo(activityId);
    }

    @Test
    void createFollowUpActivityReplayReturnsSameArtifact() {
        var rec = recommendation("CREATE_FOLLOW_UP_ACTIVITY", "ACCOUNT", UUID.randomUUID());
        var result1 = followUpAdapter.execute(rec);
        var result2 = followUpAdapter.execute(rec);

        assertThat(result1.success()).isTrue();
        assertThat(result2.success()).isTrue();
        assertThat(result2.commandReference()).isEqualTo(result1.commandReference());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? AND subject LIKE ?",
                Integer.class, tenantId, "%" + decisionId + "%")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_command_artifacts " +
                        "WHERE tenant_id = ? AND decision_id = ?",
                Integer.class, tenantId, decisionId)).isEqualTo(1);
    }

    @Test
    void scheduleContactCreatesRealScheduledCall() {
        var result = scheduleContactAdapter.execute(
                recommendation("SCHEDULE_CONTACT", "CONTACT", contactId));

        assertThat(result.success()).isTrue();
        assertThat(result.commandReference()).startsWith("scheduled-activity:");
        UUID activityId = UUID.fromString(
                result.commandReference().substring("scheduled-activity:".length()));
        Map<String, Object> activity = jdbc.queryForMap(
                "SELECT tenant_id, activity_type, related_type, related_id, due_at " +
                        "FROM crm_activities WHERE id = ?",
                activityId);
        assertThat(activity.get("tenant_id")).isEqualTo(tenantId);
        assertThat(activity.get("activity_type")).isEqualTo("CALL");
        assertThat(activity.get("related_type")).isEqualTo("CONTACT");
        assertThat(activity.get("related_id")).isEqualTo(contactId);
        assertThat(activity.get("due_at")).isNotNull();
    }

    @Test
    void scheduleContactReplayReturnsSameArtifact() {
        var rec = recommendation("SCHEDULE_CONTACT", "CONTACT", contactId);
        var result1 = scheduleContactAdapter.execute(rec);
        var result2 = scheduleContactAdapter.execute(rec);

        assertThat(result1.success()).isTrue();
        assertThat(result2.success()).isTrue();
        assertThat(result2.commandReference()).isEqualTo(result1.commandReference());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? " +
                        "AND activity_type = 'CALL' AND subject LIKE ?",
                Integer.class, tenantId, "%" + decisionId + "%")).isEqualTo(1);
    }

    @Test
    void requestOpportunityReviewCreatesRealReviewTask() {
        var result = reviewAdapter.execute(
                recommendation("REQUEST_OPPORTUNITY_REVIEW", "OPPORTUNITY", opportunityId));

        assertThat(result.success()).isTrue();
        assertThat(result.commandReference()).startsWith("review-task:");
        UUID taskId = UUID.fromString(result.commandReference().substring("review-task:".length()));
        Map<String, Object> task = jdbc.queryForMap(
                "SELECT tenant_id, title, related_type, related_id, owner_user_id, status " +
                        "FROM crm_tasks WHERE id = ?",
                taskId);
        assertThat(task.get("tenant_id")).isEqualTo(tenantId);
        assertThat(task.get("title").toString()).contains(decisionId.toString());
        assertThat(task.get("related_type")).isEqualTo("OPPORTUNITY");
        assertThat(task.get("related_id")).isEqualTo(opportunityId);
        assertThat(task.get("owner_user_id")).isEqualTo(actorId);
        assertThat(task.get("status")).isEqualTo("OPEN");
    }

    @Test
    void requestOpportunityReviewReplayReturnsSameArtifact() {
        var rec = recommendation("REQUEST_OPPORTUNITY_REVIEW", "OPPORTUNITY", opportunityId);
        var result1 = reviewAdapter.execute(rec);
        var result2 = reviewAdapter.execute(rec);

        assertThat(result1.success()).isTrue();
        assertThat(result2.success()).isTrue();
        assertThat(result2.commandReference()).isEqualTo(result1.commandReference());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_tasks WHERE tenant_id = ? AND title LIKE ?",
                Integer.class, tenantId, "%" + decisionId + "%")).isEqualTo(1);
    }

    @Test
    void findExistingReturnsEmptyWhenNoArtifact() {
        Optional<ConfirmedRecommendationCommandPort.CommandExecutionResult> existing =
                followUpAdapter.findExisting(
                        tenantId, decisionId, "CREATE_FOLLOW_UP_ACTIVITY");
        assertThat(existing).isEmpty();
    }

    @Test
    void findExistingReturnsResultAfterExecution() {
        var result = followUpAdapter.execute(
                recommendation("CREATE_FOLLOW_UP_ACTIVITY", "ACCOUNT", UUID.randomUUID()));
        Optional<ConfirmedRecommendationCommandPort.CommandExecutionResult> existing =
                followUpAdapter.findExisting(
                        tenantId, decisionId, "CREATE_FOLLOW_UP_ACTIVITY");

        assertThat(result.success()).isTrue();
        assertThat(existing).isPresent();
        assertThat(existing.orElseThrow().commandReference()).isEqualTo(result.commandReference());
    }

    private ConfirmedRecommendationCommandPort.ConfirmedRecommendation recommendation(
            String actionCode,
            String entityType,
            UUID entityId) {
        return new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                tenantId, actorId, requestId, actionCode,
                entityType, entityId, 0, "corr", decisionId);
    }
}
