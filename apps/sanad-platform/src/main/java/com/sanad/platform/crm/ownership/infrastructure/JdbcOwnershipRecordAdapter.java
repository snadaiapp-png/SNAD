package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipRecordPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Tenant-scoped CRM record validation and backward-compatible owner projection. */
@Component
public class JdbcOwnershipRecordAdapter implements OwnershipRecordPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOwnershipRecordAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(UUID tenantId, AssignmentRecordType recordType, UUID recordId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table(recordType)
                        + " WHERE tenant_id=:tenantId AND id=:recordId",
                parameters(tenantId, recordId), Long.class);
        return count != null && count == 1L;
    }

    @Override
    public void updateOwner(UUID tenantId,
                            AssignmentRecordType recordType,
                            UUID recordId,
                            OwnerType ownerType,
                            UUID ownerId) {
        if (tenantId == null || recordType == null || recordId == null
                || ownerType == null || ownerId == null) {
            throw new OwnershipDomainException("Complete owner projection command required");
        }
        UUID user = ownerType == OwnerType.USER ? ownerId : null;
        UUID team = ownerType == OwnerType.TEAM ? ownerId : null;
        UUID queue = ownerType == OwnerType.QUEUE ? ownerId : null;
        int rows = jdbc.update(
                "UPDATE " + table(recordType) + " SET "
                        + "owner_user_id=:ownerUserId, "
                        + "owner_team_id=:ownerTeamId, "
                        + "owner_queue_id=:ownerQueueId "
                        + "WHERE tenant_id=:tenantId AND id=:recordId",
                parameters(tenantId, recordId)
                        .addValue("ownerUserId", user)
                        .addValue("ownerTeamId", team)
                        .addValue("ownerQueueId", queue));
        if (rows != 1) {
            throw new OwnershipDomainException(
                    "CRM record not found for owner projection: " + recordType + "/" + recordId);
        }
    }

    private MapSqlParameterSource parameters(UUID tenantId, UUID recordId) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("recordId", recordId);
    }

    private String table(AssignmentRecordType recordType) {
        return switch (recordType) {
            case ACCOUNT -> "crm_accounts";
            case CONTACT -> "crm_contacts";
            case LEAD -> "crm_leads";
            case OPPORTUNITY -> "crm_opportunities";
            case ACTIVITY -> "crm_activities";
            case TASK -> "crm_tasks";
        };
    }
}
