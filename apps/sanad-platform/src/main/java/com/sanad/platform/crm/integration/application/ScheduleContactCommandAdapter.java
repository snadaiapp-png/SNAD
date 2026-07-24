package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
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
 * Production-grade adapter for {@code SCHEDULE_CONTACT} with atomic
 * artifact idempotency.
 *
 * <p>Creates a SCHEDULED_CALL activity linked to the source contact.
 * Uses {@code crm_integration_command_artifacts} to enforce exactly-once
 * creation — a replay returns the original scheduled activity, not a
 * duplicate.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class ScheduleContactCommandAdapter implements ConfirmedRecommendationCommandPort {

    private static final Logger log = LoggerFactory.getLogger(ScheduleContactCommandAdapter.class);
    private static final String ACTION_CODE = "SCHEDULE_CONTACT";
    private static final String ARTIFACT_TYPE = "SCHEDULED_ACTIVITY";

    private final ActivityUseCases activityUseCases;
    private final JdbcTemplate jdbc;
    private final CrmIntegrationStore store;

    public ScheduleContactCommandAdapter(ActivityUseCases activityUseCases,
                                           JdbcTemplate jdbc,
                                           CrmIntegrationStore store) {
        this.activityUseCases = activityUseCases;
        this.jdbc = jdbc;
        this.store = store;
    }

    @Override
    @Transactional
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!ACTION_CODE.equals(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }
        if (!"CONTACT".equals(recommendation.sourceEntityType())) {
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
                UUID originalActivityId = reservation.artifact().artifactId();
                log.info("Idempotent replay: returning existing scheduled activity {} for decision {}",
                        originalActivityId, decisionId);
                return new CommandExecutionResult(
                        true, ACTION_CODE, "scheduled-activity:" + originalActivityId, null);
            }

            // Step 2: Create the scheduled-call activity
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ActivityRepository.CreateActivityCommand cmd = new ActivityRepository.CreateActivityCommand(
                    "CALL",
                    "Scheduled contact follow-up — " + decisionId,
                    "Auto-scheduled by confirmed AI recommendation "
                            + recommendation.integrationRequestId()
                            + " for contact " + recommendation.sourceEntityId()
                            + " (decision=" + decisionId + ")",
                    "CONTACT",
                    recommendation.sourceEntityId(),
                    recommendation.actorId(),
                    3,
                    now,
                    now.plusHours(24));
            ActivityRepository.ActivityRecord created = activityUseCases.create(
                    tenantId, recommendation.actorId(), cmd);

            // Step 3: Persist the artifact_id
            store.persistArtifactId(tenantId, decisionId, ACTION_CODE, created.id());

            return new CommandExecutionResult(
                    true, ACTION_CODE, "scheduled-activity:" + created.id(), null);
        } catch (Exception e) {
            log.error("SCHEDULE_CONTACT failed for decision {}", recommendation.decisionId(), e);
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
                        true, ACTION_CODE, "scheduled-activity:" + a.artifactId(), null));
    }
}
