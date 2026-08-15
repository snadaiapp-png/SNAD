package com.sanad.platform.website.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.website.api.WebsiteDtos.*;
import com.sanad.platform.website.domain.WebsiteDomain;
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
import java.util.Locale;
import java.util.UUID;

/**
 * Website Domain application service (v20260816.3).
 * Handles default domain generation, custom domain registration, verification, activation.
 */
@Service
public class WebsiteDomainService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public WebsiteDomainService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    /**
     * Generate a default domain for a website using the platform base domain.
     * Format: <website-slug>.<platform-base-domain>
     * If no platform base domain is configured, returns null.
     */
    public String generateDefaultDomain(String websiteSlug) {
        String baseDomain = resolvePlatformBaseDomain();
        if (baseDomain == null || baseDomain.isBlank()) return null;
        return (websiteSlug + "." + baseDomain).toLowerCase(Locale.ROOT);
    }

    /**
     * Register a custom domain for a website.
     * The domain starts as PENDING + INACTIVE and must be verified before activation.
     */
    @Transactional
    public DomainResponse registerCustomDomain(UUID tenantId, UUID websiteId, CreateDomainRequest request, Authentication auth) {
        ensureWebsite(tenantId, websiteId);
        if (request == null || request.hostname() == null || request.hostname().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hostname is required");
        String hostname = request.hostname().trim().toLowerCase(Locale.ROOT);
        if (!isValidHostname(hostname))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid hostname format");
        if (WebsiteDomain.isReservedHostname(hostname))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hostname is reserved or protected");

        UUID id = UUID.randomUUID();
        String token = "snad-site-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Instant now = Instant.now();
        var method = request.verificationMethod() != null ? request.verificationMethod() : WebsiteDomain.VerificationMethod.DNS_TXT;
        try {
            jdbc.update("INSERT INTO website_domains (id, tenant_id, website_id, hostname, domain_type, "
                            + "verification_status, activation_status, is_primary, verification_token, verification_method, "
                            + "version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'CUSTOM', 'PENDING', 'INACTIVE', FALSE, ?, ?, 0, ?, ?)",
                    id, tenantId, websiteId, hostname, token, method.name(),
                    Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "hostname already claimed: " + hostname);
        }
        audit(tenantId, auth, "DOMAIN.REGISTERED", id, "hostname=" + hostname);
        return getOrThrow(tenantId, websiteId, id);
    }

    /**
     * Generate default domain and register it automatically.
     */
    @Transactional
    public DomainResponse generateAndRegisterDefaultDomain(UUID tenantId, UUID websiteId, String websiteSlug, Authentication auth) {
        ensureWebsite(tenantId, websiteId);
        String hostname = generateDefaultDomain(websiteSlug);
        if (hostname == null) return null; // no base domain configured

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO website_domains (id, tenant_id, website_id, hostname, domain_type, "
                            + "verification_status, activation_status, is_primary, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'DEFAULT_GENERATED', 'VERIFIED', 'ACTIVE', TRUE, 0, ?, ?)",
                    id, tenantId, websiteId, hostname, Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            // Already exists — return existing
        }
        return getOrThrow(tenantId, websiteId, id);
    }

    /** Provide DNS/HTTP verification instructions for a custom domain. */
    @Transactional(readOnly = true)
    public DomainVerificationInstructions getVerificationInstructions(UUID tenantId, UUID websiteId, UUID domainId) {
        DomainResponse domain = getOrThrow(tenantId, websiteId, domainId);
        if (domain.verificationToken() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no verification token for this domain");
        return switch (domain.verificationMethod() != null ? domain.verificationMethod() : WebsiteDomain.VerificationMethod.DNS_TXT) {
            case DNS_TXT -> new DomainVerificationInstructions(
                    domain.hostname(), "DNS_TXT",
                    "_snad-verify." + domain.hostname(), domain.verificationToken(),
                    null, null, null);
            case DNS_CNAME -> new DomainVerificationInstructions(
                    domain.hostname(), "DNS_CNAME",
                    null, null,
                    domain.hostname() + " → snad-verify.vercel-dns.com", null, null);
            case HTTP -> new DomainVerificationInstructions(
                    domain.hostname(), "HTTP",
                    null, null,
                    null, "/.well-known/snad-verify.txt", domain.verificationToken());
        };
    }

    /** Verify domain ownership using the provided token. */
    @Transactional
    public DomainResponse verifyDomain(UUID tenantId, UUID websiteId, UUID domainId, VerifyDomainRequest request, Authentication auth) {
        DomainResponse existing = getOrThrow(tenantId, websiteId, domainId);
        if (request == null || request.verificationToken() == null || !request.verificationToken().equals(existing.verificationToken()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "verification token mismatch");
        Instant now = Instant.now();
        UUID actor = actorUserId(auth);
        jdbc.update("UPDATE website_domains SET verification_status = 'VERIFIED', verified_at = ?, verified_by = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), actor, Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "DOMAIN.VERIFIED", domainId, "hostname=" + existing.hostname());
        return getOrThrow(tenantId, websiteId, domainId);
    }

    /** Activate a verified domain. */
    @Transactional
    public DomainResponse activate(UUID tenantId, UUID websiteId, UUID domainId, Authentication auth) {
        DomainResponse existing = getOrThrow(tenantId, websiteId, domainId);
        if (existing.verificationStatus() != WebsiteDomain.VerificationStatus.VERIFIED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "domain must be VERIFIED before activation (current=" + existing.verificationStatus() + ")");
        Instant now = Instant.now();
        jdbc.update("UPDATE website_domains SET activation_status = 'ACTIVE', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "DOMAIN.ACTIVATED", domainId, "hostname=" + existing.hostname());
        return getOrThrow(tenantId, websiteId, domainId);
    }

    @Transactional
    public DomainResponse disable(UUID tenantId, UUID websiteId, UUID domainId, Authentication auth) {
        DomainResponse existing = getOrThrow(tenantId, websiteId, domainId);
        Instant now = Instant.now();
        jdbc.update("UPDATE website_domains SET activation_status = 'DISABLED', is_primary = FALSE, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "DOMAIN.DISABLED", domainId, "hostname=" + existing.hostname());
        return getOrThrow(tenantId, websiteId, domainId);
    }

    @Transactional
    public DomainResponse setPrimary(UUID tenantId, UUID websiteId, UUID domainId, Authentication auth) {
        DomainResponse existing = getOrThrow(tenantId, websiteId, domainId);
        if (existing.activationStatus() != WebsiteDomain.ActivationStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "domain must be ACTIVE before setting primary");
        Instant now = Instant.now();
        jdbc.update("UPDATE website_domains SET is_primary = FALSE, updated_at = ? WHERE tenant_id = ? AND website_id = ?",
                Timestamp.from(now), tenantId, websiteId);
        jdbc.update("UPDATE website_domains SET is_primary = TRUE, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "DOMAIN.SET_PRIMARY", domainId, "hostname=" + existing.hostname());
        return getOrThrow(tenantId, websiteId, domainId);
    }

    @Transactional(readOnly = true)
    public java.util.List<DomainResponse> list(UUID tenantId, UUID websiteId) {
        ensureWebsite(tenantId, websiteId);
        return jdbc.query("SELECT * FROM website_domains WHERE tenant_id = ? AND website_id = ? ORDER BY is_primary DESC, created_at",
                this::mapRow, tenantId, websiteId);
    }

    // ===== Public resolution helpers =====

    /** Find an active website domain by hostname (used by public resolver). */
    @Transactional(readOnly = true)
    public DomainResponse findByHostname(String hostname) {
        if (hostname == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM website_domains WHERE hostname = ? AND activation_status = 'ACTIVE'",
                    this::mapRow, hostname.trim().toLowerCase(Locale.ROOT));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    // ===== Helpers =====
    private String resolvePlatformBaseDomain() {
        // Check system property first, then env var
        String base = System.getProperty("sanad.tenancy.domains.base-domain");
        if (base == null || base.isBlank()) {
            base = System.getenv("SANAD_BASE_DOMAIN");
        }
        if (base == null || base.isBlank()) {
            base = System.getenv("PLATFORM_BASE_DOMAIN");
        }
        return (base != null && !base.isBlank()) ? base.trim().toLowerCase(Locale.ROOT) : null;
    }

    private void ensureWebsite(UUID tenantId, UUID websiteId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, websiteId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "website not found");
    }

    private DomainResponse getOrThrow(UUID tenantId, UUID websiteId, UUID domainId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM website_domains WHERE tenant_id = ? AND website_id = ? AND id = ?",
                    this::mapRow, tenantId, websiteId, domainId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "domain not found: " + domainId);
        }
    }

    private boolean isValidHostname(String h) {
        if (h == null || h.isEmpty() || h.length() > 253) return false;
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '.')) return false;
        }
        return !h.startsWith("-") && !h.startsWith(".") && !h.endsWith("-") && !h.endsWith(".");
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "WEBSITE_DOMAIN", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private DomainResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DomainResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("website_id", UUID.class), rs.getString("hostname"),
                WebsiteDomain.DomainType.valueOf(rs.getString("domain_type")),
                WebsiteDomain.VerificationStatus.valueOf(rs.getString("verification_status")),
                WebsiteDomain.ActivationStatus.valueOf(rs.getString("activation_status")),
                rs.getBoolean("is_primary"),
                rs.getString("verification_token"),
                rs.getString("verification_method") == null ? null : WebsiteDomain.VerificationMethod.valueOf(rs.getString("verification_method")),
                rs.getObject("verified_at", Timestamp.class) == null ? null : rs.getObject("verified_at", Timestamp.class).toInstant(),
                rs.getString("failure_reason"),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
