package com.sanad.platform.access.override;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL Direct read-side implementation over the canonical
 * {@code user_permission_overrides} table (Wave 1 Task 1 schema, FORCE RLS).
 * Tenant isolation is enforced by the table's RLS policy through the request
 * tenant context plus explicit tenant_id predicates.
 */
@Repository
public class UserPermissionOverrideJdbcRepository implements UserPermissionOverrideRepository {

    private final JdbcTemplate jdbc;

    public UserPermissionOverrideJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ActiveOverride> findOverrides(UUID tenantId, UUID userId, UUID capabilityId) {
        return jdbc.query(
                "SELECT id, effect, scope_type, scope_reference, valid_from, valid_until "
                        + "FROM user_permission_overrides "
                        + "WHERE tenant_id = ? AND user_id = ? AND capability_id = ? "
                        + "ORDER BY created_at, id",
                (rs, rowNum) -> map(rs),
                tenantId, userId, capabilityId);
    }

    private static ActiveOverride map(ResultSet rs) throws SQLException {
        return new ActiveOverride(
                rs.getObject("id", UUID.class),
                rs.getString("effect"),
                rs.getString("scope_type"),
                rs.getObject("scope_reference", UUID.class),
                toInstant(rs.getTimestamp("valid_from")),
                toInstant(rs.getTimestamp("valid_until")));
    }

    private static Instant toInstant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
