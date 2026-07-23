package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import com.sanad.platform.crm.ownership.domain.QueueRepository;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.TeamMembershipRepository;
import com.sanad.platform.crm.ownership.domain.TerritoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OwnershipModuleConfiguration {

    @Bean
    public SalesTeamUseCases salesTeamUseCases(SalesTeamRepository salesTeamRepository,
                                               TeamMembershipRepository teamMembershipRepository,
                                               OwnershipUserValidationPort userValidationPort,
                                               QueueRepository queueRepository,
                                               TerritoryRepository territoryRepository,
                                               AuditPort auditPort,
                                               TimelineEventPort timelineEventPort,
                                               ObjectMapper objectMapper) {
        return new SalesTeamUseCases(
                salesTeamRepository,
                teamMembershipRepository,
                userValidationPort,
                queueRepository,
                territoryRepository,
                auditPort,
                timelineEventPort,
                objectMapper);
    }
}
