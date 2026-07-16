package com.sanad.platform.crm.lead.application;

import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.lead.domain.LeadRepository;
import com.sanad.platform.crm.lead.domain.LeadRepository.ConvertLeadCommand;
import com.sanad.platform.crm.lead.domain.LeadRepository.CreateLeadCommand;
import com.sanad.platform.crm.lead.domain.LeadRepository.LeadConversionRecord;
import com.sanad.platform.crm.lead.domain.LeadRepository.LeadRecord;
import com.sanad.platform.crm.lead.domain.LeadStatusPolicy;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class LeadUseCases {
    private final LeadRepository repo;
    private final TimelineEventPort timeline;

    public LeadUseCases(LeadRepository repo, TimelineEventPort timeline) {
        this.repo = repo;
        this.timeline = timeline;
    }

    @Transactional
    public LeadRecord create(UUID tenantId, UUID actorId, CreateLeadCommand cmd) {
        LeadRecord created = repo.create(tenantId, actorId, cmd);
        timeline.record(tenantId, "LEAD", created.id(), "crm.lead.created", "Lead created",
                "CRM_LEAD", created.id(), actorId, Instant.now());
        return created;
    }

    public LeadRecord getById(UUID tenantId, UUID leadId) {
        return repo.findById(tenantId, leadId);
    }

    public List<LeadRecord> list(UUID tenantId, int limit, String status) {
        return repo.findAll(tenantId, limit, status);
    }

    @Transactional
    public LeadRecord changeStatus(UUID tenantId, UUID actorId, UUID leadId, String status,
                                   String reason, long expectedVersion) {
        LeadStatusPolicy.validateStatus(status);
        return repo.changeStatus(tenantId, actorId, leadId, status, reason, expectedVersion);
    }

    @Transactional
    public LeadConversionRecord convert(UUID tenantId, UUID actorId, UUID leadId,
                                        ConvertLeadCommand cmd, long expectedVersion) {
        LeadRecord lead = repo.findById(tenantId, leadId);
        if ("CONVERTED".equals(lead.status())) {
            return new LeadConversionRecord(lead, lead.convertedAccountId(), lead.convertedContactId(),
                    lead.convertedOpportunityId(), true);
        }
        LeadStatusPolicy.assertCanConvert(lead.status());
        LeadConversionRecord converted = repo.convert(tenantId, actorId, leadId, cmd, expectedVersion);
        timeline.record(tenantId, "LEAD", leadId, "crm.lead.converted", "Lead converted",
                "CRM_LEAD", leadId, actorId, Instant.now());
        return converted;
    }
}
