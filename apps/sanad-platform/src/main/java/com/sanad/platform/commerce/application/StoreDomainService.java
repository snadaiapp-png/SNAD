package com.sanad.platform.commerce.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.domain.CommerceDomain;
import com.sanad.platform.tenancy.routing.DomainOwnershipVerifier;
import com.sanad.platform.tenancy.routing.HostRoutingService;
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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Store Domain application service (v20260816.5).
 *
 * <p>Mirrors {@code WebsiteDomainService} but for the {@code commerce_store_domains}
 * table. Reuses the same platform base domain resolution chain
 * (system property {@code sanad.tenancy.domains.base-domain} → env var
 * {@code SANAD_BASE_DOMAIN} → env var {@code PLATFORM_BASE_DOMAIN}).
 *
 * <p>Includes {@link #findByHostname(String)} for public storefront resolution
 * — used by {@link com.sanad.platform.commerce.api.PublicStoreController}.
 */
@Service
public class StoreDomainService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final HostRoutingService hostRoutingService;
    private final DomainOwnershipVerifier ownershipVerifier;

    public StoreDomainService(
            JdbcTemplate jdbc,
            PlatformAuditService auditService,
            ObjectMapper objectMapper,
            HostRoutingService hostRoutingService,
            DomainOwnershipVerifier ownershipVerifier
    ) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.hostRoutingService = hostRoutingService;
        this.ownershipVerifier = ownershipVerifier;
    }

    /**
     * Generate a default storefront hostname using the platform base domain.
     * Format: {@code <store-slug>.<platform-base-domain>}.
     * Returns {@code null} if no platform base domain is configured.
     */
    public String generateDefaultDomain(String storeSlug) {
        String baseDomain = resolvePlatformBaseDomain();
        if (baseDomain == null || baseDomain.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SANAD_BASE_DOMAIN is required for generated storefront hostnames");
        }
        String hostname = hostRoutingService.normalizeHostname(storeSlug + "." + baseDomain);
        if (hostname == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "configured platform base domain cannot produce a valid storefront hostname");
        }
        return hostname;
    }

    /** Tenant-scoped generated storefront hostname. */
    public String generateDefaultDomain(UUID tenantId, String storeSlug) {
        String baseDomain = resolvePlatformBaseDomain();
        if (baseDomain == null || baseDomain.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SANAD_BASE_DOMAIN is required for generated storefront hostnames");
        }
        String tenantSubdomain = tenantSubdomain(tenantId);
        String hostname = hostRoutingService.normalizeHostname(
                storeSlug + "." + tenantSubdomain + "." + baseDomain);
        if (hostname == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "configured platform base domain cannot produce a valid tenant-scoped storefront hostname");
        }
        return hostname;
    }

    /**
     * Generate and register the platform-owned default storefront hostname.
     * The operation is idempotent and reconciles an existing generated row.
     */
    @Transactional
    public DomainResponse generateAndRegisterDefaultDomain(
            UUID tenantId,
            UUID storeId,
            String storeSlug,
            Authentication auth
    ) {
        ensureStore(tenantId, storeId);
        String hostname = generateDefaultDomain(tenantId, storeSlug);

        hostRoutingService.requireHostnameAvailable(
                hostname, HostRoutingService.Surface.STORE, tenantId, storeId, true);

        DomainResponse existing = findExactHostname(hostname);
        if (existing != null) {
            if (!tenantId.equals(existing.tenantId())
                    || !storeId.equals(existing.storeId())
                    || existing.domainType() != CommerceDomain.DomainType.DEFAULT_GENERATED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "hostname already claimed: " + hostname);
            }
            Instant now = Instant.now();
            jdbc.update("UPDATE commerce_store_domains SET is_primary = FALSE, updated_at = ? "
                            + "WHERE tenant_id = ? AND store_id = ? AND id <> ?",
                    Timestamp.from(now), tenantId, storeId, existing.id());
            jdbc.update("UPDATE commerce_store_domains SET verification_status = 'VERIFIED', "
                            + "activation_status = 'ACTIVE', is_primary = TRUE, failure_reason = NULL, "
                            + "updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND store_id = ? AND id = ?",
                    Timestamp.from(now), tenantId, storeId, existing.id());
            audit(tenantId, auth, "STORE_DOMAIN.DEFAULT_RECONCILED", existing.id(), "hostname=" + hostname);
            return getOrThrow(tenantId, storeId, existing.id());
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("UPDATE commerce_store_domains SET is_primary = FALSE, updated_at = ? "
                        + "WHERE tenant_id = ? AND store_id = ?",
                Timestamp.from(now), tenantId, storeId);
        try {
            jdbc.update("INSERT INTO commerce_store_domains "
                            + "(id, tenant_id, store_id, hostname, domain_type, verification_status, "
                            + " activation_status, is_primary, verification_token, verification_method, "
                            + " version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'DEFAULT_GENERATED', 'VERIFIED', 'ACTIVE', TRUE, "
                            + " NULL, NULL, 0, ?, ?)",
                    id, tenantId, storeId, hostname, Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "hostname already claimed: " + hostname, e);
        }
        audit(tenantId, auth, "STORE_DOMAIN.DEFAULT_CREATED", id, "hostname=" + hostname);
        return getOrThrow(tenantId, storeId, id);
    }

    @Transactional
    public DomainResponse registerCustomDomain(UUID tenantId, UUID storeId,
                                                  CreateDomainRequest request, Authentication auth) {
        ensureStore(tenantId, storeId);
        if (request == null || request.hostname() == null || request.hostname().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hostname is required");
        String hostname = hostRoutingService.normalizeHostname(request.hostname());
        if (hostname == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid hostname format");
        if (CommerceDomain.isReservedHostname(hostname))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hostname is reserved or protected");

        hostRoutingService.requireHostnameAvailable(
                hostname, HostRoutingService.Surface.STORE, tenantId, storeId, false);

        UUID id = UUID.randomUUID();
        String token = "snad-store-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO commerce_store_domains (id, tenant_id, store_id, hostname, domain_type, "
                            + "verification_status, activation_status, is_primary, verification_token, "
                            + "verification_method, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'CUSTOM', 'PENDING', 'INACTIVE', FALSE, ?, 'DNS_TXT', 0, ?, ?)",
                    id, tenantId, storeId, hostname, token, Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "hostname already claimed: " + hostname);
        }
        audit(tenantId, auth, "STORE_DOMAIN.REGISTERED", id, "hostname=" + hostname);
        return getOrThrow(tenantId, storeId, id);
    }

    /**
     * Verify domain ownership using the provided token.
     * For the default adapter, the token returned at registration time must match.
     */
    @Transactional
    public DomainResponse verify(UUID tenantId, UUID storeId, UUID domainId,
                                    String verificationToken, Authentication auth) {
        DomainResponse existing = getOrThrow(tenantId, storeId, domainId);
        if (verificationToken == null || !verificationToken.equals(existing.verificationToken()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "verification token mismatch");
        if (!ownershipVerifier.verify(
                existing.hostname(),
                DomainOwnershipVerifier.Method.DNS_TXT,
                existing.verificationToken())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "external domain ownership challenge is not satisfied");
        }
        Instant now = Instant.now();
        jdbc.update("UPDATE commerce_store_domains SET verification_status = 'VERIFIED', "
                        + "verified_at = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "STORE_DOMAIN.VERIFIED", domainId, "hostname=" + existing.hostname());
        return getOrThrow(tenantId, storeId, domainId);
    }

    /** Activate a verified domain. */
    @Transactional
    public DomainResponse activate(UUID tenantId, UUID storeId, UUID domainId, Authentication auth) {
        DomainResponse existing = getOrThrow(tenantId, storeId, domainId);
        if (existing.verificationStatus() != CommerceDomain.VerificationStatus.VERIFIED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "domain must be VERIFIED before activation (current=" + existing.verificationStatus() + ")");
        Instant now = Instant.now();
        jdbc.update("UPDATE commerce_store_domains SET activation_status = 'ACTIVE', "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), tenantId, domainId);
        audit(tenantId, auth, "STORE_DOMAIN.ACTIVATED", domainId, "hostname=" + existing.hostname());
        return getOrThrow(tenantId, storeId, domainId);
    }

    @Transactional
    public DomainResponse disable(UUID tenantId, UUID storeId, UUID domainId, Authentication auth) {
        DomainResponse existing = getOrThrow(tenantId, storeId, domainId);
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE commerce_store_domains "
                        + "SET activation_status = 'DISABLED', is_primary = FALSE, "
                        + "updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND store_id = ? AND id = ?",
                Timestamp.from(now), tenantId, storeId, domainId);
        audit(tenantId, auth, "STORE_DOMAIN.DISABLED", domainId, "hostname=" + existing.hostname());
        return getOrThrow(tenantId, storeId, domainId);
    }

    @Transactional
    public DomainResponse setPrimary(UUID tenantId, UUID storeId, UUID domainId, Authentication auth) {
        DomainResponse existing = getOrThrow(tenantId, storeId, domainId);
        if (existing.activationStatus() != CommerceDomain.ActivationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "domain must be ACTIVE before setting primary");
        }
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE commerce_store_domains SET is_primary = FALSE, updated_at = ? "
                        + "WHERE tenant_id = ? AND store_id = ? AND id <> ?",
                Timestamp.from(now), tenantId, storeId, domainId);
        jdbc.update(
                "UPDATE commerce_store_domains SET is_primary = TRUE, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND store_id = ? AND id = ?",
                Timestamp.from(now), tenantId, storeId, domainId);
        audit(tenantId, auth, "STORE_DOMAIN.SET_PRIMARY", domainId, "hostname=" + existing.hostname());
        return getOrThrow(tenantId, storeId, domainId);
    }

    @Transactional(readOnly = true)
    public List<DomainResponse> list(UUID tenantId, UUID storeId) {
        ensureStore(tenantId, storeId);
        return jdbc.query("SELECT * FROM commerce_store_domains WHERE tenant_id = ? AND store_id = ? "
                        + "ORDER BY is_primary DESC, created_at",
                this::mapRow, tenantId, storeId);
    }

    // ===== Public resolution helpers =====

    /** Find an active storefront domain by hostname (used by public resolver). */
    @Transactional(readOnly = true)
    public DomainResponse findByHostname(String hostname) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_store_domains WHERE hostname = ? AND activation_status = 'ACTIVE'",
                    this::mapRow, hostname.trim().toLowerCase(Locale.ROOT));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** Find a store by its slug (for default-domain resolution). */
    @Transactional(readOnly = true)
    public UUID findStoreIdBySlug(UUID tenantId, String slug) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM commerce_stores WHERE tenant_id = ? AND slug = ?",
                    UUID.class, tenantId, slug);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // ===== Helpers =====
    private DomainResponse findExactHostname(String hostname) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_store_domains WHERE lower(hostname) = lower(?)",
                    this::mapRow,
                    hostname
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private String resolvePlatformBaseDomain() {
        String base = System.getProperty("sanad.tenancy.domains.base-domain");
        if (base == null || base.isBlank()) base = System.getenv("SANAD_BASE_DOMAIN");
        if (base == null || base.isBlank()) base = System.getenv("PLATFORM_BASE_DOMAIN");
        return (base != null && !base.isBlank()) ? base.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String tenantSubdomain(UUID tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT subdomain FROM tenants WHERE id = ?",
                    String.class,
                    tenantId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found");
        }
    }

    private void ensureStore(UUID tenantId, UUID storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, storeId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "store not found");
    }

    private DomainResponse getOrThrow(UUID tenantId, UUID storeId, UUID domainId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_store_domains WHERE tenant_id = ? AND store_id = ? AND id = ?",
                    this::mapRow, tenantId, storeId, domainId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "store domain not found: " + domainId);
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
        auditService.success(auth, tenantId, action, "STORE_DOMAIN",
                resourceId == null ? null : resourceId.toString(), reason, null, null);
    }

    private DomainResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DomainResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("store_id", UUID.class), rs.getString("hostname"),
                CommerceDomain.DomainType.valueOf(rs.getString("domain_type")),
                CommerceDomain.VerificationStatus.valueOf(rs.getString("verification_status")),
                CommerceDomain.ActivationStatus.valueOf(rs.getString("activation_status")),
                rs.getBoolean("is_primary"),
                rs.getString("verification_token"),
                rs.getObject("verified_at", Timestamp.class) == null ? null
                        : rs.getObject("verified_at", Timestamp.class).toInstant(),
                rs.getString("failure_reason"),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
