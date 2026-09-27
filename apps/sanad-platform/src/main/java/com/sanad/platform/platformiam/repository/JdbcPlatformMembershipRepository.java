package com.sanad.platform.platformiam.repository;

import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL/JDBC persistence for the explicit Platform IAM membership boundary.
 * RLS remains authoritative; every lookup is also explicitly control-tenant scoped.
 */
@Repository
public class JdbcPlatformMembershipRepository implements PlatformMembershipRepository {

    private static final String COLUMNS = """
            id, control_tenant_id, user_id, status,
            invited_at, activated_at, suspended_at, locked_at, disabled_at,
            created_by, updated_by, status_reason, created_at, updated_at
            """;

    private static final String PM_COLUMNS = """
            pm.id, pm.control_tenant_id, pm.user_id, pm.status,
            pm.invited_at, pm.activated_at, pm.suspended_at, pm.locked_at, pm.disabled_at,
            pm.created_by, pm.updated_by, pm.status_reason, pm.created_at, pm.updated_at
            """;

    private static final RowMapper<PlatformMembership> ROW_MAPPER =
            JdbcPlatformMembershipRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPlatformMembershipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<PlatformMembership> findByControlTenantIdAndUserId(UUID controlTenantId, UUID userId) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");
        Objects.requireNonNull(userId, "userId");

        List<PlatformMembership> rows = jdbc.query("""
                SELECT %s
                FROM platform_memberships
                WHERE control_tenant_id = :controlTenantId
                  AND user_id = :userId
                """.formatted(COLUMNS),
                new MapSqlParameterSource()
                        .addValue("controlTenantId", controlTenantId)
                        .addValue("userId", userId),
                ROW_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public List<PlatformMembership> findByControlTenantId(UUID controlTenantId) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");

        return jdbc.query("""
                SELECT %s
                FROM platform_memberships
                WHERE control_tenant_id = :controlTenantId
                ORDER BY created_at, id
                """.formatted(COLUMNS),
                new MapSqlParameterSource("controlTenantId", controlTenantId),
                ROW_MAPPER);
    }

    @Override
    public PlatformMembership save(PlatformMembership membership) {
        Objects.requireNonNull(membership, "membership");
        Objects.requireNonNull(membership.controlTenantId(), "membership.controlTenantId");
        Objects.requireNonNull(membership.userId(), "membership.userId");
        Objects.requireNonNull(membership.status(), "membership.status");

        UUID id = membership.id() == null ? UUID.randomUUID() : membership.id();
        Instant createdAt = membership.createdAt() == null ? Instant.now() : membership.createdAt();
        Instant updatedAt = membership.updatedAt() == null ? createdAt : membership.updatedAt();

        jdbc.update("""
                INSERT INTO platform_memberships (
                    id, control_tenant_id, user_id, status,
                    invited_at, activated_at, suspended_at, locked_at, disabled_at,
                    created_by, updated_by, status_reason, created_at, updated_at
                ) VALUES (
                    :id, :controlTenantId, :userId, :status,
                    :invitedAt, :activatedAt, :suspendedAt, :lockedAt, :disabledAt,
                    :createdBy, :updatedBy, :statusReason, :createdAt, :updatedAt
                )
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    invited_at = EXCLUDED.invited_at,
                    activated_at = EXCLUDED.activated_at,
                    suspended_at = EXCLUDED.suspended_at,
                    locked_at = EXCLUDED.locked_at,
                    disabled_at = EXCLUDED.disabled_at,
                    updated_by = EXCLUDED.updated_by,
                    status_reason = EXCLUDED.status_reason,
                    updated_at = EXCLUDED.updated_at
                """,
                parameters(membership, id, createdAt, updatedAt));

        return findByControlTenantIdAndUserId(membership.controlTenantId(), membership.userId())
                .orElseThrow(() -> new IllegalStateException("Platform membership was not visible after save"));
    }

    @Override
    public List<PlatformMembership> lockActiveMembershipsByRoleCode(UUID controlTenantId, String roleCode) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("roleCode must not be blank");
        }

        return jdbc.query("""
                SELECT %s
                FROM platform_memberships pm
                WHERE pm.control_tenant_id = :controlTenantId
                  AND pm.status = 'ACTIVE'
                  AND EXISTS (
                      SELECT 1
                      FROM user_role_assignments ura
                      JOIN roles r
                        ON r.tenant_id = ura.tenant_id
                       AND r.id = ura.role_id
                      WHERE ura.tenant_id = pm.control_tenant_id
                        AND ura.user_id = pm.user_id
                        AND ura.status = 'ACTIVE'
                        AND r.status = 'ACTIVE'
                        AND r.code = :roleCode
                  )
                ORDER BY pm.user_id, pm.id
                FOR UPDATE OF pm
                """.formatted(PM_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("controlTenantId", controlTenantId)
                        .addValue("roleCode", roleCode.trim()),
                ROW_MAPPER);
    }

    private static MapSqlParameterSource parameters(
            PlatformMembership membership,
            UUID id,
            Instant createdAt,
            Instant updatedAt) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("controlTenantId", membership.controlTenantId())
                .addValue("userId", membership.userId())
                .addValue("status", membership.status().name())
                .addValue("invitedAt", timestamp(membership.invitedAt()))
                .addValue("activatedAt", timestamp(membership.activatedAt()))
                .addValue("suspendedAt", timestamp(membership.suspendedAt()))
                .addValue("lockedAt", timestamp(membership.lockedAt()))
                .addValue("disabledAt", timestamp(membership.disabledAt()))
                .addValue("createdBy", membership.createdBy())
                .addValue("updatedBy", membership.updatedBy())
                .addValue("statusReason", membership.statusReason())
                .addValue("createdAt", Timestamp.from(createdAt))
                .addValue("updatedAt", Timestamp.from(updatedAt));
    }

    private static PlatformMembership mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PlatformMembership(
                rs.getObject("id", UUID.class),
                rs.getObject("control_tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                PlatformMembershipStatus.valueOf(rs.getString("status")),
                instant(rs, "invited_at"),
                instant(rs, "activated_at"),
                instant(rs, "suspended_at"),
                instant(rs, "locked_at"),
                instant(rs, "disabled_at"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("updated_by", UUID.class),
                rs.getString("status_reason"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
