package com.sanad.platform.admin.service;

import com.sanad.platform.admin.api.AdminDtos.ChangeTenantStatusRequest;
import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.admin.api.AdminDtos.DashboardResponse;
import com.sanad.platform.admin.api.AdminDtos.SystemServiceResponse;
import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.api.AdminDtos.UpdateSystemStatusRequest;
import com.sanad.platform.security.service.RegistrationProvisioner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Application service for the SNAD platform control plane. */
@Service
public class AdminPlatformService {

    private static final Set<String> TENANT_STATUSES = Set.of(
            "PENDING", "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED");
    private static final Set<String> SYSTEM_STATUSES = Set.of(
            "OPERATIONAL", "DEGRADED", "MAINTENANCE", "DISABLED", "INCIDENT");

    private static final Map<String, Set<String>> TENANT_TRANSITIONS = Map.of(
            "PENDING", Set.of("TRIAL", "ACTIVE", "ARCHIVED"),
            "TRIAL", Set.of("ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED"),
            "ACTIVE", Set.of("PAST_DUE", "SUSPENDED", "CANCELLED"),
            "PAST_DUE", Set.of("ACTIVE", "SUSPENDED", "CANCELLED"),
            "SUSPENDED", Set.of("ACTIVE", "CANCELLED", "ARCHIVED"),
            "CANCELLED", Set.of("ACTIVE", "ARCHIVED"),
            "ARCHIVED", Set.of()
    );

    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditService auditService;
    private final RegistrationProvisioner registrationProvisioner;

    public AdminPlatformService(
            JdbcTemplate jdbcTemplate,
            PlatformAuditService auditService,
            RegistrationProvisioner registrationProvisioner
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.registrationProvisioner = registrationProvisioner;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        return new DashboardResponse(
                count("SELECT COUNT(*) FROM tenants"),
                count("SELECT COUNT(*) FROM tenants WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM tenants WHERE status = 'TRIAL'"),
                count("SELECT COUNT(*) FROM tenants WHERE status = 'SUSPENDED'"),
                count("SELECT COUNT(*) FROM users"),
                count("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM system_services WHERE status = 'OPERATIONAL'"),
                count("SELECT COUNT(*) FROM system_services WHERE status IN ('DEGRADED', 'INCIDENT')"),
                auditService.recent(12)
        );
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants(String search, String status, int requestedLimit, int requestedOffset) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        int offset = Math.max(0, requestedOffset);
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, legal_name, subdomain, status, billing_email, country_code, locale, "
                        + "timezone, currency_code, trial_ends_at, suspension_reason, created_at, updated_at "
                        + "FROM tenants WHERE 1=1");

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (LOWER(name) LIKE ? OR LOWER(subdomain) LIKE ? OR LOWER(COALESCE(legal_name, '')) LIKE ?)");
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (status != null && !status.isBlank()) {
            String normalizedStatus = normalizedTenantStatus(status);
            sql.append(" AND status = ?");
            parameters.add(normalizedStatus);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        return jdbcTemplate.query(sql.toString(), this::mapTenant, parameters.toArray());
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID tenantId) {
        List<TenantResponse> matches = jdbcTemplate.query(
                "SELECT id, name, legal_name, subdomain, status, billing_email, country_code, locale, "
                        + "timezone, currency_code, trial_ends_at, suspension_reason, created_at, updated_at "
                        + "FROM tenants WHERE id = ?",
                this::mapTenant,
                tenantId
        );
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        return matches.get(0);
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request, Authentication authentication) {
        String subdomain = request.subdomain().trim().toLowerCase(Locale.ROOT);
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE subdomain = ?", Long.class, subdomain);
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant subdomain already exists");
        }

        int trialDays = request.trialDays() == null ? 0 : request.trialDays();
        if (trialDays < 0 || trialDays > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trialDays must be between 0 and 365");
        }

        Instant now = Instant.now();
        Instant trialEndsAt = trialDays > 0 ? now.plus(trialDays, ChronoUnit.DAYS) : null;
        String status = trialDays > 0 ? "TRIAL" : "ACTIVE";
        RegistrationProvisioner.ProvisionedRegistration provisioned = registrationProvisioner.provision(
                request.adminEmail(),
                request.adminDisplayName(),
                request.name(),
                subdomain,
                null,
                upperOrNull(request.countryCode())
        );
        UUID tenantId = provisioned.tenantId();

        jdbcTemplate.update(
                "UPDATE tenants SET name = ?, legal_name = ?, status = ?, billing_email = ?, country_code = ?, "
                        + "locale = ?, timezone = ?, currency_code = ?, trial_ends_at = ?, "
                        + "suspension_reason = NULL, updated_at = ? WHERE id = ?",
                request.name().trim(),
                blankToNull(request.legalName()),
                status,
                lowerOrNull(request.billingEmail()),
                upperOrNull(request.countryCode()),
                defaultValue(request.locale(), "ar-SA"),
                defaultValue(request.timezone(), "Asia/Riyadh"),
                defaultValue(upperOrNull(request.currencyCode()), "SAR"),
                trialEndsAt,
                now,
                tenantId
        );

