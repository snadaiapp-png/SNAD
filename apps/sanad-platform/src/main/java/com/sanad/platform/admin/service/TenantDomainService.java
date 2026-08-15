package com.sanad.platform.admin.service;

import com.sanad.platform.admin.api.TenantDomainDtos.CreateDomainRequest;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainResponse;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainSummary;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainType;
import com.sanad.platform.admin.api.TenantDomainDtos.Origin;
import com.sanad.platform.admin.api.TenantDomainDtos.Status;
import com.sanad.platform.admin.api.TenantDomainDtos.UpdateDomainRequest;
import com.sanad.platform.admin.api.TenantDomainDtos.VerificationMethod;
import com.sanad.platform.admin.api.TenantDomainDtos.VerifyDomainRequest;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tenant Domain Management — tenant-scoped routing hostnames for the
 * application/store/website surfaces.
 *
 * <p>This service backs the {@code /api/v1/executive/tenants/{tenantId}/domains}
 * API surface and the future {@code DomainRoutingFilter}. It does NOT
 * perform DNS resolution or SSL cert provisioning — those are external
 * concerns handled by the CDN/reverse-proxy layer (Vercel in production,
 * localhost otherwise). This service is responsible for:
 * <ul>
 *   <li>Persisting the tenant's claim on a hostname.</li>
 *   <li>Issuing a verification challenge token.</li>
 *   <li>Recording the verified/active/inactive lifecycle transitions.</li>
 *   <li>Maintaining the single "primary" domain invariant per (tenant, type).</li>
 *   <li>Auditing every mutation via {@link PlatformAuditService}.</li>
 * </ul>
 *
 * <p>No permanent public domain value is hard-coded here. The
 * {@link #generateDefaultHostname} method derives the default hostname
 * from the configured {@code sanad.tenancy.domains.base-domain} property
 * (env var {@code SANAD_BASE_DOMAIN}); the absence of that property is
 * tolerated and results in a {@code null} derived hostname (caller decides
 * how to handle).
 */
@Service
public class TenantDomainService {

    private static final java.util.Set<String> ALLOWED_HOSTNAME_CHARS =
            java.util.Set.of("-", ".");

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public TenantDomainService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public DomainResponse createDomain(UUID tenantId, CreateDomainRequest request, Authentication auth) {
        ensureTenant(tenantId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        String hostname = normalizeHostname(request.hostname());
        if (hostname == null || hostname.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hostname is required");
        }
        DomainType type = request.domainType() == null ? DomainType.APPLICATION : request.domainType();
        Origin origin = request.origin() == null ? Origin.CUSTOM : request.origin();
        VerificationMethod method = request.verificationMethod() == null
                ? VerificationMethod.DNS_TXT : request.verificationMethod();

        UUID id = UUID.randomUUID();
        String token = generateToken();
        Instant now = Instant.now();

        try {
            jdbc.update(
                    "INSERT INTO tenant_domains "
                            + "(id, tenant_id, hostname, domain_type, origin, status, "
                            + " verification_token, verification_method, is_primary, "
                            + " version, created_at, updated_at, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, tenantId, hostname, type.name(), origin.name(),
                    Status.UNVERIFIED.name(),
                    token, method.name(), false,
                    0L, Timestamp.from(now), Timestamp.from(now), actorUserId(auth)
            );
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "hostname already claimed by this tenant: " + hostname);
        }

        audit(tenantId, auth, "DOMAIN.CREATED", id, "tenant_domains", hostname);
        return getDomainOrThrow(tenantId, id);
    }

    // ============================================================
    // READ
    // ============================================================

    @Transactional(readOnly = true)
    public List<DomainResponse> listDomains(UUID tenantId, DomainType filterType) {
        ensureTenant(tenantId);
        if (filterType == null) {
            return jdbc.query(
                    "SELECT * FROM tenant_domains WHERE tenant_id = ? ORDER BY is_primary DESC, created_at",
                    this::mapRow, tenantId);
        }
        return jdbc.query(
                "SELECT * FROM tenant_domains WHERE tenant_id = ? AND domain_type = ? "
                        + "ORDER BY is_primary DESC, created_at",
                this::mapRow, tenantId, filterType.name());
    }

    @Transactional(readOnly = true)
    public DomainResponse getDomain(UUID tenantId, UUID domainId) {
        ensureTenant(tenantId);
        return getDomainOrThrow(tenantId, domainId);
    }

    @Transactional(readOnly = true)
    public DomainSummary summarize(UUID tenantId) {
        ensureTenant(tenantId);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_domains WHERE tenant_id = ?",
                Integer.class, tenantId);
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_domains WHERE tenant_id = ? AND status = ?",
                Integer.class, tenantId, Status.ACTIVE.name());
        Integer verified = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_domains WHERE tenant_id = ? AND status IN (?, ?)",
                Integer.class, tenantId, Status.VERIFIED.name(), Status.ACTIVE.name());
        Integer unverified = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_domains WHERE tenant_id = ? AND status = ?",
                Integer.class, tenantId, Status.UNVERIFIED.name());
        Integer inactive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_domains WHERE tenant_id = ? AND status = ?",
                Integer.class, tenantId, Status.INACTIVE.name());
        boolean hasPrimary = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM tenant_domains WHERE tenant_id = ? AND is_primary = TRUE)",
                Boolean.class, tenantId));
        String primaryHostname = hasPrimary ? jdbc.queryForObject(
                "SELECT hostname FROM tenant_domains WHERE tenant_id = ? AND is_primary = TRUE LIMIT 1",
                String.class, tenantId) : null;

        int t = total == null ? 0 : total;
        return new DomainSummary(
                t,
                active == null ? 0 : active,
                verified == null ? 0 : verified,
                unverified == null ? 0 : unverified,
                inactive == null ? 0 : inactive,
                hasPrimary,
                primaryHostname
        );
    }

    // ============================================================
    // UPDATE / VERIFY / ACTIVATE
    // ============================================================

    @Transactional
    public DomainResponse updateDomain(UUID tenantId, UUID domainId, UpdateDomainRequest request, Authentication auth) {
        DomainResponse existing = getDomainOrThrow(tenantId, domainId);
        Instant now = Instant.now();

        if (request.verificationMethod() != null) {
            jdbc.update(
                    "UPDATE tenant_domains SET verification_method = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?",
                    request.verificationMethod().name(), Timestamp.from(now), tenantId, domainId);
        }
        if (Boolean.TRUE.equals(request.isPrimary())) {
            // Demote any other primary of the same domain_type first
            jdbc.update(
                    "UPDATE tenant_domains SET is_primary = FALSE, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND domain_type = ? AND id <> ?",
                    Timestamp.from(now), tenantId, existing.domainType().name(), domainId);
            jdbc.update(
                    "UPDATE tenant_domains SET is_primary = TRUE, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?",
                    Timestamp.from(now), tenantId, domainId);
        }
        audit(tenantId, auth, "DOMAIN.UPDATED", domainId, "tenant_domains", existing.hostname());
        return getDomainOrThrow(tenantId, domainId);
    }

    @Transactional
    public DomainResponse verifyDomain(UUID tenantId, UUID domainId, VerifyDomainRequest request, Authentication auth) {
        DomainResponse existing = getDomainOrThrow(tenantId, domainId);
        if (request == null || request.verificationToken() == null
                || request.verificationToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "verificationToken is required");
        }
        if (!request.verificationToken().equals(existing.verificationToken())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "verification token mismatch — DNS challenge not satisfied");
        }
        Instant now = Instant.now();
        // Verify transitions UNVERIFIED → VERIFIED. If ACTIVE/INACTIVE, verification is a no-op refresh.
        jdbc.update(
                "UPDATE tenant_domains "
                        + "SET status = ?, verified_at = ?, verified_by = ?, last_verified_at = ?, "
                        + " failure_reason = NULL, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                Status.VERIFIED.name(),
                Timestamp.from(now), actorUserId(auth), Timestamp.from(now),
                Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "DOMAIN.VERIFIED", domainId, "tenant_domains", existing.hostname());
        return getDomainOrThrow(tenantId, domainId);
    }

    @Transactional
    public DomainResponse activateDomain(UUID tenantId, UUID domainId, Authentication auth) {
        DomainResponse existing = getDomainOrThrow(tenantId, domainId);
        if (existing.status() != Status.VERIFIED && existing.status() != Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "domain must be VERIFIED before activation (current=" + existing.status() + ")");
        }
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE tenant_domains SET status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                Status.ACTIVE.name(), Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "DOMAIN.ACTIVATED", domainId, "tenant_domains", existing.hostname());
        return getDomainOrThrow(tenantId, domainId);
    }

    @Transactional
    public DomainResponse deactivateDomain(UUID tenantId, UUID domainId, String reason, Authentication auth) {
        DomainResponse existing = getDomainOrThrow(tenantId, domainId);
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE tenant_domains SET status = ?, is_primary = FALSE, failure_reason = ?, "
                        + " updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Status.INACTIVE.name(), reason, Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "DOMAIN.DEACTIVATED", domainId, "tenant_domains", existing.hostname());
        return getDomainOrThrow(tenantId, domainId);
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Transactional
    public void deleteDomain(UUID tenantId, UUID domainId, Authentication auth) {
        DomainResponse existing = getDomainOrThrow(tenantId, domainId);
        int rows = jdbc.update(
                "DELETE FROM tenant_domains WHERE tenant_id = ? AND id = ?",
                tenantId, domainId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "domain not found");
        }
        audit(tenantId, auth, "DOMAIN.DELETED", domainId, "tenant_domains", existing.hostname());
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** Derive the default-generated hostname for a tenant from the configured base domain. */
    public String generateDefaultHostname(String subdomain, DomainType type) {
        if (subdomain == null || subdomain.isBlank()) {
            return null;
        }
        String baseDomain = System.getenv("SANAD_BASE_DOMAIN");
        if (baseDomain == null || baseDomain.isBlank()) {
            baseDomain = System.getProperty("sanad.tenancy.domains.base-domain");
        }
        if (baseDomain == null || baseDomain.isBlank()) {
            return null; // caller decides how to handle (e.g. skip default-generation)
        }
        String prefix = switch (type) {
            case APPLICATION -> subdomain;
            case STORE -> "store." + subdomain;
            case WEBSITE -> "www." + subdomain;
        };
        return (prefix + "." + baseDomain).toLowerCase(Locale.ROOT);
    }

    private DomainResponse getDomainOrThrow(UUID tenantId, UUID domainId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM tenant_domains WHERE tenant_id = ? AND id = ?",
                    this::mapRow, tenantId, domainId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "domain not found: " + domainId);
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

    private String normalizeHostname(String raw) {
        if (raw == null) return null;
        String h = raw.trim().toLowerCase(Locale.ROOT);
        if (h.isEmpty()) return null;
        if (h.length() > 253) return null;
        // Validate against RFC 1035 label rules: alphanumeric + hyphen + dot.
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '.')) {
                return null;
            }
        }
        if (h.startsWith("-") || h.startsWith(".") || h.endsWith("-") || h.endsWith(".")) {
            return null;
        }
        return h;
    }

    private String generateToken() {
        return "snad-verify-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        String name = auth.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void audit(UUID tenantId, Authentication auth, String action,
                       UUID resourceId, String resourceType, String hostname) {
        try {
            auditService.success(auth, tenantId, action, resourceType,
                    resourceId == null ? null : resourceId.toString(),
                    "domain=" + hostname, null, null);
        } catch (Exception ignored) {
            // audit failure must not break the business operation
        }
    }

    private DomainResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DomainResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("hostname"),
                DomainType.valueOf(rs.getString("domain_type")),
                Origin.valueOf(rs.getString("origin")),
                Status.valueOf(rs.getString("status")),
                rs.getString("verification_token"),
                rs.getString("verification_method") == null
                        ? null : VerificationMethod.valueOf(rs.getString("verification_method")),
                rs.getObject("verified_at", java.sql.Timestamp.class) == null
                        ? null : rs.getObject("verified_at", java.sql.Timestamp.class).toInstant(),
                rs.getObject("verified_by", UUID.class),
                rs.getString("ssl_cert_arn"),
                rs.getBoolean("is_primary"),
                rs.getString("failure_reason"),
                rs.getObject("last_verified_at", java.sql.Timestamp.class) == null
                        ? null : rs.getObject("last_verified_at", java.sql.Timestamp.class).toInstant(),
                rs.getObject("created_at", java.sql.Timestamp.class).toInstant(),
                rs.getObject("updated_at", java.sql.Timestamp.class).toInstant()
        );
    }
}
