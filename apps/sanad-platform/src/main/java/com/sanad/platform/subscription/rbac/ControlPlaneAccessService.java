package com.sanad.platform.subscription.rbac;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.security.SecurityContextUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ControlPlaneAccessService {

    public static final List<String> CONTROL_PLANE_CAPABILITIES = List.of(
            "EXECUTIVE_VIEW", "EXECUTIVE_MANAGE",
            "subscription.read", "subscription.create", "subscription.change_plan",
            "subscription.cancel", "subscription.suspend",
            "catalog.read", "catalog.manage",
            "application.read", "application.manage",
            "plan.read", "plan.manage",
            "pricing.read", "pricing.manage",
            "entitlement.read", "entitlement.manage", "entitlement.override",
            "usage.read",
            "billing.read", "billing.adjust",
            "provisioning.read", "provisioning.retry",
            "audit.read",
            // Wave 1 UAC administration is surfaced through the same fail-closed
            // access-check provider. These are canonical backend capabilities,
            // not UI-only mirrors.
            "ROLE.READ",
            "AUTHORIZATION.OVERRIDE.MANAGE",
            "AUTHORIZATION.RELATIONSHIP.MANAGE",
            "AUTHORIZATION.RESYNC",
            "AUTHORIZATION.BREAK_GLASS",
            "AUTHORIZATION.RECOVER",
            "AUTHORIZATION.PLATFORM.MANAGE");

    private final CapabilityEvaluationService evaluationService;
    private final JdbcTemplate jdbc;

    public ControlPlaneAccessService(CapabilityEvaluationService evaluationService,
                                     JdbcTemplate jdbc) {
        this.evaluationService = evaluationService;
        this.jdbc = jdbc;
    }

    public record AccessCheckV2(boolean authenticated, Map<String, Boolean> capabilities) {}

    @Transactional(readOnly = true)
    public AccessCheckV2 accessCheck(Authentication authentication) {
        UUID tenantId = null;
        UUID userId = null;
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                tenantId = SecurityContextUtils.tenantId(authentication);
                userId = SecurityContextUtils.userId(authentication);
            } catch (RuntimeException extractionFailure) {
                tenantId = null;
                userId = null;
            }
        }
        if (tenantId == null || userId == null) return new AccessCheckV2(false, Map.of());

        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        for (String code : CONTROL_PLANE_CAPABILITIES) {
            try {
                AccessDecisionResponse decision = evaluationService.evaluate(tenantId, userId, code, null);
                capabilities.put(code, decision != null && decision.allowed());
            } catch (Exception e) {
                capabilities.put(code, false);
            }
        }
        return new AccessCheckV2(true, capabilities);
    }

    @Transactional(readOnly = true)
    public List<String> activeCapabilityCodes() {
        return jdbc.queryForList("SELECT code FROM access_capabilities WHERE status = 'ACTIVE' ORDER BY code", String.class);
    }
}
