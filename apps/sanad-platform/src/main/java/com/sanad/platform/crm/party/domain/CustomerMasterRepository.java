package com.sanad.platform.crm.party.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise customer-master persistence port.
 * The existing CRM Account remains the golden customer record.
 */
public interface CustomerMasterRepository {

    CustomerMasterProfile findProfile(UUID tenantId, UUID accountId);

    CustomerMasterProfile updateProfile(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UpdateCustomerMasterCommand command,
            long expectedVersion);

    List<AccountAddress> listAddresses(UUID tenantId, UUID accountId);

    AccountAddress addAddress(UUID tenantId, UUID actorId, UUID accountId, CreateAddressCommand command);

    void deactivateAddress(UUID tenantId, UUID actorId, UUID accountId, UUID addressId);

    List<AccountIdentifier> listIdentifiers(UUID tenantId, UUID accountId);

    AccountIdentifier addIdentifier(UUID tenantId, UUID actorId, UUID accountId, CreateIdentifierCommand command);

    List<AccountRelationship> listRelationships(UUID tenantId, UUID accountId);

    AccountRelationship addRelationship(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            CreateRelationshipCommand command);

    List<DuplicateCandidate> findDuplicateCandidates(UUID tenantId, UUID accountId, int limit);

    MergeResult mergeAccounts(
            UUID tenantId,
            UUID actorId,
            UUID sourceAccountId,
            UUID targetAccountId,
            long expectedSourceVersion,
            long expectedTargetVersion,
            String reason);

    record CustomerMasterProfile(
            UUID accountId,
            long version,
            String displayName,
            String accountType,
            String lifecycleStatus,
            String legalName,
            String tradingName,
            String registrationNumber,
            String taxNumber,
            String industryCode,
            String customerSegment,
            String customerTier,
            String website,
            String primaryEmail,
            String primaryPhone,
            String countryCode,
            String riskRating,
            BigDecimal creditLimit,
            Integer paymentTermsDays,
            int dataQualityScore,
            UUID mergedIntoAccountId,
            Instant createdAt,
            Instant updatedAt) {}

    record UpdateCustomerMasterCommand(
            String legalName,
            String tradingName,
            String registrationNumber,
            String taxNumber,
            String industryCode,
            String customerSegment,
            String customerTier,
            String website,
            String primaryEmail,
            String primaryPhone,
            String countryCode,
            String riskRating,
            BigDecimal creditLimit,
            Integer paymentTermsDays) {}

    record AccountAddress(
            UUID id,
            long version,
            UUID accountId,
            String addressType,
            String label,
            String line1,
            String line2,
            String city,
            String stateRegion,
            String postalCode,
            String countryCode,
            boolean primaryAddress,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {}

    record CreateAddressCommand(
            String addressType,
            String label,
            String line1,
            String line2,
            String city,
            String stateRegion,
            String postalCode,
            String countryCode,
            boolean primaryAddress) {}

    record AccountIdentifier(
            UUID id,
            UUID accountId,
            String identifierType,
            String identifierValue,
            String issuerCountryCode,
            boolean primaryIdentifier,
            boolean verified,
            boolean active,
            Instant createdAt) {}

    record CreateIdentifierCommand(
            String identifierType,
            String identifierValue,
            String issuerCountryCode,
            boolean primaryIdentifier,
            boolean verified) {}

    record AccountRelationship(
            UUID id,
            UUID sourceAccountId,
            UUID targetAccountId,
            String relationshipType,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String notes,
            Instant createdAt,
            Instant updatedAt) {}

    record CreateRelationshipCommand(
            UUID targetAccountId,
            String relationshipType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String notes) {}

    record DuplicateCandidate(
            UUID accountId,
            String displayName,
            String legalName,
            String registrationNumber,
            String taxNumber,
            String primaryEmail,
            int confidenceScore,
            List<String> matchedFields) {}

    record MergeResult(
            UUID sourceAccountId,
            UUID targetAccountId,
            long sourceVersion,
            long targetVersion,
            int contactsMoved,
            int opportunitiesMoved,
            int activitiesMoved,
            int addressesMoved,
            int identifiersMoved,
            int relationshipsMoved,
            Instant mergedAt) {}
}
