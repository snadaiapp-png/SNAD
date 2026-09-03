package com.sanad.platform.organization.worklocation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcWorkLocationRepository implements WorkLocationRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkLocationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkLocation> findByTenantIdAndId(UUID tenantId, UUID id) {
        return jdbc.query(
                "SELECT id, tenant_id, code, name, country_code, city, timezone, status, created_at, updated_at FROM work_locations WHERE tenant_id = ? AND id = ?",
                (rs, rowNum) -> new WorkLocation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("country_code"),
                        rs.getString("city"),
                        rs.getString("timezone"),
                        WorkLocationStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                tenantId, id
        ).stream().findFirst();
    }

    @Override
    public Optional<WorkLocation> findByTenantIdAndCode(UUID tenantId, String code) {
        return jdbc.query(
                "SELECT id, tenant_id, code, name, country_code, city, timezone, status, created_at, updated_at FROM work_locations WHERE tenant_id = ? AND code = ?",
                (rs, rowNum) -> new WorkLocation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("country_code"),
                        rs.getString("city"),
                        rs.getString("timezone"),
                        WorkLocationStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                tenantId, code
        ).stream().findFirst();
    }

    @Override
    public WorkLocation save(WorkLocation entity) {
        if (entity.id() == null) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO work_locations (id, tenant_id, code, name, country_code, city, timezone, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """, id, entity.tenantId(), entity.code(), entity.name(),
                    entity.countryCode(), entity.city(), entity.timezone(),
                    entity.status().name());
            return findByTenantIdAndId(entity.tenantId(), id).orElseThrow();
        } else {
            jdbc.update("""
                    UPDATE work_locations SET code = ?, name = ?, country_code = ?, city = ?, timezone = ?, status = ?, updated_at = NOW()
                    WHERE tenant_id = ? AND id = ?
                    """, entity.code(), entity.name(), entity.countryCode(),
                    entity.city(), entity.timezone(), entity.status().name(),
                    entity.tenantId(), entity.id());
            return findByTenantIdAndId(entity.tenantId(), entity.id()).orElseThrow();
        }
    }
}
