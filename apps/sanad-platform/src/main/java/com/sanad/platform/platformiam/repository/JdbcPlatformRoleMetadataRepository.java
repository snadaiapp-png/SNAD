package com.sanad.platform.platformiam.repository;

import com.sanad.platform.platformiam.domain.PlatformRoleMetadata;
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
 * PostgreSQL/JDBC persistence for Platform IAM role metadata.
 */
@Repository
public class JdbcPlatformRoleMetadataRepository implements PlatformRoleMetadataRepository {

    private static final String COLUMNS = """
            control_tenant_id, role_id, role_type, protected, owner_role, created_at, updated_at
            """;

    private static final RowMapper<PlatformRoleMetadata> ROW_MAPPER =
            JdbcPlatformRoleMetadataRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPlatformRoleMetadataRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<PlatformRoleMetadata> findByControlTenantIdAndRoleId(UUID controlTenantId, UUID roleId) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");
        Objects.requireNonNull(roleId, "roleId");

        List<PlatformRoleMetadata> rows = jdbc.query("""
                SELECT %s
                FROM platform_role_metadata
                WHERE control_tenant_id = :controlTenantId
                  AND role_id = :roleId
                """.formatted(COLUMNS),
                new MapSqlParameterSource()
                        .addValue("controlTenantId", controlTenantId)
                        .addValue("roleId", roleId),
                ROW_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public List<PlatformRoleMetadata> findByControlTenantId(UUID controlTenantId) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");

        return jdbc.query("""
                SELECT %s
                FROM platform_role_metadata
                WHERE control_tenant_id = :controlTenantId
                ORDER BY role_id
                """.formatted(COLUMNS),
                new MapSqlParameterSource("controlTenantId", controlTenantId),
                ROW_MAPPER);
    }

    @Override
    public PlatformRoleMetadata save(PlatformRoleMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(metadata.controlTenantId(), "metadata.controlTenantId");
        Objects.requireNonNull(metadata.roleId(), "metadata.roleId");
        Objects.requireNonNull(metadata.roleType(), "metadata.roleType");

        Instant createdAt = metadata.createdAt() == null ? Instant.now() : metadata.createdAt();
        Instant updatedAt = metadata.updatedAt() == null ? createdAt : metadata.updatedAt();

        jdbc.update("""
                INSERT INTO platform_role_metadata (
                    control_tenant_id, role_id, role_type, protected, owner_role, created_at, updated_at
                ) VALUES (
                    :controlTenantId, :roleId, :roleType, :protectedRole, :ownerRole, :createdAt, :updatedAt
                )
                ON CONFLICT (control_tenant_id, role_id) DO UPDATE SET
                    role_type = EXCLUDED.role_type,
                    protected = EXCLUDED.protected,
                    owner_role = EXCLUDED.owner_role,
                    updated_at = EXCLUDED.updated_at
                """,
                new MapSqlParameterSource()
                        .addValue("controlTenantId", metadata.controlTenantId())
                        .addValue("roleId", metadata.roleId())
                        .addValue("roleType", metadata.roleType().name())
                        .addValue("protectedRole", metadata.protectedRole())
                        .addValue("ownerRole", metadata.ownerRole())
                        .addValue("createdAt", Timestamp.from(createdAt))
                        .addValue("updatedAt", Timestamp.from(updatedAt)));

        return findByControlTenantIdAndRoleId(metadata.controlTenantId(), metadata.roleId())
                .orElseThrow(() -> new IllegalStateException("Platform role metadata was not visible after save"));
    }

    private static PlatformRoleMetadata mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PlatformRoleMetadata(
                rs.getObject("control_tenant_id", UUID.class),
                rs.getObject("role_id", UUID.class),
                PlatformRoleMetadata.RoleType.valueOf(rs.getString("role_type")),
                rs.getBoolean("protected"),
                rs.getBoolean("owner_role"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
