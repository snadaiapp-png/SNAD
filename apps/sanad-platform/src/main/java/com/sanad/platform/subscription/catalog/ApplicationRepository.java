package com.sanad.platform.subscription.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate repository for {@link ApplicationEntity} — raw SQL, consistent
 * with {@code ModuleRepository} and {@code SaasAdministrationService}.
 */
@Repository
public class ApplicationRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    static final RowMapper<ApplicationEntity> ROW_MAPPER = (rs, rowNum) -> {
        ApplicationEntity a = new ApplicationEntity();
        a.setId(rs.getObject("id", UUID.class));
        a.setCode(rs.getString("code"));
        a.setName(rs.getString("name"));
        a.setLocalizedName(rs.getString("localized_name"));
        a.setDescription(rs.getString("description"));
        a.setCategory(rs.getString("category"));
        a.setStatus(rs.getString("status"));
        a.setVersion(rs.getString("version"));
        a.setDisplayOrder(rs.getInt("display_order"));
        a.setIconKey(rs.getString("icon_key"));
        a.setProvisioningMode(rs.getString("provisioning_mode"));
        a.setSupportedCountries(parseList(rs.getString("supported_countries")));
        a.setDependencies(parseList(rs.getString("dependencies")));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        a.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        a.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return a;
    };

    private static List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    static String toJson(List<String> values) {
        if (values == null) return "[]";
        try {
            return JSON.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private final JdbcTemplate jdbc;

    public ApplicationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ApplicationEntity> findAll() {
        return jdbc.query("SELECT * FROM applications ORDER BY display_order, code", ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public List<ApplicationEntity> findAvailable() {
        return jdbc.query(
                "SELECT * FROM applications WHERE status = 'ACTIVE' ORDER BY display_order, code",
                ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationEntity> findById(UUID id) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject("SELECT * FROM applications WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationEntity> findByCode(String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM applications WHERE code = ?", ROW_MAPPER, code));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE code = ?", Long.class, code);
        return count != null && count > 0;
    }

    @Transactional
    public void insert(ApplicationEntity a) {
        jdbc.update("""
                        INSERT INTO applications (
                            id, code, name, localized_name, description, category, status,
                            version, display_order, icon_key, provisioning_mode,
                            supported_countries, dependencies, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                        """,
                a.getId(), a.getCode(), a.getName(), a.getLocalizedName(), a.getDescription(),
                a.getCategory(), a.getStatus(), a.getVersion(), a.getDisplayOrder(),
                a.getIconKey(), a.getProvisioningMode(),
                toJson(a.getSupportedCountries()), toJson(a.getDependencies()),
                Timestamp.from(a.getCreatedAt()), Timestamp.from(a.getUpdatedAt()));
    }

    @Transactional
    public void update(ApplicationEntity a) {
        jdbc.update("""
                        UPDATE applications SET
                            name = ?, localized_name = ?, description = ?, category = ?, status = ?,
                            version = ?, display_order = ?, icon_key = ?, provisioning_mode = ?,
                            supported_countries = ?::jsonb, dependencies = ?::jsonb, updated_at = ?
                        WHERE id = ?
                        """,
                a.getName(), a.getLocalizedName(), a.getDescription(), a.getCategory(),
                a.getStatus(), a.getVersion(), a.getDisplayOrder(), a.getIconKey(),
                a.getProvisioningMode(),
                toJson(a.getSupportedCountries()), toJson(a.getDependencies()),
                Timestamp.from(a.getUpdatedAt()), a.getId());
    }
}
