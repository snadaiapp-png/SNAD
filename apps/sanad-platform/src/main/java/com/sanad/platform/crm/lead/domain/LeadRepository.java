package com.sanad.platform.crm.lead.domain;

import java.util.List;
import java.util.UUID;

public interface LeadRepository {
    LeadRecord findById(UUID tenantId, UUID leadId);
    List<LeadRecord> findAll(UUID tenantId, int limit, String status);
    LeadRecord create(UUID tenantId, UUID actorId, CreateLeadCommand command);
    LeadRecord changeStatus(UUID tenantId, UUID actorId, UUID leadId, String status, String reason, long expectedVersion);
    LeadConversionRecord convert(UUID tenantId, UUID actorId, UUID leadId, ConvertLeadCommand command, long expectedVersion);

    record LeadRecord(UUID id, long version, String displayName, String companyName, String email,
            String phone, String source, String status, UUID ownerUserId, java.math.BigDecimal score,
            UUID convertedAccountId, UUID convertedContactId, UUID convertedOpportunityId,
            java.time.Instant createdAt, java.time.Instant updatedAt) {}
    record CreateLeadCommand(String displayName, String companyName, String email, String phone,
            String source, UUID ownerUserId, java.math.BigDecimal score) {}
    record ConvertLeadCommand(String accountName, Boolean createOpportunity, UUID pipelineId,
            UUID stageId, String opportunityName, java.math.BigDecimal amount, String currencyCode,
            java.time.LocalDate expectedCloseDate) {}
    record LeadConversionRecord(LeadRecord lead, UUID accountId, UUID contactId, UUID opportunityId, boolean idempotent) {}
}
