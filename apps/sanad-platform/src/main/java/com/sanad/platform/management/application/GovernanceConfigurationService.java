package com.sanad.platform.management.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.management.api.GovernanceConfigDtos.ConfigurationResponse;
import com.sanad.platform.management.api.GovernanceConfigDtos.ConfigType;
import com.sanad.platform.management.api.GovernanceConfigDtos.CreateConfigurationRequest;
import com.sanad.platform.management.api.GovernanceConfigDtos.UpdateConfigurationRequest;
import com.sanad.platform.management.api.GovernanceConfigDtos.ResolvedValue;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped governance configuration store (V20260815_24+).
 *
 * <p>Replaces hard-coded SLA/alert/escalation thresholds with
 * tenant-overridable defaults. When no row exists for a key, callers
 * fall back to the Java-side default registered via
 * {@link #registerDefault(String, ConfigType, Object)}.
 *
 * <p>Safe defaults are registered once at bean construction time
 * (see {@link GovernanceConfigurationDefaults}). The service never
 * throws when a key is missing — it returns the default.
 *
 * <p>All mutations are audited via {@link ManagementAuditRepository}
 * (management_audit_trail table — RLS-enabled).
 */
@Service
public class GovernanceConfigurationService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final GovernanceConfigurationDefaults defaults;

    public GovernanceConfigurationService(
            JdbcTemplate jdbc,
            PlatformAuditService auditService,
            GovernanceConfigurationDefaults defaults) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.defaults = defaults;
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public ConfigurationResponse create(UUID tenantId, CreateConfigurationRequest request, Authentication auth) {
        ensureTenant(tenantId);
        if (request == null || request.configKey() == null || request.configKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configKey is required");
        }
        if (request.configValue() == null || request.configValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configValue is required");
        }
        ConfigType type = request.configType() == null ? ConfigType.STRING : request.configType();
        validateValue(type, request.configValue());

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UUID actor = actorUserId(auth);
        try {
            jdbc.update(
                    "INSERT INTO governance_configurations "
                            + "(id, tenant_id, config_key, config_value, config_type, description, enabled, "
                            + " updated_by, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, 0, ?, ?)",
                    id, tenantId, request.configKey().trim(), request.configValue().trim(),
                    type.name(),
                    request.description() == null ? null : request.description().trim(),
                    actor, Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "configuration key already exists: " + request.configKey());
        }
        audit(tenantId, auth, "GOVERNANCE_CONFIG.CREATED", id, request.configKey(), null, request.configValue());
        return getOrThrow(tenantId, id);
    }

    // ============================================================
    // READ
    // ============================================================

    @Transactional(readOnly = true)
    public List<ConfigurationResponse> list(UUID tenantId) {
        ensureTenant(tenantId);
        return jdbc.query(
                "SELECT * FROM governance_configurations WHERE tenant_id = ? ORDER BY config_key",
                this::mapRow, tenantId);
    }

    @Transactional(readOnly = true)
    public ConfigurationResponse get(UUID tenantId, UUID configId) {
        ensureTenant(tenantId);
        return getOrThrow(tenantId, configId);
    }

    @Transactional(readOnly = true)
    public Optional<ConfigurationResponse> findByKey(UUID tenantId, String key) {
        if (tenantId == null || key == null) return Optional.empty();
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM governance_configurations WHERE tenant_id = ? AND config_key = ?",
                    this::mapRow, tenantId, key.trim()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // ============================================================
    // UPDATE / ENABLE / DISABLE
    // ============================================================

    @Transactional
    public ConfigurationResponse update(UUID tenantId, UUID configId, UpdateConfigurationRequest request, Authentication auth) {
        ConfigurationResponse existing = getOrThrow(tenantId, configId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        Instant now = Instant.now();
        if (request.configValue() != null) {
            validateValue(existing.configType(), request.configValue());
            jdbc.update(
                    "UPDATE governance_configurations SET config_value = ?, updated_by = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?",
                    request.configValue().trim(), actorUserId(auth), Timestamp.from(now), tenantId, configId);
        }
        if (request.enabled() != null) {
            jdbc.update(
                    "UPDATE governance_configurations SET enabled = ?, updated_by = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?",
                    request.enabled(), actorUserId(auth), Timestamp.from(now), tenantId, configId);
        }
        ConfigurationResponse after = getOrThrow(tenantId, configId);
        audit(tenantId, auth, "GOVERNANCE_CONFIG.UPDATED", configId, existing.configKey(),
                existing.configValue(), after.configValue());
        return after;
    }

    @Transactional
    public void delete(UUID tenantId, UUID configId, Authentication auth) {
        ConfigurationResponse existing = getOrThrow(tenantId, configId);
        int rows = jdbc.update(
                "DELETE FROM governance_configurations WHERE tenant_id = ? AND id = ?",
                tenantId, configId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "configuration not found");
        }
        audit(tenantId, auth, "GOVERNANCE_CONFIG.DELETED", configId, existing.configKey(),
                existing.configValue(), null);
    }

    // ============================================================
    // EFFECTIVE VALUE RESOLUTION (with default fallback)
    // ============================================================

    @Transactional(readOnly = true)
    public ResolvedValue<String> resolveString(UUID tenantId, String key, String defaultValue) {
        Optional<ConfigurationResponse> row = findByKey(tenantId, key);
        if (row.isPresent() && row.get().enabled()) {
            return new ResolvedValue<>(key, row.get().configValue(), true, "TENANT_OVERRIDE");
        }
        String defaultVal = defaults != null ? defaults.getStringDefault(key) : null;
        if (defaultVal != null) {
            return new ResolvedValue<>(key, defaultVal, false, "REGISTERED_DEFAULT");
        }
        return new ResolvedValue<>(key, defaultValue, false, "CALLER_DEFAULT");
    }

    @Transactional(readOnly = true)
    public ResolvedValue<Integer> resolveInteger(UUID tenantId, String key, int defaultValue) {
        Optional<ConfigurationResponse> row = findByKey(tenantId, key);
        if (row.isPresent() && row.get().enabled()) {
            try {
                return new ResolvedValue<>(key, Integer.parseInt(row.get().configValue().trim()), true, "TENANT_OVERRIDE");
            } catch (NumberFormatException e) {
                // fall through to default
            }
        }
        Integer defaultVal = defaults != null ? defaults.getIntegerDefault(key) : null;
        if (defaultVal != null) {
            return new ResolvedValue<>(key, defaultVal, false, "REGISTERED_DEFAULT");
        }
        return new ResolvedValue<>(key, defaultValue, false, "CALLER_DEFAULT");
    }

    @Transactional(readOnly = true)
    public ResolvedValue<Boolean> resolveBoolean(UUID tenantId, String key, boolean defaultValue) {
        Optional<ConfigurationResponse> row = findByKey(tenantId, key);
        if (row.isPresent() && row.get().enabled()) {
            String v = row.get().configValue().trim().toLowerCase();
            return new ResolvedValue<>(key, "true".equals(v) || "1".equals(v) || "yes".equals(v), true, "TENANT_OVERRIDE");
        }
        Boolean defaultVal = defaults != null ? defaults.getBooleanDefault(key) : null;
        if (defaultVal != null) {
            return new ResolvedValue<>(key, defaultVal, false, "REGISTERED_DEFAULT");
        }
        return new ResolvedValue<>(key, defaultValue, false, "CALLER_DEFAULT");
    }

    @Transactional(readOnly = true)
    public ResolvedValue<Duration> resolveDuration(UUID tenantId, String key, Duration defaultValue) {
        Optional<ConfigurationResponse> row = findByKey(tenantId, key);
        if (row.isPresent() && row.get().enabled()) {
            try {
                return new ResolvedValue<>(key, Duration.parse(row.get().configValue().trim()), true, "TENANT_OVERRIDE");
            } catch (Exception e) {
                // fall through
            }
        }
        Duration defaultVal = defaults != null ? defaults.getDurationDefault(key) : null;
        if (defaultVal != null) {
            return new ResolvedValue<>(key, defaultVal, false, "REGISTERED_DEFAULT");
        }
        return new ResolvedValue<>(key, defaultValue, false, "CALLER_DEFAULT");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private ConfigurationResponse getOrThrow(UUID tenantId, UUID configId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM governance_configurations WHERE tenant_id = ? AND id = ?",
                    this::mapRow, tenantId, configId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "configuration not found: " + configId);
        }
    }

    private void ensureTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, tenantId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found: " + tenantId);
        }
    }

    private void validateValue(ConfigType type, String value) {
        switch (type) {
            case INTEGER -> {
                try { Integer.parseInt(value.trim()); }
                catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "configValue is not a valid INTEGER: " + value);
                }
            }
            case BOOLEAN -> {
                String v = value.trim().toLowerCase();
                if (!v.equals("true") && !v.equals("false") && !v.equals("1") && !v.equals("0")
                        && !v.equals("yes") && !v.equals("no")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "configValue is not a valid BOOLEAN: " + value);
                }
            }
            case DECIMAL -> {
                try { new java.math.BigDecimal(value.trim()); }
                catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "configValue is not a valid DECIMAL: " + value);
                }
            }
            case DURATION_ISO -> {
                try { Duration.parse(value.trim()); }
                catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "configValue is not a valid DURATION_ISO (e.g. PT168H): " + value);
                }
            }
            case JSON -> {
                if (value.trim().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON configValue cannot be blank");
                }
                // Light validation: must start with { or [
                String v = value.trim();
                if (!(v.startsWith("{") || v.startsWith("["))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "JSON configValue must start with { or [: " + value);
                }
            }
            case STRING -> { /* any non-blank string is valid */ }
        }
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId,
                       String key, String oldValue, String newValue) {
        try {
            java.util.Map<String, Object> before = new java.util.HashMap<>();
            before.put("key", key);
            before.put("value", oldValue);
            java.util.Map<String, Object> after = new java.util.HashMap<>();
            after.put("key", key);
            after.put("value", newValue);
            auditService.success(auth, tenantId, action, "GOVERNANCE_CONFIGURATION",
                    resourceId == null ? null : resourceId.toString(),
                    "key=" + key, before, after);
        } catch (Exception ignored) {
            // audit failure must not break business operation
        }
    }

    private ConfigurationResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ConfigurationResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("config_key"),
                rs.getString("config_value"),
                ConfigType.valueOf(rs.getString("config_type")),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                rs.getObject("updated_by", UUID.class),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant()
        );
    }
}
