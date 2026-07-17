package com.sanad.platform.crm.party.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Canonical owner-scoped addresses and communication-method persistence port. */
public interface AddressCommunicationRepository {

    AddressRecord address(UUID tenantId, UUID addressId);

    List<AddressRecord> addresses(
            UUID tenantId,
            String ownerType,
            UUID ownerId,
            boolean includeArchived,
            int limit,
            Instant beforeUpdatedAt,
            UUID beforeId);

    AddressRecord createAddress(
            UUID tenantId,
            UUID actorId,
            String ownerType,
            UUID ownerId,
            CreateAddressCommand command);

    AddressRecord updateAddress(
            UUID tenantId,
            UUID actorId,
            UUID addressId,
            UpdateAddressCommand command,
            long expectedVersion);

    AddressRecord setPrimaryAddress(
            UUID tenantId,
            UUID actorId,
            UUID addressId,
            long expectedVersion);

    AddressRecord changeAddressStatus(
            UUID tenantId,
            UUID actorId,
            UUID addressId,
            String status,
            long expectedVersion);

    List<AddressHistoryRecord> addressHistory(UUID tenantId, UUID addressId, int limit);

    CommunicationMethodRecord communicationMethod(UUID tenantId, UUID communicationMethodId);

    List<CommunicationMethodRecord> communicationMethods(
            UUID tenantId,
            String ownerType,
            UUID ownerId,
            boolean includeArchived,
            String methodType,
            String verificationStatus,
            int limit,
            Instant beforeUpdatedAt,
            UUID beforeId);

    CommunicationMethodRecord createCommunicationMethod(
            UUID tenantId,
            UUID actorId,
            String ownerType,
            UUID ownerId,
            CreateCommunicationMethodCommand command);

    CommunicationMethodRecord updateCommunicationMethod(
            UUID tenantId,
            UUID actorId,
            UUID communicationMethodId,
            UpdateCommunicationMethodCommand command,
            long expectedVersion);

    CommunicationMethodRecord setPreferredCommunicationMethod(
            UUID tenantId,
            UUID actorId,
            UUID communicationMethodId,
            long expectedVersion);

    CommunicationMethodRecord changeVerification(
            UUID tenantId,
            UUID actorId,
            UUID communicationMethodId,
            String verificationStatus,
            Instant verifiedAt,
            long expectedVersion);

    CommunicationMethodRecord changeCommunicationStatus(
            UUID tenantId,
            UUID actorId,
            UUID communicationMethodId,
            String status,
            long expectedVersion);

    List<CommunicationHistoryRecord> communicationHistory(
            UUID tenantId,
            UUID communicationMethodId,
            int limit);

    CommunicationPolicy policy(UUID tenantId);

    record AddressRecord(
            UUID id,
            long version,
            String ownerType,
            UUID ownerId,
            String addressType,
            String label,
            String rawFormattedAddress,
            String line1,
            String line2,
            String line3,
            String district,
            String city,
            String stateRegion,
            String postalCode,
            String countryCode,
            String countryExtensionJson,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean primaryAddress,
            boolean verified,
            String verificationSource,
            String status,
            LocalDate validFrom,
            LocalDate validTo,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt) {}

    record CreateAddressCommand(
            String addressType,
            String label,
            String rawFormattedAddress,
            String line1,
            String line2,
            String line3,
            String district,
            String city,
            String stateRegion,
            String postalCode,
            String countryCode,
            String countryExtensionJson,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean primaryAddress,
            boolean verified,
            String verificationSource,
            LocalDate validFrom,
            LocalDate validTo) {}

    record UpdateAddressCommand(
            String addressType,
            String label,
            String rawFormattedAddress,
            String line1,
            String line2,
            String line3,
            String district,
            String city,
            String stateRegion,
            String postalCode,
            String countryCode,
            String countryExtensionJson,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean verified,
            String verificationSource,
            LocalDate validFrom,
            LocalDate validTo) {}

    record AddressHistoryRecord(
            UUID id,
            UUID addressId,
            String ownerType,
            UUID ownerId,
            String eventType,
            Long previousVersion,
            long newVersion,
            String snapshot,
            UUID changedBy,
            Instant changedAt) {}

    record CommunicationMethodRecord(
            UUID id,
            long version,
            String ownerType,
            UUID ownerId,
            String methodType,
            String rawValue,
            String normalizedValue,
            String displayValue,
            String label,
            boolean preferred,
            boolean verified,
            String verificationStatus,
            Instant verifiedAt,
            String privacyClassification,
            String consentStateReference,
            String usagePurpose,
            String status,
            LocalDate validFrom,
            LocalDate validTo,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt) {}

    record CreateCommunicationMethodCommand(
            String methodType,
            String rawValue,
            String normalizedValue,
            String displayValue,
            String label,
            boolean preferred,
            String privacyClassification,
            String consentStateReference,
            String usagePurpose,
            LocalDate validFrom,
            LocalDate validTo) {}

    record UpdateCommunicationMethodCommand(
            String rawValue,
            String normalizedValue,
            String displayValue,
            String label,
            String privacyClassification,
            String consentStateReference,
            String usagePurpose,
            LocalDate validFrom,
            LocalDate validTo) {}

    record CommunicationHistoryRecord(
            UUID id,
            UUID communicationMethodId,
            String ownerType,
            UUID ownerId,
            String eventType,
            Long previousVersion,
            long newVersion,
            String snapshot,
            UUID changedBy,
            Instant changedAt) {}

    record CommunicationPolicy(
            boolean emailUniqueWithinOwner,
            boolean phoneUniqueWithinOwner,
            boolean singlePreferredPerType) {}
}
