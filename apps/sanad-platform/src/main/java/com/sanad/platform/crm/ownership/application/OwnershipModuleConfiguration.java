package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.*;
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
                salesTeamRepository, teamMembershipRepository, userValidationPort,
                queueRepository, territoryRepository, auditPort, timelineEventPort, objectMapper);
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
                queueRepository, queueMembershipRepository, assignmentRepository,
                ownershipReadPort, ownershipWritePort, idempotencyPort, userValidationPort,
                auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public TerritoryUseCases territoryUseCases(TerritoryRepository territoryRepository,
                                                TerritoryAssignmentRepository assignmentRepository,
                                                SalesTeamRepository salesTeamRepository,
                                                OwnershipUserValidationPort userValidationPort,
                                                AuditPort auditPort,
                                                TimelineEventPort timelineEventPort,
                                                ObjectMapper objectMapper) {
        return new TerritoryUseCases(
                territoryRepository, assignmentRepository, salesTeamRepository,
                userValidationPort, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public AssignmentRuleUseCases assignmentRuleUseCases(
            AssignmentRuleRepository assignmentRuleRepository,
            SalesTeamRepository salesTeamRepository,
            TeamMembershipRepository teamMembershipRepository,
            QueueRepository queueRepository,
            AssignmentRepository assignmentRepository,
            TerritoryUseCases territoryUseCases,
            OwnershipUserValidationPort userValidationPort,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new AssignmentRuleUseCases(
                assignmentRuleRepository, salesTeamRepository, teamMembershipRepository,
                queueRepository, assignmentRepository, territoryUseCases, userValidationPort,
                auditPort, timelineEventPort, objectMapper);
    }
}
