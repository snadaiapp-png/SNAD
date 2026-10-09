package com.sanad.platform.user.access;

import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.security.scope.AccessScopeType;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserApplicationAccessProjectionService {

    private static final String SUPPORTED_CONTRACT_VERSION = "1";
    private static final Pattern NAMESPACE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private final ApplicationIamRegistryRepository registry;
    private final UserApplicationAccessReadRepository accessRead;
    private final UserRepository users;

    public UserApplicationAccessProjectionService(
            ApplicationIamRegistryRepository registry,
            UserApplicationAccessReadRepository accessRead,
            UserRepository users) {
        this.registry = registry;
        this.accessRead = accessRead;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ApplicationAccessProjection> project(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null
                || users.findByTenantIdAndId(tenantId, userId).isEmpty()) {
            throw new IllegalArgumentException("User not found in tenant");
        }

        Set<String> knownCapabilities = normalize(accessRead.knownCapabilities());
        Set<String> effectiveCapabilities = normalize(accessRead.effectiveCapabilityCodes(tenantId, userId));
        List<UserAccessResponse> grants = accessRead.activeRoleGrants(tenantId, userId);
        Set<UUID> roleIds = new LinkedHashSet<>();
        for (UserAccessResponse grant : grants) {
            roleIds.add(grant.roleId());
        }
        Map<UUID, Set<String>> roleCapabilities = accessRead.capabilityCodesByRoleIds(tenantId, roleIds);

        List<ApplicationAccessProjection> result = new ArrayList<>();
        for (ApplicationIamRegistration registration : registry.findDiscoverable()) {
            result.add(projectOne(
                    registration,
                    knownCapabilities,
                    effectiveCapabilities,
                    grants,
                    roleCapabilities));
        }
        result.sort(Comparator.comparing(ApplicationAccessProjection::applicationCode));
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<ModuleUserAccessProjection> projectModuleUsers(UUID tenantId, String routeRoot) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        ApplicationIamRegistration registration = registry.findDiscoverableByRouteRoot(routeRoot)
                .orElseThrow(() -> new IllegalArgumentException("No governed IAM application matches route"));

        Set<String> knownCapabilities = normalize(accessRead.knownCapabilities());
        Set<String> namespaces = normalize(registration.capabilityNamespaces());
        Set<String> declared = normalize(registration.declaredCapabilities());
        Set<String> supportedScopes = normalize(registration.supportedScopes());
        String validationReason = validate(
                registration,
                namespaces,
                declared,
                supportedScopes,
                knownCapabilities);
        if (validationReason != null) {
            throw new IllegalStateException("Application IAM contract is not projectable: " + validationReason);
        }

        List<User> tenantUsers = users.findByTenantId(tenantId).stream()
                .sorted(Comparator.comparing(User::getEmail))
                .toList();

        List<UserAccessResponse> grants = accessRead.activeRoleGrants(tenantId);
        Set<UUID> roleIds = new LinkedHashSet<>();
        Map<UUID, List<UserAccessResponse>> grantsByUser = new java.util.LinkedHashMap<>();
        for (UserAccessResponse grant : grants) {
            roleIds.add(grant.roleId());
            grantsByUser.computeIfAbsent(grant.userId(), ignored -> new ArrayList<>()).add(grant);
        }
        Map<UUID, Set<String>> roleCapabilities = accessRead.capabilityCodesByRoleIds(tenantId, roleIds);

        Map<UUID, Set<String>> effectiveByUser = new java.util.LinkedHashMap<>();
        for (UserAccessResponse grant : grants) {
            effectiveByUser
                    .computeIfAbsent(grant.userId(), ignored -> new LinkedHashSet<>())
                    .addAll(normalize(roleCapabilities.getOrDefault(grant.roleId(), Set.of())));
        }

        Map<UUID, Set<String>> deniedByUser = new java.util.LinkedHashMap<>();
        for (UserApplicationAccessReadRepository.UserCapabilityOverride override
                : accessRead.activePermissionOverrides(tenantId)) {
            String capability = normalizeCode(override.capabilityCode());
            if ("DENY".equals(normalizeCode(override.effect()))) {
                deniedByUser.computeIfAbsent(override.userId(), ignored -> new LinkedHashSet<>())
                        .add(capability);
            } else if ("ALLOW".equals(normalizeCode(override.effect()))) {
                effectiveByUser.computeIfAbsent(override.userId(), ignored -> new LinkedHashSet<>())
                        .add(capability);
            }
        }
        deniedByUser.forEach((userId, denied) ->
                effectiveByUser.computeIfAbsent(userId, ignored -> new LinkedHashSet<>()).removeAll(denied));

        List<ModuleUserAccessProjection> result = new ArrayList<>();
        for (User user : tenantUsers) {
            List<UserAccessResponse> userGrants = grantsByUser.getOrDefault(user.getId(), List.of());
            List<String> assignedRoles = userGrants.stream()
                    .filter(grant -> roleOwnsApplicationCapability(
                            roleCapabilities.getOrDefault(grant.roleId(), Set.of()), namespaces))
                    .map(UserAccessResponse::roleCode)
                    .distinct()
                    .sorted()
                    .toList();

            Set<String> matchingEffective = new LinkedHashSet<>();
            for (String capability : effectiveByUser.getOrDefault(user.getId(), Set.of())) {
                if (ownedByAnyNamespace(capability, namespaces)) {
                    matchingEffective.add(normalizeCode(capability));
                }
            }

            result.add(new ModuleUserAccessProjection(
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getStatus().name(),
                    !matchingEffective.isEmpty(),
                    assignedRoles,
                    Set.copyOf(matchingEffective)));
        }
        return List.copyOf(result);
    }

    private ApplicationAccessProjection projectOne(
            ApplicationIamRegistration registration,
            Set<String> knownCapabilities,
            Set<String> effectiveCapabilities,
            List<UserAccessResponse> grants,
            Map<UUID, Set<String>> roleCapabilities) {

        Set<String> namespaces = normalize(registration.capabilityNamespaces());
        Set<String> declared = normalize(registration.declaredCapabilities());
        Set<String> supportedScopes = normalize(registration.supportedScopes());

        String validationReason = validate(registration, namespaces, declared, supportedScopes, knownCapabilities);
        if (validationReason != null) {
            return new ApplicationAccessProjection(
                    normalizeCode(registration.applicationCode()),
                    registration.name(),
                    registration.localizedName(),
                    normalizeCode(registration.status()),
                    registration.contractVersion(),
                    false,
                    false,
                    List.of(),
                    supportedScopes,
                    Set.of(),
                    validationReason);
        }

        Set<String> matchingEffective = new LinkedHashSet<>();
        for (String capability : effectiveCapabilities) {
            if (ownedByAnyNamespace(capability, namespaces)) {
                matchingEffective.add(capability);
            }
        }

        List<String> assignedRoles = grants.stream()
                .filter(grant -> roleOwnsApplicationCapability(
                        roleCapabilities.getOrDefault(grant.roleId(), Set.of()), namespaces))
                .map(UserAccessResponse::roleCode)
                .distinct()
                .sorted()
                .toList();

        boolean allowed = !matchingEffective.isEmpty();
        return new ApplicationAccessProjection(
                normalizeCode(registration.applicationCode()),
                registration.name(),
                registration.localizedName(),
                normalizeCode(registration.status()),
                registration.contractVersion(),
                true,
                allowed,
                assignedRoles,
                supportedScopes,
                Set.copyOf(matchingEffective),
                allowed ? "ALLOWED" : "NO_EFFECTIVE_CAPABILITY");
    }

    private static String validate(
            ApplicationIamRegistration registration,
            Set<String> namespaces,
            Set<String> declared,
            Set<String> supportedScopes,
            Set<String> knownCapabilities) {
        if (!"ACTIVE".equals(normalizeCode(registration.status()))) {
            return "REGISTRATION_NOT_ACTIVE";
        }
        if (!SUPPORTED_CONTRACT_VERSION.equals(registration.contractVersion())) {
            return "UNSUPPORTED_CONTRACT_VERSION";
        }
        if (namespaces.isEmpty() || namespaces.stream().anyMatch(ns -> !NAMESPACE.matcher(ns).matches())) {
            return "INVALID_CAPABILITY_NAMESPACE";
        }
        if (declared.stream().anyMatch(capability ->
                !knownCapabilities.contains(capability) || !ownedByAnyNamespace(capability, namespaces))) {
            return "UNKNOWN_DECLARED_CAPABILITY";
        }
        if (supportedScopes.isEmpty() || supportedScopes.stream().anyMatch(scope -> !isCanonicalScope(scope))) {
            return "UNKNOWN_OR_UNSUPPORTED_SCOPE";
        }
        return null;
    }

    private static boolean isCanonicalScope(String scope) {
        try {
            AccessScopeType.valueOf(scope);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean roleOwnsApplicationCapability(Set<String> capabilities, Set<String> namespaces) {
        for (String capability : normalize(capabilities)) {
            if (ownedByAnyNamespace(capability, namespaces)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownedByAnyNamespace(String capability, Set<String> namespaces) {
        String normalized = normalizeCode(capability);
        for (String namespace : namespaces) {
            if (normalized.equals(namespace) || normalized.startsWith(namespace + ".")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(normalizeCode(value));
            }
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
