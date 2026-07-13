package com.sanad.platform.crm.opportunity.domain;

import java.util.List;
import java.util.UUID;

public interface OpportunityRepository {
    OpportunityRecord findById(UUID tenantId, UUID opportunityId);
    List<OpportunityRecord> findAll(UUID tenantId, int limit, UUID accountId);
    OpportunityRecord create(UUID tenantId, UUID actorId, CreateOpportunityCommand command);
    OpportunityRecord update(UUID tenantId, UUID actorId, UUID opportunityId, UpdateOpportunityCommand command, long expectedVersion);
    OpportunityRecord moveStage(UUID tenantId, UUID actorId, UUID opportunityId, UUID stageId, String status, String reason, long expectedVersion);

    record OpportunityRecord(UUID id, long version, UUID accountId, UUID contactId, UUID pipelineId,
            UUID stageId, String name, java.math.BigDecimal amount, String currencyCode,
            java.math.BigDecimal probability, String status, String winLossReason,
            java.time.LocalDate expectedCloseDate, UUID ownerUserId,
            java.time.Instant createdAt, java.time.Instant updatedAt) {}
    record CreateOpportunityCommand(UUID accountId, UUID contactId, UUID pipelineId, UUID stageId,
            String name, java.math.BigDecimal amount, String currencyCode,
            java.time.LocalDate expectedCloseDate, UUID ownerUserId) {}
    record UpdateOpportunityCommand(String name, java.math.BigDecimal amount, UUID ownerUserId,
            java.time.LocalDate expectedCloseDate) {}
}
