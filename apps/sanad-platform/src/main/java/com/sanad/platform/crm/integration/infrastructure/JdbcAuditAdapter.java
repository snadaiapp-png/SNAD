package com.sanad.platform.crm.integration.infrastructure;

import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.admin.service.PlatformAuditService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM AuditPort adapter that delegates to the central PlatformAuditService.
 * Uses the correct platform_audit_logs schema with:
 *   actor_tenant_id, actor_user_id, target_tenant_id, resource_type,
 *   resource_id, result='SUCCESS', correlation_id, before_state, after_state.
 *
 * Replaces the previous incompatible JdbcAuditAdapter that used wrong column names.
 */
@Component
public class JdbcAuditAdapter implements AuditPort {

    private final PlatformAuditService platformAuditService;

    public JdbcAuditAdapter(PlatformAuditService platformAuditService) {
        this.platformAuditService = platformAuditService;
    }

    @Override
    public void record(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId,
                       AuditChange change, Instant timestamp) {
        // Build a synthetic Authentication for PlatformAuditService.principal()
        Authentication auth = new UsernamePasswordAuthenticationToken(
                actorId == null ? "system" : actorId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("tenant_id", tenantId == null ? null : tenantId.toString());
        details.put("user_id", actorId == null ? null : actorId.toString());
        ((UsernamePasswordAuthenticationToken) auth).setDetails(details);

        platformAuditService.success(
                auth,
                tenantId,
                action,
                entityType,
                entityId == null ? null : entityId.toString(),
                null,
                change.beforeJson(),
                change.afterJson());
    }
}
