package com.sanad.platform.crm.lead.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.lead.domain.LeadRepository;
import com.sanad.platform.crm.lead.domain.LeadRepository.LeadRecord;
import com.sanad.platform.crm.opportunity.application.OpportunityUseCases;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.OpportunityRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.PipelineRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.StageRecord;
import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.application.ContactUseCases;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Atomic application orchestration for lead conversion.
 * All entity mutations, audit records and timeline events join one transaction.
 */
public class LeadConversionUseCases {
    private static final List<String> DEFAULT_STAGES =
            List.of("New", "Qualified", "Proposal", "Won", "Lost");

    private final LeadUseCases leadUseCases;
    private final AccountUseCases accountUseCases;
    private final ContactUseCases contactUseCases;
    private final OpportunityUseCases opportunityUseCases;
    private final AuditPort audit;
    private final ObjectMapper objectMapper;

    public LeadConversionUseCases(
            LeadUseCases leadUseCases,
            AccountUseCases accountUseCases,
            ContactUseCases contactUseCases,
            OpportunityUseCases opportunityUseCases,
            AuditPort audit,
            ObjectMapper objectMapper
    ) {
        this.leadUseCases = leadUseCases;
        this.accountUseCases = accountUseCases;
        this.contactUseCases = contactUseCases;
        this.opportunityUseCases = opportunityUseCases;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConversionResult convert(UUID tenantId, UUID actorId, UUID leadId,
                                    ConvertCommand command) {
        LeadRecord lead = leadUseCases.getById(tenantId, leadId);
        if ("CONVERTED".equals(lead.status())) {
            return new ConversionResult(lead, null, null, null, true);
        }

        String accountName = fallback(command.accountName(), lead.displayName());
        String currencyCode = fallback(command.currencyCode(), "SAR").toUpperCase();
        AccountRecord account = accountUseCases.create(tenantId, actorId,
                new AccountRepository.CreateAccountCommand(
                        accountName,
                        "PROSPECT",
                        lead.ownerUserId(),
                        null,
                        currencyCode,
                        "ar-SA",
                        "Asia/Riyadh",
                        "LEAD_CONVERSION"));

        NameParts name = splitName(lead.displayName());
        ContactRecord contact = contactUseCases.create(tenantId, actorId,
                new ContactRepository.CreateContactCommand(
                        account.id(),
                        name.givenName(),
                        name.familyName(),
                        lead.email(),
                        lead.phone(),
                        "ar-SA",
                        "Asia/Riyadh",
                        lead.ownerUserId(),
                        "UNKNOWN"));

        OpportunityRecord opportunity = null;
        UUID pipelineId = command.pipelineId();
        UUID stageId = command.stageId();
        if (Boolean.TRUE.equals(command.createOpportunity())) {
            PipelineRecord pipeline = pipelineId == null
                    ? defaultPipeline(tenantId, actorId, currencyCode)
                    : opportunityUseCases.getPipeline(tenantId, pipelineId);
            pipelineId = pipeline.id();
            stageId = stageId == null ? firstActiveStage(tenantId, pipelineId) : stageId;
            assertStageBelongsToPipeline(tenantId, pipelineId, stageId);
            opportunity = opportunityUseCases.create(tenantId, actorId,
                    new OpportunityRepository.CreateOpportunityCommand(
                            account.id(),
                            contact.id(),
                            pipelineId,
                            stageId,
                            fallback(command.opportunityName(), "Opportunity - " + lead.displayName()),
                            command.amount(),
                            currencyCode,
                            command.expectedCloseDate(),
                            lead.ownerUserId()));
        }

        UUID opportunityId = opportunity == null ? null : opportunity.id();
        var converted = leadUseCases.convert(tenantId, actorId, leadId,
                new LeadRepository.ConvertLeadCommand(
                        accountName,
                        command.createOpportunity(),
                        pipelineId,
                        stageId,
                        fallback(command.opportunityName(), "Opportunity - " + lead.displayName()),
                        command.amount(),
                        currencyCode,
                        command.expectedCloseDate(),
                        account.id(),
                        contact.id(),
                        opportunityId),
                lead.version());

        recordConversionAudit(
                tenantId,
                actorId,
                leadId,
                account.id(),
                contact.id(),
                opportunityId,
                pipelineId,
                stageId,
                currencyCode,
                command.amount());

        return new ConversionResult(converted.lead(), account, contact, opportunity,
                converted.idempotent());
    }

    private void recordConversionAudit(
            UUID tenantId,
            UUID actorId,
            UUID leadId,
            UUID accountId,
            UUID contactId,
            UUID opportunityId,
            UUID pipelineId,
            UUID stageId,
            String currencyCode,
            BigDecimal amount
    ) {
        Instant occurredAt = Instant.now();
        ObjectNode after = objectMapper.createObjectNode();
        after.put("leadId", leadId.toString());
        after.put("accountId", accountId.toString());
        after.put("contactId", contactId.toString());
        if (opportunityId != null) after.put("opportunityId", opportunityId.toString());
        if (pipelineId != null) after.put("pipelineId", pipelineId.toString());
        if (stageId != null) after.put("stageId", stageId.toString());
        after.put("currencyCode", currencyCode);
        if (amount != null) after.put("amount", amount);
        after.put("idempotent", false);

        AuditChange conversionChange = new AuditChange(null, after);
        audit.record(tenantId, actorId, "CONVERT", "LEAD", leadId,
                conversionChange, occurredAt);

        ObjectNode accountLink = objectMapper.createObjectNode();
        accountLink.put("sourceLeadId", leadId.toString());
        accountLink.put("contactId", contactId.toString());
        if (opportunityId != null) accountLink.put("opportunityId", opportunityId.toString());
        audit.record(tenantId, actorId, "LEAD_CONVERSION_LINK", "ACCOUNT", accountId,
                new AuditChange(null, accountLink), occurredAt);

        if (opportunityId != null) {
            ObjectNode opportunityLink = objectMapper.createObjectNode();
            opportunityLink.put("sourceLeadId", leadId.toString());
            opportunityLink.put("accountId", accountId.toString());
            opportunityLink.put("contactId", contactId.toString());
            audit.record(tenantId, actorId, "LEAD_CONVERSION_LINK", "OPPORTUNITY", opportunityId,
                    new AuditChange(null, opportunityLink), occurredAt);
        }
    }

    private PipelineRecord defaultPipeline(UUID tenantId, UUID actorId, String currencyCode) {
        return opportunityUseCases.listPipelines(tenantId).stream()
                .filter(pipeline -> "Default Sales Pipeline".equals(pipeline.name()) && pipeline.active())
                .findFirst()
                .orElseGet(() -> opportunityUseCases.createPipeline(tenantId, actorId,
                        new PipelineRepository.CreatePipelineCommand(
                                "Default Sales Pipeline", currencyCode, DEFAULT_STAGES)));
    }

    private UUID firstActiveStage(UUID tenantId, UUID pipelineId) {
        return opportunityUseCases.listStages(tenantId, pipelineId).stream()
                .filter(StageRecord::active)
                .map(StageRecord::id)
                .findFirst()
                .orElseThrow(() -> new CrmContractException(
                        CrmErrorCode.VALIDATION_ERROR, "CRM pipeline has no active stage"));
    }

    private void assertStageBelongsToPipeline(UUID tenantId, UUID pipelineId, UUID stageId) {
        boolean exists = opportunityUseCases.listStages(tenantId, pipelineId).stream()
                .anyMatch(stage -> stage.active() && stage.id().equals(stageId));
        if (!exists) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "CRM pipeline stage not found");
        }
    }

    private static NameParts splitName(String displayName) {
        String normalized = displayName == null ? "Lead" : displayName.trim();
        int separator = normalized.indexOf(' ');
        return separator < 0
                ? new NameParts(normalized, null)
                : new NameParts(normalized.substring(0, separator),
                        normalized.substring(separator + 1).trim());
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record NameParts(String givenName, String familyName) {}

    public record ConvertCommand(String accountName, Boolean createOpportunity, UUID pipelineId,
                                 UUID stageId, String opportunityName, BigDecimal amount,
                                 String currencyCode, LocalDate expectedCloseDate) {}

    public record ConversionResult(LeadRecord lead, AccountRecord account, ContactRecord contact,
                                   OpportunityRecord opportunity, boolean idempotent) {}
}
