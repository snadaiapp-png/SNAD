package com.sanad.platform.security.service;

import com.sanad.platform.access.override.UserPermissionOverrideRepository;
import com.sanad.platform.access.override.UserPermissionOverrideRepository.OverrideCapabilityEffect;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Effective-capability introspection for identity surfaces (auth {@code /me}
 * and the login/refresh bootstrap).
 *
 * <p>Effective capabilities = role capabilities UNION active ALLOW overrides
 * MINUS active DENY overrides. This mirrors the authoritative decision
 * pipeline semantics ({@code CapabilityEvaluationService}: an explicit direct
 * DENY dominates, an ALLOW override grants) so introspection advertises what
 * enforcement would actually grant — e.g. the canonical platform owner's
 * {@code HRM.PAYROLL.VIEW} grant is a {@code user_permission_overrides} row
 * (migration V20261010_99), not a role capability.</p>
 *
 * <p>{@code user_permission_overrides} is FORCE-RLS protected with a
 * fail-closed tenant policy. The read therefore runs inside a read-only
 * transaction and explicitly applies the trusted tenant GUC via
 * {@link TenantRlsTransactionContext} so the query is tenant-scoped even on
 * paths without an authenticated security context (login/refresh bootstrap).
 * The tenant id argument is the authenticated subject's own tenant id loaded
 * from the user record — never caller-supplied raw input.</p>
 *
 * <p>This service is an introspection view only. It never expands access:
 * authoritative enforcement remains the scope-aware, fail-closed decision
 * pipeline invoked by {@code CapabilityAuthorizationAspect}.</p>
 */
@Service
public class EffectiveCapabilityService {

    private final RoleCapabilityRepository roleCapabilityRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final TenantRlsTransactionContext tenantRlsTransactionContext;

    public EffectiveCapabilityService(
            RoleCapabilityRepository roleCapabilityRepository,
            UserPermissionOverrideRepository userPermissionOverrideRepository,
            TenantRlsTransactionContext tenantRlsTransactionContext) {
        this.roleCapabilityRepository = roleCapabilityRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.tenantRlsTransactionContext = tenantRlsTransactionContext;
    }

    /**
     * Resolves the effective capability codes for one subject within the
     * subject's own tenant.
     *
     * @param tenantId the subject's own tenant id (trusted, loaded from the
     *                 authenticated user record)
     * @param userId   the subject's user id
     * @param roleIds  the subject's active role ids (may be empty)
     */
    @Transactional(readOnly = true)
    public List<String> effectiveCapabilityCodes(UUID tenantId, UUID userId, Set<UUID> roleIds) {
        tenantRlsTransactionContext.applyForCurrentTransaction(tenantId);

        List<String> roleCaps = roleIds.isEmpty()
                ? List.of()
                : roleCapabilityRepository.findCapabilityCodesByTenantIdAndRoleIds(tenantId, roleIds);

        LinkedHashSet<String> effective = new LinkedHashSet<>(roleCaps);
        for (OverrideCapabilityEffect effectCode : userPermissionOverrideRepository.findActiveCapabilityEffects(tenantId, userId)) {
            if ("ALLOW".equals(effectCode.effect())) {
                effective.add(effectCode.code());
            } else if ("DENY".equals(effectCode.effect())) {
                effective.remove(effectCode.code());
            }
        }
        return List.copyOf(effective);
    }
}
