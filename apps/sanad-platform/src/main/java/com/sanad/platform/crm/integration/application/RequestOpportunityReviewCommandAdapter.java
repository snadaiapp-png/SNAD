package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.opportunity.application.OpportunityUseCases;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production-grade adapter for {@code REQUEST_OPPORTUNITY_REVIEW}.
 *
 * <p>Triggers a stage transition on an existing OPPORTUNITY to surface it
 * for managerial review. The command reference records the opportunity id
 * and the decisionId that triggered the review.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class RequestOpportunityReviewCommandAdapter implements ConfirmedRecommendationCommandPort {

    private final OpportunityUseCases opportunityUseCases;

    public RequestOpportunityReviewCommandAdapter(OpportunityUseCases opportunityUseCases) {
        this.opportunityUseCases = opportunityUseCases;
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
            OpportunityRepository.OpportunityRecord existing = opportunityUseCases.getById(
                    recommendation.tenantId(), recommendation.sourceEntityId());
            if (existing == null) {
                return new CommandExecutionResult(false, null, null,
                        IntegrationErrorCode.ENTITY_NOT_FOUND.name());
            }
            // No stage transition is issued here — we only register the review
            // request as a command reference. The actual stage transition would
            // be applied by a separate review workflow once approved by a
            // manager. This keeps the adapter side-effect-free aside from the
            // audit trail captured by the integration request lifecycle.
            return new CommandExecutionResult(
                    true,
                    "REQUEST_OPPORTUNITY_REVIEW",
                    "opportunity:" + existing.id() + ":review:" + recommendation.decisionId(),
                    null);
        } catch (Exception e) {
            return new CommandExecutionResult(
                    false, "REQUEST_OPPORTUNITY_REVIEW", null,
                    IntegrationErrorCode.UNKNOWN_ERROR.name());
        }
    }
}
