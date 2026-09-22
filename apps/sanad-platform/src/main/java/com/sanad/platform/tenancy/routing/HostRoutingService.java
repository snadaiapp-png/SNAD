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
        return value;
    }
}
