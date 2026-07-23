package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** PostgreSQL-backed tenant and ACTIVE-status validation for ownership users. */
@Component
public class JdbcOwnershipUserValidationAdapter implements OwnershipUserValidationPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOwnershipUserValidationAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActiveUser(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return false;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM users
                 WHERE tenant_id=:tenantId
                   AND id=:userId
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId), Integer.class);
        return count != null && count == 1;
    }

    @Override
    public boolean lockActiveUser(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return false;
        }
        try {
            UUID locked = jdbc.queryForObject("""
                    SELECT id
                      FROM users
                     WHERE tenant_id=:tenantId
                       AND id=:userId
                       AND status='ACTIVE'
                     FOR UPDATE
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("userId", userId), UUID.class);
            return userId.equals(locked);
        } catch (EmptyResultDataAccessException missing) {
            return false;
        }
    }
}
