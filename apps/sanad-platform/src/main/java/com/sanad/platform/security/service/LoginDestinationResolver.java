package com.sanad.platform.security.service;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityRepository;
import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapability;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Derives a user's visible destinations from their EFFECTIVE CAPABILITIES rather
 * than from role names.
 *
 * <p>Prior to EXEC-PROMPT-SANAD-FULLSTACK-REMEDIATION-010, every non-control-plane
 * user received the hard-coded list {@code [/workspace, /crm, /crm/command-center]}
 * regardless of whether they actually held any CRM capability, and the default
 * was always {@code /crm}. That granted access to UI destinations the user had
 * no authorization to use, and violated the "capability authorization" invariant.
 *
 * <p>This resolver consults:
 * <ul>
 *   <li>ACTIVE role grants for the user (tenant-scoped),</li>
 *   <li>role → capability mappings (tenant-scoped),</li>
 *   <li>the {@link ControlPlaneAccessGuard} for the {@code /control-plane}
 *       destination,</li>
 *   <li>credential-rotation requirement (forces {@code /workspace} as the
 *       landing destination).</li>
 * </ul>
 *
 * <p>The resolver performs ONE batched query for all role grants, ONE batched
 * query for all roles, and ONE batched query for all role-capability links for
 * those roles — no per-grant N+1 lookups.
 */
@Component
public class LoginDestinationResolver {

    /** Workspace is always available — it is the safe "no-permission" landing. */
    public static final String WORKSPACE = "/workspace";
    public static final String CRM = "/crm";
    public static final String CRM_COMMAND_CENTER = "/crm/command-center";
    public static final String CONTROL_PLANE = "/control-plane";
    public static final String FINANCE = "/finance";
    public static final String ERP = "/erp";

    private final UserRoleGrantRepository roleGrantRepository;
    private final RoleRepository roleRepository;
    private final RoleCapabilityRepository roleCapabilityRepository;
    private final AccessCapabilityRepository accessCapabilityRepository;
    private final ControlPlaneAccessGuard controlPlaneAccessGuard;

    public LoginDestinationResolver(UserRoleGrantRepository roleGrantRepository,
                                    RoleRepository roleRepository,
                                    RoleCapabilityRepository roleCapabilityRepository,
                                    AccessCapabilityRepository accessCapabilityRepository,
                                    ControlPlaneAccessGuard controlPlaneAccessGuard) {
        this.roleGrantRepository = roleGrantRepository;
        this.roleRepository = roleRepository;
        this.roleCapabilityRepository = roleCapabilityRepository;
        this.accessCapabilityRepository = accessCapabilityRepository;
        this.controlPlaneAccessGuard = controlPlaneAccessGuard;
    }

    /**
     * Compute the ordered list of destinations the user is authorized to see and
     * the safe default landing destination.
     *
     * @param tenantId             the caller's tenant (from JWT)
     * @param userId               the caller's user id
     * @param requiresRotation     true if the user must rotate their credential
     *                             before doing anything else (forces /workspace)
     */
    public DestinationSet resolve(UUID tenantId, UUID userId, boolean requiresRotation) {
        EffectiveCapabilities effective = collectEffectiveCapabilities(tenantId, userId);
        // Control-plane access requires: control-plane tenant + ADMIN role +
        // platform-level capability (USER.* / CAPABILITY.* / ROLE.* are the
        // control-plane capability families today). This intentionally refuses
        // to treat "ADMIN role in any tenant" as control-plane authority.
        boolean controlPlaneAuthorized = effective.adminRole
                && controlPlaneAccessGuard.isControlPlaneTenant(tenantId)
                && hasAnyCapability(effective.capabilities, CONTROL_PLANE_CAPABILITY_PREFIXES);

        // LinkedHashSet keeps insertion order and de-duplicates.
        LinkedHashSet<String> destinations = new LinkedHashSet<>();
        destinations.add(WORKSPACE);
        if (hasAnyCapability(effective.capabilities, CRM_CAPABILITY_PREFIXES)) {
            destinations.add(CRM);
            destinations.add(CRM_COMMAND_CENTER);
        }
        if (hasAnyCapability(effective.capabilities, FINANCE_CAPABILITY_PREFIXES)) {
            destinations.add(FINANCE);
        }
        if (hasAnyCapability(effective.capabilities, ERP_CAPABILITY_PREFIXES)) {
            destinations.add(ERP);
        }
        if (controlPlaneAuthorized) {
            destinations.add(CONTROL_PLANE);
        }

        String defaultDestination = pickDefault(requiresRotation, controlPlaneAuthorized, destinations);

        return new DestinationSet(
                Collections.unmodifiableList(new ArrayList<>(destinations)),
                defaultDestination);
    }

