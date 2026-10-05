package com.sanad.platform.subscription.billing.api;

import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.billing.application.BillingReconciliationService;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import com.sanad.platform.subscription.read.ExecutiveBillingQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * R0C13 / R13-G07 controlled billing operator surface.
 *
 * <p>This controller is additive. Legacy executive billing routes remain
 * unchanged for compatibility. New G07 routes require granular billing
 * capabilities in addition to the canonical control-plane boundary.</p>
 */
@RestController
@RequestMapping("/api/v1/executive/billing")
public class BillingOperatorController {

    private final ControlPlaneAccessGuard accessGuard;
    private final ExecutiveBillingQueryService billingQueryService;
    private final BillingReconciliationService reconciliationService;
    private final BillingPaymentProvider paymentProvider;
    private final String providerMode;

    public BillingOperatorController(
            ControlPlaneAccessGuard accessGuard,
            ExecutiveBillingQueryService billingQueryService,
            BillingReconciliationService reconciliationService,
            BillingPaymentProvider paymentProvider,
            @Value("${sanad.subscription.billing.provider.mode:DISABLED}") String providerMode
    ) {
        this.accessGuard = accessGuard;
        this.billingQueryService = billingQueryService;
        this.reconciliationService = reconciliationService;
        this.paymentProvider = paymentProvider;
        this.providerMode = normalizeMode(providerMode);
    }

    @GetMapping("/v3")
    @RequireCapability("BILLING.READ")
    public ResponseEntity<List<ExecutiveBillingQueryService.BillingRow>> billing(
            Authentication authentication,
            @RequestParam UUID tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(billingQueryService.list(tenantId));
    }

    @PostMapping("/reconciliation")
    @RequireCapability("BILLING.RECONCILE")
    public ResponseEntity<BillingReconciliationService.ReconciliationResult> reconcile(
            Authentication authentication,
            @Valid @RequestBody ReconciliationRequest request
    ) {
        accessGuard.require(authentication);
        UUID actorId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(reconciliationService.reconcileReadOnly(
                request.tenantId(),
                request.idempotencyKey(),
                actorId));
    }

    @GetMapping("/provider/readiness")
    @RequireCapability("BILLING.PROVIDER_ADMIN")
    public ResponseEntity<ProviderReadiness> providerReadiness(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(new ProviderReadiness(
                paymentProvider.providerCode(),
                providerMode,
                "LIVE".equals(providerMode)));
    }

    public record ReconciliationRequest(
            @NotNull UUID tenantId,
            @NotBlank String idempotencyKey
    ) {}

    /**
     * Sanitized provider state only. No credentials, webhook secrets, customer
     * references or payment references are exposed by this diagnostic.
     */
    public record ProviderReadiness(
            String providerCode,
            String mode,
            boolean liveCollectionEnabled
    ) {}

    private static String normalizeMode(String value) {
        if (value == null || value.isBlank()) {
            return "DISABLED";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
