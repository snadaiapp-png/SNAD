package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.AssignmentRepository;
import com.sanad.platform.crm.ownership.domain.OwnershipReadPort;
import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import com.sanad.platform.crm.ownership.domain.OwnershipWritePort;
import com.sanad.platform.crm.ownership.domain.QueueClaimIdempotencyPort;
import com.sanad.platform.crm.ownership.domain.QueueMembershipRepository;
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

    @Bean
    public QueueUseCases queueUseCases(QueueRepository queueRepository,
                                       QueueMembershipRepository queueMembershipRepository,
                                       AssignmentRepository assignmentRepository,
                                       OwnershipReadPort ownershipReadPort,
                                       OwnershipWritePort ownershipWritePort,
                                       QueueClaimIdempotencyPort idempotencyPort,
                                       OwnershipUserValidationPort userValidationPort,
                                       AuditPort auditPort,
                                       TimelineEventPort timelineEventPort,
                                       ObjectMapper objectMapper) {
        return new QueueUseCases(
                queueRepository,
                queueMembershipRepository,
                assignmentRepository,
                ownershipReadPort,
                ownershipWritePort,
                idempotencyPort,
                userValidationPort,
                auditPort,
                timelineEventPort,
                objectMapper);
    }
}
