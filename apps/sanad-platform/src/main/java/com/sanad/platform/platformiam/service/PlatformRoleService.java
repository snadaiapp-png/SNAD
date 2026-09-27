package com.sanad.platform.platformiam.service;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityResponse;
import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.role.CreateRoleRequest;
import com.sanad.platform.access.role.RoleAccessResponse;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleResponse;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.UpdateRoleRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.platformiam.domain.PlatformRoleMetadata;
import com.sanad.platform.platformiam.dto.CreatePlatformRoleRequest;
import com.sanad.platform.platformiam.dto.PlatformRoleResponse;
import com.sanad.platform.platformiam.dto.ReplacePlatformRolesRequest;
import com.sanad.platform.platformiam.dto.ReplaceRoleCapabilitiesRequest;
import com.sanad.platform.platformiam.dto.UpdatePlatformRoleRequest;
import com.sanad.platform.platformiam.repository.PlatformRoleMetadataRepository;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PlatformRoleService {

    private final ControlPlaneAccessGuard controlPlaneAccessGuard;
    private final RoleService roles;
    private final RoleCapabilityService roleCapabilities;
    private final AccessCapabilityService capabilities;
    private final PlatformRoleMetadataRepository metadata;
    private final UserRoleGrantService grants;
    private final PlatformOwnerSafetyService ownerSafety;
    private final PlatformAuditService audit;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformRoleService(
            ControlPlaneAccessGuard controlPlaneAccessGuard,
            RoleService roles,
            RoleCapabilityService roleCapabilities,
            AccessCapabilityService capabilities,
            PlatformRoleMetadataRepository metadata,
            UserRoleGrantService grants,
            PlatformOwnerSafetyService ownerSafety,
            PlatformAuditService audit) {
        this(controlPlaneAccessGuard, roles, roleCapabilities, capabilities, metadata,
                grants, ownerSafety, audit, Clock.systemUTC());
    }

    public PlatformRoleService(
            ControlPlaneAccessGuard controlPlaneAccessGuard,
            RoleService roles,
            RoleCapabilityService roleCapabilities,
            AccessCapabilityService capabilities,
            PlatformRoleMetadataRepository metadata,
            UserRoleGrantService grants,
            PlatformOwnerSafetyService ownerSafety,
            PlatformAuditService audit,
            Clock clock) {
        this.controlPlaneAccessGuard = Objects.requireNonNull(controlPlaneAccessGuard, "controlPlaneAccessGuard");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.roleCapabilities = Objects.requireNonNull(roleCapabilities, "roleCapabilities");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.ownerSafety = Objects.requireNonNull(ownerSafety, "ownerSafety");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public List<PlatformRoleResponse> list(Authentication actor) {
        UUID tenantId = controlTenant(actor);
        Map<UUID, PlatformRoleMetadata> meta = new HashMap<>();
        metadata.findByControlTenantId(tenantId).forEach(item -> meta.put(item.roleId(), item));
        return roles.list(tenantId).stream()
                .filter(role -> meta.containsKey(role.id()))
                .map(role -> response(role, meta.get(role.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformRoleResponse get(Authentication actor, UUID roleId) {
        UUID tenantId = controlTenant(actor);
        PlatformRoleMetadata item = requireMetadata(tenantId, roleId);
        return response(roles.get(tenantId, roleId), item);
    }

    @Transactional
    public PlatformRoleResponse create(Authentication actor, CreatePlatformRoleRequest request) {
        Objects.requireNonNull(request, "request");
        UUID tenantId = controlTenant(actor);
        RoleResponse role = roles.create(tenantId,
                new CreateRoleRequest(request.code(), request.name(), request.description()));
        Instant now = clock.instant();
        PlatformRoleMetadata item = metadata.save(new PlatformRoleMetadata(
                tenantId, role.id(), PlatformRoleMetadata.RoleType.CUSTOM,
                false, false, now, now));
        PlatformRoleResponse result = response(role, item);
        audit.success(actor, tenantId, "PLATFORM_ROLE_CREATED", "PLATFORM_ROLE",
                role.id().toString(), null, null, result);
        return result;
    }

    @Transactional
    public PlatformRoleResponse update(Authentication actor, UUID roleId, UpdatePlatformRoleRequest request) {
        Objects.requireNonNull(request, "request");
        UUID tenantId = controlTenant(actor);
        PlatformRoleMetadata item = requireMutableCustomRole(tenantId, roleId);
        PlatformRoleResponse before = response(roles.get(tenantId, roleId), item);
        RoleResponse updated = roles.update(tenantId, roleId,
                new UpdateRoleRequest(request.name(), request.description()));
        PlatformRoleResponse result = response(updated, item);
        audit.success(actor, tenantId, "PLATFORM_ROLE_UPDATED", "PLATFORM_ROLE",
                roleId.toString(), null, before, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<CapabilityResponse> listCapabilities(Authentication actor) {
        controlTenant(actor);
        return capabilities.list();
    }

    @Transactional(readOnly = true)
    public List<RoleAccessResponse> listRoleCapabilities(Authentication actor, UUID roleId) {
        UUID tenantId = controlTenant(actor);
        requireMetadata(tenantId, roleId);
        return roleCapabilities.list(tenantId, roleId);
    }

    @Transactional
    public List<RoleAccessResponse> replaceRoleCapabilities(
            Authentication actor,
            UUID roleId,
            ReplaceRoleCapabilitiesRequest request) {
        Objects.requireNonNull(request, "request");
        UUID tenantId = controlTenant(actor);
        requireMutableCustomRole(tenantId, roleId);
        List<RoleAccessResponse> current = roleCapabilities.list(tenantId, roleId);
        Set<UUID> desired = new HashSet<>(request.capabilityIds());
        Set<UUID> existing = new HashSet<>();
        current.forEach(link -> existing.add(link.capabilityId()));

        for (RoleAccessResponse link : current) {
            if (!desired.contains(link.capabilityId())) {
                roleCapabilities.detach(tenantId, roleId, link.capabilityId());
            }
        }
        for (UUID capabilityId : desired) {
            if (!existing.contains(capabilityId)) {
                roleCapabilities.attach(tenantId, roleId, capabilityId);
            }
        }
        List<RoleAccessResponse> result = roleCapabilities.list(tenantId, roleId);
        audit.success(actor, tenantId, "PLATFORM_PERMISSION_CHANGED", "PLATFORM_ROLE",
                roleId.toString(), null, current, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<UserAccessResponse> listUserRoles(Authentication actor, UUID userId) {
        UUID tenantId = controlTenant(actor);
        return grants.list(tenantId, userId).stream()
                .filter(grant -> metadata.findByControlTenantIdAndRoleId(tenantId, grant.roleId()).isPresent())
                .toList();
    }

    @Transactional
    public List<UserAccessResponse> replaceUserRoles(
            Authentication actor,
            UUID userId,
            ReplacePlatformRolesRequest request) {
        Objects.requireNonNull(request, "request");
        UUID tenantId = controlTenant(actor);
        List<UserAccessResponse> current = grants.list(tenantId, userId).stream()
                .filter(grant -> grant.status() == UserGrantStatus.ACTIVE)
                .filter(grant -> metadata.findByControlTenantIdAndRoleId(tenantId, grant.roleId()).isPresent())
                .toList();
        Set<UUID> desired = new HashSet<>(request.roleIds());
        Set<UUID> existing = new HashSet<>();
        current.forEach(grant -> existing.add(grant.roleId()));

        for (UserAccessResponse grant : current) {
            if (!desired.contains(grant.roleId())) {
                ownerSafety.assertMayRemoveOwnerRole(tenantId, userId, grant.roleId());
                grants.revoke(tenantId, grant.id());
            }
        }
        for (UUID roleId : desired) {
            requireMetadata(tenantId, roleId);
            if (!existing.contains(roleId)) {
                grants.grant(tenantId, userId, roleId, null);
            }
        }
        List<UserAccessResponse> result = grants.list(tenantId, userId).stream()
                .filter(grant -> metadata.findByControlTenantIdAndRoleId(tenantId, grant.roleId()).isPresent())
                .toList();
        audit.success(actor, tenantId, "PLATFORM_ROLE_ASSIGNMENT_CHANGED", "PLATFORM_USER",
                userId.toString(), blankToNull(request.reason()), current, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<String> effectivePermissions(Authentication actor, UUID userId) {
        UUID tenantId = controlTenant(actor);
        Set<String> codes = new HashSet<>();
        for (UserAccessResponse grant : grants.list(tenantId, userId)) {
            if (grant.status() != UserGrantStatus.ACTIVE
                    || metadata.findByControlTenantIdAndRoleId(tenantId, grant.roleId()).isEmpty()) {
                continue;
            }
            roleCapabilities.list(tenantId, grant.roleId()).forEach(link -> codes.add(link.capabilityCode()));
        }
        return codes.stream().sorted().toList();
    }

    private PlatformRoleMetadata requireMetadata(UUID tenantId, UUID roleId) {
        return metadata.findByControlTenantIdAndRoleId(tenantId, Objects.requireNonNull(roleId, "roleId"))
                .orElseThrow(() -> new AccessResourceNotFoundException("Platform role not found"));
    }

    private PlatformRoleMetadata requireMutableCustomRole(UUID tenantId, UUID roleId) {
        PlatformRoleMetadata item = requireMetadata(tenantId, roleId);
        if (item.protectedRole() || item.roleType() == PlatformRoleMetadata.RoleType.SYSTEM) {
            throw new AccessConflictException("Protected platform role cannot be mutated");
        }
        return item;
    }

    private UUID controlTenant(Authentication actor) {
        controlPlaneAccessGuard.require(actor);
        UUID tenantId = requiredUuid(actor, "tenant_id");
        if (!controlPlaneAccessGuard.isControlPlaneTenant(tenantId)) {
            throw new AccessDeniedException("Control-plane tenant required");
        }
        return tenantId;
    }

    private static UUID requiredUuid(Authentication authentication, String key) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            throw new AccessDeniedException("Trusted authentication details required");
        }
        Object raw = details.get(key);
        try {
            return raw instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(raw));
        } catch (RuntimeException exception) {
            throw new AccessDeniedException("Invalid authenticated " + key);
        }
    }

    private static PlatformRoleResponse response(RoleResponse role, PlatformRoleMetadata item) {
        return new PlatformRoleResponse(
                role.id(), role.code(), role.name(), role.description(), role.status(),
                item.roleType(), item.protectedRole(), item.ownerRole());
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
