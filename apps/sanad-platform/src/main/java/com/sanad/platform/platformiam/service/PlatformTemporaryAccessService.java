package com.sanad.platform.platformiam.service;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.dto.CreatePlatformTemporaryAccessRequest;
import com.sanad.platform.platformiam.dto.PlatformTemporaryAccessResponse;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.user.domain.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Application adapter for canonical access_scope_grants; no parallel authorization store. */
@Service
public class PlatformTemporaryAccessService {
    private final JdbcTemplate jdbc;
    private final ControlPlaneAccessGuard controlPlane;
    private final PlatformAuthorizationService authorization;
    private final PlatformUserService users;
    private final PlatformAuditService audit;
    private final Clock clock;

    @Autowired
    public PlatformTemporaryAccessService(JdbcTemplate jdbc, ControlPlaneAccessGuard controlPlane,
            PlatformAuthorizationService authorization, PlatformUserService users, PlatformAuditService audit) {
        this(jdbc, controlPlane, authorization, users, audit, Clock.systemUTC());
    }

    public PlatformTemporaryAccessService(JdbcTemplate jdbc, ControlPlaneAccessGuard controlPlane,
            PlatformAuthorizationService authorization, PlatformUserService users, PlatformAuditService audit,
            Clock clock) {
        this.jdbc = jdbc;
        this.controlPlane = controlPlane;
        this.authorization = authorization;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlatformTemporaryAccessResponse> list(Authentication actor, UUID userId) {
        UUID tenantId = authorize(actor, "PLATFORM.PERMISSION.READ");
        users.get(actor, userId);
        Instant now = clock.instant();
        return jdbc.query("""
                SELECT g.*, c.code AS capability_code
                FROM access_scope_grants g JOIN access_capabilities c ON c.id = g.capability_id
                WHERE g.tenant_id = ? AND g.user_id = ? AND g.role_id IS NULL
                  AND g.is_direct_exception = TRUE AND g.scope_type = 'TENANT'
                ORDER BY g.created_at DESC, g.id
                """, (rs, row) -> response(rs, now), tenantId, userId);
    }

    @Transactional
    public PlatformTemporaryAccessResponse grant(Authentication actor, UUID userId,
            CreatePlatformTemporaryAccessRequest request) {
        UUID tenantId = authorize(actor, "PLATFORM.PERMISSION.MANAGE");
        var user = users.get(actor, userId);
        if (user.accountStatus() != UserStatus.ACTIVE
                || user.membershipStatus() != PlatformMembershipStatus.ACTIVE) {
            throw new AccessConflictException("Active platform user required");
        }
        Instant now = clock.instant();
        if (request == null || request.capabilityId() == null || request.effectiveTo() == null
                || !request.effectiveTo().isAfter(now)) {
            throw new IllegalArgumentException("Capability and future expiry required");
        }
        String reason = requireReason(request.reason());
        UUID actorId = identity(actor, "user_id");
        List<String> codes = jdbc.query(
                "SELECT code FROM access_capabilities WHERE id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getString("code"), request.capabilityId());
        if (codes.isEmpty()) throw new AccessResourceNotFoundException("Active capability not found");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO access_scope_grants
                    (id, tenant_id, user_id, capability_id, scope_type, is_direct_exception,
                     reason, granted_by, effective_from, effective_to, status)
                VALUES (?, ?, ?, ?, 'TENANT', TRUE, ?, ?, ?, ?, 'ACTIVE')
                """, id, tenantId, userId, request.capabilityId(), reason, actorId,
                Timestamp.from(now), Timestamp.from(request.effectiveTo()));
        var result = new PlatformTemporaryAccessResponse(id, userId, request.capabilityId(),
                codes.getFirst(), now, request.effectiveTo(), reason, actorId, "ACTIVE");
        audit.success(actor, tenantId, "PLATFORM_TEMPORARY_ACCESS_GRANTED", "PLATFORM_USER",
                userId.toString(), reason, null, result);
        return result;
    }

    @Transactional
    public void revoke(Authentication actor, UUID userId, UUID grantId, String reason) {
        UUID tenantId = authorize(actor, "PLATFORM.PERMISSION.MANAGE");
        users.get(actor, userId);
        String normalized = requireReason(reason);
        int changed = jdbc.update("""
                UPDATE access_scope_grants SET status = 'REVOKED'
                WHERE tenant_id = ? AND user_id = ? AND id = ?
                  AND role_id IS NULL AND is_direct_exception = TRUE AND scope_type = 'TENANT'
                  AND status = 'ACTIVE'
                """, tenantId, userId, grantId);
        if (changed != 1) throw new AccessResourceNotFoundException("Active temporary grant not found");
        audit.success(actor, tenantId, "PLATFORM_TEMPORARY_ACCESS_REVOKED", "PLATFORM_USER",
                userId.toString(), normalized, Map.of("grantId", grantId, "status", "ACTIVE"),
                Map.of("grantId", grantId, "status", "REVOKED"));
    }

    private UUID authorize(Authentication actor, String capability) {
        controlPlane.require(actor);
        UUID tenantId = identity(actor, "tenant_id");
        identity(actor, "user_id");
        // Transaction-local context derives exclusively from the validated control-plane identity.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
        var decision = authorization.evaluate(actor, capability);
        if (decision == null || !decision.allowed()) throw new AccessDeniedException("Platform capability required");
        return tenantId;
    }

    private static UUID identity(Authentication actor, String key) {
        if (actor == null || !actor.isAuthenticated() || !(actor.getDetails() instanceof Map<?, ?> details)) {
            throw new AccessDeniedException("Trusted identity required");
        }
        try {
            return UUID.fromString(String.valueOf(details.get(key)));
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid authenticated identity");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("Reason required, maximum 500 characters");
        }
        return reason.trim();
    }

    private static PlatformTemporaryAccessResponse response(ResultSet rs, Instant now) throws SQLException {
        Instant expiry = rs.getTimestamp("effective_to").toInstant();
        String status = rs.getString("status");
        if ("ACTIVE".equals(status) && !now.isBefore(expiry)) status = "EXPIRED";
        return new PlatformTemporaryAccessResponse(rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getObject("capability_id", UUID.class),
                rs.getString("capability_code"), rs.getTimestamp("effective_from").toInstant(),
                expiry, rs.getString("reason"), rs.getObject("granted_by", UUID.class), status);
    }
}
