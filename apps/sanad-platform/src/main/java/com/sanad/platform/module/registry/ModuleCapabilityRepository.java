package com.sanad.platform.module.registry;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate-based repository for {@link ModuleCapabilityEntity}.
 */
@Repository
public class ModuleCapabilityRepository {

    private static final RowMapper<ModuleCapabilityEntity> ROW_MAPPER = (rs, rowNum) -> {
        ModuleCapabilityEntity c = new ModuleCapabilityEntity();
        c.setId(rs.getObject("id", UUID.class));
        c.setModuleId(rs.getObject("module_id", UUID.class));
        c.setCode(rs.getString("code"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setCapabilityType(rs.getString("capability_type"));
        c.setDefaultValue(rs.getString("default_value"));
        c.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        c.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        c.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return c;
    };

    private final JdbcTemplate jdbc;

    public ModuleCapabilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ModuleCapabilityEntity> findByModuleId(UUID moduleId) {
        return jdbc.query(
                "SELECT * FROM module_capabilities WHERE module_id = ? ORDER BY code",
                ROW_MAPPER, moduleId);
    }

    @Transactional(readOnly = true)
    public List<ModuleCapabilityEntity> findAllActive() {
        return jdbc.query(
                "SELECT * FROM module_capabilities WHERE status = 'ACTIVE' ORDER BY code",
                ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public Optional<ModuleCapabilityEntity> findByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return jdbc.query(
                "SELECT * FROM module_capabilities WHERE code = ?",
                ROW_MAPPER, code.trim().toUpperCase())
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<ModuleCapabilityEntity> findById(UUID id) {
        return jdbc.query(
                "SELECT * FROM module_capabilities WHERE id = ?",
                ROW_MAPPER, id)
                .stream().findFirst();
    }
}
