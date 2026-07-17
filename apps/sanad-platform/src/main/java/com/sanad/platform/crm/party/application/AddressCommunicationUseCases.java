package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.*;
import com.sanad.platform.crm.party.domain.LegacyAddressProjectionPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Application rules for CRM-007 addresses and communication methods. */
public class AddressCommunicationUseCases {
    private static final Set<String> OWNER_TYPES = Set.of("ACCOUNT", "PERSON");
    private static final Set<String> ADDRESS_TYPES = Set.of("REGISTERED", "BILLING", "SHIPPING", "OFFICE", "HOME", "OTHER");
    private static final Set<String> METHOD_TYPES = Set.of("EMAIL", "PHONE", "MOBILE", "FAX", "WHATSAPP", "SMS", "MESSAGING_HANDLE", "WEBSITE", "OTHER");
    private static final Set<String> PHONE_TYPES = Set.of("PHONE", "MOBILE", "FAX", "WHATSAPP", "SMS");
    private static final Set<String> PRIVACY = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");
    private static final Set<String> VERIFICATION = Set.of("UNVERIFIED", "PENDING", "VERIFIED", "FAILED", "REVOKED");
    private static final Set<String> STATUS = Set.of("ACTIVE", "INACTIVE", "ARCHIVED");
    private static final Set<String> EXTENSION_FIELDS = Set.of(
            "buildingNumber", "additionalNumber", "unitNumber", "shortAddress", "poBox",
            "county", "province", "suburb", "landmark");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final AddressCommunicationRepository repository;
    private final LegacyAddressProjectionPort legacyAddresses;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public AddressCommunicationUseCases(
            AddressCommunicationRepository repository,
            LegacyAddressProjectionPort legacyAddresses,
            AuditPort audit,
            TimelineEventPort timeline,
            ObjectMapper mapper) {
        this.repository = repository;
        this.legacyAddresses = legacyAddresses;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    public AddressRecord address(UUID tenantId, UUID addressId) {
        return repository.address(required(tenantId, "tenantId"), required(addressId, "addressId"));
    }

    public List<AddressRecord> addresses(
            UUID tenantId, String ownerType, UUID ownerId, boolean includeArchived,
            int limit, Instant beforeUpdatedAt, UUID beforeId) {
        return repository.addresses(required(tenantId, "tenantId"), owner(ownerType), required(ownerId, "ownerId"),
                includeArchived, bounded(limit), beforeUpdatedAt, beforeId);
    }

    @Transactional
    public AddressRecord createAddress(
            UUID tenantId, UUID actorId, String ownerType, UUID ownerId, CreateAddressCommand command) {
        CreateAddressCommand clean = validateAddress(command);
        AddressRecord created = repository.createAddress(required(tenantId, "tenantId"), required(actorId, "actorId"),
                owner(ownerType), required(ownerId, "ownerId"), clean);
        legacyAddresses.upsert(tenantId, actorId, created);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE_ADDRESS", "CRM_ADDRESS", created.id(),
                new AuditChange(null, json(created)), now);
        timeline.record(tenantId, created.ownerType(), created.ownerId(), "crm.address.created",
                "Address created", "CRM_ADDRESS", created.id(), actorId, now);
        return created;
    }

