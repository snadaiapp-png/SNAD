package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Composite dispatcher that routes a confirmed recommendation to the
 * appropriate {@link ConfirmedRecommendationCommandPort} adapter based
 * on the recommendation's {@code actionCode}.
 *
 * <p>Delegates {@link #findExisting} to the matching adapter so crash
 * recovery queries the correct artifact type.</p>
 */
@Component
@Primary
@Profile({"!test", "!local", "!crm-acceptance"})
public class CompositeConfirmedRecommendationCommandAdapter implements ConfirmedRecommendationCommandPort {

    private final CreateFollowUpActivityCommandAdapter followUpAdapter;
    private final ScheduleContactCommandAdapter scheduleContactAdapter;
    private final RequestOpportunityReviewCommandAdapter reviewAdapter;

    public CompositeConfirmedRecommendationCommandAdapter(
            CreateFollowUpActivityCommandAdapter followUpAdapter,
            ScheduleContactCommandAdapter scheduleContactAdapter,
            RequestOpportunityReviewCommandAdapter reviewAdapter) {
        this.followUpAdapter = followUpAdapter;
        this.scheduleContactAdapter = scheduleContactAdapter;
        this.reviewAdapter = reviewAdapter;
    }

    @Override
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        ConfirmedRecommendationCommandPort delegate = route(recommendation.actionCode());
        return delegate.execute(recommendation);
    }

    @Override
    public Optional<CommandExecutionResult> findExisting(UUID tenantId,
                                                            UUID decisionId,
                                                            String actionCode) {
        return route(actionCode).findExisting(tenantId, decisionId, actionCode);
    }

    private ConfirmedRecommendationCommandPort route(String actionCode) {
        return switch (actionCode) {
            case "CREATE_FOLLOW_UP_ACTIVITY" -> followUpAdapter;
            case "SCHEDULE_CONTACT" -> scheduleContactAdapter;
            case "REQUEST_OPPORTUNITY_REVIEW" -> reviewAdapter;
            default -> throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Unknown actionCode: " + actionCode);
        };
    }
}
