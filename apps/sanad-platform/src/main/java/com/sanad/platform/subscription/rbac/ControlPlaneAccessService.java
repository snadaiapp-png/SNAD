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

/**
 * Evaluates the control-plane capability codes for the current user.
 * Exposed additively through {@code /access-check/v2} so the console can
 * render read surfaces by granular capability while gating mutation controls
 * by the exact broad backend authority where the endpoint still requires it.
 *
 * <p>Identity extraction contract (R0C-12 corrective recertification): the
 * production {@code JwtAuthenticationFilter} stores {@code tenant_id} and
 * {@code user_id} in the authentication details as <strong>String</strong>
 * values. This consumer therefore extracts identity through the canonical
 * {@link SecurityContextUtils} accessors — which accept both String and UUID
 * representations and are shared with the rest of the authorization chain
 * ({@code CapabilityAuthorizationAspect}, {@code ControlPlaneAccessGuard}).
 * Any extraction failure fails closed: {@code authenticated=false,
 * capabilities={}}. Identity is never taken from request parameters, never
 * defaulted, and never fails open.</p>
 */
@Service
public class ControlPlaneAccessService {

    public static final List<String> CONTROL_PLANE_CAPABILITIES = List.of(
            // Broad authorities are included because several write endpoints
            // still enforce these exact codes. UI mutation gates must match
            // backend authority exactly; granular mirrors are not equivalent
            // in the reverse direction (a granular grant need not imply broad).
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
        if (tenantId == null || userId == null) {
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
