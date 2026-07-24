package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Production-grade adapter for {@code SCHEDULE_CONTACT}.
 *
 * <p><strong>Real side effect:</strong> creates a SCHEDULED_CALL activity
 * linked to the source contact, with the scheduled time set to 24 hours
 * from now. The activity's subject contains the decisionId for
 * database-enforced idempotency — a replay returns the original scheduled
 * activity, not a duplicate.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class ScheduleContactCommandAdapter implements ConfirmedRecommendationCommandPort {

    private static final Logger log = LoggerFactory.getLogger(ScheduleContactCommandAdapter.class);

    private final ActivityUseCases activityUseCases;
    private final JdbcTemplate jdbc;

    public ScheduleContactCommandAdapter(ActivityUseCases activityUseCases, JdbcTemplate jdbc) {
        this.activityUseCases = activityUseCases;
        this.jdbc = jdbc;
    }

    @Override
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!"SCHEDULE_CONTACT".equals(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }
        if (!"CONTACT".equals(recommendation.sourceEntityType())) {
            return new CommandExecutionResult(false, null, null,
                    IntegrationErrorCode.INVALID_CONTRACT.name());
        }
        try {
            UUID tenantId = recommendation.tenantId();
            UUID actorId = recommendation.actorId();
            UUID decisionId = recommendation.decisionId();

            // Step 1: Idempotency check — find existing scheduled activity
            String existingActivityId = findExistingScheduledActivity(tenantId, decisionId);
            if (existingActivityId != null) {
                log.info("Idempotent replay: returning existing scheduled activity {} for decision {}",
                        existingActivityId, decisionId);
                return new CommandExecutionResult(
                        true,
                        "SCHEDULE_CONTACT",
                        "scheduled-activity:" + existingActivityId,
                        null);
            }

            // Step 2: Create a real SCHEDULED_CALL activity
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ActivityRepository.CreateActivityCommand cmd = new ActivityRepository.CreateActivityCommand(
                    "SCHEDULED_CALL",
                    "Scheduled contact follow-up — " + decisionId,
                    "Auto-scheduled by confirmed AI recommendation "
                            + recommendation.integrationRequestId()
                            + " for contact " + recommendation.sourceEntityId()
                            + " (decision=" + decisionId + ")",
                    "CONTACT",
                    recommendation.sourceEntityId(),
                    actorId,
                    3,
                    now,
                    now.plusHours(24));
            ActivityRepository.ActivityRecord created = activityUseCases.create(tenantId, actorId, cmd);

            return new CommandExecutionResult(
                    true,
                    "SCHEDULE_CONTACT",
                    "scheduled-activity:" + created.id(),
                    null);
        } catch (Exception e) {
            log.error("SCHEDULE_CONTACT failed for decision {}", recommendation.decisionId(), e);
            return new CommandExecutionResult(
                    false, "SCHEDULE_CONTACT", null,
                    IntegrationErrorCode.UNKNOWN_ERROR.name());
        }
    }

    private String findExistingScheduledActivity(UUID tenantId, UUID decisionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT CAST(id AS VARCHAR) FROM crm_activities " +
                            "WHERE tenant_id = ? AND activity_type = 'SCHEDULED_CALL' " +
                            "AND subject LIKE ? " +
                            "ORDER BY created_at LIMIT 1",
                    String.class, tenantId, "Scheduled contact follow-up — " + decisionId + "%");
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }
}
