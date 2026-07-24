package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.task.application.TaskUseCases;
import com.sanad.platform.crm.task.domain.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Production-grade adapter for {@code REQUEST_OPPORTUNITY_REVIEW} with
 * atomic artifact idempotency.
 *
 * <p>Creates a managerial review task linked to the source opportunity.
 * Uses {@code crm_integration_command_artifacts} to enforce exactly-once
 * creation — a replay returns the original review task, not a duplicate.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class RequestOpportunityReviewCommandAdapter implements ConfirmedRecommendationCommandPort {

    private static final Logger log = LoggerFactory.getLogger(RequestOpportunityReviewCommandAdapter.class);
    private static final String ACTION_CODE = "REQUEST_OPPORTUNITY_REVIEW";
    private static final String ARTIFACT_TYPE = "REVIEW_TASK";

    private final TaskUseCases taskUseCases;
    private final JdbcTemplate jdbc;
    private final CrmIntegrationStore store;

    public RequestOpportunityReviewCommandAdapter(TaskUseCases taskUseCases,
                                                     JdbcTemplate jdbc,
                                                     CrmIntegrationStore store) {
        this.taskUseCases = taskUseCases;
        this.jdbc = jdbc;
        this.store = store;
    }

    @Override
    @Transactional
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!ACTION_CODE.equals(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }
        if (!"OPPORTUNITY".equals(recommendation.sourceEntityType())) {
            return new CommandExecutionResult(false, null, null,
                    IntegrationErrorCode.INVALID_CONTRACT.name());
        }
        try {
            UUID tenantId = recommendation.tenantId();
            UUID decisionId = recommendation.decisionId();

            // Step 1: Atomic reservation
            CrmIntegrationStore.ArtifactReservation reservation = store.reserveOrGetArtifact(
                    tenantId, decisionId, ACTION_CODE, ARTIFACT_TYPE);

            if (!reservation.created() && reservation.artifact().artifactId() != null) {
                UUID originalTaskId = reservation.artifact().artifactId();
                log.info("Idempotent replay: returning existing review task {} for decision {}",
                        originalTaskId, decisionId);
                return new CommandExecutionResult(
                        true, ACTION_CODE, "review-task:" + originalTaskId, null);
            }

            // Step 2: Create the review task
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
                    null,
                    recommendation.actorId(),
                    60,
                    now,
                    now.plusDays(3));
            TaskRepository.TaskRecord created = taskUseCases.create(
                    tenantId, recommendation.actorId(), cmd);

            // Step 3: Persist the artifact_id
            store.persistArtifactId(tenantId, decisionId, ACTION_CODE, created.id());

            return new CommandExecutionResult(
                    true, ACTION_CODE, "review-task:" + created.id(), null);
        } catch (Exception e) {
            log.error("REQUEST_OPPORTUNITY_REVIEW failed for decision {}", recommendation.decisionId(), e);
            return new CommandExecutionResult(
                    false, ACTION_CODE, null,
                    IntegrationErrorCode.UNKNOWN_ERROR.name());
        }
    }

    @Override
    public Optional<CommandExecutionResult> findExisting(UUID tenantId, UUID decisionId, String actionCode) {
        if (!ACTION_CODE.equals(actionCode)) return Optional.empty();
        return store.findArtifact(tenantId, decisionId, actionCode)
                .filter(a -> a.artifactId() != null)
                .map(a -> new CommandExecutionResult(
                        true, ACTION_CODE, "review-task:" + a.artifactId(), null));
    }
}
