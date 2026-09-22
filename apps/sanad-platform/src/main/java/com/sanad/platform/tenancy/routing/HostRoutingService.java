package com.sanad.platform.tenancy.routing;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical hostname routing authority for tenant application, website and
 * commerce-store surfaces.
 *
 * <p>Public callers never supply a tenant id. The tenant/resource identity is
 * derived only from an ACTIVE hostname claim. A hostname resolving to more
 * than one active surface fails closed instead of choosing an arbitrary row.
 */
@Service
public class HostRoutingService {

    public enum Surface {
        TENANT_APPLICATION,
        WEBSITE,
        STORE
    }

    public record RouteTarget(
            Surface surface,
            UUID tenantId,
            UUID resourceId,
            String hostname
    ) {}

    private final JdbcTemplate jdbc;

    public HostRoutingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serialize hostname claims across all three routing tables and reject any
     * cross-surface duplicate before it can become ambiguous at runtime.
     *
     * <p>The PostgreSQL transaction-scoped advisory lock closes the race that
     * three independent per-table UNIQUE constraints cannot close. A default
     * generated hostname may reconcile its own existing row when
     * {@code allowExistingOwner} is true; custom registrations must pass false.
     */
    @Transactional
    public void requireHostnameAvailable(
            String rawHostname,
            Surface ownerSurface,
            UUID tenantId,
            UUID resourceId,
            boolean allowExistingOwner
    ) {
        String hostname = normalizeHostname(rawHostname);
        if (hostname == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid hostname");
        }
        if (ownerSurface == null || tenantId == null || resourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hostname owner is required");
        }

        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> null,
                hostname
        );

        List<Map<String, Object>> claims = jdbc.queryForList("""
                SELECT 'TENANT_APPLICATION' AS surface,
                       d.tenant_id,
                       d.tenant_id AS resource_id
                  FROM tenant_domains d
                 WHERE lower(d.hostname) = ?
                UNION ALL
                SELECT 'WEBSITE' AS surface,
                       d.tenant_id,
                       d.website_id AS resource_id
                  FROM website_domains d
                 WHERE lower(d.hostname) = ?
                UNION ALL
                SELECT 'STORE' AS surface,
                       d.tenant_id,
                       d.store_id AS resource_id
                  FROM commerce_store_domains d
                 WHERE lower(d.hostname) = ?
                """, hostname, hostname, hostname);

        if (claims.isEmpty()) {
            return;
        }
        if (allowExistingOwner && claims.size() == 1) {
            Map<String, Object> claim = claims.get(0);
            if (ownerSurface.name().equals(claim.get("surface"))
                    && tenantId.equals(claim.get("tenant_id"))
                    && resourceId.equals(claim.get("resource_id"))) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "hostname already claimed: " + hostname);
    }

    @Transactional(readOnly = true)
    public Optional<RouteTarget> resolve(String rawHostname) {
        String hostname = normalizeHostname(rawHostname);
        if (hostname == null) {
            return Optional.empty();
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT 'TENANT_APPLICATION' AS surface,
                       d.tenant_id,
                       d.tenant_id AS resource_id,
                       d.hostname
                  FROM tenant_domains d
                 WHERE lower(d.hostname) = ?
                   AND d.domain_type = 'APPLICATION'
                   AND d.status = 'ACTIVE'
                UNION ALL
                SELECT 'WEBSITE' AS surface,
                       d.tenant_id,
                       d.website_id AS resource_id,
                       d.hostname
                  FROM website_domains d
                 WHERE lower(d.hostname) = ?
                   AND d.activation_status = 'ACTIVE'
                UNION ALL
                SELECT 'STORE' AS surface,
                       d.tenant_id,
                       d.store_id AS resource_id,
                       d.hostname
                  FROM commerce_store_domains d
                 WHERE lower(d.hostname) = ?
                   AND d.activation_status = 'ACTIVE'
                """, hostname, hostname, hostname);

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "hostname resolves to multiple active platform resources"
            );
        }

        Map<String, Object> row = rows.get(0);
        return Optional.of(new RouteTarget(
                Surface.valueOf((String) row.get("surface")),
                (UUID) row.get("tenant_id"),
                (UUID) row.get("resource_id"),
                ((String) row.get("hostname")).toLowerCase(Locale.ROOT)
        ));
    }

    @Transactional(readOnly = true)
    public Optional<RouteTarget> resolve(String rawHostname, Surface expectedSurface) {
        return resolve(rawHostname)
                .filter(target -> target.surface() == expectedSurface);
    }

    /**
     * Resolve the single platform base-domain configuration used by every
     * generated tenant/website/store hostname. Keeping this chain here avoids
     * three subtly different environment fallbacks that can make one surface
     * routable while another fails.
     */
    public String configuredBaseDomain() {
        String raw = System.getProperty("sanad.tenancy.domains.base-domain");
        if (raw == null || raw.isBlank()) raw = System.getenv("SANAD_BASE_DOMAIN");
        if (raw == null || raw.isBlank()) raw = System.getenv("PLATFORM_BASE_DOMAIN");
        if (raw == null || raw.isBlank()) return null;

        String normalized = normalizeHostname(raw);
        if (normalized == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "configured platform base domain is not a valid DNS hostname");
        }
        return normalized;
    }

    /**
     * Normalize an HTTP Host value to the persisted DNS hostname form.
     * Browser-visible ports are accepted for local/dev routing but never stored.
     */
    public String normalizeHostname(String rawHostname) {
        if (rawHostname == null) {
            return null;
        }
        String value = rawHostname.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.contains("/") || value.contains("\\")
                || value.contains("@") || value.contains(" ")) {
            return null;
        }

        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            String port = value.substring(firstColon + 1);
            if (!port.isEmpty() && port.chars().allMatch(Character::isDigit)) {
                value = value.substring(0, firstColon);
            }
        }

        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty() || value.length() > 253) {
            return null;
        }

        // Persist and resolve only canonical DNS hostnames. Reject empty labels,
        // overlong labels, underscores and leading/trailing hyphens instead of
        // allowing an invalid claim that can never resolve at the edge.
        String[] labels = value.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")) {
                return null;
            }
            for (int i = 0; i < label.length(); i++) {
                char ch = label.charAt(i);
                if (!((ch >= 'a' && ch <= 'z')
                        || (ch >= '0' && ch <= '9')
                        || ch == '-')) {
                    return null;
                }
            }
        }
        return value;
    }
}
