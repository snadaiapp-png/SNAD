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
 * JdbcTemplate-based repository for {@link ModuleEntity}.
 *
 * <p>Uses raw SQL (consistent with {@code SaasAdministrationService}) — NOT JPA.
 * This avoids the hybrid JdbcTemplate+JPA anti-pattern for the same tables.
 */
@Repository
public class ModuleRepository {

    private static final RowMapper<ModuleEntity> ROW_MAPPER = (rs, rowNum) -> {
        ModuleEntity m = new ModuleEntity();
        m.setId(rs.getObject("id", UUID.class));
        m.setCode(rs.getString("code"));
        m.setName(rs.getString("name"));
        m.setDescription(rs.getString("description"));
        m.setStatus(rs.getString("status"));
        m.setDisplayOrder(rs.getInt("display_order"));
        m.setVersion(rs.getString("version"));
        m.setEnabled(rs.getBoolean("enabled"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        m.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        m.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return m;
    };

    private final JdbcTemplate jdbc;

    public ModuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ModuleEntity> findAll() {
        return jdbc.query("SELECT * FROM modules ORDER BY display_order, code", ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public List<ModuleEntity> findAllEnabled() {
        return jdbc.query(
                "SELECT * FROM modules WHERE enabled = true AND status = 'ACTIVE' ORDER BY display_order, code",
                ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public Optional<ModuleEntity> findById(UUID id) {
        return jdbc.query("SELECT * FROM modules WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<ModuleEntity> findByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return jdbc.query("SELECT * FROM modules WHERE code = ?", ROW_MAPPER, code.trim().toUpperCase())
                .stream().findFirst();
    }
}
