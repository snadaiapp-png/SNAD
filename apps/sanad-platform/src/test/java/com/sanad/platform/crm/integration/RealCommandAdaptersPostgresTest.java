package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.activity.infrastructure.JdbcActivityRepository;
import com.sanad.platform.crm.integration.application.CreateFollowUpActivityCommandAdapter;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationCommandPort;
import com.sanad.platform.crm.integration.application.RequestOpportunityReviewCommandAdapter;
import com.sanad.platform.crm.integration.application.ScheduleContactCommandAdapter;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.task.application.TaskUseCases;
import com.sanad.platform.crm.task.domain.TaskRepository;
import com.sanad.platform.crm.task.infrastructure.JdbcTaskRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-009 PostgreSQL test: real adapter integration tests.
 *
 * <p>Verifies Items 1, 3, 9: the three real adapters create real CRM
 * artifacts (activities, scheduled calls, review tasks) with atomic
 * idempotency. A replay returns the original artifact, not a duplicate.</p>
 *
 * <p>Uses the REAL adapters (not the stub) by instantiating them directly
 * with JdbcTemplate + CrmIntegrationStore + ActivityUseCases/TaskUseCases.</p>
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
        Assumptions.assumeTrue(docker, "Docker unavailable in local development — skipping in non-CI environment");

        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        store = new CrmIntegrationStore(jdbc, mapper);

        // Wire real adapters with their CRM dependencies
        JdbcActivityRepository activityRepo = new JdbcActivityRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(ds));
        ActivityUseCases activityUseCases = new ActivityUseCases(activityRepo, null);
        JdbcTaskRepository taskRepo = new JdbcTaskRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(ds));
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

        // Seed the integration request only — the adapters create activities/tasks
        // that reference source_entity_id via related_id, but there is no FK
        // constraint on related_id so we don't need to seed the actual CRM entities.
        jdbc.update("INSERT INTO crm_integration_requests " +
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
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation rec =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        tenantId, actorId, requestId, "CREATE_FOLLOW_UP_ACTIVITY",
                        "ACCOUNT", UUID.randomUUID(), 0, "corr", decisionId);

        var result = followUpAdapter.execute(rec);

        assertThat(result.success()).isTrue();
        assertThat(result.commandReference()).startsWith("activity:");

        // Verify the activity exists in the DB
        String activityIdStr = result.commandReference().substring("activity:".length());
        UUID activityId = UUID.fromString(activityIdStr);
        Map<String, Object> activity = jdbc.queryForMap(
                "SELECT tenant_id, activity_type, subject, related_type, related_id, owner_user_id " +
                        "FROM crm_activities WHERE id = ?", activityId);
        assertThat(activity.get("tenant_id")).isEqualTo(tenantId);
        assertThat(activity.get("activity_type")).isEqualTo("FOLLOW_UP");
        assertThat(activity.get("subject").toString()).contains(decisionId.toString());
        assertThat(activity.get("owner_user_id")).isEqualTo(actorId);

        // Verify artifact idempotency row exists
        Map<String, Object> artifact = jdbc.queryForMap(
                "SELECT artifact_type, artifact_id FROM crm_integration_command_artifacts " +
                        "WHERE tenant_id = ? AND decision_id = ? AND action_code = ?",
                tenantId, decisionId, "CREATE_FOLLOW_UP_ACTIVITY");
        assertThat(artifact.get("artifact_type")).isEqualTo("ACTIVITY");
        assertThat(artifact.get("artifact_id")).isEqualTo(activityId);
    }

    @Test
    void createFollowUpActivityReplayReturnsSameArtifact() {
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation rec =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        tenantId, actorId, requestId, "CREATE_FOLLOW_UP_ACTIVITY",
                        "ACCOUNT", UUID.randomUUID(), 0, "corr", decisionId);

        var result1 = followUpAdapter.execute(rec);
        var result2 = followUpAdapter.execute(rec);

        assertThat(result2.success()).isTrue();
        assertThat(result2.commandReference()).isEqualTo(result1.commandReference());

        // Verify only one activity exists
        Integer activityCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? AND subject LIKE ?",
                Integer.class, tenantId, "%" + decisionId + "%");
        assertThat(activityCount).isEqualTo(1);

        // Verify only one artifact row
        Integer artifactCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_command_artifacts WHERE tenant_id = ? AND decision_id = ?",
                Integer.class, tenantId, decisionId);
        assertThat(artifactCount).isEqualTo(1);
    }

    @Test
    void scheduleContactCreatesRealScheduledCall() {
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation rec =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        tenantId, actorId, requestId, "SCHEDULE_CONTACT",
                        "CONTACT", contactId, 0, "corr", decisionId);

        var result = scheduleContactAdapter.execute(rec);

        assertThat(result.success()).isTrue();
        assertThat(result.commandReference()).startsWith("scheduled-activity:");

        // Verify the scheduled-call activity exists
        String activityIdStr = result.commandReference().substring("scheduled-activity:".length());
        UUID activityId = UUID.fromString(activityIdStr);
        Map<String, Object> activity = jdbc.queryForMap(
                "SELECT tenant_id, activity_type, related_type, related_id, due_at " +
                        "FROM crm_activities WHERE id = ?", activityId);
        assertThat(activity.get("tenant_id")).isEqualTo(tenantId);
        assertThat(activity.get("activity_type")).isEqualTo("SCHEDULED_CALL");
        assertThat(activity.get("related_type")).isEqualTo("CONTACT");
        assertThat(activity.get("related_id")).isEqualTo(contactId);
        assertThat(activity.get("due_at")).isNotNull();
    }

    @Test
    void scheduleContactReplayReturnsSameArtifact() {
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation rec =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        tenantId, actorId, requestId, "SCHEDULE_CONTACT",
                        "CONTACT", contactId, 0, "corr", decisionId);

        var result1 = scheduleContactAdapter.execute(rec);
        var result2 = scheduleContactAdapter.execute(rec);

        assertThat(result2.commandReference()).isEqualTo(result1.commandReference());

        Integer activityCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? AND activity_type = 'SCHEDULED_CALL' " +
                        "AND subject LIKE ?",
                Integer.class, tenantId, "%" + decisionId + "%");
        assertThat(activityCount).isEqualTo(1);
    }

    @Test
    void requestOpportunityReviewCreatesRealReviewTask() {
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation rec =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        tenantId, actorId, requestId, "REQUEST_OPPORTUNITY_REVIEW",
                        "OPPORTUNITY", opportunityId, 0, "corr", decisionId);

        var result = reviewAdapter.execute(rec);

        assertThat(result.success()).isTrue();
        assertThat(result.commandReference()).startsWith("review-task:");

        // Verify the review task exists
        String taskIdStr = result.commandReference().substring("review-task:".length());
        UUID taskId = UUID.fromString(taskIdStr);
        Map<String, Object> task = jdbc.queryForMap(
                "SELECT tenant_id, title, related_type, related_id, owner_user_id, status " +
                        "FROM crm_tasks WHERE id = ?", taskId);
        assertThat(task.get("tenant_id")).isEqualTo(tenantId);
        assertThat(task.get("title").toString()).contains(decisionId.toString());
        assertThat(task.get("related_type")).isEqualTo("OPPORTUNITY");
        assertThat(task.get("related_id")).isEqualTo(opportunityId);
        assertThat(task.get("owner_user_id")).isEqualTo(actorId);
        assertThat(task.get("status")).isEqualTo("OPEN");
    }

    @Test
    void requestOpportunityReviewReplayReturnsSameArtifact() {
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation rec =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        tenantId, actorId, requestId, "REQUEST_OPPORTUNITY_REVIEW",
                        "OPPORTUNITY", opportunityId, 0, "corr", decisionId);

        var result1 = reviewAdapter.execute(rec);
        var result2 = reviewAdapter.execute(rec);

        assertThat(result2.commandReference()).isEqualTo(result1.commandReference());

        Integer taskCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_tasks WHERE tenant_id = ? AND title LIKE ?",
                Integer.class, tenantId, "%" + decisionId + "%");
        assertThat(taskCount).isEqualTo(1);
    }

    @Test
    void findExistingReturnsEmptyWhenNoArtifact() {
        Optional<ConfirmedRecommendationCommandPort.CommandExecutionResult> existing =
                followUpAdapter.findExisting(tenantId, decisionId, "CREATE_FOLLOW_UP_ACTIVITY");
        assertThat(existing).isEmpty();
    }

    @Test
    void findExistingReturnsResultAfterExecution() {
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation rec =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        tenantId, actorId, requestId, "CREATE_FOLLOW_UP_ACTIVITY",
                        "ACCOUNT", UUID.randomUUID(), 0, "corr", decisionId);

        var result = followUpAdapter.execute(rec);

        Optional<ConfirmedRecommendationCommandPort.CommandExecutionResult> existing =
                followUpAdapter.findExisting(tenantId, decisionId, "CREATE_FOLLOW_UP_ACTIVITY");
        assertThat(existing).isPresent();
        assertThat(existing.get().commandReference()).isEqualTo(result.commandReference());
    }
}
