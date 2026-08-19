package com.sanad.platform.crm.mobile.sync.service;

import com.sanad.platform.crm.mobile.sync.model.DeviceRegistrationRequest;
import com.sanad.platform.crm.mobile.sync.model.DeviceRegistrationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Runtime service for G7 mobile_device_registry lifecycle. */
@Service
public class MobileDeviceService {

    private final JdbcTemplate jdbcTemplate;

    public MobileDeviceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DeviceRegistrationResponse register(UUID tenantId, UUID userId, DeviceRegistrationRequest request) {
        UUID deviceId = request.deviceId() == null || request.deviceId().isBlank()
            ? UUID.randomUUID()
            : UUID.fromString(request.deviceId());

        int rows = jdbcTemplate.update("""
            INSERT INTO mobile_device_registry
                (device_id, tenant_id, user_id, device_name, device_platform,
                 device_version, app_version, push_token, registered_at, updated_at, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), TRUE)
            ON CONFLICT (device_id)
            DO UPDATE SET device_name = EXCLUDED.device_name,
                          device_platform = EXCLUDED.device_platform,
                          device_version = EXCLUDED.device_version,
                          app_version = EXCLUDED.app_version,
                          push_token = EXCLUDED.push_token,
                          updated_at = NOW(),
                          is_active = TRUE
            WHERE mobile_device_registry.tenant_id = EXCLUDED.tenant_id
              AND mobile_device_registry.user_id = EXCLUDED.user_id
        """, deviceId, tenantId, userId, request.deviceName(), request.devicePlatform(),
            request.deviceVersion(), request.appVersion(), request.pushToken());

        if (rows == 0) {
            throw new IllegalStateException("Device id already belongs to another tenant or user");
        }

        Map<String, Object> row = jdbcTemplate.queryForMap("""
            SELECT registered_at, updated_at
            FROM mobile_device_registry
            WHERE tenant_id = ? AND user_id = ? AND device_id = ? AND is_active = TRUE
        """, tenantId, userId, deviceId);

        return new DeviceRegistrationResponse(
            deviceId.toString(),
            "ACTIVE",
            toInstant(row.get("registered_at")),
            toInstant(row.get("updated_at"))
        );
    }

    @Transactional
    public void heartbeat(UUID tenantId, UUID userId, UUID deviceId) {
        int updated = jdbcTemplate.update("""
            UPDATE mobile_device_registry
            SET updated_at = NOW()
            WHERE tenant_id = ? AND user_id = ? AND device_id = ? AND is_active = TRUE
        """, tenantId, userId, deviceId);
        if (updated == 0) {
            throw new IllegalArgumentException("Unknown or inactive device: " + deviceId);
        }
    }

    @Transactional(readOnly = true)
    public void assertActiveDevice(UUID tenantId, UUID userId, UUID deviceId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM mobile_device_registry
            WHERE tenant_id = ? AND user_id = ? AND device_id = ? AND is_active = TRUE
        """, Integer.class, tenantId, userId, deviceId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Unknown or inactive device: " + deviceId);
        }
    }

    @Transactional
    public void deactivate(UUID tenantId, UUID userId, UUID deviceId) {
        int updated = jdbcTemplate.update("""
            UPDATE mobile_device_registry
            SET is_active = FALSE, push_token = NULL, updated_at = NOW()
            WHERE tenant_id = ? AND user_id = ? AND device_id = ? AND is_active = TRUE
        """, tenantId, userId, deviceId);
        if (updated == 0) {
            throw new IllegalArgumentException("Unknown or inactive device: " + deviceId);
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
        if (value instanceof Instant instant) return instant;
        return Instant.parse(value.toString());
    }
}
