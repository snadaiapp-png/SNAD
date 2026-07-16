package com.sanad.platform.crm.party.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Typed HTTP models for EXEC-PROMPT-CRM-005. */
public final class AccountMasterModels {
    private AccountMasterModels() { }

    public record AccountMasterOverviewResponse(
            UUID accountId,
            long accountVersion,
            String displayName,
            String accountType,
            String lifecycleStatus,
            UUID ownerUserId,
            AccountProfileResponse profile,
            List<ProjectionResponse> projections) { }

    public record AccountProfileResponse(
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
            UUID classificationId,
            UUID segmentId,
            Instant createdAt,
            Instant updatedAt) { }

    public record RiskProfileResponse(
            UUID accountId,
            long version,
            String riskLevel,
            List<String> riskFlags,
            boolean mergeCandidate,
            Instant updatedAt) { }

    public record UpdateAccountProfileRequest(
            @Size(max = 320) String legalName,
            @Size(max = 320) String tradeName,
            @Size(max = 160) String registrationNumber,
            @Size(max = 160) String taxRegistrationNumber,
            @Size(max = 160) String industry,
            @Pattern(regexp = "MICRO|SMALL|MEDIUM|LARGE|ENTERPRISE", flags = Pattern.Flag.CASE_INSENSITIVE)
            String organizationSize,
            @Size(max = 500) String websiteUrl,
            @Size(max = 40) String customerTier,
            UUID classificationId,
            UUID segmentId) { }

    public record UpdateAccountRiskRequest(
            @Pattern(regexp = "UNKNOWN|LOW|MEDIUM|HIGH|CRITICAL", flags = Pattern.Flag.CASE_INSENSITIVE)
            String riskLevel,
            @Size(max = 20) List<@NotBlank @Size(max = 60) String> riskFlags,
            Boolean mergeCandidate) { }

    public record AccountRelationshipResponse(
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

    public record CreateAccountRelationshipRequest(
            UUID targetAccountId,
            @NotBlank
            @Pattern(regexp = "PARENT|SUBSIDIARY|BRANCH|PARTNER", flags = Pattern.Flag.CASE_INSENSITIVE)
            String relationshipType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 500) String description) { }

    public record EndAccountRelationshipRequest(LocalDate effectiveTo) { }

    public record ExternalIdentifierResponse(
            UUID id,
            UUID accountId,
            String provider,
            String systemScope,
            String externalId,
            String label,
            boolean active,
            Instant createdAt,
            Instant updatedAt) { }

    public record CreateExternalIdentifierRequest(
            @NotBlank @Size(max = 120) String provider,
            @NotBlank @Size(max = 120) String systemScope,
            @NotBlank @Size(max = 240) String externalId,
            @Size(max = 240) String label) { }

    public record TaxonomyResponse(
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

    public record CreateTaxonomyRequest(
            @NotBlank
            @Pattern(regexp = "CLASSIFICATION|SEGMENT", flags = Pattern.Flag.CASE_INSENSITIVE)
            String taxonomyType,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 240) String nameAr,
            @NotBlank @Size(max = 240) String nameEn,
            UUID parentId) { }

    public record AccountHistoryResponse(
            List<StatusHistoryResponse> statusHistory,
            List<OwnershipHistoryResponse> ownershipHistory) { }

    public record StatusHistoryResponse(
            UUID id,
            String fromStatus,
            String toStatus,
            String reason,
            UUID changedBy,
            Instant changedAt) { }

    public record OwnershipHistoryResponse(
            UUID id,
            UUID fromOwnerUserId,
            UUID toOwnerUserId,
            String reason,
            UUID changedBy,
            Instant changedAt) { }

    public record ProjectionResponse(
            UUID id,
            String projectionType,
            String sourceSystem,
            String connectionStatus,
            JsonNode payload,
            Instant sourceUpdatedAt,
            Instant syncedAt) { }
}
