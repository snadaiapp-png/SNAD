package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.AdminDtos;
import com.sanad.platform.admin.api.AdminDtos.DashboardResponse;
import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.admin.api.AdminDtos.ChangeTenantStatusRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.service.RegistrationProvisioner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Executive Management application service.
 * Owns: tenant management, dashboard metrics, organization directory.
 * Does NOT own: system services, health monitoring, audit (cross-cutting infra).
 */
@Service
public class ExecutivePlatformService {

    private static final Set<String> TENANT_STATUSES = Set.of(
            "PENDING", "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED");
    private static final Map<String, Set<String>> TENANT_TRANSITIONS = Map.of(
            "PENDING", Set.of("TRIAL", "ACTIVE", "CANCELLED"),
            "TRIAL", Set.of("ACTIVE", "PAST_DUE", "CANCELLED"),
            "ACTIVE", Set.of("PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED"),
            "PAST_DUE", Set.of("ACTIVE", "SUSPENDED", "CANCELLED"),
            "SUSPENDED", Set.of("ACTIVE", "CANCELLED", "ARCHIVED"),
            "CANCELLED", Set.of("ARCHIVED"),
            "ARCHIVED", Set.of()
    );

    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditService auditService;
    private final RegistrationProvisioner registrationProvisioner;

    public ExecutivePlatformService(
            JdbcTemplate jdbcTemplate,
            PlatformAuditService auditService,
            RegistrationProvisioner registrationProvisioner
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.registrationProvisioner = registrationProvisioner;
    }

    public DashboardResponse dashboard() {
        long totalTenants = count("SELECT COUNT(*) FROM tenants");
        long activeTenants = count("SELECT COUNT(*) FROM tenants WHERE status = 'ACTIVE'");
        long trialTenants = count("SELECT COUNT(*) FROM tenants WHERE status = 'TRIAL'");
        long suspendedTenants = count("SELECT COUNT(*) FROM tenants WHERE status = 'SUSPENDED'");
        long totalUsers = count("SELECT COUNT(*) FROM users");
        long activeUsers = count("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'");
        long operationalServices = count("SELECT COUNT(*) FROM system_services WHERE status = 'OPERATIONAL'");
        long degradedServices = count("SELECT COUNT(*) FROM system_services WHERE status != 'OPERATIONAL'");
        return new DashboardResponse(totalTenants, activeTenants, trialTenants,
                suspendedTenants, totalUsers, activeUsers,
                operationalServices, degradedServices, auditService.recent(10));
    }

    public List<TenantResponse> listTenants(String search, String status, int requestedLimit, int requestedOffset) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        int offset = Math.max(0, requestedOffset);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM tenants WHERE 1=1");
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(name) LIKE ? OR LOWER(subdomain) LIKE ?)");
            String pattern = "%" + search.toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(normalizedTenantStatus(status));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql.toString(), this::mapTenant, params.toArray());
    }

    public TenantResponse getTenant(UUID tenantId) {
        List<TenantResponse> results = jdbcTemplate.query(
                "SELECT * FROM tenants WHERE id = ?", this::mapTenant, tenantId);
        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId);
        }
        return results.get(0);
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request, Authentication authentication) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, legal_name, subdomain, status, billing_email, country_code, locale, timezone, currency_code, trial_ends_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                tenantId, request.name(), request.legalName(), request.subdomain(),
                request.billingEmail(), request.countryCode(),
                request.locale() != null ? request.locale() : "en",
                request.timezone() != null ? request.timezone() : "UTC",
                request.currencyCode() != null ? request.currencyCode() : "SAR",
                request.trialDays() != null && request.trialDays() > 0
                        ? Timestamp.from(Instant.now().plusSeconds(request.trialDays() * 86400L))
                        : null
        );
        registrationProvisioner.provision(request.adminEmail(), request.adminDisplayName(), request.name(), request.subdomain(), null, request.countryCode());
        auditService.success(authentication, tenantId, "CREATE_TENANT", "TENANT", tenantId.toString(), request.name(), null, getTenant(tenantId));
        return getTenant(tenantId);
    }

    @Transactional
    public TenantResponse changeTenantStatus(UUID tenantId, ChangeTenantStatusRequest request, Authentication authentication) {
        TenantResponse before = getTenant(tenantId);
        Set<String> allowed = TENANT_TRANSITIONS.getOrDefault(before.status(), Set.of());
        if (!allowed.contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot transition from " + before.status() + " to " + request.status());
        }
        jdbcTemplate.update(
                "UPDATE tenants SET status = ?, suspension_reason = ?, updated_at = NOW() WHERE id = ?",
                request.status(),
                "SUSPENDED".equals(request.status()) || "CANCELLED".equals(request.status()) ? request.reason() : null,
                tenantId);
        auditService.success(authentication, tenantId, "CHANGE_TENANT_STATUS", "TENANT", tenantId.toString(), before.status() + " -> " + request.status(), before, getTenant(tenantId));
        return getTenant(tenantId);
    }

    public record AccessCheck(boolean authenticated, boolean canRead, boolean canWrite) {}

    public AccessCheck accessCheck(Authentication authentication) {
        boolean isAuth = authentication != null && authentication.isAuthenticated();
        return new AccessCheck(isAuth, isAuth, isAuth);
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0;
    }

    private TenantResponse mapTenant(ResultSet rs, int row) throws SQLException {
        return new TenantResponse(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("name"),
                rs.getString("legal_name"),
                rs.getString("subdomain"),
                rs.getString("status"),
                rs.getString("billing_email"),
                rs.getString("country_code"),
                rs.getString("locale"),
                rs.getString("timezone"),
                rs.getString("currency_code"),
                rs.getTimestamp("trial_ends_at") != null ? rs.getTimestamp("trial_ends_at").toInstant() : null,
                rs.getString("suspension_reason"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null);
    }

    private static String normalizedTenantStatus(String value) {
        if (value == null) return null;
        String upper = value.trim().toUpperCase();
        return TENANT_STATUSES.contains(upper) ? upper : null;
    }
}
