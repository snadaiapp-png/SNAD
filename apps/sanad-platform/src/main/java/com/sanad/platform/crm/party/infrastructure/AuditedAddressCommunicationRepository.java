package com.sanad.platform.crm.party.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository;
import com.sanad.platform.crm.party.domain.LegacyAddressProjectionPort;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adds audit-history and compatibility reconciliation for records changed
 * indirectly when another address or communication method becomes primary.
 */
@Primary
@Repository
public class AuditedAddressCommunicationRepository implements AddressCommunicationRepository {
    private static final Set<String> PHONE_PROJECTION_TYPES = Set.of("PHONE", "MOBILE");

    private final JdbcAddressCommunicationRepository delegate;
    private final LegacyAddressProjectionPort legacyAddresses;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditedAddressCommunicationRepository(
            JdbcAddressCommunicationRepository delegate,
            LegacyAddressProjectionPort legacyAddresses,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper) {
        this.delegate = delegate;
        this.legacyAddresses = legacyAddresses;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public AddressRecord address(UUID tenantId, UUID addressId) {
        return delegate.address(tenantId, addressId);
    }

    @Override
    public List<AddressRecord> addresses(
            UUID tenantId, String ownerType, UUID ownerId, boolean includeArchived,
            int limit, Instant beforeUpdatedAt, UUID beforeId) {
        return delegate.addresses(
                tenantId, ownerType, ownerId, includeArchived, limit, beforeUpdatedAt, beforeId);
    }

    @Override
    public AddressRecord createAddress(
            UUID tenantId, UUID actorId, String ownerType, UUID ownerId, CreateAddressCommand command) {
        List<AddressRecord> replaced = command.primaryAddress()
                ? primaryAddresses(tenantId, ownerType, ownerId, command.addressType(), null)
                : List.of();
        AddressRecord created = delegate.createAddress(tenantId, actorId, ownerType, ownerId, command);
        reconcileReplacedAddresses(tenantId, actorId, replaced);
        return created;
    }

    @Override
    public AddressRecord updateAddress(
            UUID tenantId, UUID actorId, UUID addressId,
            UpdateAddressCommand command, long expectedVersion) {
        AddressRecord before = delegate.address(tenantId, addressId);
        List<AddressRecord> replaced = before.primaryAddress()
                && command.addressType() != null
                && !before.addressType().equals(command.addressType())
                ? primaryAddresses(
                        tenantId, before.ownerType(), before.ownerId(), command.addressType(), addressId)
                : List.of();
        AddressRecord updated = delegate.updateAddress(
                tenantId, actorId, addressId, command, expectedVersion);
        reconcileReplacedAddresses(tenantId, actorId, replaced);
        return updated;
    }

    @Override
    public AddressRecord setPrimaryAddress(
            UUID tenantId, UUID actorId, UUID addressId, long expectedVersion) {
        AddressRecord target = delegate.address(tenantId, addressId);
        List<AddressRecord> replaced = primaryAddresses(
                tenantId, target.ownerType(), target.ownerId(), target.addressType(), addressId);
        AddressRecord updated = delegate.setPrimaryAddress(
                tenantId, actorId, addressId, expectedVersion);
        reconcileReplacedAddresses(tenantId, actorId, replaced);
        return updated;
    }

    @Override
    public AddressRecord changeAddressStatus(
            UUID tenantId, UUID actorId, UUID addressId, String status, long expectedVersion) {
        return delegate.changeAddressStatus(tenantId, actorId, addressId, status, expectedVersion);
    }

    @Override
    public List<AddressHistoryRecord> addressHistory(UUID tenantId, UUID addressId, int limit) {
        return delegate.addressHistory(tenantId, addressId, limit);
    }

    @Override
    public CommunicationMethodRecord communicationMethod(UUID tenantId, UUID communicationMethodId) {
        return delegate.communicationMethod(tenantId, communicationMethodId);
    }

    @Override
    public List<CommunicationMethodRecord> communicationMethods(
            UUID tenantId, String ownerType, UUID ownerId, boolean includeArchived,
            String methodType, String verificationStatus, int limit,
            Instant beforeUpdatedAt, UUID beforeId) {
        return delegate.communicationMethods(
                tenantId, ownerType, ownerId, includeArchived, methodType,
                verificationStatus, limit, beforeUpdatedAt, beforeId);
    }

    @Override
    public CommunicationMethodRecord createCommunicationMethod(
            UUID tenantId, UUID actorId, String ownerType, UUID ownerId,
            CreateCommunicationMethodCommand command) {
        List<CommunicationMethodRecord> replaced = command.preferred()
                ? preferredMethods(tenantId, ownerType, ownerId, command.methodType(), null)
                : List.of();
        CommunicationMethodRecord created = delegate.createCommunicationMethod(
                tenantId, actorId, ownerType, ownerId, command);
        reconcileReplacedMethods(tenantId, actorId, replaced);
        return created;
    }

    @Override
    public CommunicationMethodRecord updateCommunicationMethod(
            UUID tenantId, UUID actorId, UUID communicationMethodId,
            UpdateCommunicationMethodCommand command, long expectedVersion) {
        return delegate.updateCommunicationMethod(
                tenantId, actorId, communicationMethodId, command, expectedVersion);
    }

    @Override
    public CommunicationMethodRecord setPreferredCommunicationMethod(
            UUID tenantId, UUID actorId, UUID communicationMethodId, long expectedVersion) {
        CommunicationMethodRecord target = delegate.communicationMethod(tenantId, communicationMethodId);
        List<CommunicationMethodRecord> replaced = preferredMethods(
                tenantId, target.ownerType(), target.ownerId(), target.methodType(), communicationMethodId);
        CommunicationMethodRecord updated = delegate.setPreferredCommunicationMethod(
                tenantId, actorId, communicationMethodId, expectedVersion);
        reconcileReplacedMethods(tenantId, actorId, replaced);
        return updated;
    }

    @Override
    public CommunicationMethodRecord changeVerification(
            UUID tenantId, UUID actorId, UUID communicationMethodId,
            String verificationStatus, Instant verifiedAt, long expectedVersion) {
        CommunicationMethodRecord before = delegate.communicationMethod(tenantId, communicationMethodId);
        CommunicationMethodRecord updated = delegate.changeVerification(
                tenantId, actorId, communicationMethodId, verificationStatus, verifiedAt, expectedVersion);
        if (before.preferred() && Set.of("FAILED", "REVOKED").contains(verificationStatus)) {
            clearLegacyCommunicationProjection(tenantId, actorId, before);
        }
        return updated;
    }

    @Override
    public CommunicationMethodRecord changeCommunicationStatus(
            UUID tenantId, UUID actorId, UUID communicationMethodId,
            String status, long expectedVersion) {
        CommunicationMethodRecord before = delegate.communicationMethod(tenantId, communicationMethodId);
        CommunicationMethodRecord updated = delegate.changeCommunicationStatus(
                tenantId, actorId, communicationMethodId, status, expectedVersion);
        if (before.preferred() && "ARCHIVED".equals(status)) {
            clearLegacyCommunicationProjection(tenantId, actorId, before);
        }
        return updated;
    }

    @Override
    public List<CommunicationHistoryRecord> communicationHistory(
            UUID tenantId, UUID communicationMethodId, int limit) {
        return delegate.communicationHistory(tenantId, communicationMethodId, limit);
    }

    @Override
    public CommunicationPolicy policy(UUID tenantId) {
        return delegate.policy(tenantId);
    }

    private List<AddressRecord> primaryAddresses(
            UUID tenantId, String ownerType, UUID ownerId, String addressType, UUID exceptId) {
        return delegate.addresses(tenantId, ownerType, ownerId, false, 200, null, null).stream()
                .filter(AddressRecord::primaryAddress)
                .filter(value -> value.addressType().equals(addressType))
                .filter(value -> exceptId == null || !value.id().equals(exceptId))
                .toList();
    }

    private List<CommunicationMethodRecord> preferredMethods(
            UUID tenantId, String ownerType, UUID ownerId, String methodType, UUID exceptId) {
        return delegate.communicationMethods(
                        tenantId, ownerType, ownerId, false, methodType, null, 200, null, null).stream()
                .filter(CommunicationMethodRecord::preferred)
                .filter(value -> exceptId == null || !value.id().equals(exceptId))
                .toList();
    }

    private void reconcileReplacedAddresses(
            UUID tenantId, UUID actorId, List<AddressRecord> replaced) {
        for (AddressRecord before : replaced) {
            AddressRecord after = delegate.address(tenantId, before.id());
            insertAddressHistory(tenantId, actorId, after, before.version());
            legacyAddresses.upsert(tenantId, actorId, after);
        }
    }

    private void reconcileReplacedMethods(
            UUID tenantId, UUID actorId, List<CommunicationMethodRecord> replaced) {
        for (CommunicationMethodRecord before : replaced) {
            CommunicationMethodRecord after = delegate.communicationMethod(tenantId, before.id());
            insertCommunicationHistory(tenantId, actorId, after, before.version());
        }
    }

    private void insertAddressHistory(
            UUID tenantId, UUID actorId, AddressRecord value, long previousVersion) {
        jdbc.update(
                "INSERT INTO crm_party_address_history (id,tenant_id,address_id,owner_type,owner_id," +
                        "event_type,previous_version,new_version,snapshot,changed_by,changed_at) " +
                        "VALUES (:id,:tenantId,:addressId,:ownerType,:ownerId,'PRIMARY_REPLACED'," +
                        ":previousVersion,:newVersion,:snapshot,:actorId,:changedAt)",
                base(tenantId, actorId, previousVersion, value.version(), snapshot(value))
                        .addValue("addressId", value.id())
                        .addValue("ownerType", value.ownerType())
                        .addValue("ownerId", value.ownerId()));
    }

    private void insertCommunicationHistory(
            UUID tenantId, UUID actorId, CommunicationMethodRecord value, long previousVersion) {
        jdbc.update(
                "INSERT INTO crm_communication_method_history (id,tenant_id,communication_method_id," +
                        "owner_type,owner_id,event_type,previous_version,new_version,snapshot,changed_by,changed_at) " +
                        "VALUES (:id,:tenantId,:methodId,:ownerType,:ownerId,'PREFERRED_REPLACED'," +
                        ":previousVersion,:newVersion,:snapshot,:actorId,:changedAt)",
                base(tenantId, actorId, previousVersion, value.version(), snapshot(value))
                        .addValue("methodId", value.id())
                        .addValue("ownerType", value.ownerType())
                        .addValue("ownerId", value.ownerId()));
    }

    private MapSqlParameterSource base(
            UUID tenantId, UUID actorId, long previousVersion, long newVersion, String snapshot) {
        return new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("tenantId", tenantId)
                .addValue("actorId", actorId)
                .addValue("previousVersion", previousVersion)
                .addValue("newVersion", newVersion)
                .addValue("snapshot", snapshot)
                .addValue("changedAt", Timestamp.from(Instant.now()));
    }

    private void clearLegacyCommunicationProjection(
            UUID tenantId, UUID actorId, CommunicationMethodRecord record) {
        String column = "EMAIL".equals(record.methodType()) ? "primary_email"
                : PHONE_PROJECTION_TYPES.contains(record.methodType()) ? "primary_phone" : null;
        if (column == null) return;
        String table = "ACCOUNT".equals(record.ownerType()) ? "crm_accounts" : "crm_contacts";
        jdbc.update(
                "UPDATE " + table + " SET " + column + "=NULL,updated_by=:actorId," +
                        "updated_at=:updatedAt,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:ownerId AND " + column + "=:currentValue",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("ownerId", record.ownerId())
                        .addValue("currentValue", record.displayValue())
                        .addValue("actorId", actorId)
                        .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    private String snapshot(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new CrmContractException(
                    CrmErrorCode.INTERNAL_ERROR, "CRM replacement history serialization failed.");
        }
    }
}
