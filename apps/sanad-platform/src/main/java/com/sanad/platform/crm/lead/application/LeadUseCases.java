package com.sanad.platform.crm.lead.application;

import com.sanad.platform.crm.lead.domain.LeadRepository;
import com.sanad.platform.crm.lead.domain.LeadStatusPolicy;
import com.sanad.platform.crm.lead.domain.LeadRepository.LeadRecord;
import com.sanad.platform.crm.lead.domain.LeadRepository.CreateLeadCommand;
import com.sanad.platform.crm.lead.domain.LeadRepository.ConvertLeadCommand;
import com.sanad.platform.crm.lead.domain.LeadRepository.LeadConversionRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class LeadUseCases {
    private final LeadRepository repo;
    public LeadUseCases(LeadRepository repo) { this.repo = repo; }
    @Transactional
    public LeadRecord create(UUID tenantId, UUID actorId, CreateLeadCommand cmd) { return repo.create(tenantId, actorId, cmd); }
    public LeadRecord getById(UUID tenantId, UUID leadId) { return repo.findById(tenantId, leadId); }
    public List<LeadRecord> list(UUID tenantId, int limit, String status) { return repo.findAll(tenantId, limit, status); }
    @Transactional
    public LeadRecord changeStatus(UUID tenantId, UUID actorId, UUID leadId, String status, String reason, long expectedVersion) {
        LeadStatusPolicy.validateStatus(status);
        return repo.changeStatus(tenantId, actorId, leadId, status, reason, expectedVersion);
    }
    @Transactional
    public LeadConversionRecord convert(UUID tenantId, UUID actorId, UUID leadId, ConvertLeadCommand cmd, long expectedVersion) {
        LeadRecord lead = repo.findById(tenantId, leadId);
        LeadStatusPolicy.assertCanConvert(lead.status());
        return repo.convert(tenantId, actorId, leadId, cmd, expectedVersion);
    }
}
