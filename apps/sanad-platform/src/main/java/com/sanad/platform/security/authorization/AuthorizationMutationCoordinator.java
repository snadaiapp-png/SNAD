package com.sanad.platform.security.authorization;

import com.sanad.platform.access.evaluation.AuthorizationVersionService;
import com.sanad.platform.access.evaluation.EffectivePermissionProjectionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single runtime coordinator for version bump + durable change event + immediate
 * in-process cache invalidation. Business services stay authoritative for the
 * mutation itself; this component only propagates its authorization impact.
 */
@Component
public class AuthorizationMutationCoordinator {
    private final AuthorizationVersionService versions;
    private final EffectivePermissionProjectionService projections;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;

    public AuthorizationMutationCoordinator(
            AuthorizationVersionService versions,
            EffectivePermissionProjectionService projections,
            JdbcTemplate jdbc,
            ApplicationEventPublisher publisher) {
        this.versions = versions;
        this.projections = projections;
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Transactional
    public long subjectChanged(UUID tenantId, UUID userId, String eventType,
                               String targetType, UUID targetId) {
        scope(tenantId);
        long version = versions.bump(tenantId, userId);
        jdbc.update("INSERT INTO authorization_change_events "
                        + "(id, tenant_id, event_type, actor_user_id, target_type, target_id, payload) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)",
                UUID.randomUUID(), tenantId, eventType, actorUserId(), targetType,
                targetId == null ? userId : targetId,
                "{\"authorizationVersion\":" + version + "}");
        publishOnly(tenantId, userId, eventType, version);
        return version;
    }

    @Transactional
    public void roleChanged(UUID tenantId, UUID roleId, String eventType) {
        scope(tenantId);
        List<UUID> users = jdbc.query(
                "SELECT DISTINCT user_id FROM user_role_assignments "
                        + "WHERE tenant_id = ? AND role_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getObject(1, UUID.class), tenantId, roleId);
        for (UUID userId : users) {
            subjectChanged(tenantId, userId, eventType, "ROLE", roleId);
        }
    }

    public void publishOnly(UUID tenantId, UUID userId, String eventType, long authorizationVersion) {
        // Keep the read/explanation model in the same transaction as the
        // authorization mutation. The evaluator remains authoritative, while
        // the UI projection becomes immediately consistent after grant/revoke,
        // role-capability and override changes.
        projections.rebuild(tenantId, userId);
        publisher.publishEvent(new AuthorizationChangedEvent(
                tenantId, userId, eventType, authorizationVersion));
    }

    private void scope(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, tenantId.toString());
    }

    private static UUID actorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) return null;
        Object raw = details.get("user_id");
        if (raw == null) return null;
        try { return UUID.fromString(raw.toString()); } catch (IllegalArgumentException ignored) { return null; }
    }
}
