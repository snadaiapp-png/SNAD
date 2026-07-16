package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class CustomerMasterUseCases {
    private static final Set<String> RISK_RATINGS = Set.of("UNASSESSED", "LOW", "MEDIUM", "HIGH", "RESTRICTED");
    private static final Set<String> TIERS = Set.of("STANDARD", "SILVER", "GOLD", "PLATINUM", "STRATEGIC");
    private static final Set<String> ADDRESS_TYPES = Set.of("REGISTERED", "BILLING", "SHIPPING", "OFFICE", "OTHER");
    private static final Set<String> IDENTIFIER_TYPES = Set.of(
            "COMMERCIAL_REGISTRATION", "TAX", "VAT", "NATIONAL_ID", "DUNS", "EXTERNAL", "OTHER");
    private static final Set<String> RELATIONSHIP_TYPES = Set.of(
            "PARENT", "SUBSIDIARY", "PARTNER", "SUPPLIER", "CUSTOMER", "AFFILIATE", "OTHER");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CustomerMasterRepository repository;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public CustomerMasterUseCases(
            CustomerMasterRepository repository,
            AuditPort audit,
            TimelineEventPort timeline,
            ObjectMapper mapper) {
        this.repository = repository;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    public CustomerMasterProfile getProfile(UUID tenantId, UUID accountId) {
        return repository.findProfile(tenantId, accountId);
    }

    public List<AccountAddress> listAddresses(UUID tenantId, UUID accountId) {
        return repository.listAddresses(tenantId, accountId);
    }

    public List<AccountIdentifier> listIdentifiers(UUID tenantId, UUID accountId) {
        return repository.listIdentifiers(tenantId, accountId);
    }

    public List<AccountRelationship> listRelationships(UUID tenantId, UUID accountId) {
        return repository.listRelationships(tenantId, accountId);
    }

    public List<DuplicateCandidate> duplicateCandidates(UUID tenantId, UUID accountId, int limit) {
        return repository.findDuplicateCandidates(tenantId, accountId, Math.max(1, Math.min(limit, 100)));
    }

    @Transactional
    public CustomerMasterProfile updateProfile(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UpdateCustomerMasterCommand command,
            long expectedVersion) {
        validateProfile(command);
        CustomerMasterProfile before = repository.findProfile(tenantId, accountId);
        CustomerMasterProfile updated = repository.updateProfile(tenantId, actorId, accountId, command, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE_CUSTOMER_MASTER", "ACCOUNT", accountId,
                new AuditChange(json(before), json(updated)), now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.master.updated",
                "Enterprise customer profile updated", "CRM_ACCOUNT", accountId, actorId, now);
        return updated;
    }

    @Transactional
    public AccountAddress addAddress(
            UUID tenantId, UUID actorId, UUID accountId, CreateAddressCommand command) {
        validateAddress(command);
        AccountAddress address = repository.addAddress(tenantId, actorId, accountId, command);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE_ADDRESS", "ACCOUNT_ADDRESS", address.id(),
                new AuditChange(null, json(address)), now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.address.created",
                "Customer address added", "CRM_ACCOUNT_ADDRESS", address.id(), actorId, now);
        return address;
    }

    @Transactional
    public void deactivateAddress(UUID tenantId, UUID actorId, UUID accountId, UUID addressId) {
        repository.deactivateAddress(tenantId, actorId, accountId, addressId);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "DEACTIVATE_ADDRESS", "ACCOUNT_ADDRESS", addressId,
                new AuditChange(null, mapper.createObjectNode().put("active", false)), now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.address.deactivated",
                "Customer address deactivated", "CRM_ACCOUNT_ADDRESS", addressId, actorId, now);
    }

    @Transactional
    public AccountIdentifier addIdentifier(
            UUID tenantId, UUID actorId, UUID accountId, CreateIdentifierCommand command) {
        validateIdentifier(command);
        AccountIdentifier identifier = repository.addIdentifier(tenantId, actorId, accountId, command);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE_IDENTIFIER", "ACCOUNT_IDENTIFIER", identifier.id(),
                new AuditChange(null, json(identifier)), now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.identifier.created",
                "Customer identifier added", "CRM_ACCOUNT_IDENTIFIER", identifier.id(), actorId, now);
        return identifier;
    }

    @Transactional
    public AccountRelationship addRelationship(
            UUID tenantId, UUID actorId, UUID accountId, CreateRelationshipCommand command) {
        validateRelationship(command);
        AccountRelationship relationship = repository.addRelationship(tenantId, actorId, accountId, command);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE_RELATIONSHIP", "ACCOUNT_RELATIONSHIP", relationship.id(),
                new AuditChange(null, json(relationship)), now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.relationship.created",
                "Customer relationship added", "CRM_ACCOUNT_RELATIONSHIP", relationship.id(), actorId, now);
        return relationship;
    }

    @Transactional
    public MergeResult merge(
            UUID tenantId,
            UUID actorId,
            UUID sourceAccountId,
            UUID targetAccountId,
            long expectedSourceVersion,
            long expectedTargetVersion,
            String reason) {
        if (sourceAccountId == null || targetAccountId == null) {
            throw validation("Source and target account IDs are required.");
        }
        CustomerMasterProfile sourceBefore = repository.findProfile(tenantId, sourceAccountId);
        CustomerMasterProfile targetBefore = repository.findProfile(tenantId, targetAccountId);
        MergeResult result = repository.mergeAccounts(tenantId, actorId, sourceAccountId, targetAccountId,
                expectedSourceVersion, expectedTargetVersion, clean(reason, 500));
        Instant now = result.mergedAt();
        var mergeState = mapper.createObjectNode()
                .put("sourceAccountId", sourceAccountId.toString())
                .put("targetAccountId", targetAccountId.toString())
                .put("contactsMoved", result.contactsMoved())
                .put("opportunitiesMoved", result.opportunitiesMoved())
                .put("activitiesMoved", result.activitiesMoved())
                .put("addressesMoved", result.addressesMoved())
                .put("identifiersMoved", result.identifiersMoved())
                .put("relationshipsMoved", result.relationshipsMoved());
        audit.record(tenantId, actorId, "MERGE", "ACCOUNT", sourceAccountId,
                new AuditChange(json(sourceBefore), mergeState), now);
        audit.record(tenantId, actorId, "MERGE_TARGET", "ACCOUNT", targetAccountId,
                new AuditChange(json(targetBefore), mergeState), now);
        timeline.record(tenantId, "ACCOUNT", sourceAccountId, "crm.account.merged",
                "Customer account merged into target", "CRM_ACCOUNT", targetAccountId, actorId, now);
        timeline.record(tenantId, "ACCOUNT", targetAccountId, "crm.account.merge.received",
                "Customer account merged into this record", "CRM_ACCOUNT", sourceAccountId, actorId, now);
        return result;
    }

    private void validateProfile(UpdateCustomerMasterCommand command) {
        if (command == null) throw validation("Customer master payload is required.");
        clean(command.legalName(), 240);
        clean(command.tradingName(), 240);
        clean(command.registrationNumber(), 120);
        clean(command.taxNumber(), 120);
        clean(command.industryCode(), 80);
        clean(command.customerSegment(), 80);
        clean(command.website(), 500);
        clean(command.primaryPhone(), 64);
        if (command.primaryEmail() != null && !command.primaryEmail().isBlank()
                && !EMAIL.matcher(command.primaryEmail().trim()).matches()) {
            throw validation("primaryEmail is invalid.");
        }
        if (command.countryCode() != null && !command.countryCode().matches("(?i)[A-Z]{2}")) {
            throw validation("countryCode must be an ISO 3166-1 alpha-2 code.");
        }
        if (command.riskRating() != null && !RISK_RATINGS.contains(upper(command.riskRating()))) {
            throw validation("riskRating is invalid.");
        }
        if (command.customerTier() != null && !TIERS.contains(upper(command.customerTier()))) {
            throw validation("customerTier is invalid.");
        }
        BigDecimal limit = command.creditLimit();
        if (limit != null && limit.signum() < 0) throw validation("creditLimit cannot be negative.");
        Integer terms = command.paymentTermsDays();
        if (terms != null && (terms < 0 || terms > 365)) {
            throw validation("paymentTermsDays must be between 0 and 365.");
        }
    }

    private void validateAddress(CreateAddressCommand command) {
        if (command == null) throw validation("Address payload is required.");
        if (!ADDRESS_TYPES.contains(upper(command.addressType()))) throw validation("addressType is invalid.");
        required(command.line1(), 240, "line1");
        required(command.city(), 120, "city");
        if (command.countryCode() == null || !command.countryCode().matches("(?i)[A-Z]{2}")) {
            throw validation("countryCode must be an ISO 3166-1 alpha-2 code.");
        }
        clean(command.label(), 120);
        clean(command.line2(), 240);
        clean(command.stateRegion(), 120);
        clean(command.postalCode(), 32);
    }

    private void validateIdentifier(CreateIdentifierCommand command) {
        if (command == null) throw validation("Identifier payload is required.");
        if (!IDENTIFIER_TYPES.contains(upper(command.identifierType()))) {
            throw validation("identifierType is invalid.");
        }
        required(command.identifierValue(), 180, "identifierValue");
        if (command.issuerCountryCode() != null && !command.issuerCountryCode().matches("(?i)[A-Z]{2}")) {
            throw validation("issuerCountryCode must be an ISO 3166-1 alpha-2 code.");
        }
    }

    private void validateRelationship(CreateRelationshipCommand command) {
        if (command == null || command.targetAccountId() == null) {
            throw validation("targetAccountId is required.");
        }
        if (!RELATIONSHIP_TYPES.contains(upper(command.relationshipType()))) {
            throw validation("relationshipType is invalid.");
        }
        if (command.effectiveFrom() != null && command.effectiveTo() != null
                && command.effectiveTo().isBefore(command.effectiveFrom())) {
            throw validation("effectiveTo cannot precede effectiveFrom.");
        }
        clean(command.notes(), 1000);
    }

    private JsonNode json(Object value) { return value == null ? null : mapper.valueToTree(value); }
    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
    private static String required(String value, int max, String field) {
        if (value == null || value.isBlank()) throw validation(field + " is required.");
        return clean(value, max);
    }
    private static String clean(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.length() > max) throw validation("Value exceeds maximum length " + max + ".");
        return cleaned.isEmpty() ? null : cleaned;
    }
    private static CrmContractException validation(String message) {
        return new CrmContractException(CrmErrorCode.VALIDATION_ERROR, message);
    }
}
