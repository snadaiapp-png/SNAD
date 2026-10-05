package com.sanad.platform.user.access;

import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.access.grant.UserGrantStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class UserApplicationAccessReadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public UserApplicationAccessReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Set<String> knownCapabilities() {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT code FROM access_capabilities WHERE status = 'ACTIVE' ORDER BY code",
                new MapSqlParameterSource(), String.class));
    }

    @Transactional(readOnly = true)
    public List<UserAccessResponse> activeRoleGrants(UUID tenantId, UUID userId) {
        return jdbc.query("""
                SELECT ura.id, ura.tenant_id, ura.user_id, ura.role_id, r.code AS role_code,
                       ura.organization_id, ura.status, ura.created_at, ura.updated_at
                  FROM user_role_assignments ura
                  JOIN roles r ON r.tenant_id = ura.tenant_id AND r.id = ura.role_id
                 WHERE ura.tenant_id = :tenantId AND ura.user_id = :userId
                   AND ura.status = 'ACTIVE' AND r.status = 'ACTIVE'
                 ORDER BY r.code, ura.created_at
                """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId),
                (rs, rowNum) -> new UserAccessResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("role_id", UUID.class),
                        rs.getString("role_code"),
                        rs.getObject("organization_id", UUID.class),
                        UserGrantStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()));
    }

    @Transactional(readOnly = true)
    public Set<String> capabilitiesByRoleIds(UUID tenantId, Set<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT ac.code
                  FROM role_capabilities rc
                  JOIN access_capabilities ac ON ac.id = rc.capability_id
                 WHERE rc.tenant_id = :tenantId
                   AND rc.role_id IN (:roleIds)
                   AND ac.status = 'ACTIVE'
                 ORDER BY ac.code
                """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("roleIds", roleIds),
                String.class));
    }

    @Transactional(readOnly = true)
    public Set<String> effectiveCapabilityCodes(UUID tenantId, UUID userId) {
        Set<String> allowed = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT ac.code
                  FROM user_role_assignments ura
                  JOIN roles r ON r.tenant_id = ura.tenant_id AND r.id = ura.role_id
                  JOIN role_capabilities rc ON rc.tenant_id = ura.tenant_id AND rc.role_id = ura.role_id
                  JOIN access_capabilities ac ON ac.id = rc.capability_id
                 WHERE ura.tenant_id = :tenantId AND ura.user_id = :userId
                   AND ura.status = 'ACTIVE' AND r.status = 'ACTIVE' AND ac.status = 'ACTIVE'
                UNION
                SELECT DISTINCT ac.code
                  FROM user_permission_overrides upo
                  JOIN access_capabilities ac ON ac.id = upo.capability_id
                 WHERE upo.tenant_id = :tenantId AND upo.user_id = :userId
                   AND upo.effect = 'ALLOW' AND ac.status = 'ACTIVE'
                   AND upo.valid_from <= CURRENT_TIMESTAMP
                   AND (upo.valid_until IS NULL OR upo.valid_until > CURRENT_TIMESTAMP)
                """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId),
                String.class));

        Set<String> denied = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT ac.code
                  FROM user_permission_overrides upo
                  JOIN access_capabilities ac ON ac.id = upo.capability_id
                 WHERE upo.tenant_id = :tenantId AND upo.user_id = :userId
                   AND upo.effect = 'DENY' AND ac.status = 'ACTIVE'
                   AND upo.valid_from <= CURRENT_TIMESTAMP
                   AND (upo.valid_until IS NULL OR upo.valid_until > CURRENT_TIMESTAMP)
                """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId),
                String.class));
        allowed.removeAll(denied);
        return allowed;
    }
}
