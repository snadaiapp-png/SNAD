package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.ContactProfileRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.CreateRelationshipCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.CreateRelationshipRoleCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.OwnershipHistoryRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipHistoryRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRoleRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.UpdateContactProfileCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.UpdateRelationshipCommand;
import com.sanad.platform.crm.party.domain.OwnerValidationPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ContactRelationshipUseCases {

    private static final Set<String> STANDARD_ROLES = Set.of(
            "DECISION_MAKER", "BILLING", "TECHNICAL", "INFLUENCER", "EMPLOYEE", "PARTNER", "OTHER");
    private static final Set<String> DECISION_AUTHORITIES = Set.of(
            "NONE", "INFLUENCER", "RECOMMENDER", "DECIDER", "FINAL_APPROVER");

    private final ContactRelationshipRepository repository;
    private final OwnerValidationPort ownerValidation;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper objectMapper;

    public ContactRelationshipUseCases(
            ContactRelationshipRepository repository,
            OwnerValidationPort ownerValidation,
            AuditPort audit,
            TimelineEventPort timeline,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.ownerValidation = ownerValidation;
        this.audit = audit;
        this.timeline = timeline;
        this.objectMapper = objectMapper;
    }

    public ContactProfileRecord profile(UUID tenantId, UUID contactId) {
        requireTenant(tenantId);
        return repository.findProfile(tenantId, contactId);
    }

    @Transactional
    public ContactProfileRecord updateProfile(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            UpdateContactProfileCommand command,
            long expectedVersion) {
        requireContext(tenantId, actorId);
        ContactProfileRecord before = repository.findProfile(tenantId, contactId);
        assertContactMutable(before);
        validateOwner(tenantId, command.ownerUserId());
        validateProfile(command);
        ContactProfileRecord after = repository.updateProfile(
                tenantId, actorId, contactId, command, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE", "CONTACT", contactId,
                new AuditChange(json(before), json(after)), now);
        timeline.record(tenantId, "CONTACT", contactId, "crm.contact.profile.updated",
                "Person profile updated", "CRM_CONTACT", contactId, actorId, now);
        if (!Objects.equals(before.ownerUserId(), after.ownerUserId())) {
            audit.record(tenantId, actorId, "OWNER_CHANGE", "CONTACT", contactId,
                    new AuditChange(jsonOwner(before.ownerUserId()), jsonOwner(after.ownerUserId())), now);
            timeline.record(tenantId, "CONTACT", contactId, "crm.contact.owner.changed",
                    "Contact owner changed", "CRM_CONTACT", contactId, actorId, now);
        }
        return after;
    }

    public RelationshipRecord relationship(UUID tenantId, UUID relationshipId) {
        requireTenant(tenantId);
        return repository.findRelationship(tenantId, relationshipId);
    }

    public List<RelationshipRecord> relationshipsByContact(
            UUID tenantId, UUID contactId, int limit, Instant beforeUpdatedAt, UUID beforeId) {
        requireTenant(tenantId);
        return repository.listByContact(tenantId, contactId, boundedLimit(limit), beforeUpdatedAt, beforeId);
    }

    public List<RelationshipRecord> relationshipsByAccount(
            UUID tenantId, UUID accountId, int limit, Instant beforeUpdatedAt, UUID beforeId) {
        requireTenant(tenantId);
        return repository.listByAccount(tenantId, accountId, boundedLimit(limit), beforeUpdatedAt, beforeId);
    }

    @Transactional
    public RelationshipRecord createRelationship(
            UUID tenantId, UUID actorId, UUID contactId, CreateRelationshipCommand command) {
        requireContext(tenantId, actorId);
        ContactProfileRecord contact = repository.findProfile(tenantId, contactId);
        assertContactMutable(contact);
        validateRelationship(command.roleCode(), command.customRoleId(), command.validFrom(), command.validTo(),
                command.decisionAuthority(), command.ownerUserId(), tenantId);
        RelationshipRecord created = repository.createRelationship(tenantId, actorId, contactId, command);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "CONTACT_ACCOUNT_RELATIONSHIP", created.id(),
                new AuditChange(null, json(created)), now);
        recordRelationshipTimeline(tenantId, created, actorId, "crm.contact.relationship.created",
                "Contact relationship created", now);
        if (created.primaryRelationship()) {
            audit.record(tenantId, actorId, "PRIMARY_CHANGE", "CONTACT_ACCOUNT_RELATIONSHIP", created.id(),
                    new AuditChange(null, json(created)), now);
        }
        return created;
    }

    @Transactional
    public RelationshipRecord updateRelationship(
            UUID tenantId, UUID actorId, UUID relationshipId,
            UpdateRelationshipCommand command, long expectedVersion) {
        requireContext(tenantId, actorId);
        RelationshipRecord before = repository.findRelationship(tenantId, relationshipId);
        assertRelationshipMutable(before);
        validateRelationship(
                command.roleCode() == null ? before.roleCode() : command.roleCode(),
                command.roleCode() == null && command.customRoleId() == null
                        ? before.customRoleId() : command.customRoleId(),
                command.validFrom() == null ? before.validFrom() : command.validFrom(),
                command.validTo() == null ? before.validTo() : command.validTo(),
                command.decisionAuthority() == null ? before.decisionAuthority() : command.decisionAuthority(),
                command.ownerUserId(), tenantId);
        RelationshipRecord after = repository.updateRelationship(
                tenantId, actorId, relationshipId, command, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE", "CONTACT_ACCOUNT_RELATIONSHIP", relationshipId,
                new AuditChange(json(before), json(after)), now);
        recordRelationshipTimeline(tenantId, after, actorId, "crm.contact.relationship.updated",
                "Contact relationship updated", now);
        return after;
    }

    @Transactional
    public RelationshipRecord setPrimary(
            UUID tenantId, UUID actorId, UUID relationshipId, long expectedVersion) {
        requireContext(tenantId, actorId);
        RelationshipRecord before = repository.findRelationship(tenantId, relationshipId);
        assertRelationshipMutable(before);
        assertExpectedVersion(before.version(), expectedVersion);
        if (before.primaryRelationship()) {
            return before;
        }
        RelationshipRecord after = repository.setPrimary(tenantId, actorId, relationshipId, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "PRIMARY_CHANGE", "CONTACT_ACCOUNT_RELATIONSHIP", relationshipId,
                new AuditChange(json(before), json(after)), now);
        recordRelationshipTimeline(tenantId, after, actorId, "crm.contact.relationship.primary.changed",
                "Primary contact relationship changed", now);
        return after;
    }

    @Transactional
    public RelationshipRecord deactivate(
            UUID tenantId, UUID actorId, UUID relationshipId, long expectedVersion) {
        return transition(tenantId, actorId, relationshipId, "INACTIVE", expectedVersion,
                "DEACTIVATE", "crm.contact.relationship.deactivated", "Contact relationship deactivated");
    }

    @Transactional
    public RelationshipRecord activate(
            UUID tenantId, UUID actorId, UUID relationshipId, long expectedVersion) {
        requireContext(tenantId, actorId);
        RelationshipRecord current = repository.findRelationship(tenantId, relationshipId);
        if ("ARCHIVED".equals(current.status())) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "An archived relationship must be reactivated through the controlled reactivation operation.");
        }
        return transition(tenantId, actorId, relationshipId, "ACTIVE", expectedVersion,
                "ACTIVATE", "crm.contact.relationship.activated", "Contact relationship activated");
    }

    @Transactional
    public RelationshipRecord archive(
            UUID tenantId, UUID actorId, UUID relationshipId, long expectedVersion) {
        return transition(tenantId, actorId, relationshipId, "ARCHIVED", expectedVersion,
                "ARCHIVE", "crm.contact.relationship.archived", "Contact relationship archived");
    }

    @Transactional
    public RelationshipRecord reactivate(
            UUID tenantId, UUID actorId, UUID relationshipId, long expectedVersion) {
        requireContext(tenantId, actorId);
        RelationshipRecord current = repository.findRelationship(tenantId, relationshipId);
        if (!"ARCHIVED".equals(current.status())) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "Only an archived relationship can be reactivated.");
        }
        return transition(tenantId, actorId, relationshipId, "ACTIVE", expectedVersion,
                "REACTIVATE", "crm.contact.relationship.reactivated", "Contact relationship reactivated");
    }

    public List<RelationshipHistoryRecord> relationshipHistory(
            UUID tenantId, UUID relationshipId, int limit) {
        requireTenant(tenantId);
        return repository.relationshipHistory(tenantId, relationshipId, boundedLimit(limit));
    }

    public List<OwnershipHistoryRecord> ownershipHistory(UUID tenantId, UUID contactId, int limit) {
        requireTenant(tenantId);
        return repository.ownershipHistory(tenantId, contactId, boundedLimit(limit));
    }

    public List<RelationshipRoleRecord> customRoles(UUID tenantId, boolean includeInactive) {
        requireTenant(tenantId);
        return repository.listRoles(tenantId, includeInactive);
    }

    @Transactional
    public RelationshipRoleRecord createRole(
            UUID tenantId, UUID actorId, CreateRelationshipRoleCommand command) {
        requireContext(tenantId, actorId);
        if (command.code() == null || !command.code().matches("[A-Za-z][A-Za-z0-9_]{1,79}")) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Role code must start with a letter and contain only letters, numbers, and underscores.");
        }
        if (command.nameAr() == null || command.nameAr().isBlank()
                || command.nameEn() == null || command.nameEn().isBlank()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Arabic and English role names are required.");
        }
        RelationshipRoleRecord created = repository.createRole(tenantId, actorId, command);
        audit.record(tenantId, actorId, "CREATE", "CONTACT_RELATIONSHIP_ROLE", created.id(),
                new AuditChange(null, json(created)), Instant.now());
        return created;
    }

    private RelationshipRecord transition(
            UUID tenantId, UUID actorId, UUID relationshipId, String targetStatus,
            long expectedVersion, String auditAction, String eventType, String summary) {
        requireContext(tenantId, actorId);
        RelationshipRecord before = repository.findRelationship(tenantId, relationshipId);
        assertExpectedVersion(before.version(), expectedVersion);
        if (targetStatus.equals(before.status())) {
            return before;
        }
        if ("ARCHIVED".equals(before.status()) && !"ACTIVE".equals(targetStatus)) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "Archived relationships cannot be modified without controlled reactivation.");
        }
        RelationshipRecord after = repository.changeStatus(
                tenantId, actorId, relationshipId, targetStatus, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, auditAction, "CONTACT_ACCOUNT_RELATIONSHIP", relationshipId,
                new AuditChange(json(before), json(after)), now);
        recordRelationshipTimeline(tenantId, after, actorId, eventType, summary, now);
        return after;
    }

    private void recordRelationshipTimeline(
            UUID tenantId, RelationshipRecord relationship, UUID actorId,
            String eventType, String summary, Instant now) {
        timeline.record(tenantId, "CONTACT", relationship.contactId(), eventType, summary,
                "CRM_CONTACT_RELATIONSHIP", relationship.id(), actorId, now);
        timeline.record(tenantId, "ACCOUNT", relationship.accountId(), eventType, summary,
                "CRM_CONTACT_RELATIONSHIP", relationship.id(), actorId, now);
    }

    private void validateRelationship(
            String roleCode, UUID customRoleId, LocalDate validFrom, LocalDate validTo,
            String decisionAuthority, UUID ownerUserId, UUID tenantId) {
        String normalizedRole = normalize(roleCode);
        if (!STANDARD_ROLES.contains(normalizedRole)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Unsupported relationship role.");
        }
        if ("OTHER".equals(normalizedRole) && customRoleId == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "customRoleId is required when roleCode is OTHER.");
        }
        if (!"OTHER".equals(normalizedRole) && customRoleId != null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "customRoleId is only allowed when roleCode is OTHER.");
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "validTo cannot be earlier than validFrom.");
        }
        String normalizedAuthority = decisionAuthority == null || decisionAuthority.isBlank()
                ? "NONE" : normalize(decisionAuthority);
        if (!DECISION_AUTHORITIES.contains(normalizedAuthority)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Unsupported decision authority.");
        }
        validateOwner(tenantId, ownerUserId);
    }

    private void validateProfile(UpdateContactProfileCommand command) {
        if (command.preferredLocale() != null && command.preferredLocale().length() > 35) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "preferredLocale is too long.");
        }
        if (command.timeZone() != null && command.timeZone().length() > 64) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "timeZone is too long.");
        }
    }

    private void validateOwner(UUID tenantId, UUID ownerUserId) {
        if (ownerUserId != null && !ownerValidation.isValidOwner(tenantId, ownerUserId)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Invalid owner.");
        }
    }

    private static void assertContactMutable(ContactProfileRecord profile) {
        if ("ARCHIVED".equals(profile.lifecycleStatus())) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "Archived contacts cannot be modified without restoration.");
        }
    }

    private static void assertRelationshipMutable(RelationshipRecord relationship) {
        if ("ARCHIVED".equals(relationship.status())) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "Archived relationships cannot be modified without controlled reactivation.");
        }
    }

    private static void assertExpectedVersion(long actualVersion, long expectedVersion) {
        if (actualVersion != expectedVersion) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
    }

    private static int boundedLimit(int limit) {
        if (limit <= 0) return 50;
        return Math.min(limit, 200);
    }

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireTenant(tenantId);
        if (actorId == null) throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private JsonNode json(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    private JsonNode jsonOwner(UUID ownerId) {
        var node = objectMapper.createObjectNode();
        if (ownerId == null) node.putNull("ownerUserId");
        else node.put("ownerUserId", ownerId.toString());
        return node;
    }
}