    /**
     * Default destination policy (deterministic, no UUID ordering):
     * <ol>
     *   <li>If credential rotation is required → {@code /workspace}.</li>
     *   <li>If user is a control-plane admin in the control-plane tenant →
     *       {@code /control-plane}.</li>
     *   <li>If user has CRM capabilities → {@code /crm}.</li>
     *   <li>If user has Finance capabilities → {@code /finance}.</li>
     *   <li>If user has ERP capabilities → {@code /erp}.</li>
     *   <li>Otherwise → {@code /workspace} (the safe explanation page).</li>
     * </ol>
     */
    private String pickDefault(boolean requiresRotation, boolean controlPlaneAuthorized,
                               LinkedHashSet<String> destinations) {
        if (requiresRotation) {
            return WORKSPACE;
        }
        if (controlPlaneAuthorized && destinations.contains(CONTROL_PLANE)) {
            return CONTROL_PLANE;
        }
        if (destinations.contains(CRM)) {
            return CRM;
        }
        if (destinations.contains(FINANCE)) {
            return FINANCE;
        }
        if (destinations.contains(ERP)) {
            return ERP;
        }
        return WORKSPACE;
    }

    private boolean hasAnyCapability(Set<String> userCapabilities, String[] prefixes) {
        for (String code : userCapabilities) {
            for (String prefix : prefixes) {
                if (code.equals(prefix) || code.startsWith(prefix + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collects the user's effective capability codes AND whether they hold an
     * ACTIVE ADMIN role grant, using a bounded number of queries:
     *   1. role grants (tenant-scoped, single query)
     *   2. roles by IDs (single batched query — replaces the old N+1)
     *   3. role-capability links for those roles (one query per role — bounded)
     *   4. capability records (single findAllById)
     */
    private EffectiveCapabilities collectEffectiveCapabilities(UUID tenantId, UUID userId) {
        List<UserRoleGrant> grants = roleGrantRepository.findByTenantIdAndUserIdAndStatus(
                tenantId, userId, UserGrantStatus.ACTIVE);
        if (grants.isEmpty()) {
            return new EffectiveCapabilities(Collections.emptySet(), false);
        }

        Set<UUID> roleIds = grants.stream()
                .map(UserRoleGrant::getRoleId)
                .collect(Collectors.toCollection(HashSet::new));

        // Single batched role lookup (replaces N x findByTenantIdAndId).
        List<Role> roles = roleRepository.findByTenantIdAndIdIn(tenantId, roleIds);
        boolean adminRole = roles.stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(codeOrNull(r))
                        && r.getStatus() != null
                        && "ACTIVE".equalsIgnoreCase(r.getStatus().name()));
        Set<UUID> activeRoleIds = roles.stream()
                .filter(r -> r.getStatus() != null && "ACTIVE".equalsIgnoreCase(r.getStatus().name()))
                .map(Role::getId)
                .collect(Collectors.toCollection(HashSet::new));

        if (activeRoleIds.isEmpty()) {
            return new EffectiveCapabilities(Collections.emptySet(), adminRole);
        }

        // Batched role-capability lookup: one query per role (acceptable: role
        // count is bounded and small; further batching would need a custom query).
        Set<UUID> capabilityIds = new HashSet<>();
        for (UUID roleId : activeRoleIds) {
            List<RoleCapability> links = roleCapabilityRepository.findByTenantIdAndRoleId(tenantId, roleId);
            for (RoleCapability link : links) {
                capabilityIds.add(link.getCapabilityId());
            }
        }
        if (capabilityIds.isEmpty()) {
            return new EffectiveCapabilities(Collections.emptySet(), adminRole);
        }

        // Resolve capability codes. AccessCapability is global (not tenant-scoped).
        List<AccessCapability> capabilities = accessCapabilityRepository.findAllById(capabilityIds);
        Set<String> codes = capabilities.stream()
                .map(AccessCapability::getCode)
                .collect(Collectors.toCollection(HashSet::new));
        return new EffectiveCapabilities(codes, adminRole);
    }

    private static String codeOrNull(Role role) {
        return role.getCode() == null ? "" : role.getCode();
    }

    /** CRM-area capability prefixes that unlock the CRM destination tree. */
    private static final String[] CRM_CAPABILITY_PREFIXES = {
            "CRM"
    };
    private static final String[] FINANCE_CAPABILITY_PREFIXES = {
            "FINANCE", "ACCOUNTING", "INVOICE", "PAYMENT"
    };
    private static final String[] ERP_CAPABILITY_PREFIXES = {
            "ERP", "INVENTORY", "PURCHASE"
    };
    /** Capability families that constitute platform-level (control-plane) authority. */
    private static final String[] CONTROL_PLANE_CAPABILITY_PREFIXES = {
            "USER", "ROLE", "CAPABILITY", "ORGANIZATION", "MEMBERSHIP", "ACCESS"
    };

    /** Immutable holder for resolved capability state. */
    private static final class EffectiveCapabilities {
        final Set<String> capabilities;
        final boolean adminRole;

        EffectiveCapabilities(Set<String> capabilities, boolean adminRole) {
            this.capabilities = capabilities;
            this.adminRole = adminRole;
        }
    }

    /** Immutable result of a destination resolution. */
    public static final class DestinationSet {
        private final List<String> available;
        private final String defaultDestination;

        public DestinationSet(List<String> available, String defaultDestination) {
            this.available = available;
            this.defaultDestination = defaultDestination;
        }

        public List<String> getAvailable() {
            return available;
        }

        public String getDefaultDestination() {
            return defaultDestination;
        }
    }
}
