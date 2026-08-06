package com.sanad.platform.health.service;

import com.sanad.platform.admin.api.AdminDtos.SystemServiceResponse;
import com.sanad.platform.admin.api.AdminDtos.UpdateSystemStatusRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * System Health application service.
 * Owns: system service status, health monitoring endpoints.
 * Does NOT own: tenant management, billing, subscriptions.
 */
@Service
public class SystemHealthService {

    private static final Set<String> SYSTEM_STATUSES = Set.of(
            "OPERATIONAL", "DEGRADED", "MAINTENANCE", "DISABLED", "INCIDENT");

    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditService auditService;

    public SystemHealthService(JdbcTemplate jdbcTemplate, PlatformAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<SystemServiceResponse> listSystemServices() {
        return jdbcTemplate.query(
                "SELECT * FROM system_services ORDER BY criticality DESC, name ASC",
                this::mapSystemService);
    }

    public SystemServiceResponse getSystemService(java.util.UUID serviceId) {
        List<SystemServiceResponse> results = jdbcTemplate.query(
                "SELECT * FROM system_services WHERE id = ?",
                this::mapSystemService, serviceId);
        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System service not found: " + serviceId);
        }
        return results.get(0);
    }

    @Transactional
    public SystemServiceResponse updateSystemStatus(java.util.UUID serviceId, UpdateSystemStatusRequest request, Authentication authentication) {
        SystemServiceResponse before = getSystemService(serviceId);
        jdbcTemplate.update(
                "UPDATE system_services SET status = ?, last_message = ?, last_checked_at = NOW(), updated_at = NOW() WHERE id = ?",
                request.status(), request.reason(), serviceId);
        auditService.success(authentication, "UPDATE_SYSTEM_STATUS",
                "system_services:" + serviceId,
                before.status() + " -> " + request.status() + " (" + request.reason() + ")");
        return getSystemService(serviceId);
    }

    private SystemServiceResponse mapSystemService(ResultSet rs, int row) throws SQLException {
        return new SystemServiceResponse(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("environment"), rs.getString("status"),
                rs.getString("owner_name"), rs.getString("criticality"),
                rs.getString("last_checked_at"), rs.getString("last_latency_ms"),
                rs.getString("last_message"));
    }
}
