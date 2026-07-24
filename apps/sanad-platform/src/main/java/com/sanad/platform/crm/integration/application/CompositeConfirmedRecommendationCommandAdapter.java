package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Composite dispatcher that routes a confirmed recommendation to the
 * appropriate {@link ConfirmedRecommendationCommandPort} adapter based
 * on the recommendation's {@code actionCode}.
 *
 * <p>This bean is {@code @Primary} so Spring injects it whenever the
 * {@link ConfirmedRecommendationCommandPort} type is requested. The
 * individual adapters ({@link CreateFollowUpActivityCommandAdapter},
 * {@link ScheduleContactCommandAdapter},
 * {@link RequestOpportunityReviewCommandAdapter}) are looked up by name
 * and dispatched at runtime — they are NOT injected as separate
 * {@link ConfirmedRecommendationCommandPort} beans to avoid ambiguous
 * bean resolution.</p>
 *
 * <p>In test/local/crm-acceptance profiles, this composite is absent and
 * the {@link StubConfirmedRecommendationCommandAdapter} is used instead.</p>
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
        String actionCode = recommendation.actionCode();
        ConfirmedRecommendationCommandPort delegate = switch (actionCode) {
            case "CREATE_FOLLOW_UP_ACTIVITY" -> followUpAdapter;
            case "SCHEDULE_CONTACT" -> scheduleContactAdapter;
            case "REQUEST_OPPORTUNITY_REVIEW" -> reviewAdapter;
            default -> throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Unknown actionCode: " + actionCode);
        };
        return delegate.execute(recommendation);
    }
}
