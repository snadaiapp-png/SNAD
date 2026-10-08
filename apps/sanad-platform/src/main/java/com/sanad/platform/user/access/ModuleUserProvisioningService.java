package com.sanad.platform.user.access;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ModuleUserProvisioningService {

    private final ApplicationIamRegistryRepository registry;
    private final JdbcTemplate jdbc;

    public ModuleUserProvisioningService(
            ApplicationIamRegistryRepository registry,
            JdbcTemplate jdbc) {
        this.registry = registry;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ModuleProvisioningContext resolve(UUID tenantId, String routeRoot) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        ApplicationIamRegistration registration = registry.findDiscoverableByRouteRoot(routeRoot)
                .orElseThrow(() -> new IllegalArgumentException("No governed IAM application matches route"));

        if (!"ACTIVE".equals(normalizeCode(registration.status()))
                || !"1".equals(registration.contractVersion())
                || registration.capabilityNamespaces() == null
                || registration.capabilityNamespaces().isEmpty()) {
            throw new IllegalStateException("Application IAM contract is not provisionable");
        }

        Set<String> namespaces = normalizeSet(registration.capabilityNamespaces());
        Map<UUID, RoleRow> roles = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, code, name
                  FROM roles
                 WHERE tenant_id = ?
                   AND status = 'ACTIVE'
                 ORDER BY code
                """,
                (RowCallbackHandler) rs -> roles.put(
                        rs.getObject("id", UUID.class),
                        new RoleRow(
                                rs.getObject("id", UUID.class),
                                rs.getString("code"),
                                rs.getString("name"))),
                tenantId);

        Map<UUID, Set<String>> capabilitiesByRole = new LinkedHashMap<>();
        jdbc.query("""
                SELECT rc.role_id, ac.code
                  FROM role_capabilities rc
                  JOIN access_capabilities ac ON ac.id = rc.capability_id
                 WHERE rc.tenant_id = ?
                   AND ac.status = 'ACTIVE'
                 ORDER BY rc.role_id, ac.code
                """,
                (RowCallbackHandler) rs -> capabilitiesByRole
                        .computeIfAbsent(rs.getObject("role_id", UUID.class), ignored -> new LinkedHashSet<>())
                        .add(normalizeCode(rs.getString("code"))),
                tenantId);

        List<ModuleProvisioningRole> available = new ArrayList<>();
        for (RoleRow role : roles.values()) {
            Set<String> capabilities = capabilitiesByRole.getOrDefault(role.id(), Set.of());
            if (capabilities.isEmpty()) continue;
            if (isModuleOnlyRole(capabilities, namespaces)) {
                available.add(new ModuleProvisioningRole(
                        role.id(),
                        role.code(),
                        role.name(),
                        Set.copyOf(capabilities)));
            }
        }

        return new ModuleProvisioningContext(
                normalizeCode(registration.applicationCode()),
                registration.name(),
                registration.localizedName(),
                namespaces,
                normalizeSet(registration.declaredCapabilities()),
                normalizeSet(registration.supportedScopes()),
                List.copyOf(available));
    }

    static boolean isModuleOnlyRole(Set<String> capabilities, Set<String> namespaces) {
        if (capabilities == null || capabilities.isEmpty() || namespaces == null || namespaces.isEmpty()) {
            return false;
        }
        return capabilities.stream().allMatch(capability -> ownedByAny(capability, namespaces));
    }

    private static boolean ownedByAny(String capability, Set<String> namespaces) {
        String normalized = normalizeCode(capability);
        for (String namespace : namespaces) {
            if (normalized.equals(namespace) || normalized.startsWith(namespace + ".")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) normalized.add(normalizeCode(value));
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record RoleRow(UUID id, String code, String name) {}
}
