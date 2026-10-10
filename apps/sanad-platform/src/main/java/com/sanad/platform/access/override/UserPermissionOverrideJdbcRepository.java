package com.sanad.platform.access.override;

import com.sanad.platform.persistence.PostgresTemporalBindings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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

    @Override
    public UserPermissionOverride insert(UserPermissionOverride override) {
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO user_permission_overrides "
                            + "(id, tenant_id, user_id, capability_id, effect, scope_type, scope_reference, "
                            + "reason, valid_from, valid_until, created_by, created_at, updated_at, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)");
            ps.setObject(1, override.getId());
            ps.setObject(2, override.getTenantId());
            ps.setObject(3, override.getUserId());
            ps.setObject(4, override.getCapabilityId());
            ps.setString(5, override.getEffect());
            ps.setString(6, override.getScopeType());
            if (override.getScopeReference() == null) ps.setNull(7, Types.OTHER);
            else ps.setObject(7, override.getScopeReference());
            ps.setString(8, override.getReason());
            PostgresTemporalBindings.setTimestamptz(ps, 9, override.getValidFrom());
            PostgresTemporalBindings.setTimestamptz(ps, 10, override.getValidUntil());
            ps.setObject(11, override.getCreatedBy());
            ps.setInt(12, override.getVersion());
            return ps;
        });
        return override;
    }

    @Override
    public java.util.Optional<UserPermissionOverride> find(UUID tenantId, UUID id) {
        List<UserPermissionOverride> rows = jdbc.query(
                "SELECT id, tenant_id, user_id, capability_id, effect, scope_type, scope_reference, "
                        + "reason, valid_from, valid_until, created_by, version "
                        + "FROM user_permission_overrides WHERE tenant_id = ? AND id = ?",
                (rs, rowNum) -> mapFull(rs),
                tenantId, id);
        return rows.stream().findFirst();
    }

    @Override
    public List<UserPermissionOverride> listForUser(UUID tenantId, UUID userId) {
        return jdbc.query(
                "SELECT id, tenant_id, user_id, capability_id, effect, scope_type, scope_reference, "
                        + "reason, valid_from, valid_until, created_by, version "
                        + "FROM user_permission_overrides WHERE tenant_id = ? AND user_id = ? "
                        + "ORDER BY created_at, id",
                (rs, rowNum) -> mapFull(rs),
                tenantId, userId);
    }

    @Override
    public boolean expire(UUID tenantId, UUID id) {
        int updated = jdbc.update(
                "UPDATE user_permission_overrides "
                        + "SET valid_until = NOW(), version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE tenant_id = ? AND id = ? AND valid_until IS NULL",
                tenantId, id);
        return updated > 0;
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

    private static UserPermissionOverride mapFull(ResultSet rs) throws SQLException {
        return new UserPermissionOverride(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("capability_id", UUID.class),
                rs.getString("effect"),
                rs.getString("scope_type"),
                rs.getObject("scope_reference", UUID.class),
                rs.getString("reason"),
                toInstant(rs.getTimestamp("valid_from")),
                toInstant(rs.getTimestamp("valid_until")),
                rs.getObject("created_by", UUID.class),
                rs.getInt("version"));
    }

    private static Instant toInstant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
