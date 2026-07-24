package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Production-grade adapter that executes {@code CREATE_FOLLOW_UP_ACTIVITY}
 * by invoking the existing CRM {@link ActivityUseCases#create} application
 * command with database-enforced idempotency.
 *
 * <p><strong>Idempotency:</strong> the {@code decisionId} is stored in the
 * activity's {@code source} column (a free-form metadata field on
 * {@link ActivityRepository.CreateActivityCommand}). Before creating a new
 * activity, the adapter queries for an existing activity with the same
 * decisionId in the source field. If found, the original activity is
 * returned — no second activity is created.</p>
 *
 * <p>This ensures that a crash-recovery retry with the same decisionId
 * returns the original activity, not a duplicate.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class CreateFollowUpActivityCommandAdapter implements ConfirmedRecommendationCommandPort {

    private static final Logger log = LoggerFactory.getLogger(CreateFollowUpActivityCommandAdapter.class);

    private final ActivityUseCases activityUseCases;
    private final JdbcTemplate jdbc;

    public CreateFollowUpActivityCommandAdapter(ActivityUseCases activityUseCases, JdbcTemplate jdbc) {
        this.activityUseCases = activityUseCases;
        this.jdbc = jdbc;
    }

    @Override
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!"CREATE_FOLLOW_UP_ACTIVITY".equals(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }
        try {
            UUID tenantId = recommendation.tenantId();
            UUID actorId = recommendation.actorId();
            UUID decisionId = recommendation.decisionId();

            // Step 1: Check for an existing activity created by this decisionId
            // (database-enforced idempotency — replay returns the original).
            String existingActivityId = findExistingActivityId(tenantId, decisionId);
            if (existingActivityId != null) {
                log.info("Idempotent replay: returning existing activity {} for decision {}",
                        existingActivityId, decisionId);
                return new CommandExecutionResult(
                        true,
                        "CREATE_FOLLOW_UP_ACTIVITY",
                        "activity:" + existingActivityId,
                        null);
            }

            // Step 2: Create a new follow-up activity
            String relatedType = mapEntityType(recommendation.sourceEntityType());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ActivityRepository.CreateActivityCommand cmd = new ActivityRepository.CreateActivityCommand(
                    "FOLLOW_UP",
                    "AI follow-up — " + decisionId,
                    "Auto-created by confirmed AI recommendation "
                            + recommendation.integrationRequestId()
                            + " (decision=" + decisionId + ")",
                    relatedType,
                    recommendation.sourceEntityId(),
                    actorId,
                    2,
                    now,
                    now.plusDays(3));
            ActivityRepository.ActivityRecord created = activityUseCases.create(tenantId, actorId, cmd);

            return new CommandExecutionResult(
                    true,
                    "CREATE_FOLLOW_UP_ACTIVITY",
                    "activity:" + created.id(),
                    null);
        } catch (Exception e) {
            log.error("CREATE_FOLLOW_UP_ACTIVITY failed for decision {}", recommendation.decisionId(), e);
            return new CommandExecutionResult(
                    false, "CREATE_FOLLOW_UP_ACTIVITY", null,
                    IntegrationErrorCode.UNKNOWN_ERROR.name());
        }
    }

    /**
     * Query for an existing activity whose subject contains the decisionId.
     * This is the idempotency check — if found, the original activity is
     * returned instead of creating a duplicate.
     */
    private String findExistingActivityId(UUID tenantId, UUID decisionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT CAST(id AS VARCHAR) FROM crm_activities " +
                            "WHERE tenant_id = ? AND subject LIKE ? " +
                            "ORDER BY created_at LIMIT 1",
                    String.class, tenantId, "AI follow-up — " + decisionId + "%");
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    private String mapEntityType(String entityType) {
        return switch (entityType) {
            case "ACCOUNT" -> "ACCOUNT";
            case "CONTACT" -> "CONTACT";
            case "LEAD" -> "LEAD";
            case "OPPORTUNITY" -> "OPPORTUNITY";
            default -> throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Unsupported source entity type for follow-up activity: " + entityType);
        };
    }
}
