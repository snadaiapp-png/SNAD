package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PartyModuleConfiguration {

    @Bean
    public AccountUseCases accountUseCases(AccountRepository accountRepository,
                                           AccountHierarchyPort hierarchyPort,
                                           OwnerValidationPort ownerValidationPort,
                                           AuditPort auditPort,
                                           TimelineEventPort timelineEventPort,
                                           ObjectMapper objectMapper) {
        return new AccountUseCases(accountRepository, hierarchyPort, ownerValidationPort,
                auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public ContactUseCases contactUseCases(ContactRepository contactRepository,
                                           AuditPort auditPort,
                                           TimelineEventPort timelineEventPort,
                                           ObjectMapper objectMapper) {
        return new ContactUseCases(contactRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public ContactRelationshipUseCases contactRelationshipUseCases(
            ContactRelationshipRepository contactRelationshipRepository,
            OwnerValidationPort ownerValidationPort,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new ContactRelationshipUseCases(contactRelationshipRepository, ownerValidationPort,
                auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public CustomerMasterUseCases customerMasterUseCases(
            CustomerMasterRepository customerMasterRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new CustomerMasterUseCases(customerMasterRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public AddressCommunicationUseCases addressCommunicationUseCases(
            AddressCommunicationRepository addressCommunicationRepository,
            LegacyAddressProjectionPort legacyAddressProjectionPort,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new AddressCommunicationUseCases(addressCommunicationRepository, legacyAddressProjectionPort,
                auditPort, timelineEventPort, objectMapper);
    }

    /**
     * Contact-specific collaboration façade over the generic
     * {@link CollaborationMembershipService}. Injects ONLY
     * {@link ContactRepository} and {@link CollaborationMembershipService}
     * — no timeline / audit / outbox / RBAC ports (those are owned by C7
     * and C8).
     */
    @Bean
    public ContactCollaborationService contactCollaborationService(
            ContactRepository contactRepository,
            CollaborationMembershipService collaborationMembershipService) {
        return new ContactCollaborationService(contactRepository, collaborationMembershipService);
    }

    /**
     * C5 canonical Contact owner-transfer orchestration. Injects ONLY
     * {@link ContactRepository}, {@link CollaborationMembershipService},
     * and {@link RecipientEligibilityPort}. Does NOT inject
     * {@code CapabilityEvaluationService}, {@code TimelineEventPort},
     * {@code AuditPort}, {@code CrmEventOutboxPort},
     * {@code SecurityContextHolder}, {@code TenantRlsTransactionContext},
     * {@code JdbcTemplate}, {@code OwnershipCommandUseCases}, or
     * {@code TransferUseCases} — those concerns are owned by C7/C8/C6.
     */
    @Bean
    public ContactTransferUseCases contactTransferUseCases(
            ContactRepository contactRepository,
            CollaborationMembershipService collaborationMembershipService,
            RecipientEligibilityPort recipientEligibilityPort) {
        return new ContactTransferUseCases(
                contactRepository, collaborationMembershipService, recipientEligibilityPort);
    }
}
