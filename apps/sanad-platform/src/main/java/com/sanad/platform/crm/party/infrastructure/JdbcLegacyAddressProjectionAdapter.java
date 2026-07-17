package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.AddressRecord;
import com.sanad.platform.crm.party.domain.LegacyAddressProjectionPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcLegacyAddressProjectionAdapter implements LegacyAddressProjectionPort {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcLegacyAddressProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(UUID tenantId, UUID actorId, AddressRecord address) {
        if (!"ACCOUNT".equals(address.ownerType())) return;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", address.id())
                .addValue("tenantId", tenantId)
                .addValue("accountId", address.ownerId())
                .addValue("version", address.version())
                .addValue("addressType", address.addressType())
                .addValue("label", address.label())
                .addValue("line1", address.line1())
                .addValue("line2", address.line2())
                .addValue("city", address.city())
                .addValue("stateRegion", address.stateRegion())
                .addValue("postalCode", address.postalCode())
                .addValue("countryCode", address.countryCode())
                .addValue("primaryAddress", address.primaryAddress())
                .addValue("active", !"ARCHIVED".equals(address.status()))
                .addValue("actorId", actorId)
                .addValue("createdAt", timestamp(address.createdAt()))
                .addValue("updatedAt", timestamp(address.updatedAt()));

        if (address.primaryAddress()) {
            jdbc.update(
                    "UPDATE crm_account_addresses SET primary_address=FALSE,version=version+1," +
                            "updated_by=:actorId,updated_at=:updatedAt " +
                            "WHERE tenant_id=:tenantId AND account_id=:accountId AND address_type=:addressType " +
                            "AND id<>:id AND primary_address=TRUE",
                    params);
        }

        int updated = jdbc.update(
                "UPDATE crm_account_addresses SET version=:version,address_type=:addressType,label=:label," +
                        "line1=:line1,line2=:line2,city=:city,state_region=:stateRegion," +
                        "postal_code=:postalCode,country_code=:countryCode,primary_address=:primaryAddress," +
                        "active=:active,updated_by=:actorId,updated_at=:updatedAt " +
                        "WHERE tenant_id=:tenantId AND id=:id",
                params);
        if (updated == 1) return;

        jdbc.update(
                "INSERT INTO crm_account_addresses (id,tenant_id,account_id,version,address_type,label," +
                        "line1,line2,city,state_region,postal_code,country_code,primary_address,active," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "SELECT :id,:tenantId,:accountId,:version,:addressType,:label,:line1,:line2,:city," +
                        ":stateRegion,:postalCode,:countryCode,:primaryAddress,:active,:actorId,:actorId," +
                        ":createdAt,:updatedAt WHERE NOT EXISTS (SELECT 1 FROM crm_account_addresses " +
                        "WHERE tenant_id=:tenantId AND id=:id)",
                params);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? Timestamp.from(Instant.now()) : Timestamp.from(value);
    }
}
