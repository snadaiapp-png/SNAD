package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
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
 * <p>Pricing is country- and currency-customizable (e.g. SA→SAR, AE→AED,
 * GLOBAL→USD); the mapping is catalog data, never hardcoded rules.
 */
@RestController
@RequestMapping("/api/v1/executive")
public class PriceController {

    private final ControlPlaneAccessGuard accessGuard;
    private final PriceService priceService;
    private final CountryCurrencyRepository countryCurrencies;
    private final PlatformAuditService auditService;

    public PriceController(ControlPlaneAccessGuard accessGuard,
                           PriceService priceService,
                           CountryCurrencyRepository countryCurrencies,
                           PlatformAuditService auditService) {
        this.accessGuard = accessGuard;
        this.priceService = priceService;
        this.countryCurrencies = countryCurrencies;
        this.auditService = auditService;
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
