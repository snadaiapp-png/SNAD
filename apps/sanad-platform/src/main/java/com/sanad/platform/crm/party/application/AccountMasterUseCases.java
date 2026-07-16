package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.AccountMasterRepository;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.AccountProfileRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.AccountRelationshipRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateAccountRelationshipCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateExternalIdentifierCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateTaxonomyCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.ExternalIdentifierRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.OwnershipHistoryRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.ProjectionSnapshotRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.StatusHistoryRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.TaxonomyRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.UpdateAccountProfileCommand;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Application boundary for the enterprise Account and Customer Master. */
public class AccountMasterUseCases {
    private static final Set<String> RELATIONSHIP_TYPES =
            Set.of("PARENT", "SUBSIDIARY", "BRANCH", "PARTNER");
    private static final Set<String> HIERARCHICAL_RELATIONSHIP_TYPES =
            Set.of("PARENT", "SUBSIDIARY", "BRANCH");
    private static final Set<String> ORGANIZATION_SIZES =
            Set.of("MICRO", "SMALL", "MEDIUM", "LARGE", "ENTERPRISE");
    private static final Set<String> RISK_LEVELS =
            Set.of("UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> TAXONOMY_TYPES = Set.of("CLASSIFICATION", "SEGMENT");

    private final AccountRepository accounts;
    private final AccountMasterRepository master;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public AccountMasterUseCases(
            AccountRepository accounts,
            AccountMasterRepository master,
            AuditPort audit,
            TimelineEventPort timeline,
            ObjectMapper mapper) {
        this.accounts = accounts;
        this.master = master;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    public AccountMasterView get(UUID tenantId, UUID accountId) {
        AccountRecord account = accounts.findById(tenantId, accountId);
        AccountProfileRecord profile = master.findProfile(tenantId, accountId);
        List<ProjectionSnapshotRecord> snapshots = withUnavailableProjectionContracts(
                accountId, master.findProjectionSnapshots(tenantId, accountId));
        return new AccountMasterView(
                account,
                profile,
                master.findRelationships(tenantId, accountId),
                master.findExternalIdentifiers(tenantId, accountId),
                master.findStatusHistory(tenantId, accountId),
                master.findOwnershipHistory(tenantId, accountId),
                snapshots);
    }

    @Transactional
    public AccountProfileRecord updateProfile(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UpdateAccountProfileCommand command,
            long expectedVersion) {
        accounts.findById(tenantId, accountId);
        validateProfile(tenantId, command);
        AccountProfileRecord before = master.findProfile(tenantId, accountId);
        AccountProfileRecord after = master.updateProfile(
                tenantId, actorId, accountId, normalize(command), expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE_MASTER", "ACCOUNT", accountId,
                new AuditChange(json(before), json(after)), now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.master.updated",
                "Account master profile updated", "CRM_ACCOUNT", accountId, actorId, now);
        return after;
    }

    @Transactional
    public AccountRelationshipRecord createRelationship(
            UUID tenantId, UUID actorId, CreateAccountRelationshipCommand command) {
        accounts.findById(tenantId, command.sourceAccountId());
        accounts.findById(tenantId, command.targetAccountId());
        if (command.sourceAccountId().equals(command.targetAccountId())) {
            throw validation("An account relationship cannot reference the same account twice");
        }
        String relationshipType = upper(command.relationshipType());
        if (!RELATIONSHIP_TYPES.contains(relationshipType)) {
            throw validation("Unsupported account relationship type");
        }
        if (command.effectiveFrom() != null && command.effectiveTo() != null
                && command.effectiveTo().isBefore(command.effectiveFrom())) {
            throw validation("Relationship end date cannot precede its start date");
        }
        if (HIERARCHICAL_RELATIONSHIP_TYPES.contains(relationshipType)
                && master.hasActiveHierarchyPath(
                tenantId, command.targetAccountId(), command.sourceAccountId())) {
            throw new CrmContractException(
                    CrmErrorCode.CONFLICT, "The relationship would create an account hierarchy cycle");
        }
        AccountRelationshipRecord created = master.createRelationship(
                tenantId, actorId,
                new CreateAccountRelationshipCommand(
                        command.sourceAccountId(), command.targetAccountId(), relationshipType,
                        command.effectiveFrom(), command.effectiveTo(), clean(command.description(), 500)));
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE_RELATIONSHIP", "ACCOUNT", command.sourceAccountId(),
                new AuditChange(null, json(created)), now);
        timeline.record(tenantId, "ACCOUNT", command.sourceAccountId(),
                "crm.account.relationship.created", "Account relationship created",
                "CRM_ACCOUNT_RELATIONSHIP", created.id(), actorId, now);
        return created;
    }

    @Transactional
    public AccountRelationshipRecord endRelationship(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UUID relationshipId,
            long expectedVersion,
            LocalDate effectiveTo) {
        accounts.findById(tenantId, accountId);
        AccountRelationshipRecord ended = master.endRelationship(
                tenantId, actorId, relationshipId, expectedVersion,
                effectiveTo == null ? LocalDate.now() : effectiveTo);
        if (!accountId.equals(ended.sourceAccountId()) && !accountId.equals(ended.targetAccountId())) {
            throw new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND);
        }
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "END_RELATIONSHIP", "ACCOUNT", accountId,
                new AuditChange(null, json(ended)), now);
        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.account.relationship.ended", "Account relationship ended",
                "CRM_ACCOUNT_RELATIONSHIP", ended.id(), actorId, now);
        return ended;
    }

    @Transactional
    public ExternalIdentifierRecord addExternalIdentifier(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            CreateExternalIdentifierCommand command) {
        accounts.findById(tenantId, accountId);
        CreateExternalIdentifierCommand normalized = new CreateExternalIdentifierCommand(
                required(command.provider(), 120, "provider"),
                required(command.systemScope(), 120, "systemScope"),
                required(command.externalId(), 240, "externalId"),
                clean(command.label(), 240));
        ExternalIdentifierRecord created = master.createExternalIdentifier(
                tenantId, actorId, accountId, normalized);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "ADD_EXTERNAL_IDENTIFIER", "ACCOUNT", accountId,
                new AuditChange(null, json(created)), now);
        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.account.external_identifier.added", "External identifier added",
                "CRM_ACCOUNT_IDENTIFIER", created.id(), actorId, now);
        return created;
    }

    @Transactional
    public void removeExternalIdentifier(
            UUID tenantId, UUID actorId, UUID accountId, UUID identifierId) {
        accounts.findById(tenantId, accountId);
        master.deactivateExternalIdentifier(tenantId, actorId, accountId, identifierId);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "REMOVE_EXTERNAL_IDENTIFIER", "ACCOUNT", accountId,
                new AuditChange(null, mapper.createObjectNode().put("identifierId", identifierId.toString())), now);
        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.account.external_identifier.removed", "External identifier removed",
                "CRM_ACCOUNT_IDENTIFIER", identifierId, actorId, now);
    }

    public List<TaxonomyRecord> listTaxonomies(UUID tenantId, String taxonomyType) {
        return master.findTaxonomies(tenantId, normalizeTaxonomyType(taxonomyType));
    }

    @Transactional
    public TaxonomyRecord createTaxonomy(
            UUID tenantId, UUID actorId, CreateTaxonomyCommand command) {
        String type = normalizeTaxonomyType(command.taxonomyType());
        if (command.parentId() != null && !master.taxonomyExists(tenantId, command.parentId(), type)) {
            throw validation("Taxonomy parent does not exist in the same tenant and taxonomy type");
        }
        TaxonomyRecord created = master.createTaxonomy(
                tenantId, actorId,
                new CreateTaxonomyCommand(
                        type,
                        required(command.code(), 80, "code").toUpperCase(Locale.ROOT),
                        required(command.nameAr(), 240, "nameAr"),
                        required(command.nameEn(), 240, "nameEn"),
                        command.parentId()));
        audit.record(tenantId, actorId, "CREATE_TAXONOMY", "ACCOUNT_TAXONOMY", created.id(),
                new AuditChange(null, json(created)), Instant.now());
        return created;
    }

    private void validateProfile(UUID tenantId, UpdateAccountProfileCommand command) {
        String organizationSize = upper(command.organizationSize());
        if (organizationSize != null && !ORGANIZATION_SIZES.contains(organizationSize)) {
            throw validation("Unsupported organization size");
        }
        String riskLevel = upper(command.riskLevel());
        if (riskLevel != null && !RISK_LEVELS.contains(riskLevel)) {
            throw validation("Unsupported risk level");
        }
        if (command.websiteUrl() != null && !command.websiteUrl().isBlank()) {
            try {
                URI uri = URI.create(command.websiteUrl().trim());
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                    throw validation("Website URL must use HTTP or HTTPS");
                }
            } catch (IllegalArgumentException exception) {
                throw validation("Website URL is invalid");
            }
        }
        if (command.classificationId() != null
                && !master.taxonomyExists(tenantId, command.classificationId(), "CLASSIFICATION")) {
            throw validation("Classification does not exist in this tenant");
        }
        if (command.segmentId() != null
                && !master.taxonomyExists(tenantId, command.segmentId(), "SEGMENT")) {
            throw validation("Customer segment does not exist in this tenant");
        }
        if (command.riskFlags() != null && command.riskFlags().size() > 20) {
            throw validation("At most 20 risk flags are allowed");
        }
    }

    private UpdateAccountProfileCommand normalize(UpdateAccountProfileCommand command) {
        List<String> flags = command.riskFlags() == null ? null : command.riskFlags().stream()
                .map(flag -> required(flag, 60, "riskFlag").toUpperCase(Locale.ROOT))
                .distinct().toList();
        return new UpdateAccountProfileCommand(
                clean(command.legalName(), 320), clean(command.tradeName(), 320),
                clean(command.registrationNumber(), 160), clean(command.taxRegistrationNumber(), 160),
                clean(command.industry(), 160), upper(command.organizationSize()),
                clean(command.websiteUrl(), 500), clean(command.customerTier(), 40),
                upper(command.riskLevel()), flags, command.classificationId(), command.segmentId(),
                command.mergeCandidate());
    }

    private List<ProjectionSnapshotRecord> withUnavailableProjectionContracts(
            UUID accountId, List<ProjectionSnapshotRecord> snapshots) {
        List<ProjectionSnapshotRecord> result = new ArrayList<>(snapshots);
        addMissingProjection(result, accountId, "FINANCIAL_SUMMARY", "ACCOUNTING");
        addMissingProjection(result, accountId, "ORDERS", "ERP_ECOMMERCE");
        addMissingProjection(result, accountId, "SERVICE", "CUSTOMER_SERVICE");
        return List.copyOf(result);
    }

    private void addMissingProjection(
            List<ProjectionSnapshotRecord> snapshots,
            UUID accountId,
            String projectionType,
            String sourceSystem) {
        if (snapshots.stream().noneMatch(item -> projectionType.equals(item.projectionType()))) {
            snapshots.add(new ProjectionSnapshotRecord(
                    null, accountId, projectionType, sourceSystem,
                    "NOT_CONNECTED", null, null, null));
        }
    }

    private String normalizeTaxonomyType(String value) {
        String type = upper(value);
        if (!TAXONOMY_TYPES.contains(type)) {
            throw validation("Taxonomy type must be CLASSIFICATION or SEGMENT");
        }
        return type;
    }

    private JsonNode json(Object value) {
        return value == null ? null : mapper.valueToTree(value);
    }

    private static CrmContractException validation(String message) {
        return new CrmContractException(CrmErrorCode.VALIDATION_ERROR, message);
    }

    private static String upper(String value) {
        String cleaned = clean(value, 80);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private static String required(String value, int max, String field) {
        String cleaned = clean(value, max);
        if (cleaned == null) throw validation(field + " is required");
        return cleaned;
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isEmpty()) return null;
        if (cleaned.length() > max) throw validation("Field exceeds maximum length " + max);
        return cleaned;
    }

    public record AccountMasterView(
            AccountRecord account,
            AccountProfileRecord profile,
            List<AccountRelationshipRecord> relationships,
            List<ExternalIdentifierRecord> externalIdentifiers,
            List<StatusHistoryRecord> statusHistory,
            List<OwnershipHistoryRecord> ownershipHistory,
            List<ProjectionSnapshotRecord> projections) { }
}
