package com.sanad.platform.user.access;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Repository
public class ApplicationIamRegistryRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<java.util.Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public ApplicationIamRegistryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ApplicationIamRegistration> findDiscoverable() {
        return jdbc.query("""
                SELECT a.code, a.name, a.localized_name, c.status, c.contract_version,
                       c.capability_namespaces::text, c.supported_scopes::text,
                       c.declared_capabilities::text, c.role_templates::text,
                       c.compatibility_metadata::text
                  FROM applications a
                  JOIN application_iam_contracts c ON c.application_code = a.code
                 WHERE a.status = 'ACTIVE'
                 ORDER BY a.display_order, a.code
                """, (rs, rowNum) -> new ApplicationIamRegistration(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("localized_name"),
                rs.getString("status"),
                rs.getString("contract_version"),
                parseSet(rs.getString("capability_namespaces")),
                parseSet(rs.getString("supported_scopes")),
                parseSet(rs.getString("declared_capabilities")),
                parseSet(rs.getString("role_templates")),
                parseMap(rs.getString("compatibility_metadata"))));
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationIamRegistration> findDiscoverableByRouteRoot(String routeRoot) {
        String normalized = normalizeRouteRoot(routeRoot);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return findDiscoverable().stream()
                .filter(candidate -> matchesRoute(candidate, normalized))
                .findFirst();
    }

    static boolean matchesRoute(ApplicationIamRegistration registration, String normalizedRoute) {
        Object routeRoots = registration.compatibilityMetadata() == null
                ? null
                : registration.compatibilityMetadata().get("routeRoots");
        if (routeRoots instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && normalizeRouteRoot(item.toString()).equals(normalizedRoute)) {
                    return true;
                }
            }
        }
        return normalizeRouteRoot(registration.applicationCode()).equals(normalizedRoute);
    }

    static String normalizeRouteRoot(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(0, slash) : normalized;
    }

    private static java.util.Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        try {
            return JSON.readValue(json, OBJECT_MAP);
        } catch (Exception ignored) {
            return java.util.Map.of();
        }
    }

    private static Set<String> parseSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            return new LinkedHashSet<>(JSON.readValue(json, STRING_LIST));
        } catch (Exception ignored) {
            return Set.of();
        }
    }
}
