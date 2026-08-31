package com.sanad.platform.subscription.rbac;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates the granular control-plane capability codes for the current
 * user. Exposed additively through {@code /access-check/v2} so the console
 * can hide/show surfaces by permission instead of the broad
 * {@code EXECUTIVE_*} codes only (existing access-check stays untouched).
 */
@Service
public class ControlPlaneAccessService {

    public static final List<String> CONTROL_PLANE_CAPABILITIES = List.of(
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
            "audit.read");

    private final CapabilityEvaluationService evaluationService;
    private final JdbcTemplate jdbc;

    public ControlPlaneAccessService(CapabilityEvaluationService evaluationService,
                                     JdbcTemplate jdbc) {
        this.evaluationService = evaluationService;
        this.jdbc = jdbc;
    }

    public record AccessCheckV2(boolean authenticated, Map<String, Boolean> capabilities) {
    }

    @Transactional(readOnly = true)
    public AccessCheckV2 accessCheck(Authentication authentication) {
        boolean isAuth = authentication != null && authentication.isAuthenticated();
        if (!isAuth
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || !(details.get("tenant_id") instanceof UUID tenantId)
                || !(details.get("user_id") instanceof UUID userId)) {
            return new AccessCheckV2(false, Map.of());
        }
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        for (String code : CONTROL_PLANE_CAPABILITIES) {
            try {
                AccessDecisionResponse decision =
                        evaluationService.evaluate(tenantId, userId, code, null);
                capabilities.put(code, decision != null && decision.allowed());
            } catch (Exception e) {
                capabilities.put(code, false);
            }
        }
        return new AccessCheckV2(true, capabilities);
    }

    @Transactional(readOnly = true)
    public List<String> activeCapabilityCodes() {
        return jdbc.queryForList(
                "SELECT code FROM access_capabilities WHERE status = 'ACTIVE' ORDER BY code",
                String.class);
    }
}
