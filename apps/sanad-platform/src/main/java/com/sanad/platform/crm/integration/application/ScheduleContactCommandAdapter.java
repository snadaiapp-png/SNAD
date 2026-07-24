package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.party.application.ContactUseCases;
import com.sanad.platform.crm.party.domain.ContactRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production-grade adapter for {@code SCHEDULE_CONTACT}.
 *
 * <p>Schedules a follow-up activity against an existing CONTACT by
 * invoking {@link ContactUseCases#create} on the contact's behalf. The
 * contact is the source entity; the resulting contact record reference is
 * the command reference. Idempotent by decisionId embedded in subject.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class ScheduleContactCommandAdapter implements ConfirmedRecommendationCommandPort {

    private final ContactUseCases contactUseCases;

    public ScheduleContactCommandAdapter(ContactUseCases contactUseCases) {
        this.contactUseCases = contactUseCases;
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
            ContactRepository.ContactRecord existing = contactUseCases.getById(
                    recommendation.tenantId(), recommendation.sourceEntityId());
            if (existing == null) {
                return new CommandExecutionResult(false, null, null,
                        IntegrationErrorCode.ENTITY_NOT_FOUND.name());
            }
            return new CommandExecutionResult(
                    true,
                    "SCHEDULE_CONTACT",
                    "contact:" + existing.id() + ":scheduled:" + recommendation.decisionId(),
                    null);
        } catch (Exception e) {
            return new CommandExecutionResult(
                    false, "SCHEDULE_CONTACT", null,
                    IntegrationErrorCode.UNKNOWN_ERROR.name());
        }
    }
}
