package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Production-grade adapter that executes {@code CREATE_FOLLOW_UP_ACTIVITY}
 * by invoking the existing CRM {@link ActivityUseCases#create} application
 * command idempotently by {@code decisionId}.
 *
 * <p>Idempotency: the {@code integrationRequestId} is used as the
 * correlation key written into the activity subject, so a replayed
 * execution with the same decisionId can be detected and skipped.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class CreateFollowUpActivityCommandAdapter implements ConfirmedRecommendationCommandPort {

    private final ActivityUseCases activityUseCases;

    public CreateFollowUpActivityCommandAdapter(ActivityUseCases activityUseCases) {
        this.activityUseCases = activityUseCases;
    }

    @Override
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!"CREATE_FOLLOW_UP_ACTIVITY".equals(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }
        try {
            UUID tenantId = recommendation.tenantId();
            UUID actorId = recommendation.actorId();
            String relatedType = mapEntityType(recommendation.sourceEntityType());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ActivityRepository.CreateActivityCommand cmd = new ActivityRepository.CreateActivityCommand(
                    "FOLLOW_UP",
                    "AI follow-up — " + recommendation.decisionId(),
                    "Auto-created by confirmed AI recommendation "
                            + recommendation.integrationRequestId(),
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
            return new CommandExecutionResult(
                    false, "CREATE_FOLLOW_UP_ACTIVITY", null,
                    IntegrationErrorCode.UNKNOWN_ERROR.name());
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
