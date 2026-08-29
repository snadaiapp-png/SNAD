package com.sanad.platform.subscription.api;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.read.ExecutiveOverviewService;
import com.sanad.platform.subscription.read.PageResponse;
import com.sanad.platform.subscription.read.SubscriptionDetailService;
import com.sanad.platform.subscription.read.SubscriptionGridQueryService;
import com.sanad.platform.subscription.read.TenantDirectoryQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Executive read models — server-side overview metrics, paginated tenant
 * directory, subscription grid and subscription detail. All are additive,
 * versioned routes ({@code /overview}, {@code /tenants/v2},
 * {@code /subscriptions/v2}); the legacy endpoints stay untouched.
 */
@RestController
@RequestMapping("/api/v1/executive")
public class ExecutiveReadController {

    private final ControlPlaneAccessGuard accessGuard;
    private final ExecutiveOverviewService overviewService;
    private final TenantDirectoryQueryService tenantDirectoryQueryService;
    private final SubscriptionGridQueryService subscriptionGridQueryService;
    private final SubscriptionDetailService subscriptionDetailService;

    public ExecutiveReadController(ControlPlaneAccessGuard accessGuard,
                                   ExecutiveOverviewService overviewService,
                                   TenantDirectoryQueryService tenantDirectoryQueryService,
                                   SubscriptionGridQueryService subscriptionGridQueryService,
                                   SubscriptionDetailService subscriptionDetailService) {
        this.accessGuard = accessGuard;
        this.overviewService = overviewService;
        this.tenantDirectoryQueryService = tenantDirectoryQueryService;
        this.subscriptionGridQueryService = subscriptionGridQueryService;
        this.subscriptionDetailService = subscriptionDetailService;
    }

    @GetMapping("/overview")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<ExecutiveOverviewService.Overview> overview(Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(overviewService.overview());
    }

    @GetMapping("/tenants/v2")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<PageResponse<TenantDirectoryQueryService.TenantRow>> tenants(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "country", required = false) String country,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "name") String sort,
            @RequestParam(name = "direction", defaultValue = "ASC") String direction,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(tenantDirectoryQueryService
                .search(search, status, country, page, size, sort, direction));
    }

    @GetMapping("/subscriptions/v2")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<PageResponse<SubscriptionGridQueryService.SubscriptionRow>> subscriptions(
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "country", required = false) String country,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "trialOnly", defaultValue = "false") boolean trialOnly,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "created_at") String sort,
            @RequestParam(name = "direction", defaultValue = "DESC") String direction,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(subscriptionGridQueryService
                .search(tenantId, status, country, search, trialOnly, page, size, sort, direction));
    }

    @GetMapping("/subscriptions/{id}/detail")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<SubscriptionDetailService.SubscriptionDetail> detail(
            @PathVariable UUID id,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(subscriptionDetailService.detail(id));
    }
}
