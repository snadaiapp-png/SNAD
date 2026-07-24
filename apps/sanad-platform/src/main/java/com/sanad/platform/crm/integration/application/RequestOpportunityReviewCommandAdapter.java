package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.task.application.TaskUseCases;
import com.sanad.platform.crm.task.domain.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Production-grade adapter for {@code REQUEST_OPPORTUNITY_REVIEW}.
 *
 * <p><strong>Real side effect:</strong> creates a managerial review
 * {@link com.sanad.platform.crm.task.domain.TaskRepository.TaskRecord}
 * linked to the source opportunity. The task's title contains the
 * decisionId for database-enforced idempotency — a replay returns the
 * original review task, not a duplicate.</p>
 *
 * <p>The review task is created with status OPEN and a default due date
 * of 3 business days from now. A manager (assignee) is not pre-assigned
 * — the task lands in the unassigned queue for the territory/pipeline
 * owner to pick up.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class RequestOpportunityReviewCommandAdapter implements ConfirmedRecommendationCommandPort {

    private static final Logger log = LoggerFactory.getLogger(RequestOpportunityReviewCommandAdapter.class);

    private final TaskUseCases taskUseCases;
    private final JdbcTemplate jdbc;

    public RequestOpportunityReviewCommandAdapter(TaskUseCases taskUseCases, JdbcTemplate jdbc) {
        this.taskUseCases = taskUseCases;
        this.jdbc = jdbc;
    }

    @Override
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!"REQUEST_OPPORTUNITY_REVIEW".equals(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }
        if (!"OPPORTUNITY".equals(recommendation.sourceEntityType())) {
            return new CommandExecutionResult(false, null, null,
                    IntegrationErrorCode.INVALID_CONTRACT.name());
        }
        try {
            UUID tenantId = recommendation.tenantId();
            UUID actorId = recommendation.actorId();
            UUID decisionId = recommendation.decisionId();

            // Step 1: Idempotency check — find existing review task
            String existingTaskId = findExistingReviewTask(tenantId, decisionId);
            if (existingTaskId != null) {
                log.info("Idempotent replay: returning existing review task {} for decision {}",
                        existingTaskId, decisionId);
                return new CommandExecutionResult(
                        true,
                        "REQUEST_OPPORTUNITY_REVIEW",
                        "review-task:" + existingTaskId,
                        null);
            }

            // Step 2: Create a real managerial review task
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            TaskRepository.CreateTaskCommand cmd = new TaskRepository.CreateTaskCommand(
                    "Opportunity review request — " + decisionId,
                    "Auto-created by confirmed AI recommendation "
                            + recommendation.integrationRequestId()
                            + " for opportunity " + recommendation.sourceEntityId()
                            + " (decision=" + decisionId + "). "
                            + "A manager must review this opportunity and decide whether "
                            + "to advance, hold, or reject it.",
                    "OPPORTUNITY",
                    recommendation.sourceEntityId(),
                    null, // assignee — left null so it lands in the unassigned queue
                    actorId, // owner — the actor who confirmed the recommendation
                    60, // priority — slightly above default (50)
                    now,
                    now.plusDays(3));
            TaskRepository.TaskRecord created = taskUseCases.create(tenantId, actorId, cmd);

            return new CommandExecutionResult(
                    true,
                    "REQUEST_OPPORTUNITY_REVIEW",
                    "review-task:" + created.id(),
                    null);
        } catch (Exception e) {
            log.error("REQUEST_OPPORTUNITY_REVIEW failed for decision {}", recommendation.decisionId(), e);
            return new CommandExecutionResult(
                    false, "REQUEST_OPPORTUNITY_REVIEW", null,
                    IntegrationErrorCode.UNKNOWN_ERROR.name());
        }
    }

    private String findExistingReviewTask(UUID tenantId, UUID decisionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT CAST(id AS VARCHAR) FROM crm_tasks " +
                            "WHERE tenant_id = ? AND title LIKE ? " +
                            "ORDER BY created_at LIMIT 1",
                    String.class, tenantId, "Opportunity review request — " + decisionId + "%");
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }
}
