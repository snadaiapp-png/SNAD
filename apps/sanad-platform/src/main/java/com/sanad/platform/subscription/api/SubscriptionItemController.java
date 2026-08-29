package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.item.SubscriptionItemEntity;
import com.sanad.platform.subscription.item.SubscriptionItemService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Executive API for subscription items — the 1..N billable lines that make a
 * subscription multi-product (application plans, add-ons, metered products).
 */
@RestController
@RequestMapping("/api/v1/executive")
public class SubscriptionItemController {

    private final ControlPlaneAccessGuard accessGuard;
    private final SubscriptionItemService itemService;
    private final PlatformAuditService auditService;

    public SubscriptionItemController(ControlPlaneAccessGuard accessGuard,
                                      SubscriptionItemService itemService,
                                      PlatformAuditService auditService) {
        this.accessGuard = accessGuard;
        this.itemService = itemService;
        this.auditService = auditService;
    }

    @GetMapping("/subscriptions/{subscriptionId}/items")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ScpDtos.SubscriptionItemResponse>> listItems(
            @PathVariable UUID subscriptionId,
            @RequestParam(name = "activeOnly", required = false, defaultValue = "false")
            boolean activeOnly,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(itemService.listItems(subscriptionId, activeOnly).stream()
                .map(ScpDtos.SubscriptionItemResponse::from).toList());
    }

    @PostMapping("/subscriptions/{subscriptionId}/items")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ScpDtos.SubscriptionItemResponse> addItem(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody ScpDtos.AddSubscriptionItemRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        SubscriptionItemEntity created = itemService.addItem(
                subscriptionId,
                request.itemType(),
                request.applicationId(),
                request.productId(),
                request.planId(),
                request.planVersionId(),
                request.quantity() == null ? 1 : request.quantity(),
                request.unitAmountMinor(),
                request.currencyCode());
        auditService.success(authentication, created.getTenantId(), "SUBSCRIPTION_ITEM_ADD",
                "subscription_item", created.getId().toString(), null, null, created);
        return ResponseEntity.ok(ScpDtos.SubscriptionItemResponse.from(created));
    }

    @PatchMapping("/subscriptions/{subscriptionId}/items/{itemId}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ScpDtos.SubscriptionItemResponse> updateItem(
            @PathVariable UUID subscriptionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ScpDtos.UpdateSubscriptionItemRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        SubscriptionItemEntity before = itemService.listItems(subscriptionId, false).stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Item " + itemId + " is not part of subscription " + subscriptionId));
        String action = request.action() == null ? "" : request.action().toUpperCase();
        switch (action) {
            case "CANCEL" -> itemService.cancelItem(itemId);
            case "SET_QUANTITY" -> itemService.updateQuantity(
                    itemId, request.quantity() == null ? before.getQuantity() : request.quantity());
            default -> throw new IllegalArgumentException("Unknown action: " + request.action());
        }
        SubscriptionItemEntity after = itemService.listItems(subscriptionId, false).stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElse(before);
        auditService.success(authentication, before.getTenantId(), "SUBSCRIPTION_ITEM_" + action,
                "subscription_item", itemId.toString(), null, before, after);
        return ResponseEntity.ok(ScpDtos.SubscriptionItemResponse.from(after));
    }
}
