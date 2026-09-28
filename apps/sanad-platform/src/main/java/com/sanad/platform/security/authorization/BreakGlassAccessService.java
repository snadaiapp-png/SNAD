package com.sanad.platform.security.authorization;

import com.sanad.platform.access.override.UserPermissionOverrideService;
import com.sanad.platform.access.override.dto.CreateOverrideRequest;
import com.sanad.platform.access.override.dto.OverrideResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wave 1 Task 15 — break-glass as a NORMALLY-EVALUATED time-boxed grant.
 *
 * <p>Break-glass creates an ordinary ALLOW override (TENANT_ALL) with a
 * mandatory descriptive reason (≥ 20 chars), a hard 4-hour ceiling, and the
 * full audited trail (platform_audit_logs + authorization_change_events +
 * authorization_version bump, all enforced by the override service). It is
 * NOT a runtime precedence layer: an active direct DENY still beats a
 * break-glass ALLOW because both are evaluated by the five-stage pipeline
 * (spec §5.1 Rev B / §4.1 Revision D).</p>
 */
@Service
public class BreakGlassAccessService {

    static final int MIN_REASON_LENGTH = 20;
    static final Duration MAX_WINDOW = Duration.ofHours(4);
    static final String REASON_MARKER = "[BREAK_GLASS]";

    private final UserPermissionOverrideService overrideService;

    public BreakGlassAccessService(UserPermissionOverrideService overrideService) {
        this.overrideService = overrideService;
    }

    @Transactional
    public OverrideResponse grantEmergencyOverride(
            UUID tenantId, UUID actorUserId, UUID targetUserId,
            String capabilityCode, String reason, Instant validUntil) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new IllegalArgumentException("BREAK_GLASS_REASON_INSUFFICIENT");
        }
        if (validUntil == null || validUntil.isAfter(Instant.now().plus(MAX_WINDOW))) {
            throw new IllegalArgumentException("BREAK_GLASS_WINDOW_EXCEEDED");
        }
        return overrideService.create(tenantId, actorUserId, new CreateOverrideRequest(
                targetUserId, capabilityCode, "ALLOW", "TENANT_ALL", null,
                REASON_MARKER + " " + reason.trim(), Instant.now(), validUntil));
    }

    @Transactional(readOnly = true)
    public List<OverrideResponse> listActive(UUID tenantId, UUID userId) {
        return overrideService.list(tenantId, userId);
    }

    @Transactional
    public void revoke(UUID tenantId, UUID actorUserId, UUID overrideId) {
        overrideService.revoke(tenantId, actorUserId, overrideId);
    }
}
