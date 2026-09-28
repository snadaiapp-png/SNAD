package com.sanad.platform.access.override;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.evaluation.AuthorizationVersionService;
import com.sanad.platform.access.override.dto.CreateOverrideRequest;
import com.sanad.platform.access.override.dto.OverrideResponse;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Audited access-administration of user permission overrides (Wave 1 Task 9).
 *
 * <p>Validation matrix runs BEFORE persistence; the DB CHECK
 * {@code ck_upo_deny_is_capability_wide} is the backstop for scoped DENY.
 * Every mutation writes platform_audit_logs (PlatformAuditWriter), an
 * authorization_change_events row (USER_OVERRIDE_CHANGED) and bumps
 * users.authorization_version — revocation never relies on TTL.</p>
 */
@Service
public class UserPermissionOverrideService {

    private static final Set<String> SCOPE_TYPES = Set.of(
            "SELF", "OWN", "DIRECT_REPORTS", "REPORTING_TREE", "TEAM", "DEPARTMENT",
            "ORG_UNIT", "ORGANIZATION", "BRANCH", "BUSINESS_UNIT", "LEGAL_ENTITY",
            "PROJECT", "TENANT_ALL");

    private final AccessCapabilityService capabilityService;
    private final UserPermissionOverrideRepository repository;
    private final AuthorizationVersionService authorizationVersionService;
    private final PlatformAuditWriter auditWriter;
    private final JdbcTemplate jdbc;

    public UserPermissionOverrideService(
            AccessCapabilityService capabilityService,
            UserPermissionOverrideRepository repository,
            AuthorizationVersionService authorizationVersionService,
            PlatformAuditWriter auditWriter,
            JdbcTemplate jdbc) {
        this.capabilityService = capabilityService;
        this.repository = repository;
        this.authorizationVersionService = authorizationVersionService;
        this.auditWriter = auditWriter;
        this.jdbc = jdbc;
    }

    @Transactional
    public OverrideResponse create(UUID tenantId, UUID actorUserId, CreateOverrideRequest request) {
        if (tenantId == null || actorUserId == null) {
            throw new IllegalArgumentException("SUBJECT_CONTEXT_REQUIRED");
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("REASON_REQUIRED");
        }
        String effect = request.effect() == null ? "" : request.effect().trim().toUpperCase();
        if (!"ALLOW".equals(effect) && !"DENY".equals(effect)) {
            throw new IllegalArgumentException("INVALID_EFFECT");
        }
        if (request.targetUserId() == null) {
            throw new IllegalArgumentException("TARGET_USER_REQUIRED");
        }

        AccessCapability capability;
        try {
            capability = capabilityService.loadByCode(request.capabilityCode());
        } catch (Exception exception) {
            throw new IllegalArgumentException("CAPABILITY_NOT_FOUND", exception);
        }
        if (capability.getStatus() != CapabilityStatus.ACTIVE) {
            throw new IllegalArgumentException("CAPABILITY_INACTIVE");
        }

        Integer userInTenant = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, request.targetUserId());
        if (userInTenant == null || userInTenant == 0) {
            throw new IllegalArgumentException("USER_TENANT_MISMATCH");
        }

        String scopeType = request.scopeType() == null ? null : request.scopeType().trim().toUpperCase();
        if ("DENY".equals(effect)) {
            // v1 invariant: DIRECT DENY IS CAPABILITY-WIDE. Service-level
            // rejection before persistence; DB CHECK is the backstop.
            if (scopeType != null || request.scopeReference() != null) {
                throw new IllegalArgumentException("DENY_IS_CAPABILITY_WIDE");
            }
        } else {
            if (scopeType != null) {
                if (!SCOPE_TYPES.contains(scopeType)) {
                    throw new IllegalArgumentException("INVALID_SCOPE_TYPE");
                }
                if (!"TENANT_ALL".equals(scopeType) && !supportsScope(capability.getId())) {
                    throw new IllegalArgumentException("SCOPE_NOT_SUPPORTED");
                }
            }
        }

