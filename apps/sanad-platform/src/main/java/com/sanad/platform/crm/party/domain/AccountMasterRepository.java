package com.sanad.platform.crm.party.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Persistence port for the tenant-scoped enterprise Account/Customer Master.
 * The port intentionally exposes no JDBC, ERP, accounting or ecommerce types.
 */
public interface AccountMasterRepository {

    void initializeProfile(UUID tenantId, UUID actorId, UUID accountId, String legalName, String tradeName);

    AccountProfileRecord findProfile(UUID tenantId, UUID accountId);

    AccountProfileRecord updateProfile(
            UUID tenantId, UUID actorId, UUID accountId,
            UpdateAccountProfileCommand command, long expectedVersion);

    List<AccountRelationshipRecord> findRelationships(UUID tenantId, UUID accountId);

    AccountRelationshipRecord createRelationship(
            UUID tenantId, UUID actorId, CreateAccountRelationshipCommand command);

    AccountRelationshipRecord endRelationship(
            UUID tenantId, UUID actorId, UUID relationshipId, long expectedVersion, LocalDate effectiveTo);

    boolean hasActiveHierarchyPath(UUID tenantId, UUID fromAccountId, UUID toAccountId);

    List<ExternalIdentifierRecord> findExternalIdentifiers(UUID tenantId, UUID accountId);

    ExternalIdentifierRecord createExternalIdentifier(
            UUID tenantId, UUID actorId, UUID accountId, CreateExternalIdentifierCommand command);

    void deactivateExternalIdentifier(UUID tenantId, UUID actorId, UUID accountId, UUID identifierId);

    List<TaxonomyRecord> findTaxonomies(UUID tenantId, String taxonomyType);

    TaxonomyRecord createTaxonomy(UUID tenantId, UUID actorId, CreateTaxonomyCommand command);

    boolean taxonomyExists(UUID tenantId, UUID taxonomyId, String taxonomyType);

    void recordStatusChange(
            UUID tenantId, UUID actorId, UUID accountId,
            String fromStatus, String toStatus, String reason, Instant changedAt);

    void recordOwnershipChange(
            UUID tenantId, UUID actorId, UUID accountId,
            UUID fromOwnerUserId, UUID toOwnerUserId, String reason, Instant changedAt);

    List<StatusHistoryRecord> findStatusHistory(UUID tenantId, UUID accountId);

    List<OwnershipHistoryRecord> findOwnershipHistory(UUID tenantId, UUID accountId);

    List<ProjectionSnapshotRecord> findProjectionSnapshots(UUID tenantId, UUID accountId);

    record AccountProfileRecord(
            UUID accountId,
            long version,
            String legalName,
            String tradeName,
            String registrationNumber,
            String taxRegistrationNumber,
            String industry,
            String organizationSize,
            String websiteUrl,
            String customerTier,
            String riskLevel,
            List<String> riskFlags,
            UUID classificationId,
            UUID segmentId,
            boolean mergeCandidate,
            Instant createdAt,
            Instant updatedAt) { }

    record UpdateAccountProfileCommand(
            String legalName,
            String tradeName,
            String registrationNumber,
            String taxRegistrationNumber,
            String industry,
            String organizationSize,
            String websiteUrl,
            String customerTier,
            String riskLevel,
            List<String> riskFlags,
            UUID classificationId,
            UUID segmentId,
            Boolean mergeCandidate) { }

    record AccountRelationshipRecord(
            UUID id,
            long version,
            UUID sourceAccountId,
            UUID targetAccountId,
            String relationshipType,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String description,
            Instant createdAt,
            Instant updatedAt) { }

    record CreateAccountRelationshipCommand(
            UUID sourceAccountId,
            UUID targetAccountId,
            String relationshipType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String description) { }

    record ExternalIdentifierRecord(
            UUID id,
            UUID accountId,
            String provider,
            String systemScope,
            String externalId,
            String label,
            boolean active,
            Instant createdAt,
            Instant updatedAt) { }

    record CreateExternalIdentifierCommand(
            String provider,
            String systemScope,
            String externalId,
            String label) { }

    record TaxonomyRecord(
            UUID id,
            long version,
            String taxonomyType,
            String code,
            String nameAr,
            String nameEn,
            UUID parentId,
            boolean active,
            Instant createdAt,
            Instant updatedAt) { }

    record CreateTaxonomyCommand(
            String taxonomyType,
            String code,
            String nameAr,
            String nameEn,
            UUID parentId) { }

    record StatusHistoryRecord(
            UUID id,
            UUID accountId,
            String fromStatus,
            String toStatus,
            String reason,
            UUID changedBy,
            Instant changedAt) { }

    record OwnershipHistoryRecord(
            UUID id,
            UUID accountId,
            UUID fromOwnerUserId,
            UUID toOwnerUserId,
            String reason,
            UUID changedBy,
            Instant changedAt) { }

    record ProjectionSnapshotRecord(
            UUID id,
            UUID accountId,
            String projectionType,
            String sourceSystem,
            String connectionStatus,
            String payloadJson,
            Instant sourceUpdatedAt,
            Instant syncedAt) { }
}
