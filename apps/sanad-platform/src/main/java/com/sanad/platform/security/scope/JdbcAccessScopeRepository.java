package com.sanad.platform.security.scope;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcAccessScopeRepository {

    private final JdbcTemplate jdbc;

    public JdbcAccessScopeRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public List<AccessScopeGrant> findEffectiveGrants(
            UUID tenantId,
            UUID userId,
            UUID matchedRoleId,
            String capabilityCode,
            Instant authorizationTime) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(capabilityCode, "capabilityCode");
        Objects.requireNonNull(authorizationTime, "authorizationTime");

        return jdbc.query(
                "SELECT g.id,g.tenant_id,g.role_id,g.user_id,g.scope_type,g.organization_id,g.org_unit_id," +
                        "g.legal_entity_id,g.is_direct_exception,g.reason,g.granted_by,g.effective_from,g.effective_to " +
                        "FROM access_scope_grants g " +
                        "JOIN access_capabilities c ON c.id = g.capability_id " +
                        "WHERE g.tenant_id = ? AND c.code = ? AND c.status = 'ACTIVE' AND g.status = 'ACTIVE' " +
                        "AND g.effective_from <= ? AND (g.effective_to IS NULL OR g.effective_to >= ?) " +
                        "AND (g.role_id = ? OR g.user_id = ?) " +
                        "ORDER BY g.is_direct_exception DESC, g.created_at ASC, g.id ASC",
                (rs, rowNum) -> new AccessScopeGrant(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("role_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        AccessScopeType.valueOf(rs.getString("scope_type")),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("org_unit_id", UUID.class),
                        rs.getObject("legal_entity_id", UUID.class),
                        rs.getBoolean("is_direct_exception"),
                        rs.getString("reason"),
                        rs.getObject("granted_by", UUID.class),
                        toInstant(rs.getTimestamp("effective_from")),
                        toInstant(rs.getTimestamp("effective_to"))),
                tenantId,
                capabilityCode.trim(),
                Timestamp.from(authorizationTime),
                Timestamp.from(authorizationTime),
                matchedRoleId,
                userId);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
