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

    public AccountAddress getAddress(UUID tenantId, UUID accountId, UUID addressId) {
        return repository.findAddress(tenantId, accountId, addressId);
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
        UpdateCustomerMasterCommand normalized = normalizeProfile(command);
        CustomerMasterProfile before = repository.findProfile(tenantId, accountId);
        CustomerMasterProfile updated = repository.updateProfile(
                tenantId, actorId, accountId, normalized, expectedVersion);
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
        CreateAddressCommand normalized = normalizeAddress(command);
        AccountAddress address = repository.addAddress(tenantId, actorId, accountId, normalized);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE_ADDRESS", "ACCOUNT_ADDRESS", address.id(),
                new AuditChange(null, json(address)), now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.address.created",
                "Customer address added", "CRM_ACCOUNT_ADDRESS", address.id(), actorId, now);
        return address;
    }

    @Transactional
    public void deactivateAddress(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UUID addressId,
            long expectedVersion) {
        repository.deactivateAddress(tenantId, actorId, accountId, addressId, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "DEACTIVATE_ADDRESS", "ACCOUNT_ADDRESS", addressId,
                new AuditChange(null, mapper.createObjectNode().put("active", false)), now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.address.deactivated",
                "Customer address deactivated", "CRM_ACCOUNT_ADDRESS", addressId, actorId, now);
    }

    @Transactional
    public AccountIdentifier addIdentifier(
            UUID tenantId, UUID actorId, UUID accountId, CreateIdentifierCommand command) {
        CreateIdentifierCommand normalized = normalizeIdentifier(command);
        AccountIdentifier identifier = repository.addIdentifier(tenantId, actorId, accountId, normalized);
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
        CreateRelationshipCommand normalized = normalizeRelationship(command);
        AccountRelationship relationship = repository.addRelationship(tenantId, actorId, accountId, normalized);
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
        if (sourceAccountId.equals(targetAccountId)) {
            throw validation("Source and target accounts must differ.");
        }
        String normalizedReason = required(reason, 500, "reason");
        CustomerMasterProfile sourceBefore = repository.findProfile(tenantId, sourceAccountId);
        CustomerMasterProfile targetBefore = repository.findProfile(tenantId, targetAccountId);
        MergeResult result = repository.mergeAccounts(tenantId, actorId, sourceAccountId, targetAccountId,
                expectedSourceVersion, expectedTargetVersion, normalizedReason);
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

    private UpdateCustomerMasterCommand normalizeProfile(UpdateCustomerMasterCommand command) {
        if (command == null) throw validation("Customer master payload is required.");
        String legalName = optional(command.legalName(), 240);
        String tradingName = optional(command.tradingName(), 240);
        String registrationNumber = optional(command.registrationNumber(), 120);
        String taxNumber = optional(command.taxNumber(), 120);
        String industryCode = optional(command.industryCode(), 80);
        String customerSegment = optional(command.customerSegment(), 80);
        String tier = upper(optional(command.customerTier(), 40));
        String website = optional(command.website(), 500);
        String email = lower(optional(command.primaryEmail(), 255));
        String phone = optional(command.primaryPhone(), 64);
        String country = upper(optional(command.countryCode(), 2));
        String risk = upper(optional(command.riskRating(), 24));
        if (email != null && !email.isBlank() && !EMAIL.matcher(email).matches()) {
            throw validation("primaryEmail is invalid.");
        }
        if (country != null && !country.matches("[A-Z]{2}")) {
            throw validation("countryCode must be an ISO 3166-1 alpha-2 code.");
        }
        if (risk != null && !RISK_RATINGS.contains(risk)) {
            throw validation("riskRating is invalid.");
        }
        if (tier != null && !TIERS.contains(tier)) {
            throw validation("customerTier is invalid.");
        }
        BigDecimal creditLimit = command.creditLimit();
        if (creditLimit != null) {
            if (creditLimit.signum() < 0) throw validation("creditLimit cannot be negative.");
            int integerDigits = creditLimit.precision() - creditLimit.scale();
            if (creditLimit.scale() > 2 || integerDigits > 16) {
                throw validation("creditLimit must fit NUMERIC(18,2).");
            }
        }
        Integer terms = command.paymentTermsDays();
        if (terms != null && (terms < 0 || terms > 365)) {
            throw validation("paymentTermsDays must be between 0 and 365.");
        }
        return new UpdateCustomerMasterCommand(
                legalName, tradingName, registrationNumber, taxNumber, industryCode,
                customerSegment, tier, website, email, phone, country, risk, creditLimit, terms);
    }

    private CreateAddressCommand normalizeAddress(CreateAddressCommand command) {
        if (command == null) throw validation("Address payload is required.");
        String type = upper(required(command.addressType(), 24, "addressType"));
        if (!ADDRESS_TYPES.contains(type)) throw validation("addressType is invalid.");
        String line1 = required(command.line1(), 240, "line1");
        String city = required(command.city(), 120, "city");
        String country = upper(required(command.countryCode(), 2, "countryCode"));
        if (!country.matches("[A-Z]{2}")) {
            throw validation("countryCode must be an ISO 3166-1 alpha-2 code.");
        }
        return new CreateAddressCommand(
                type,
                clean(command.label(), 120),
                line1,
                clean(command.line2(), 240),
                city,
                clean(command.stateRegion(), 120),
                clean(command.postalCode(), 32),
                country,
                command.primaryAddress());
    }

    private CreateIdentifierCommand normalizeIdentifier(CreateIdentifierCommand command) {
        if (command == null) throw validation("Identifier payload is required.");
        String type = upper(required(command.identifierType(), 40, "identifierType"));
        if (!IDENTIFIER_TYPES.contains(type)) {
            throw validation("identifierType is invalid.");
        }
        String value = required(command.identifierValue(), 180, "identifierValue");
        String country = upper(clean(command.issuerCountryCode(), 2));
        if (country != null && !country.matches("[A-Z]{2}")) {
            throw validation("issuerCountryCode must be an ISO 3166-1 alpha-2 code.");
        }
        return new CreateIdentifierCommand(type, value, country,
                command.primaryIdentifier(), command.verified());
    }

    private CreateRelationshipCommand normalizeRelationship(CreateRelationshipCommand command) {
        if (command == null || command.targetAccountId() == null) {
            throw validation("targetAccountId is required.");
        }
        String type = upper(required(command.relationshipType(), 40, "relationshipType"));
        if (!RELATIONSHIP_TYPES.contains(type)) {
            throw validation("relationshipType is invalid.");
        }
        if (command.effectiveFrom() != null && command.effectiveTo() != null
                && command.effectiveTo().isBefore(command.effectiveFrom())) {
            throw validation("effectiveTo cannot precede effectiveFrom.");
        }
        return new CreateRelationshipCommand(
                command.targetAccountId(), type, command.effectiveFrom(), command.effectiveTo(),
                clean(command.notes(), 1000));
    }

    private JsonNode json(Object value) { return value == null ? null : mapper.valueToTree(value); }
    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
    private static String required(String value, int max, String field) {
        if (value == null || value.isBlank()) throw validation(field + " is required.");
        return clean(value, max);
    }
    private static String optional(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.length() > max) throw validation("Value exceeds maximum length " + max + ".");
        return cleaned;
    }
    private static String clean(String value, int max) {
        String cleaned = optional(value, max);
        return cleaned == null || cleaned.isEmpty() ? null : cleaned;
    }
    private static CrmContractException validation(String message) {
        return new CrmContractException(CrmErrorCode.VALIDATION_ERROR, message);
    }
}
