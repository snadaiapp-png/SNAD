package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Production-grade adapter that executes {@code CREATE_FOLLOW_UP_ACTIVITY}
 * with atomic artifact idempotency.
 *
 * <p><strong>Atomic idempotency model:</strong></p>
 * <ol>
 *   <li>{@link CrmIntegrationStore#reserveOrGetArtifact} reserves a row in
 *       {@code crm_integration_command_artifacts} (INSERT...ON CONFLICT DO
 *       NOTHING). If the row already exists with a non-null artifact_id,
 *       the original activity is returned.</li>
 *   <li>If the reservation is new, the adapter creates the activity and
 *       calls {@link CrmIntegrationStore#persistArtifactId} to link it.</li>
 *   <li>Both operations happen inside a single {@code @Transactional}
 *       boundary — any failure propagates so Spring rolls the full unit of work back.</li>
 * </ol>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class CreateFollowUpActivityCommandAdapter implements ConfirmedRecommendationCommandPort {

    private static final Logger log = LoggerFactory.getLogger(CreateFollowUpActivityCommandAdapter.class);
    private static final String ACTION_CODE = "CREATE_FOLLOW_UP_ACTIVITY";
    private static final String ARTIFACT_TYPE = "ACTIVITY";

    private final ActivityUseCases activityUseCases;
    private final CrmIntegrationStore store;

    public CreateFollowUpActivityCommandAdapter(ActivityUseCases activityUseCases,
                                                  CrmIntegrationStore store) {
        this.activityUseCases = activityUseCases;
        this.store = store;
    }

    @Override
    @Transactional
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!ACTION_CODE.equals(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }

        UUID tenantId = recommendation.tenantId();
        UUID decisionId = recommendation.decisionId();

        CrmIntegrationStore.ArtifactReservation reservation = store.reserveOrGetArtifact(
                tenantId, decisionId, ACTION_CODE, ARTIFACT_TYPE);

        if (!reservation.created() && reservation.artifact().artifactId() != null) {
            UUID originalActivityId = reservation.artifact().artifactId();
            log.info("Idempotent replay: returning existing activity {} for decision {}",
                    originalActivityId, decisionId);
            return new CommandExecutionResult(
                    true, ACTION_CODE, "activity:" + originalActivityId, null);
        }

        String relatedType = mapEntityType(recommendation.sourceEntityType());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ActivityRepository.CreateActivityCommand cmd = new ActivityRepository.CreateActivityCommand(
                "TASK",
                "AI follow-up — " + decisionId,
                "Auto-created by confirmed AI recommendation "
                        + recommendation.integrationRequestId()
                        + " (decision=" + decisionId + ")",
                relatedType,
                recommendation.sourceEntityId(),
                recommendation.actorId(),
                2,
                now,
                now.plusDays(3));
        ActivityRepository.ActivityRecord created = activityUseCases.create(
                tenantId, recommendation.actorId(), cmd);

        store.persistArtifactId(tenantId, decisionId, ACTION_CODE, created.id());

        return new CommandExecutionResult(
                true, ACTION_CODE, "activity:" + created.id(), null);
    }

    @Override
    public Optional<CommandExecutionResult> findExisting(UUID tenantId, UUID decisionId, String actionCode) {
        if (!ACTION_CODE.equals(actionCode)) return Optional.empty();
        return store.findArtifact(tenantId, decisionId, actionCode)
                .filter(a -> a.artifactId() != null)
                .map(a -> new CommandExecutionResult(
                        true, ACTION_CODE, "activity:" + a.artifactId(), null));
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