        TenantResponse created = getTenant(tenantId);
        auditService.success(
                authentication, tenantId, "TENANT.PROVISION", "TENANT", tenantId.toString(),
                "Control-plane tenant provisioning with administrator and default organization",
                null,
                Map.of(
                        "tenant", created,
                        "administratorUserId", provisioned.userId()
                ));
        return created;
    }

    @Transactional
    public TenantResponse changeTenantStatus(
            UUID tenantId,
            ChangeTenantStatusRequest request,
            Authentication authentication
    ) {
        TenantResponse before = getTenant(tenantId);
        String requestedStatus = normalizedTenantStatus(request.status());
        if (before.status().equals(requestedStatus)) {
            return before;
        }
        Set<String> allowed = TENANT_TRANSITIONS.getOrDefault(before.status(), Set.of());
        if (!allowed.contains(requestedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid tenant transition from " + before.status() + " to " + requestedStatus);
        }

        String operationalReason = Set.of("PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED")
                .contains(requestedStatus) ? request.reason().trim() : null;
        jdbcTemplate.update(
                "UPDATE tenants SET status = ?, suspension_reason = ?, updated_at = ? WHERE id = ?",
                requestedStatus, operationalReason, Instant.now(), tenantId);
        TenantResponse after = getTenant(tenantId);
        auditService.success(
                authentication, tenantId, "TENANT.STATUS.CHANGE", "TENANT", tenantId.toString(),
                request.reason(), before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<SystemServiceResponse> listSystemServices() {
        return jdbcTemplate.query(
                "SELECT id, code, name, description, version, environment, status, health_url, owner_name, "
                        + "criticality, dependencies, last_checked_at, last_latency_ms, last_message, updated_at "
                        + "FROM system_services ORDER BY criticality DESC, code",
                this::mapSystemService
        );
    }

    @Transactional
    public SystemServiceResponse updateSystemStatus(
            UUID serviceId,
            UpdateSystemStatusRequest request,
            Authentication authentication
    ) {
        SystemServiceResponse before = getSystemService(serviceId);
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!SYSTEM_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported system status");
        }
        jdbcTemplate.update(
                "UPDATE system_services SET status = ?, last_checked_at = ?, last_latency_ms = ?, "
                        + "last_message = ?, updated_at = ? WHERE id = ?",
                status,
                Instant.now(),
                request.latencyMs(),
                blankToNull(request.message()),
                Instant.now(),
                serviceId
        );
        SystemServiceResponse after = getSystemService(serviceId);
        auditService.success(
                authentication, null, "SYSTEM.STATUS.CHANGE", "SYSTEM_SERVICE", serviceId.toString(),
                request.reason(), before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public SystemServiceResponse getSystemService(UUID serviceId) {
        List<SystemServiceResponse> matches = jdbcTemplate.query(
                "SELECT id, code, name, description, version, environment, status, health_url, owner_name, "
                        + "criticality, dependencies, last_checked_at, last_latency_ms, last_message, updated_at "
                        + "FROM system_services WHERE id = ?",
                this::mapSystemService,
                serviceId
        );
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System service not found");
        }
        return matches.get(0);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private TenantResponse mapTenant(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TenantResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("legal_name"),
                resultSet.getString("subdomain"),
                resultSet.getString("status"),
                resultSet.getString("billing_email"),
                resultSet.getString("country_code"),
                resultSet.getString("locale"),
                resultSet.getString("timezone"),
                resultSet.getString("currency_code"),
                instant(resultSet, "trial_ends_at"),
                resultSet.getString("suspension_reason"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at")
        );
    }

    private SystemServiceResponse mapSystemService(ResultSet resultSet, int rowNumber) throws SQLException {
        Object latencyValue = resultSet.getObject("last_latency_ms");
        Long latency = latencyValue == null ? null : ((Number) latencyValue).longValue();
        return new SystemServiceResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("version"),
                resultSet.getString("environment"),
                resultSet.getString("status"),
                resultSet.getString("health_url"),
                resultSet.getString("owner_name"),
                resultSet.getString("criticality"),
                resultSet.getString("dependencies"),
                instant(resultSet, "last_checked_at"),
                latency,
                resultSet.getString("last_message"),
                instant(resultSet, "updated_at")
        );
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new SQLException("Unsupported timestamp value for " + column + ": " + value.getClass());
    }

    private static String normalizedTenantStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!TENANT_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tenant status");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String lowerOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String defaultValue(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }
}
