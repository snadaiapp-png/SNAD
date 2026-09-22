package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.AdminDtos.DashboardResponse;
import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.admin.api.AdminDtos.UpdateTenantRequest;
import com.sanad.platform.admin.api.AdminDtos.ChangeTenantStatusRequest;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainType;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.TenantDomainService;
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
import java.time.ZoneId;
import java.util.*;

/**
 * Executive Management application service.
 * Owns tenant management, dashboard metrics, and organization directory.
 */
@Service
public class ExecutivePlatformService {

    private static final Set<String> TENANT_STATUSES = Set.of(
            "PENDING", "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED");
    private static final Map<String, Set<String>> TENANT_TRANSITIONS = Map.of(
            "PENDING", Set.of("TRIAL", "ACTIVE", "CANCELLED", "ARCHIVED"),
            "TRIAL", Set.of("ACTIVE", "PAST_DUE", "CANCELLED", "ARCHIVED"),
            "ACTIVE", Set.of("PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED"),
            "PAST_DUE", Set.of("ACTIVE", "SUSPENDED", "CANCELLED", "ARCHIVED"),
            "SUSPENDED", Set.of("ACTIVE", "CANCELLED", "ARCHIVED"),
            "CANCELLED", Set.of("ARCHIVED"),
            "ARCHIVED", Set.of()
    );

    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditService auditService;
    private final RegistrationProvisioner registrationProvisioner;
    private final TenantDomainService tenantDomainService;

    public ExecutivePlatformService(
            JdbcTemplate jdbcTemplate,
            PlatformAuditService auditService,
            RegistrationProvisioner registrationProvisioner,
            TenantDomainService tenantDomainService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.registrationProvisioner = registrationProvisioner;
        this.tenantDomainService = tenantDomainService;
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
        // RegistrationProvisioner is the single tenant-creation authority. The
        // previous implementation inserted one tenant here and then called the
        // provisioner, which created a second tenant containing the administrator
        // and roles. Use the provisioner's tenant id and update that same row.
        String countryCode = normalizeCountryCode(request.countryCode());
        String locale = normalizeLocale(request.locale(), "en");
        String timezone = normalizeTimezone(request.timezone(), "UTC");
        String currencyCode = normalizeCurrencyCode(request.currencyCode(), "SAR");

        RegistrationProvisioner.ProvisionedRegistration provisioned = registrationProvisioner.provision(
                request.adminEmail(), request.adminDisplayName(), request.name(), request.subdomain(),
                null, countryCode);
        UUID tenantId = provisioned.tenantId();

        Timestamp trialEndsAt = request.trialDays() != null && request.trialDays() > 0
                ? Timestamp.from(Instant.now().plusSeconds(request.trialDays() * 86400L))
                : null;
        jdbcTemplate.update(
                "UPDATE tenants SET name=?, legal_name=?, subdomain=?, status='PENDING', billing_email=?, "
                        + "country_code=?, locale=?, timezone=?, currency_code=?, trial_ends_at=?, updated_at=NOW() "
                        + "WHERE id=?",
                request.name().trim(), trimToNull(request.legalName()), request.subdomain().trim().toLowerCase(Locale.ROOT),
                lowerEmail(request.billingEmail()), countryCode, locale, timezone, currencyCode,
                trialEndsAt, tenantId);

        tenantDomainService.ensureDefaultDomain(
                tenantId,
                request.subdomain(),
                DomainType.APPLICATION,
                authentication
        );

        TenantResponse created = getTenant(tenantId);
        auditService.success(authentication, tenantId, "CREATE_TENANT", "TENANT", tenantId.toString(),
                request.name(), null, created);
        return created;
    }

