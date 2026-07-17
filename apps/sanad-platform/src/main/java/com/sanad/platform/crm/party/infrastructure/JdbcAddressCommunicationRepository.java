package com.sanad.platform.crm.party.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public class JdbcAddressCommunicationRepository implements AddressCommunicationRepository {
    private static final String ADDRESS_COLUMNS = "id,version,owner_type,owner_id,address_type,label," +
            "raw_formatted_address,line1,line2,line3,district,city,state_region,postal_code,country_code," +
            "country_extension_json,latitude,longitude,primary_address,verified,verification_source,status," +
            "valid_from,valid_to,created_at,updated_at,archived_at";
    private static final String COMMUNICATION_COLUMNS = "id,version,owner_type,owner_id,method_type,raw_value," +
            "normalized_value,display_value,label,preferred,verified,verification_status,verified_at," +
            "privacy_classification,consent_state_reference,usage_purpose,status,valid_from,valid_to," +
            "created_at,updated_at,archived_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcAddressCommunicationRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public AddressRecord address(UUID tenantId, UUID addressId) {
        try {
            return jdbc.queryForObject(
                    "SELECT " + ADDRESS_COLUMNS + " FROM crm_party_addresses WHERE tenant_id=:tenantId AND id=:id",
                    p().addValue("tenantId", tenantId).addValue("id", addressId),
                    (rs, rowNum) -> address(rs));
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("Address not found.");
        }
    }

    @Override
    public List<AddressRecord> addresses(
            UUID tenantId, String ownerType, UUID ownerId, boolean includeArchived,
            int limit, Instant beforeUpdatedAt, UUID beforeId) {
        ownerExists(tenantId, ownerType, ownerId);
        String sql = "SELECT " + ADDRESS_COLUMNS + " FROM crm_party_addresses " +
                "WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId " +
                (includeArchived ? "" : "AND status<>'ARCHIVED' ") +
                "AND (:beforeTime IS NULL OR updated_at<:beforeTime " +
                "OR (updated_at=:beforeTime AND (:beforeId IS NULL OR id<:beforeId))) " +
                "ORDER BY updated_at DESC,id DESC LIMIT :limit";
        return jdbc.query(sql, p().addValue("tenantId", tenantId).addValue("ownerType", ownerType)
                        .addValue("ownerId", ownerId).addValue("beforeTime", timestamp(beforeUpdatedAt))
                        .addValue("beforeId", beforeId).addValue("limit", limit),
                (rs, rowNum) -> address(rs));
    }

    @Override
    public AddressRecord createAddress(
            UUID tenantId, UUID actorId, String ownerType, UUID ownerId, CreateAddressCommand command) {
        ownerExists(tenantId, ownerType, ownerId);
        Instant now = Instant.now();
        if (command.primaryAddress()) clearPrimaryAddresses(
                tenantId, actorId, ownerType, ownerId, command.addressType(), now, null);
        UUID id = UUID.randomUUID();
        MapSqlParameterSource params = ownerParams(tenantId, ownerType, ownerId)
                .addValue("id", id).addValue("actorId", actorId).addValue("now", timestamp(now))
                .addValue("addressType", command.addressType()).addValue("label", command.label())
                .addValue("raw", command.rawFormattedAddress()).addValue("line1", command.line1())
                .addValue("line2", command.line2()).addValue("line3", command.line3())
                .addValue("district", command.district()).addValue("city", command.city())
                .addValue("stateRegion", command.stateRegion()).addValue("postalCode", command.postalCode())
                .addValue("countryCode", command.countryCode()).addValue("extension", command.countryExtensionJson())
                .addValue("latitude", command.latitude()).addValue("longitude", command.longitude())
                .addValue("primary", command.primaryAddress()).addValue("primarySlot", command.primaryAddress() ? 1 : null)
                .addValue("verified", command.verified()).addValue("verificationSource", command.verificationSource())
                .addValue("validFrom", date(command.validFrom())).addValue("validTo", date(command.validTo()));
        jdbc.update("INSERT INTO crm_party_addresses (id,tenant_id,version,owner_type,owner_id,account_id,contact_id," +
                        "address_type,label,raw_formatted_address,line1,line2,line3,district,city,state_region,postal_code," +
                        "country_code,country_extension_json,latitude,longitude,primary_address,primary_slot,verified," +
                        "verification_source,status,valid_from,valid_to,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,:ownerType,:ownerId,:accountId,:contactId,:addressType,:label,:raw," +
                        ":line1,:line2,:line3,:district,:city,:stateRegion,:postalCode,:countryCode,:extension,:latitude," +
                        ":longitude,:primary,:primarySlot,:verified,:verificationSource,'ACTIVE',:validFrom,:validTo," +
                        ":actorId,:actorId,:now,:now)", params);
        AddressRecord created = address(tenantId, id);
        addressHistory(tenantId, actorId, created, "CREATED", null);
        syncLegacyAccountAddress(created, tenantId, actorId);
        return created;
    }

    @Override
    public AddressRecord updateAddress(
            UUID tenantId, UUID actorId, UUID addressId, UpdateAddressCommand command, long expectedVersion) {
        AddressRecord before = address(tenantId, addressId);
        Instant now = Instant.now();
        if (before.primaryAddress() && !before.addressType().equals(command.addressType())) {
            clearPrimaryAddresses(tenantId, actorId, before.ownerType(), before.ownerId(), command.addressType(), now, addressId);
        }
        int updated = jdbc.update("UPDATE crm_party_addresses SET address_type=:addressType,label=:label," +
                        "raw_formatted_address=:raw,line1=:line1,line2=:line2,line3=:line3,district=:district,city=:city," +
                        "state_region=:stateRegion,postal_code=:postalCode,country_code=:countryCode," +
                        "country_extension_json=:extension,latitude=:latitude,longitude=:longitude,verified=:verified," +
                        "verification_source=:verificationSource,valid_from=:validFrom,valid_to=:validTo," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                p().addValue("tenantId", tenantId).addValue("id", addressId)
                        .addValue("expectedVersion", expectedVersion).addValue("actorId", actorId)
                        .addValue("now", timestamp(now)).addValue("addressType", command.addressType())
                        .addValue("label", command.label()).addValue("raw", command.rawFormattedAddress())
                        .addValue("line1", command.line1()).addValue("line2", command.line2())
                        .addValue("line3", command.line3()).addValue("district", command.district())
                        .addValue("city", command.city()).addValue("stateRegion", command.stateRegion())
                        .addValue("postalCode", command.postalCode()).addValue("countryCode", command.countryCode())
                        .addValue("extension", command.countryExtensionJson()).addValue("latitude", command.latitude())
                        .addValue("longitude", command.longitude()).addValue("verified", command.verified())
                        .addValue("verificationSource", command.verificationSource())
                        .addValue("validFrom", date(command.validFrom())).addValue("validTo", date(command.validTo())));
        concurrency(updated);
        AddressRecord after = address(tenantId, addressId);
        addressHistory(tenantId, actorId, after, "UPDATED", before.version());
        syncLegacyAccountAddress(after, tenantId, actorId);
        return after;
    }

    @Override
    public AddressRecord setPrimaryAddress(
            UUID tenantId, UUID actorId, UUID addressId, long expectedVersion) {
        AddressRecord before = address(tenantId, addressId);
        Instant now = Instant.now();
        clearPrimaryAddresses(tenantId, actorId, before.ownerType(), before.ownerId(), before.addressType(), now, addressId);
        int updated = jdbc.update("UPDATE crm_party_addresses SET primary_address=TRUE,primary_slot=1," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion AND status<>'ARCHIVED'",
                p().addValue("tenantId", tenantId).addValue("id", addressId)
                        .addValue("expectedVersion", expectedVersion).addValue("actorId", actorId)
                        .addValue("now", timestamp(now)));
        concurrency(updated);
        AddressRecord after = address(tenantId, addressId);
        addressHistory(tenantId, actorId, after, "PRIMARY_CHANGED", before.version());
        syncLegacyAccountAddress(after, tenantId, actorId);
        return after;
    }

    @Override
    public AddressRecord changeAddressStatus(
            UUID tenantId, UUID actorId, UUID addressId, String status, long expectedVersion) {
        AddressRecord before = address(tenantId, addressId);
        Instant now = Instant.now();
        int updated = jdbc.update("UPDATE crm_party_addresses SET status=:status," +
                        "primary_address=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE primary_address END," +
                        "primary_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE primary_slot END," +
                        "archived_at=CASE WHEN :status='ARCHIVED' THEN :now ELSE NULL END," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                p().addValue("tenantId", tenantId).addValue("id", addressId)
                        .addValue("expectedVersion", expectedVersion).addValue("actorId", actorId)
                        .addValue("status", status).addValue("now", timestamp(now)));
        concurrency(updated);
        AddressRecord after = address(tenantId, addressId);
        addressHistory(tenantId, actorId, after, status.equals("ARCHIVED") ? "ARCHIVED" : "REACTIVATED", before.version());
        syncLegacyAccountAddress(after, tenantId, actorId);
        return after;
    }

    @Override
    public List<AddressHistoryRecord> addressHistory(UUID tenantId, UUID addressId, int limit) {
        return jdbc.query("SELECT id,address_id,owner_type,owner_id,event_type,previous_version,new_version," +
                        "snapshot,changed_by,changed_at FROM crm_party_address_history " +
                        "WHERE tenant_id=:tenantId AND address_id=:addressId ORDER BY changed_at DESC,id DESC LIMIT :limit",
                p().addValue("tenantId", tenantId).addValue("addressId", addressId).addValue("limit", limit),
                (rs, rowNum) -> new AddressHistoryRecord(uuid(rs, "id"), uuid(rs, "address_id"),
                        rs.getString("owner_type"), uuid(rs, "owner_id"), rs.getString("event_type"),
                        nullableLong(rs, "previous_version"), rs.getLong("new_version"), rs.getString("snapshot"),
                        uuid(rs, "changed_by"), instant(rs, "changed_at")));
    }

    @Override
    public CommunicationMethodRecord communicationMethod(UUID tenantId, UUID communicationMethodId) {
        try {
            return jdbc.queryForObject("SELECT " + COMMUNICATION_COLUMNS + " FROM crm_communication_methods " +
                            "WHERE tenant_id=:tenantId AND id=:id",
                    p().addValue("tenantId", tenantId).addValue("id", communicationMethodId),
                    (rs, rowNum) -> communication(rs));
        } catch (EmptyResultDataAccessException exception) {
            throw notFound("Communication method not found.");
        }
    }

    @Override
    public List<CommunicationMethodRecord> communicationMethods(
            UUID tenantId, String ownerType, UUID ownerId, boolean includeArchived,
            String methodType, String verificationStatus, int limit, Instant beforeUpdatedAt, UUID beforeId) {
        ownerExists(tenantId, ownerType, ownerId);
        String sql = "SELECT " + COMMUNICATION_COLUMNS + " FROM crm_communication_methods " +
                "WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId " +
                (includeArchived ? "" : "AND status<>'ARCHIVED' ") +
                "AND (:methodType IS NULL OR method_type=:methodType) " +
                "AND (:verificationStatus IS NULL OR verification_status=:verificationStatus) " +
                "AND (:beforeTime IS NULL OR updated_at<:beforeTime " +
                "OR (updated_at=:beforeTime AND (:beforeId IS NULL OR id<:beforeId))) " +
                "ORDER BY updated_at DESC,id DESC LIMIT :limit";
        return jdbc.query(sql, p().addValue("tenantId", tenantId).addValue("ownerType", ownerType)
                        .addValue("ownerId", ownerId).addValue("methodType", methodType)
                        .addValue("verificationStatus", verificationStatus)
                        .addValue("beforeTime", timestamp(beforeUpdatedAt)).addValue("beforeId", beforeId)
                        .addValue("limit", limit), (rs, rowNum) -> communication(rs));
    }

    @Override
    public CommunicationMethodRecord createCommunicationMethod(
            UUID tenantId, UUID actorId, String ownerType, UUID ownerId, CreateCommunicationMethodCommand command) {
        ownerExists(tenantId, ownerType, ownerId);
        enforceDuplicatePolicy(tenantId, ownerType, ownerId, command.methodType(), command.normalizedValue(), null);
        Instant now = Instant.now();
        if (command.preferred()) clearPreferredMethods(tenantId, actorId, ownerType, ownerId, command.methodType(), now, null);
        UUID id = UUID.randomUUID();
        MapSqlParameterSource params = ownerParams(tenantId, ownerType, ownerId)
                .addValue("id", id).addValue("actorId", actorId).addValue("now", timestamp(now))
                .addValue("methodType", command.methodType()).addValue("rawValue", command.rawValue())
                .addValue("normalizedValue", command.normalizedValue()).addValue("displayValue", command.displayValue())
                .addValue("label", command.label()).addValue("preferred", command.preferred())
                .addValue("preferredSlot", command.preferred() ? 1 : null)
                .addValue("privacy", command.privacyClassification())
                .addValue("consent", command.consentStateReference()).addValue("purpose", command.usagePurpose())
                .addValue("validFrom", date(command.validFrom())).addValue("validTo", date(command.validTo()));
        jdbc.update("INSERT INTO crm_communication_methods (id,tenant_id,version,owner_type,owner_id,account_id,contact_id," +
                        "method_type,raw_value,normalized_value,display_value,label,preferred,preferred_slot,verified," +
                        "verification_status,privacy_classification,consent_state_reference,usage_purpose,status," +
                        "valid_from,valid_to,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,:ownerType,:ownerId,:accountId,:contactId,:methodType,:rawValue," +
                        ":normalizedValue,:displayValue,:label,:preferred,:preferredSlot,FALSE,'UNVERIFIED',:privacy," +
                        ":consent,:purpose,'ACTIVE',:validFrom,:validTo,:actorId,:actorId,:now,:now)", params);
        CommunicationMethodRecord created = communicationMethod(tenantId, id);
        communicationHistory(tenantId, actorId, created, "CREATED", null);
        syncLegacyCommunication(created, tenantId, actorId);
        return created;
    }

    @Override
    public CommunicationMethodRecord updateCommunicationMethod(
            UUID tenantId, UUID actorId, UUID communicationMethodId,
            UpdateCommunicationMethodCommand command, long expectedVersion) {
        CommunicationMethodRecord before = communicationMethod(tenantId, communicationMethodId);
        enforceDuplicatePolicy(tenantId, before.ownerType(), before.ownerId(), before.methodType(),
                command.normalizedValue(), communicationMethodId);
        Instant now = Instant.now();
        int updated = jdbc.update("UPDATE crm_communication_methods SET raw_value=:rawValue," +
                        "normalized_value=:normalizedValue,display_value=:displayValue,label=:label," +
                        "privacy_classification=:privacy,consent_state_reference=:consent,usage_purpose=:purpose," +
                        "valid_from=:validFrom,valid_to=:validTo,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                p().addValue("tenantId", tenantId).addValue("id", communicationMethodId)
                        .addValue("expectedVersion", expectedVersion).addValue("actorId", actorId)
                        .addValue("now", timestamp(now)).addValue("rawValue", command.rawValue())
                        .addValue("normalizedValue", command.normalizedValue()).addValue("displayValue", command.displayValue())
                        .addValue("label", command.label()).addValue("privacy", command.privacyClassification())
                        .addValue("consent", command.consentStateReference()).addValue("purpose", command.usagePurpose())
                        .addValue("validFrom", date(command.validFrom())).addValue("validTo", date(command.validTo())));
        concurrency(updated);
        CommunicationMethodRecord after = communicationMethod(tenantId, communicationMethodId);
        communicationHistory(tenantId, actorId, after, "UPDATED", before.version());
        syncLegacyCommunication(after, tenantId, actorId);
        return after;
    }

    @Override
    public CommunicationMethodRecord setPreferredCommunicationMethod(
            UUID tenantId, UUID actorId, UUID communicationMethodId, long expectedVersion) {
        CommunicationMethodRecord before = communicationMethod(tenantId, communicationMethodId);
        Instant now = Instant.now();
        clearPreferredMethods(tenantId, actorId, before.ownerType(), before.ownerId(), before.methodType(), now, communicationMethodId);
        int updated = jdbc.update("UPDATE crm_communication_methods SET preferred=TRUE,preferred_slot=1," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion AND status='ACTIVE' " +
                        "AND verification_status NOT IN ('FAILED','REVOKED')",
                p().addValue("tenantId", tenantId).addValue("id", communicationMethodId)
                        .addValue("expectedVersion", expectedVersion).addValue("actorId", actorId)
                        .addValue("now", timestamp(now)));
        concurrency(updated);
        CommunicationMethodRecord after = communicationMethod(tenantId, communicationMethodId);
        communicationHistory(tenantId, actorId, after, "PREFERRED_CHANGED", before.version());
        syncLegacyCommunication(after, tenantId, actorId);
        return after;
    }

    @Override
    public CommunicationMethodRecord changeVerification(
            UUID tenantId, UUID actorId, UUID communicationMethodId,
            String verificationStatus, Instant verifiedAt, long expectedVersion) {
        CommunicationMethodRecord before = communicationMethod(tenantId, communicationMethodId);
        Instant now = Instant.now();
        int updated = jdbc.update("UPDATE crm_communication_methods SET verification_status=:verificationStatus," +
                        "verified=:verified,verified_at=:verifiedAt," +
                        "preferred=CASE WHEN :verificationStatus IN ('FAILED','REVOKED') THEN FALSE ELSE preferred END," +
                        "preferred_slot=CASE WHEN :verificationStatus IN ('FAILED','REVOKED') THEN NULL ELSE preferred_slot END," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                p().addValue("tenantId", tenantId).addValue("id", communicationMethodId)
                        .addValue("expectedVersion", expectedVersion).addValue("actorId", actorId)
                        .addValue("verificationStatus", verificationStatus)
                        .addValue("verified", "VERIFIED".equals(verificationStatus))
                        .addValue("verifiedAt", timestamp(verifiedAt)).addValue("now", timestamp(now)));
        concurrency(updated);
        CommunicationMethodRecord after = communicationMethod(tenantId, communicationMethodId);
        communicationHistory(tenantId, actorId, after, "VERIFICATION_" + verificationStatus, before.version());
        return after;
    }

    @Override
    public CommunicationMethodRecord changeCommunicationStatus(
            UUID tenantId, UUID actorId, UUID communicationMethodId, String status, long expectedVersion) {
        CommunicationMethodRecord before = communicationMethod(tenantId, communicationMethodId);
        Instant now = Instant.now();
        int updated = jdbc.update("UPDATE crm_communication_methods SET status=:status," +
                        "preferred=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE preferred END," +
                        "preferred_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE preferred_slot END," +
                        "archived_at=CASE WHEN :status='ARCHIVED' THEN :now ELSE NULL END," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                p().addValue("tenantId", tenantId).addValue("id", communicationMethodId)
                        .addValue("expectedVersion", expectedVersion).addValue("actorId", actorId)
                        .addValue("status", status).addValue("now", timestamp(now)));
        concurrency(updated);
        CommunicationMethodRecord after = communicationMethod(tenantId, communicationMethodId);
        communicationHistory(tenantId, actorId, after, status.equals("ARCHIVED") ? "ARCHIVED" : "REACTIVATED", before.version());
        syncLegacyCommunication(after, tenantId, actorId);
        return after;
    }

    @Override
    public List<CommunicationHistoryRecord> communicationHistory(UUID tenantId, UUID communicationMethodId, int limit) {
        return jdbc.query("SELECT id,communication_method_id,owner_type,owner_id,event_type,previous_version," +
                        "new_version,snapshot,changed_by,changed_at FROM crm_communication_method_history " +
                        "WHERE tenant_id=:tenantId AND communication_method_id=:methodId " +
                        "ORDER BY changed_at DESC,id DESC LIMIT :limit",
                p().addValue("tenantId", tenantId).addValue("methodId", communicationMethodId).addValue("limit", limit),
                (rs, rowNum) -> new CommunicationHistoryRecord(uuid(rs, "id"), uuid(rs, "communication_method_id"),
                        rs.getString("owner_type"), uuid(rs, "owner_id"), rs.getString("event_type"),
                        nullableLong(rs, "previous_version"), rs.getLong("new_version"), rs.getString("snapshot"),
                        uuid(rs, "changed_by"), instant(rs, "changed_at")));
    }

    @Override
    public CommunicationPolicy policy(UUID tenantId) {
        List<CommunicationPolicy> policies = jdbc.query("SELECT email_unique_within_owner,phone_unique_within_owner," +
                        "single_preferred_per_type FROM crm_communication_policies WHERE tenant_id=:tenantId",
                p().addValue("tenantId", tenantId),
                (rs, rowNum) -> new CommunicationPolicy(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)));
        return policies.isEmpty() ? new CommunicationPolicy(true, true, true) : policies.get(0);
    }

    private void ownerExists(UUID tenantId, String ownerType, UUID ownerId) {
        String table = "ACCOUNT".equals(ownerType) ? "crm_accounts" : "crm_contacts";
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=:tenantId AND id=:ownerId",
                p().addValue("tenantId", tenantId).addValue("ownerId", ownerId), Long.class);
        if (count == null || count != 1L) throw notFound("CRM owner not found.");
    }

    private MapSqlParameterSource ownerParams(UUID tenantId, String ownerType, UUID ownerId) {
        return p().addValue("tenantId", tenantId).addValue("ownerType", ownerType).addValue("ownerId", ownerId)
                .addValue("accountId", "ACCOUNT".equals(ownerType) ? ownerId : null)
                .addValue("contactId", "PERSON".equals(ownerType) ? ownerId : null);
    }

    private void clearPrimaryAddresses(
            UUID tenantId, UUID actorId, String ownerType, UUID ownerId, String addressType,
            Instant now, UUID exceptId) {
        jdbc.update("UPDATE crm_party_addresses SET primary_address=FALSE,primary_slot=NULL," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId " +
                        "AND address_type=:addressType AND primary_address=TRUE " +
                        "AND (:exceptId IS NULL OR id<>:exceptId)",
                p().addValue("tenantId", tenantId).addValue("ownerType", ownerType).addValue("ownerId", ownerId)
                        .addValue("addressType", addressType).addValue("exceptId", exceptId)
                        .addValue("actorId", actorId).addValue("now", timestamp(now)));
    }

    private void clearPreferredMethods(
            UUID tenantId, UUID actorId, String ownerType, UUID ownerId, String methodType,
            Instant now, UUID exceptId) {
        jdbc.update("UPDATE crm_communication_methods SET preferred=FALSE,preferred_slot=NULL," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId " +
                        "AND method_type=:methodType AND preferred=TRUE AND (:exceptId IS NULL OR id<>:exceptId)",
                p().addValue("tenantId", tenantId).addValue("ownerType", ownerType).addValue("ownerId", ownerId)
                        .addValue("methodType", methodType).addValue("exceptId", exceptId)
                        .addValue("actorId", actorId).addValue("now", timestamp(now)));
    }

    private void enforceDuplicatePolicy(
            UUID tenantId, String ownerType, UUID ownerId, String methodType, String normalizedValue, UUID exceptId) {
        CommunicationPolicy policy = policy(tenantId);
        boolean enforce = "EMAIL".equals(methodType) ? policy.emailUniqueWithinOwner()
                : Set.of("PHONE", "MOBILE", "FAX", "WHATSAPP", "SMS").contains(methodType)
                && policy.phoneUniqueWithinOwner();
        if (!enforce) return;
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM crm_communication_methods " +
                        "WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId " +
                        "AND method_type=:methodType AND normalized_value=:normalizedValue AND status<>'ARCHIVED' " +
                        "AND (:exceptId IS NULL OR id<>:exceptId)",
                p().addValue("tenantId", tenantId).addValue("ownerType", ownerType).addValue("ownerId", ownerId)
                        .addValue("methodType", methodType).addValue("normalizedValue", normalizedValue)
                        .addValue("exceptId", exceptId), Long.class);
        if (count != null && count > 0) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "The normalized communication value already exists for this owner and type.");
        }
    }

    private void addressHistory(UUID tenantId, UUID actorId, AddressRecord record, String eventType, Long previousVersion) {
        jdbc.update("INSERT INTO crm_party_address_history (id,tenant_id,address_id,owner_type,owner_id,event_type," +
                        "previous_version,new_version,snapshot,changed_by,changed_at) " +
                        "VALUES (:id,:tenantId,:addressId,:ownerType,:ownerId,:eventType,:previousVersion," +
                        ":newVersion,:snapshot,:actorId,:changedAt)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("addressId", record.id()).addValue("ownerType", record.ownerType())
                        .addValue("ownerId", record.ownerId()).addValue("eventType", eventType)
                        .addValue("previousVersion", previousVersion).addValue("newVersion", record.version())
                        .addValue("snapshot", snapshot(record)).addValue("actorId", actorId)
                        .addValue("changedAt", timestamp(Instant.now())));
    }

    private void communicationHistory(
            UUID tenantId, UUID actorId, CommunicationMethodRecord record, String eventType, Long previousVersion) {
        jdbc.update("INSERT INTO crm_communication_method_history (id,tenant_id,communication_method_id,owner_type," +
                        "owner_id,event_type,previous_version,new_version,snapshot,changed_by,changed_at) " +
                        "VALUES (:id,:tenantId,:methodId,:ownerType,:ownerId,:eventType,:previousVersion," +
                        ":newVersion,:snapshot,:actorId,:changedAt)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("methodId", record.id()).addValue("ownerType", record.ownerType())
                        .addValue("ownerId", record.ownerId()).addValue("eventType", eventType)
                        .addValue("previousVersion", previousVersion).addValue("newVersion", record.version())
                        .addValue("snapshot", snapshot(record)).addValue("actorId", actorId)
                        .addValue("changedAt", timestamp(Instant.now())));
    }

    private void syncLegacyAccountAddress(AddressRecord record, UUID tenantId, UUID actorId) {
        if (!"ACCOUNT".equals(record.ownerType())) return;
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM crm_account_addresses WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", tenantId).addValue("id", record.id()), Long.class);
        if (exists != null && exists > 0) {
            jdbc.update("UPDATE crm_account_addresses SET address_type=:addressType,label=:label,line1=:line1,line2=:line2," +
                            "city=:city,state_region=:stateRegion,postal_code=:postalCode,country_code=:countryCode," +
                            "primary_address=:primary,active=:active,updated_by=:actorId,updated_at=:updatedAt,version=:version " +
                            "WHERE tenant_id=:tenantId AND id=:id",
                    p().addValue("tenantId", tenantId).addValue("id", record.id()).addValue("addressType", record.addressType())
                            .addValue("label", record.label()).addValue("line1", record.line1()).addValue("line2", record.line2())
                            .addValue("city", record.city()).addValue("stateRegion", record.stateRegion())
                            .addValue("postalCode", record.postalCode()).addValue("countryCode", record.countryCode())
                            .addValue("primary", record.primaryAddress()).addValue("active", !"ARCHIVED".equals(record.status()))
                            .addValue("actorId", actorId).addValue("updatedAt", timestamp(record.updatedAt()))
                            .addValue("version", record.version()));
        }
    }

    private void syncLegacyCommunication(CommunicationMethodRecord record, UUID tenantId, UUID actorId) {
        if (!record.preferred() || !"ACTIVE".equals(record.status())) return;
        String column = "EMAIL".equals(record.methodType()) ? "primary_email"
                : Set.of("PHONE", "MOBILE").contains(record.methodType()) ? "primary_phone" : null;
        if (column == null) return;
        String table = "ACCOUNT".equals(record.ownerType()) ? "crm_accounts" : "crm_contacts";
        jdbc.update("UPDATE " + table + " SET " + column + "=:value,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:ownerId",
                p().addValue("value", record.displayValue()).addValue("actorId", actorId)
                        .addValue("now", timestamp(Instant.now())).addValue("tenantId", tenantId)
                        .addValue("ownerId", record.ownerId()));
    }

    private AddressRecord address(ResultSet rs) throws SQLException {
        return new AddressRecord(uuid(rs, "id"), rs.getLong("version"), rs.getString("owner_type"),
                uuid(rs, "owner_id"), rs.getString("address_type"), rs.getString("label"),
                rs.getString("raw_formatted_address"), rs.getString("line1"), rs.getString("line2"),
                rs.getString("line3"), rs.getString("district"), rs.getString("city"),
                rs.getString("state_region"), rs.getString("postal_code"), rs.getString("country_code"),
                rs.getString("country_extension_json"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                rs.getBoolean("primary_address"), rs.getBoolean("verified"), rs.getString("verification_source"),
                rs.getString("status"), localDate(rs, "valid_from"), localDate(rs, "valid_to"),
                instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "archived_at"));
    }

    private CommunicationMethodRecord communication(ResultSet rs) throws SQLException {
        return new CommunicationMethodRecord(uuid(rs, "id"), rs.getLong("version"), rs.getString("owner_type"),
                uuid(rs, "owner_id"), rs.getString("method_type"), rs.getString("raw_value"),
                rs.getString("normalized_value"), rs.getString("display_value"), rs.getString("label"),
                rs.getBoolean("preferred"), rs.getBoolean("verified"), rs.getString("verification_status"),
                instant(rs, "verified_at"), rs.getString("privacy_classification"),
                rs.getString("consent_state_reference"), rs.getString("usage_purpose"), rs.getString("status"),
                localDate(rs, "valid_from"), localDate(rs, "valid_to"), instant(rs, "created_at"),
                instant(rs, "updated_at"), instant(rs, "archived_at"));
    }

    private String snapshot(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new CrmContractException(CrmErrorCode.INTERNAL_ERROR, "CRM history serialization failed.");
        }
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Date date(LocalDate value) { return value == null ? null : Date.valueOf(value); }
    private static void concurrency(int updated) {
        if (updated != 1) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
    }
    private static CrmContractException notFound(String message) {
        return new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