    @Transactional
    public AddressRecord updateAddress(
            UUID tenantId, UUID actorId, UUID addressId, UpdateAddressCommand command, long expectedVersion) {
        AddressRecord before = address(tenantId, addressId);
        UpdateAddressCommand clean = validateAddressUpdate(command, before);
        AddressRecord after = repository.updateAddress(tenantId, required(actorId, "actorId"), addressId, clean, expectedVersion);
        legacyAddresses.upsert(tenantId, actorId, after);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE_ADDRESS", "CRM_ADDRESS", addressId,
                new AuditChange(json(before), json(after)), now);
        timeline.record(tenantId, after.ownerType(), after.ownerId(), "crm.address.updated",
                "Address updated", "CRM_ADDRESS", addressId, actorId, now);
        return after;
    }

    @Transactional
    public AddressRecord setPrimaryAddress(
            UUID tenantId, UUID actorId, UUID addressId, long expectedVersion) {
        AddressRecord before = address(tenantId, addressId);
        if ("ARCHIVED".equals(before.status())) throw validation("An archived address cannot be primary.");
        AddressRecord after = repository.setPrimaryAddress(tenantId, required(actorId, "actorId"), addressId, expectedVersion);
        legacyAddresses.upsert(tenantId, actorId, after);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "SET_PRIMARY_ADDRESS", "CRM_ADDRESS", addressId,
                new AuditChange(json(before), json(after)), now);
        timeline.record(tenantId, after.ownerType(), after.ownerId(), "crm.address.primary.changed",
                "Primary address changed", "CRM_ADDRESS", addressId, actorId, now);
        return after;
    }

    @Transactional
    public AddressRecord changeAddressStatus(
            UUID tenantId, UUID actorId, UUID addressId, String status, long expectedVersion) {
        String target = status(status);
        AddressRecord before = address(tenantId, addressId);
        AddressRecord after = repository.changeAddressStatus(tenantId, required(actorId, "actorId"), addressId,
                target, expectedVersion);
        legacyAddresses.upsert(tenantId, actorId, after);
        Instant now = Instant.now();
        String action = "ARCHIVED".equals(target) ? "ARCHIVE_ADDRESS" : "REACTIVATE_ADDRESS";
        String event = "ARCHIVED".equals(target) ? "crm.address.archived" : "crm.address.reactivated";
        audit.record(tenantId, actorId, action, "CRM_ADDRESS", addressId,
                new AuditChange(json(before), json(after)), now);
        timeline.record(tenantId, after.ownerType(), after.ownerId(), event,
                "Address status changed to " + target, "CRM_ADDRESS", addressId, actorId, now);
        return after;
    }

    public List<AddressHistoryRecord> addressHistory(UUID tenantId, UUID addressId, int limit) {
        address(tenantId, addressId);
        return repository.addressHistory(tenantId, addressId, bounded(limit));
    }

    public CommunicationMethodRecord communicationMethod(UUID tenantId, UUID communicationMethodId) {
        return repository.communicationMethod(required(tenantId, "tenantId"), required(communicationMethodId, "communicationMethodId"));
    }

    public List<CommunicationMethodRecord> communicationMethods(
            UUID tenantId, String ownerType, UUID ownerId, boolean includeArchived,
            String methodType, String verificationStatus, int limit, Instant beforeUpdatedAt, UUID beforeId) {
        String type = methodType == null || methodType.isBlank() ? null : methodType(methodType);
        String verification = verificationStatus == null || verificationStatus.isBlank()
                ? null : verification(verificationStatus);
        return repository.communicationMethods(required(tenantId, "tenantId"), owner(ownerType),
                required(ownerId, "ownerId"), includeArchived, type, verification,
                bounded(limit), beforeUpdatedAt, beforeId);
    }

    @Transactional
    public CommunicationMethodRecord createCommunicationMethod(
            UUID tenantId, UUID actorId, String ownerType, UUID ownerId,
            CreateCommunicationMethodCommand command, String countryHint) {
        CreateCommunicationMethodCommand clean = normalize(command, countryHint);
        CommunicationMethodRecord created = repository.createCommunicationMethod(
                required(tenantId, "tenantId"), required(actorId, "actorId"), owner(ownerType),
                required(ownerId, "ownerId"), clean);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE_COMMUNICATION_METHOD", "CRM_COMMUNICATION_METHOD", created.id(),
                new AuditChange(null, communicationAudit(created)), now);
        timeline.record(tenantId, created.ownerType(), created.ownerId(), "crm.communication.created",
                "Communication method created", "CRM_COMMUNICATION_METHOD", created.id(), actorId, now);
        return created;
    }

    @Transactional
    public CommunicationMethodRecord updateCommunicationMethod(
            UUID tenantId, UUID actorId, UUID communicationMethodId,
            UpdateCommunicationMethodCommand command, String countryHint, long expectedVersion) {
        CommunicationMethodRecord before = communicationMethod(tenantId, communicationMethodId);
        UpdateCommunicationMethodCommand clean = normalize(command, before.methodType(), countryHint, before);
        CommunicationMethodRecord after = repository.updateCommunicationMethod(
                tenantId, required(actorId, "actorId"), communicationMethodId, clean, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE_COMMUNICATION_METHOD", "CRM_COMMUNICATION_METHOD", communicationMethodId,
                new AuditChange(communicationAudit(before), communicationAudit(after)), now);
        timeline.record(tenantId, after.ownerType(), after.ownerId(), "crm.communication.updated",
                "Communication method updated", "CRM_COMMUNICATION_METHOD", communicationMethodId, actorId, now);
        if (!before.privacyClassification().equals(after.privacyClassification())) {
            timeline.record(tenantId, after.ownerType(), after.ownerId(), "crm.communication.privacy.changed",
                    "Communication privacy classification changed", "CRM_COMMUNICATION_METHOD", communicationMethodId, actorId, now);
        }
        return after;
    }

    @Transactional
    public CommunicationMethodRecord setPreferred(
            UUID tenantId, UUID actorId, UUID communicationMethodId, long expectedVersion) {
        CommunicationMethodRecord before = communicationMethod(tenantId, communicationMethodId);
        if (!"ACTIVE".equals(before.status())) throw validation("Only active communication methods can be preferred.");
        if (Set.of("FAILED", "REVOKED").contains(before.verificationStatus())) {
            throw validation("Failed or revoked communication methods cannot be preferred.");
        }
        CommunicationMethodRecord after = repository.setPreferredCommunicationMethod(
                tenantId, required(actorId, "actorId"), communicationMethodId, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "SET_PREFERRED_COMMUNICATION", "CRM_COMMUNICATION_METHOD", communicationMethodId,
                new AuditChange(communicationAudit(before), communicationAudit(after)), now);
        timeline.record(tenantId, after.ownerType(), after.ownerId(), "crm.communication.preferred.changed",
                "Preferred communication channel changed", "CRM_COMMUNICATION_METHOD", communicationMethodId, actorId, now);
        return after;
    }

    @Transactional
    public CommunicationMethodRecord changeVerification(
            UUID tenantId, UUID actorId, UUID communicationMethodId,
            String verificationStatus, long expectedVersion) {
        CommunicationMethodRecord before = communicationMethod(tenantId, communicationMethodId);
        String target = verification(verificationStatus);
        validateVerificationTransition(before.verificationStatus(), target);
        Instant verifiedAt = "VERIFIED".equals(target) ? Instant.now() : null;
        CommunicationMethodRecord after = repository.changeVerification(
                tenantId, required(actorId, "actorId"), communicationMethodId, target, verifiedAt, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CHANGE_COMMUNICATION_VERIFICATION", "CRM_COMMUNICATION_METHOD", communicationMethodId,
                new AuditChange(communicationAudit(before), communicationAudit(after)), now);
        String event = switch (target) {
            case "PENDING" -> "crm.communication.verification.requested";
            case "VERIFIED" -> "crm.communication.verification.completed";
            case "FAILED", "REVOKED" -> "crm.communication.verification.failed";
            default -> "crm.communication.verification.reset";
        };
        timeline.record(tenantId, after.ownerType(), after.ownerId(), event,
                "Communication verification changed to " + target,
                "CRM_COMMUNICATION_METHOD", communicationMethodId, actorId, now);
        return after;
    }

    @Transactional
    public CommunicationMethodRecord changeCommunicationStatus(
            UUID tenantId, UUID actorId, UUID communicationMethodId, String status, long expectedVersion) {
        String target = status(status);
        CommunicationMethodRecord before = communicationMethod(tenantId, communicationMethodId);
        CommunicationMethodRecord after = repository.changeCommunicationStatus(
                tenantId, required(actorId, "actorId"), communicationMethodId, target, expectedVersion);
        Instant now = Instant.now();
        String action = "ARCHIVED".equals(target) ? "ARCHIVE_COMMUNICATION_METHOD" : "REACTIVATE_COMMUNICATION_METHOD";
        String event = "ARCHIVED".equals(target) ? "crm.communication.archived" : "crm.communication.reactivated";
        audit.record(tenantId, actorId, action, "CRM_COMMUNICATION_METHOD", communicationMethodId,
                new AuditChange(communicationAudit(before), communicationAudit(after)), now);
        timeline.record(tenantId, after.ownerType(), after.ownerId(), event,
                "Communication status changed to " + target,
                "CRM_COMMUNICATION_METHOD", communicationMethodId, actorId, now);
        return after;
    }

    public List<CommunicationHistoryRecord> communicationHistory(
            UUID tenantId, UUID communicationMethodId, int limit) {
        communicationMethod(tenantId, communicationMethodId);
        return repository.communicationHistory(tenantId, communicationMethodId, bounded(limit));
    }

    public CommunicationMethodRecord masked(CommunicationMethodRecord record, boolean exposeSensitive) {
        if (record == null || exposeSensitive || Set.of("PUBLIC", "INTERNAL").contains(record.privacyClassification())) {
            return record;
        }
        String masked = mask(record.methodType(), record.displayValue());
        return new CommunicationMethodRecord(record.id(), record.version(), record.ownerType(), record.ownerId(),
                record.methodType(), null, null, masked, record.label(), record.preferred(), record.verified(),
                record.verificationStatus(), record.verifiedAt(), record.privacyClassification(),
                record.consentStateReference(), record.usagePurpose(), record.status(), record.validFrom(),
                record.validTo(), record.createdAt(), record.updatedAt(), record.archivedAt());
    }

    private CreateAddressCommand validateAddress(CreateAddressCommand command) {
        if (command == null) throw validation("Address payload is required.");
        String country = country(command.countryCode());
        String extension = extension(command.countryExtensionJson(), country);
        dates(command.validFrom(), command.validTo());
        coordinates(command.latitude(), command.longitude());
        return new CreateAddressCommand(addressType(command.addressType()), clean(command.label(), 120),
                clean(command.rawFormattedAddress(), 1200), required(command.line1(), 240, "line1"),
                clean(command.line2(), 240), clean(command.line3(), 240), clean(command.district(), 160),
                required(command.city(), 160, "city"), clean(command.stateRegion(), 160),
                clean(command.postalCode(), 40), country, extension, command.latitude(), command.longitude(),
                command.primaryAddress(), command.verified(), clean(command.verificationSource(), 120),
                command.validFrom(), command.validTo());
    }

    private UpdateAddressCommand validateAddressUpdate(UpdateAddressCommand command, AddressRecord current) {
        if (command == null) throw validation("Address payload is required.");
        String country = command.countryCode() == null ? current.countryCode() : country(command.countryCode());
        String extension = command.countryExtensionJson() == null
                ? current.countryExtensionJson() : extension(command.countryExtensionJson(), country);
        LocalDate from = command.validFrom() == null ? current.validFrom() : command.validFrom();
        LocalDate to = command.validTo() == null ? current.validTo() : command.validTo();
        dates(from, to);
        BigDecimal latitude = command.latitude() == null ? current.latitude() : command.latitude();
        BigDecimal longitude = command.longitude() == null ? current.longitude() : command.longitude();
        coordinates(latitude, longitude);
        return new UpdateAddressCommand(
                command.addressType() == null ? current.addressType() : addressType(command.addressType()),
                command.label() == null ? current.label() : clean(command.label(), 120),
                command.rawFormattedAddress() == null ? current.rawFormattedAddress() : clean(command.rawFormattedAddress(), 1200),
                command.line1() == null ? current.line1() : required(command.line1(), 240, "line1"),
                command.line2() == null ? current.line2() : clean(command.line2(), 240),
                command.line3() == null ? current.line3() : clean(command.line3(), 240),
                command.district() == null ? current.district() : clean(command.district(), 160),
                command.city() == null ? current.city() : required(command.city(), 160, "city"),
                command.stateRegion() == null ? current.stateRegion() : clean(command.stateRegion(), 160),
                command.postalCode() == null ? current.postalCode() : clean(command.postalCode(), 40),
                country, extension, latitude, longitude,
                command.verified() == null ? current.verified() : command.verified(),
                command.verificationSource() == null ? current.verificationSource() : clean(command.verificationSource(), 120),
                from, to);
    }

    private CreateCommunicationMethodCommand normalize(CreateCommunicationMethodCommand command, String countryHint) {
        if (command == null) throw validation("Communication method payload is required.");
        String type = methodType(command.methodType());
        String raw = required(command.rawValue(), 1000, "rawValue");
        String normalized = normalizeValue(type, raw, countryHint);
        dates(command.validFrom(), command.validTo());
        return new CreateCommunicationMethodCommand(type, raw, normalized,
                display(type, raw, normalized, command.displayValue()), clean(command.label(), 120),
                command.preferred(), privacy(command.privacyClassification()),
                clean(command.consentStateReference(), 120), clean(command.usagePurpose(), 120),
                command.validFrom(), command.validTo());
    }

    private UpdateCommunicationMethodCommand normalize(
            UpdateCommunicationMethodCommand command, String type, String countryHint, CommunicationMethodRecord current) {
        if (command == null) throw validation("Communication method payload is required.");
        String raw = command.rawValue() == null ? current.rawValue() : required(command.rawValue(), 1000, "rawValue");
        String normalized = normalizeValue(type, raw, countryHint);
        LocalDate from = command.validFrom() == null ? current.validFrom() : command.validFrom();
        LocalDate to = command.validTo() == null ? current.validTo() : command.validTo();
        dates(from, to);
        return new UpdateCommunicationMethodCommand(raw, normalized,
                display(type, raw, normalized, command.displayValue()),
                command.label() == null ? current.label() : clean(command.label(), 120),
                command.privacyClassification() == null ? current.privacyClassification() : privacy(command.privacyClassification()),
                command.consentStateReference() == null ? current.consentStateReference() : clean(command.consentStateReference(), 120),
                command.usagePurpose() == null ? current.usagePurpose() : clean(command.usagePurpose(), 120),
                from, to);
    }

    private String normalizeValue(String type, String raw, String countryHint) {
        String value = raw.trim();
        if ("EMAIL".equals(type)) {
            if (!EMAIL.matcher(value).matches()) throw validation("Email format is invalid.");
            return value.toLowerCase(Locale.ROOT);
        }
        if (PHONE_TYPES.contains(type)) return normalizePhone(value, countryHint);
        if ("WEBSITE".equals(type)) {
            try {
                URI uri = URI.create(value);
                if (uri.getScheme() == null || uri.getHost() == null
                        || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
                    throw validation("Website must be an absolute HTTP or HTTPS URL.");
                }
                return uri.normalize().toString();
            } catch (IllegalArgumentException exception) {
                throw validation("Website format is invalid.");
            }
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String raw, String countryHint) {
        String compact = raw.replaceAll("[\\s().-]", "");
        if (compact.startsWith("00")) compact = "+" + compact.substring(2);
        if (E164.matcher(compact).matches()) return compact;
        String hint = countryHint == null ? null : countryHint.trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(hint)) {
            if (compact.matches("05[0-9]{8}")) compact = "+966" + compact.substring(1);
            else if (compact.matches("5[0-9]{8}")) compact = "+966" + compact;
            else if (compact.matches("966[0-9]{9}")) compact = "+" + compact;
            if (E164.matcher(compact).matches()) return compact;
        }
        throw validation("Phone number must be E.164 or include an explicit supported country hint.");
    }

    private String extension(String value, String countryCode) {
        if (value == null || value.isBlank()) return null;
        try {
            JsonNode node = mapper.readTree(value);
            if (!node.isObject()) throw validation("countryExtension must be a JSON object.");
            if (node.size() > 12) throw validation("countryExtension contains too many fields.");
            var fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!EXTENSION_FIELDS.contains(field)) throw validation("Unsupported countryExtension field: " + field);
                JsonNode item = node.get(field);
                if (!(item.isTextual() || item.isNumber() || item.isBoolean() || item.isNull())) {
                    throw validation("countryExtension fields must be scalar values.");
                }
                if (item.isTextual() && item.asText().length() > 160) {
                    throw validation("countryExtension value exceeds 160 characters.");
                }
            }
            if ("SA".equals(countryCode) && node.has("shortAddress")
                    && !node.get("shortAddress").asText().matches("[A-Za-z0-9 -]{4,32}")) {
                throw validation("Saudi shortAddress format is invalid.");
            }
            return mapper.writeValueAsString(node);
        } catch (CrmContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw validation("countryExtension is invalid JSON.");
        }
    }

    private ObjectNode communicationAudit(CommunicationMethodRecord value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", value.id().toString());
        node.put("ownerType", value.ownerType());
        node.put("ownerId", value.ownerId().toString());
        node.put("methodType", value.methodType());
        node.put("preferred", value.preferred());
        node.put("verificationStatus", value.verificationStatus());
        node.put("privacyClassification", value.privacyClassification());
        node.put("status", value.status());
        node.put("version", value.version());
        return node;
    }

    private JsonNode json(Object value) { return value == null ? null : mapper.valueToTree(value); }
    private static int bounded(int limit) { return limit <= 0 ? 50 : Math.min(limit, 200); }
    private static String owner(String value) { return allowed(value, OWNER_TYPES, "ownerType"); }
    private static String addressType(String value) { return allowed(value, ADDRESS_TYPES, "addressType"); }
    private static String methodType(String value) { return allowed(value, METHOD_TYPES, "methodType"); }
    private static String privacy(String value) {
        return value == null || value.isBlank() ? "INTERNAL" : allowed(value, PRIVACY, "privacyClassification");
    }
    private static String verification(String value) { return allowed(value, VERIFICATION, "verificationStatus"); }
    private static String status(String value) { return allowed(value, STATUS, "status"); }
    private static String country(String value) {
        String country = required(value, 2, "countryCode").toUpperCase(Locale.ROOT);
        if (!country.matches("[A-Z]{2}")) throw validation("countryCode must be ISO 3166-1 alpha-2.");
        return country;
    }
    private static String allowed(String value, Set<String> values, String field) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !values.contains(normalized)) throw validation(field + " is invalid.");
        return normalized;
    }
    private static void dates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) throw validation("validTo cannot precede validFrom.");
    }
    private static void coordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) throw validation("latitude is invalid.");
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) throw validation("longitude is invalid.");
    }
    private static void validateVerificationTransition(String current, String target) {
        if (current.equals(target)) return;
        boolean allowed = switch (current) {
            case "UNVERIFIED" -> Set.of("PENDING", "VERIFIED").contains(target);
            case "PENDING" -> Set.of("VERIFIED", "FAILED", "UNVERIFIED").contains(target);
            case "VERIFIED" -> Set.of("REVOKED", "UNVERIFIED").contains(target);
            case "FAILED" -> Set.of("PENDING", "UNVERIFIED").contains(target);
            case "REVOKED" -> Set.of("PENDING", "UNVERIFIED").contains(target);
            default -> false;
        };
        if (!allowed) throw validation("Verification transition is invalid.");
    }
    private static String display(String type, String raw, String normalized, String proposed) {
        if (proposed != null && !proposed.isBlank()) return clean(proposed, 1000);
        return PHONE_TYPES.contains(type) ? normalized : raw.trim();
    }
    private static String mask(String type, String value) {
        if (value == null || value.isBlank()) return "••••";
        if ("EMAIL".equals(type)) {
            int at = value.indexOf('@');
            if (at > 0) return value.substring(0, 1) + "***" + value.substring(at);
        }
        if (PHONE_TYPES.contains(type)) {
            String digits = value.replaceAll("\\D", "");
            return digits.length() <= 4 ? "••••" : "••••" + digits.substring(digits.length() - 4);
        }
        return "••••";
    }
    private static <T> T required(T value, String field) {
        if (value == null) throw validation(field + " is required.");
        return value;
    }
    private static String required(String value, int max, String field) {
        String result = clean(value, max);
        if (result == null) throw validation(field + " is required.");
        return result;
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
