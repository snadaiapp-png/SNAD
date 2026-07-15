package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.party.domain.OwnerValidationPort;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class JdbcOwnerValidationAdapter implements OwnerValidationPort {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcOwnerValidationAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isValidOwner(UUID tenantId, UUID ownerUserId) {
        if (ownerUserId == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE tenant_id = :t AND id = :id AND status = 'ACTIVE'",
                new MapSqlParameterSource().addValue("t", tenantId).addValue("id", ownerUserId),
                Integer.class);
        return count != null && count > 0;
    }
}
