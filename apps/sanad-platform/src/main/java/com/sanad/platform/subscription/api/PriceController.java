package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.plan.PlanVersionEntity;
import com.sanad.platform.subscription.plan.PlanVersionService;
import com.sanad.platform.subscription.pricing.CountryCurrencyRepository;
import com.sanad.platform.subscription.pricing.PriceEntity;
import com.sanad.platform.subscription.pricing.PriceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Executive API for prices and the country/currency catalog.
 *
 * <p>Pricing is country- and currency-customizable; the mapping is catalog
 * data, never hardcoded rules. Nested plan-version routes validate that the
 * version belongs to the path plan before any price read/write is delegated.</p>
 */
@RestController
@RequestMapping("/api/v1/executive")
public class PriceController {

    private final ControlPlaneAccessGuard accessGuard;
    private final PriceService priceService;
    private final CountryCurrencyRepository countryCurrencies;
    private final PlatformAuditService auditService;
    private final PlanVersionService planVersionService;

    public PriceController(ControlPlaneAccessGuard accessGuard,
                           PriceService priceService,
                           CountryCurrencyRepository countryCurrencies,
                           PlatformAuditService auditService,
                           PlanVersionService planVersionService) {
        this.accessGuard = accessGuard;
        this.priceService = priceService;
        this.countryCurrencies = countryCurrencies;
        this.auditService = auditService;
        this.planVersionService = planVersionService;
    }

    @GetMapping("/country-currencies")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ScpDtos.CountryCurrencyResponse>> listCountryCurrencies(
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(countryCurrencies.findAll().stream()
                .map(cc -> new ScpDtos.CountryCurrencyResponse(
                        cc.countryCode(), cc.currencyCode(), cc.isDefault()))
                .toList());
    }

    @GetMapping("/plans/{planId}/versions/{versionId}/prices")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ScpDtos.PriceResponse>> listPlanVersionPrices(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            Authentication authentication) {
        accessGuard.require(authentication);
        requireVersionOwnership(planId, versionId);
        return ResponseEntity.ok(priceService.listForPlanVersion(versionId).stream()
                .map(ScpDtos.PriceResponse::from).toList());
    }

    @PostMapping("/plans/{planId}/versions/{versionId}/prices")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ScpDtos.PriceResponse> createPlanVersionPrice(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            @Valid @RequestBody ScpDtos.PriceRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        requireVersionOwnership(planId, versionId);
        PriceEntity created = priceService.createForPlanVersion(versionId, fromRequest(request));
        auditService.success(authentication, null, "PRICE_CREATE",
                "price", created.getId().toString(), null, null, created);
        return ResponseEntity.ok(ScpDtos.PriceResponse.from(created));
    }

    @GetMapping("/products/{productId}/prices")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ScpDtos.PriceResponse>> listProductPrices(
            @PathVariable UUID productId,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(priceService.listForProduct(productId).stream()
                .map(ScpDtos.PriceResponse::from).toList());
    }

    @PostMapping("/products/{productId}/prices")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ScpDtos.PriceResponse> createProductPrice(
            @PathVariable UUID productId,
            @Valid @RequestBody ScpDtos.PriceRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        PriceEntity created = priceService.createForProduct(productId, fromRequest(request));
        auditService.success(authentication, null, "PRICE_CREATE",
                "price", created.getId().toString(), null, null, created);
        return ResponseEntity.ok(ScpDtos.PriceResponse.from(created));
    }

    private PlanVersionEntity requireVersionOwnership(UUID planId, UUID versionId) {
        PlanVersionEntity version = planVersionService.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan version: " + versionId));
        if (!planId.equals(version.getPlanId())) {
            throw new IllegalArgumentException(
                    "Plan version " + versionId + " does not belong to plan " + planId);
        }
        return version;
    }

    private static PriceEntity fromRequest(ScpDtos.PriceRequest request) {
        PriceEntity entity = new PriceEntity();
        entity.setPriceModel(request.priceModel());
        entity.setCountryCode(request.countryCode());
        entity.setCurrencyCode(request.currencyCode());
        entity.setBillingInterval(request.billingInterval());
        entity.setBaseAmountMinor(request.baseAmountMinor() == null ? 0 : request.baseAmountMinor());
        entity.setUnitAmountMinor(request.unitAmountMinor());
        entity.setTiersJson(request.tiersJson());
        entity.setMinAmountMinor(request.minAmountMinor());
        entity.setMaxAmountMinor(request.maxAmountMinor());
        return entity;
    }
}
