package com.sanad.platform.crm.party.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain persistence port for the person profile and multi-account relationship model.
 * Tenant identity is always supplied by the authenticated application boundary.
 */
public interface ContactRelationshipRepository {

    ContactProfileRecord findProfile(UUID tenantId, UUID contactId);

    ContactProfileRecord updateProfile(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            UpdateContactProfileCommand command,
            long expectedVersion);

    RelationshipRecord findRelationship(UUID tenantId, UUID relationshipId);

    List<RelationshipRecord> listByContact(
            UUID tenantId,
            UUID contactId,
            int limit,
            Instant beforeUpdatedAt,
            UUID beforeId);

    List<RelationshipRecord> listByAccount(
            UUID tenantId,
            UUID accountId,
            int limit,
            Instant beforeUpdatedAt,
            UUID beforeId);

    RelationshipRecord createRelationship(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            CreateRelationshipCommand command);

    RelationshipRecord updateRelationship(
            UUID tenantId,
            UUID actorId,
            UUID relationshipId,
            UpdateRelationshipCommand command,
            long expectedVersion);

    RelationshipRecord changeStatus(
            UUID tenantId,
            UUID actorId,
            UUID relationshipId,
            String status,
            long expectedVersion);

    RelationshipRecord setPrimary(
            UUID tenantId,
            UUID actorId,
            UUID relationshipId,
            long expectedVersion);

    List<RelationshipHistoryRecord> relationshipHistory(
            UUID tenantId,
            UUID relationshipId,
            int limit);

    List<OwnershipHistoryRecord> ownershipHistory(
            UUID tenantId,
            UUID contactId,
            int limit);

    List<RelationshipRoleRecord> listRoles(UUID tenantId, boolean includeInactive);

    RelationshipRoleRecord createRole(
            UUID tenantId,
            UUID actorId,
            CreateRelationshipRoleCommand command);

    record ContactProfileRecord(
            UUID id,
            long version,
            String legalName,
            String preferredName,
            String givenName,
            String middleName,
            String familyName,
            String displayName,
            String primaryEmail,
            String primaryPhone,
            String preferredLocale,
            String timeZone,
            String pronouns,
            String lifecycleStatus,
            UUID ownerUserId,
            String source,
            Instant createdAt,
            Instant updatedAt) {}

    record UpdateContactProfileCommand(
            String legalName,
            String preferredName,
            String givenName,
            String middleName,
            String familyName,
            String primaryEmail,
            String primaryPhone,
            String preferredLocale,
            String timeZone,
            String pronouns,
            UUID ownerUserId,
            String source,
            String ownerChangeReason) {}

    record RelationshipRecord(
            UUID id,
            long version,
            UUID contactId,
            UUID accountId,
            String contactDisplayName,
            String accountDisplayName,
            String roleCode,
            UUID customRoleId,
            String customRoleNameAr,
            String customRoleNameEn,
            String status,
            boolean primaryRelationship,
            LocalDate validFrom,
            LocalDate validTo,
            String jobTitle,
            String department,
            String decisionAuthority,
            UUID ownerUserId,
            Instant createdAt,
            Instant updatedAt) {}

    record CreateRelationshipCommand(
            UUID accountId,
            String roleCode,
            UUID customRoleId,
            boolean primaryRelationship,
            LocalDate validFrom,
            LocalDate validTo,
            String jobTitle,
            String department,
            String decisionAuthority,
            UUID ownerUserId) {}

    record UpdateRelationshipCommand(
            String roleCode,
            UUID customRoleId,
            LocalDate validFrom,
            LocalDate validTo,
            String jobTitle,
            String department,
            String decisionAuthority,
            UUID ownerUserId) {}

    record RelationshipHistoryRecord(
            UUID id,
            UUID relationshipId,
            UUID contactId,
            UUID accountId,
            String eventType,
            Long previousVersion,
            long newVersion,
            String snapshot,
            UUID changedBy,
            Instant changedAt) {}

    record OwnershipHistoryRecord(
            UUID id,
            UUID contactId,
            UUID previousOwnerUserId,
            UUID newOwnerUserId,
            UUID changedBy,
            Instant changedAt,
            String reason) {}

    record RelationshipRoleRecord(
            UUID id,
            long version,
            String code,
            String nameAr,
            String nameEn,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {}

    record CreateRelationshipRoleCommand(
            String code,
            String nameAr,
            String nameEn) {}
}
