package com.sanad.platform.access.api;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.security.tenant.TenantResolver;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/evaluation")
public class CapabilityEvaluationController {

    private final CapabilityEvaluationService evaluationService;
    private final com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    public CapabilityEvaluationController(CapabilityEvaluationService evaluationService,
            com.sanad.platform.security.tenant.TenantResolver tenantResolver) {
        this.evaluationService = evaluationService;
        this.tenantResolver = tenantResolver;
    }

    @RequireCapability("ACCESS.EVALUATE")
    @GetMapping
    ResponseEntity<AccessDecisionResponse> evaluate(
            @RequestParam UUID tenantId,
            @RequestParam UUID userId,
            @RequestParam String capabilityCode,
            @RequestParam(required = false) UUID organizationId) {
        return ResponseEntity.ok(evaluationService.evaluate(
                tenantId, userId, capabilityCode, organizationId));
    }
}