        Instant validFrom = request.validFrom() == null ? Instant.now() : request.validFrom();
        Instant validUntil = request.validUntil();
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("INVALID_VALIDITY_WINDOW");
        }

        UserPermissionOverride override = repository.insert(new UserPermissionOverride(
                UUID.randomUUID(), tenantId, request.targetUserId(), capability.getId(),
                effect, "ALLOW".equals(effect) && scopeType == null ? "TENANT_ALL" : scopeType,
                request.scopeReference(), request.reason().trim(), validFrom, validUntil,
                actorUserId, 0));

        auditWriter.writeSuccess(tenantId, actorUserId, tenantId,
                "USER_OVERRIDE_CREATE", "USER_PERMISSION_OVERRIDE",
                override.getId().toString(), override.getReason(), null,
                override.getEffect() + ":" + capability.getCode(), null, Instant.now());
        jdbc.update("INSERT INTO authorization_change_events "
                        + "(id, tenant_id, event_type, actor_user_id, target_type, target_id, payload) "
                        + "VALUES (?, ?, 'USER_OVERRIDE_CHANGED', ?, 'USER_PERMISSION_OVERRIDE', ?, ?::jsonb)",
                UUID.randomUUID(), tenantId, actorUserId, request.targetUserId(),
                "{\"operation\":\"CREATE\",\"effect\":\"" + effect + "\","
                        + "\"capability\":\"" + capability.getCode() + "\"}");
        authorizationVersionService.bump(tenantId, request.targetUserId());

        return toResponse(override, capability.getCode());
    }

    @Transactional
    public void revoke(UUID tenantId, UUID actorUserId, UUID overrideId) {
        UserPermissionOverride override = repository.find(tenantId, overrideId)
                .orElseThrow(() -> new IllegalArgumentException("OVERRIDE_NOT_FOUND"));
        if (!repository.expire(tenantId, overrideId)) {
            throw new IllegalArgumentException("OVERRIDE_NOT_ACTIVE");
        }

        auditWriter.writeSuccess(tenantId, actorUserId, tenantId,
                "USER_OVERRIDE_REVOKE", "USER_PERMISSION_OVERRIDE",
                overrideId.toString(), override.getReason(), null,
                override.getEffect(), null, Instant.now());
        jdbc.update("INSERT INTO authorization_change_events "
                        + "(id, tenant_id, event_type, actor_user_id, target_type, target_id, payload) "
                        + "VALUES (?, ?, 'USER_OVERRIDE_CHANGED', ?, 'USER_PERMISSION_OVERRIDE', ?, ?::jsonb)",
                UUID.randomUUID(), tenantId, actorUserId, override.getUserId(),
                "{\"operation\":\"REVOKE\",\"effect\":\"" + override.getEffect() + "\"}");
        authorizationVersionService.bump(tenantId, override.getUserId());
    }

    @Transactional(readOnly = true)
    public List<OverrideResponse> list(UUID tenantId, UUID userId) {
        return repository.listForUser(tenantId, userId).stream()
                .map(override -> toResponse(override, capabilityCode(override.getCapabilityId())))
                .toList();
    }

    private boolean supportsScope(UUID capabilityId) {
        Boolean supportsScope = jdbc.queryForObject(
                "SELECT supports_scope FROM access_capabilities WHERE id = ?",
                Boolean.class, capabilityId);
        return Boolean.TRUE.equals(supportsScope);
    }

    private String capabilityCode(UUID capabilityId) {
        return jdbc.queryForObject(
                "SELECT code FROM access_capabilities WHERE id = ?", String.class, capabilityId);
    }

    private static OverrideResponse toResponse(UserPermissionOverride override, String capabilityCode) {
        return new OverrideResponse(override.getId(), override.getUserId(), capabilityCode,
                override.getEffect(), override.getScopeType(), override.getScopeReference(),
                override.getReason(), override.getValidFrom(), override.getValidUntil(),
                override.getVersion());
    }
}