    @Transactional
    public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request, Authentication authentication) {
        TenantResponse before = getTenant(tenantId);
        if ("ARCHIVED".equals(before.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived tenant cannot be modified");
        }

        jdbcTemplate.update(
                "UPDATE tenants SET "
                        + "name = COALESCE(?, name), "
                        + "legal_name = COALESCE(?, legal_name), "
                        + "billing_email = COALESCE(?, billing_email), "
                        + "country_code = COALESCE(?, country_code), "
                        + "locale = COALESCE(?, locale), "
                        + "timezone = COALESCE(?, timezone), "
                        + "currency_code = COALESCE(?, currency_code), "
                        + "updated_at = NOW() WHERE id = ?",
                trimToNull(request.name()), trimToNull(request.legalName()), lowerEmail(request.billingEmail()),
                normalizeCountryCode(request.countryCode()), normalizeLocale(request.locale(), null),
                normalizeTimezone(request.timezone(), null), normalizeCurrencyCode(request.currencyCode(), null),
                tenantId);

        TenantResponse after = getTenant(tenantId);
        auditService.success(authentication, tenantId, "UPDATE_TENANT", "TENANT", tenantId.toString(),
                "Executive tenant profile update", before, after);
        return after;
    }

    @Transactional
    public TenantResponse changeTenantStatus(UUID tenantId, ChangeTenantStatusRequest request, Authentication authentication) {
        TenantResponse before = getTenant(tenantId);
        String targetStatus = normalizedTenantStatus(request.status());
        if (targetStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tenant status: " + request.status());
        }
        Set<String> allowed = TENANT_TRANSITIONS.getOrDefault(before.status(), Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot transition from " + before.status() + " to " + targetStatus);
        }
        jdbcTemplate.update(
                "UPDATE tenants SET status = ?, suspension_reason = ?, updated_at = NOW() WHERE id = ?",
                targetStatus,
                Set.of("SUSPENDED", "CANCELLED", "ARCHIVED").contains(targetStatus) ? request.reason() : null,
                tenantId);
        TenantResponse after = getTenant(tenantId);
        auditService.success(authentication, tenantId, "CHANGE_TENANT_STATUS", "TENANT", tenantId.toString(),
                request.reason(), before, after);
        return after;
    }

    public String recordTenantLoginLinkEvent(UUID tenantId, String requestedAction, Authentication authentication) {
        String action = requestedAction == null ? "" : requestedAction.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("OPEN", "COPY").contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported tenant login link action");
        }

        TenantResponse tenant = getTenant(tenantId);
        if (!"ACTIVE".equals(tenant.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant login link is available only for active tenants");
        }
        Integer effectiveSubscriptions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM tenant_subscriptions
                 WHERE tenant_id = ?
                   AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')
                """, Integer.class, tenantId);
        if (effectiveSubscriptions == null || effectiveSubscriptions != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tenant login link requires exactly one effective subscription");
        }

        var defaultDomain = tenantDomainService.ensureDefaultDomain(
                tenantId, tenant.subdomain(), DomainType.APPLICATION, authentication);
        if (defaultDomain == null || defaultDomain.hostname() == null || defaultDomain.hostname().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tenant login link requires a configured platform base domain");
        }

        auditService.success(
                authentication,
                tenantId,
                "TENANT_LOGIN_LINK_" + action,
                "TENANT",
                tenantId.toString(),
                "Executive " + ("OPEN".equals(action) ? "opened" : "copied") + " tenant sign-in link",
                null,
                Map.of("action", action, "hostname", defaultDomain.hostname())
        );
        return defaultDomain.hostname();
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

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String lowerEmail(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalizeCountryCode(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z]{2}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "countryCode must be ISO-3166 alpha-2");
        }
        return normalized;
    }

    private static String normalizeCurrencyCode(String value, String fallback) {
        String normalized = trimToNull(value);
        if (normalized == null) normalized = fallback;
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyCode must be ISO-4217");
        }
        return normalized;
    }

    private static String normalizeLocale(String value, String fallback) {
        String normalized = trimToNull(value);
        if (normalized == null) normalized = fallback;
        if (normalized == null) return null;
        if (!normalized.matches("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "locale must be a valid language tag");
        }
        return normalized;
    }

    private static String normalizeTimezone(String value, String fallback) {
        String normalized = trimToNull(value);
        if (normalized == null) normalized = fallback;
        if (normalized == null) return null;
        try {
            return ZoneId.of(normalized).getId();
        } catch (RuntimeException invalidZone) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timezone must be a valid IANA zone", invalidZone);
        }
    }
}
