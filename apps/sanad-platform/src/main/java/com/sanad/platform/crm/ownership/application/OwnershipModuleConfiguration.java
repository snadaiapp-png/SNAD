package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.*;
import com.sanad.platform.crm.ownership.domain.availability.AvailabilityRepository;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityRepository;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentRepository;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplateRepository;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignmentRepository;
import com.sanad.platform.crm.ownership.domain.skills.SkillRepository;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadRepository;
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

    @Bean
    public OwnershipCommandUseCases ownershipCommandUseCases(
            AssignmentRepository assignmentRepository,
            OwnershipRecordPort ownershipRecordPort,
            OwnershipUserValidationPort userValidationPort,
            SalesTeamRepository salesTeamRepository,
            QueueRepository queueRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new OwnershipCommandUseCases(
                assignmentRepository, ownershipRecordPort, userValidationPort,
                salesTeamRepository, queueRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public OwnershipQueryUseCases ownershipQueryUseCases(OwnershipReadPort ownershipReadPort) {
        return new OwnershipQueryUseCases(ownershipReadPort);
    }

    @Bean
    public TransferUseCases transferUseCases(
            TransferRequestRepository transferRequestRepository,
            AssignmentRepository assignmentRepository,
            OwnershipRecordPort ownershipRecordPort,
            OwnershipCommandUseCases ownershipCommandUseCases,
            OwnershipUserValidationPort userValidationPort,
            SalesTeamRepository salesTeamRepository,
            WorkflowPort workflowPort,
            HrmPort hrmPort,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new TransferUseCases(
                transferRequestRepository, assignmentRepository, ownershipRecordPort,
                ownershipCommandUseCases, userValidationPort, salesTeamRepository,
                workflowPort, hrmPort, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public TransferQueryUseCases transferQueryUseCases(
            TransferRequestRepository transferRequestRepository) {
        return new TransferQueryUseCases(transferRequestRepository);
    }

    // ── CRM-008 Team Management Use Cases ────────────────────────────────

    @Bean
    public TeamManagementUseCases teamManagementUseCases(
            SalesTeamRepository salesTeamRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new TeamManagementUseCases(salesTeamRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public ShiftManagementUseCases shiftManagementUseCases(
            ShiftTemplateRepository shiftTemplateRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            SalesTeamRepository salesTeamRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new ShiftManagementUseCases(shiftTemplateRepository, shiftAssignmentRepository,
                salesTeamRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public AvailabilityManagementUseCases availabilityManagementUseCases(
            AvailabilityRepository availabilityRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new AvailabilityManagementUseCases(availabilityRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public SkillManagementUseCases skillManagementUseCases(
            SkillRepository skillRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new SkillManagementUseCases(skillRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public CapacityManagementUseCases capacityManagementUseCases(
            CapacityRepository capacityRepository,
            SalesTeamRepository salesTeamRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new CapacityManagementUseCases(capacityRepository, salesTeamRepository,
                auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public WorkloadManagementUseCases workloadManagementUseCases(
            WorkloadRepository workloadRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new WorkloadManagementUseCases(workloadRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public ServiceAssignmentUseCases serviceAssignmentUseCases(
            ServiceAssignmentRepository serviceAssignmentRepository,
            SalesTeamRepository salesTeamRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new ServiceAssignmentUseCases(serviceAssignmentRepository, salesTeamRepository,
                auditPort, timelineEventPort, objectMapper);
    }
}
