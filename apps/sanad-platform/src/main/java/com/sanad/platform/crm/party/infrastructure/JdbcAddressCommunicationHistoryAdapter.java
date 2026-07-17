package com.sanad.platform.crm.party.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.AddressCommunicationHistoryPort;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.AddressRecord;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CommunicationMethodRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcAddressCommunicationHistoryAdapter implements AddressCommunicationHistoryPort {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcAddressCommunicationHistoryAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void addressChanged(
            UUID tenantId, UUID actorId, AddressRecord record, String eventType, Long previousVersion) {
        jdbc.update(
                "INSERT INTO crm_party_address_history (id,tenant_id,address_id,owner_type,owner_id,event_type," +
                        "previous_version,new_version,snapshot,changed_by,changed_at) " +
                        "VALUES (:id,:tenantId,:addressId,:ownerType,:ownerId,:eventType,:previousVersion," +
                        ":newVersion,:snapshot,:actorId,:changedAt)",
                base(tenantId, actorId, eventType, previousVersion, record.version(), snapshot(record))
                        .addValue("addressId", record.id())
                        .addValue("ownerType", record.ownerType())
                        .addValue("ownerId", record.ownerId()));
    }

    @Override
    public void communicationChanged(
            UUID tenantId, UUID actorId, CommunicationMethodRecord record, String eventType, Long previousVersion) {
        jdbc.update(
                "INSERT INTO crm_communication_method_history (id,tenant_id,communication_method_id,owner_type," +
                        "owner_id,event_type,previous_version,new_version,snapshot,changed_by,changed_at) " +
                        "VALUES (:id,:tenantId,:methodId,:ownerType,:ownerId,:eventType,:previousVersion," +
                        ":newVersion,:snapshot,:actorId,:changedAt)",
                base(tenantId, actorId, eventType, previousVersion, record.version(), snapshot(record))
                        .addValue("methodId", record.id())
                        .addValue("ownerType", record.ownerType())
                        .addValue("ownerId", record.ownerId()));
    }

    private MapSqlParameterSource base(
            UUID tenantId, UUID actorId, String eventType, Long previousVersion,
            long newVersion, String snapshot) {
        return new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("tenantId", tenantId)
                .addValue("eventType", eventType)
                .addValue("previousVersion", previousVersion)
                .addValue("newVersion", newVersion)
                .addValue("snapshot", snapshot)
                .addValue("actorId", actorId)
                .addValue("changedAt", Timestamp.from(Instant.now()));
    }

    private String snapshot(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new CrmContractException(CrmErrorCode.INTERNAL_ERROR, "CRM history serialization failed.");
        }
    }
}
