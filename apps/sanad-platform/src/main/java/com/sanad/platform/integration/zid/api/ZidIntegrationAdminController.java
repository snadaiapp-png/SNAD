package com.sanad.platform.integration.zid.api;

import com.sanad.platform.integration.zid.application.ZidCustomerSyncService;
import com.sanad.platform.integration.zid.application.ZidIntegrationFacade;
import com.sanad.platform.integration.zid.application.ZidInventorySyncService;
import com.sanad.platform.integration.zid.application.ZidOrderSyncService;
import com.sanad.platform.integration.zid.application.ZidProductSyncService;
import com.sanad.platform.integration.zid.application.ZidReconciliationService;
import com.sanad.platform.integration.zid.application.ZidSyncAuditService;
import com.sanad.platform.integration.zid.application.ZidTenantBindingService;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.dto.ZidConnectionStatusResponse;
import com.sanad.platform.integration.zid.dto.ZidLinkStartResponse;
import com.sanad.platform.integration.zid.dto.ZidSyncSummaryResponse;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant-scoped admin API for the Zid integration ("ZidIntegrationAdmin"
 * in the integration design). Every route derives the tenant EXCLUSIVELY
 * from the authenticated principal (JWT → details map → RLS GUC) — no
 * route accepts a tenant id parameter from the client.
 *
 * <p>Permission model (deny-by-default):</p>
 * <ul>
 *   <li>Reads: {@code ZID_INTEGRATION_VIEW}</li>
 *   <li>Mutations (link/unlink/sync/reconcile): {@code ZID_INTEGRATION_MANAGE}</li>
 * </ul>
 *
 * <p>The callback route is JWT-exempt because the merchant's browser
 * lands on it directly from Zid's consent screen. It carries NO secret
 * material (only the single-use code + state) and completes the flow
 * against the state row minted during an authenticated
 * {@code /link/start} call, so tenant attribution remains trusted.</p>
 */
@RestController
@RequestMapping("/api/v1/integrations/zid")
public class ZidIntegrationAdminController {

    private static final Logger log = LoggerFactory.getLogger(ZidIntegrationAdminController.class);

    private final ZidTenantBindingService bindingService;
    private final ZidProductSyncService productSyncService;
    private final ZidOrderSyncService orderSyncService;
    private final ZidInventorySyncService inventorySyncService;
    private final ZidCustomerSyncService customerSyncService;
    private final ZidReconciliationService reconciliationService;
    private final ZidIntegrationFacade facade;
    private final ZidSyncAuditService auditService;

    public ZidIntegrationAdminController(ZidTenantBindingService bindingService,
                                         ZidProductSyncService productSyncService,
                                         ZidOrderSyncService orderSyncService,
                                         ZidInventorySyncService inventorySyncService,
                                         ZidCustomerSyncService customerSyncService,
                                         ZidReconciliationService reconciliationService,
                                         ZidIntegrationFacade facade,
                                         ZidSyncAuditService auditService) {
        this.bindingService = bindingService;
        this.productSyncService = productSyncService;
        this.orderSyncService = orderSyncService;
        this.inventorySyncService = inventorySyncService;
        this.customerSyncService = customerSyncService;
        this.reconciliationService = reconciliationService;
        this.facade = facade;
        this.auditService = auditService;
    }

    // ---------------------------------------------------------
    // Configuration status (no secrets ever returned)
    // ---------------------------------------------------------
    @GetMapping("/configuration")
    @RequireCapability("ZID_INTEGRATION_VIEW")
    public ResponseEntity<Map<String, Object>> configuration(Authentication authentication) {
        return ResponseEntity.ok(auditService.configurationStatus());
    }

    // ---------------------------------------------------------
    // Link lifecycle
    // ---------------------------------------------------------
    @PostMapping("/link/start")
    @RequireCapability("ZID_INTEGRATION_MANAGE")
    public ResponseEntity<ZidLinkStartResponse> startLink(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        ZidTenantBindingService.LinkStart link = bindingService.beginLink(tenantId, userId);
        return ResponseEntity.accepted().body(new ZidLinkStartResponse(
                link.authorizationUrl(), link.state(), link.expiresAt()));
    }

    /**
     * OAuth callback (JWT-exempt; see class comment). Completes the
     * binding then redirects the merchant's browser to the SPA route.
     * The state value itself is never echoed into the redirect target.
     */
    @GetMapping("/link/callback")
    public RedirectView linkCallback(@RequestParam(name = "code", required = false) String code,
                                     @RequestParam(name = "state", required = false) String state,
                                     @RequestParam(name = "error", required = false) String error) {
        String redirectUrl = facade.linkRedirectUrl();
        if (error != null && !error.isBlank()) {
            log.info("Zid link callback received merchant error (not logged in detail)");
            return new RedirectView(redirectUrl + "?link=merchant_denied");
        }
        try {
            if (code == null || code.isBlank() || state == null || state.isBlank()) {
                return new RedirectView(redirectUrl + "?link=invalid_request");
            }
            // The state hash uniquely determines the tenant; completeLink enforces it.
            bindingService.completeLink(facade.tenantForState(state), code, state);
            return new RedirectView(redirectUrl + "?link=success");
        } catch (ZidIntegrationException e) {
            log.info("Zid link callback failed: {}", e.getMessage());
            return new RedirectView(redirectUrl + "?link=" + switch (e.reason()) {
                case INVALID_LINK_STATE -> "invalid_state";
                case STORE_ALREADY_BOUND -> "store_already_bound";
                case INVALID_AUTHORIZATION_CODE -> "authorization_failed";
                case STORE_IDENTITY_UNRESOLVED -> "identity_unresolved";
                default -> "failed";
            });
        }
    }

    @GetMapping("/connection")
    @RequireCapability("ZID_INTEGRATION_VIEW")
    public ResponseEntity<ZidConnectionStatusResponse> connection(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return facade.connectionStatus(tenantId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(ZidConnectionStatusResponse.notLinked()));
    }

    @DeleteMapping("/connection")
    @RequireCapability("ZID_INTEGRATION_MANAGE")
    public ResponseEntity<Map<String, Object>> unlink(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        boolean removed = facade.unlink(tenantId);
        return ResponseEntity.ok(Map.of("unlinked", removed));
    }

    // ---------------------------------------------------------
    // Manual syncs
    // ---------------------------------------------------------
    @PostMapping("/sync/products")
    @RequireCapability("ZID_INTEGRATION_MANAGE")
    public ResponseEntity<?> syncProducts(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        ZidProductSyncService.SyncResult result = facade.syncProductsForTenant(tenantId);
        return ResponseEntity.ok(Map.of("processed", result.processed(), "failed", result.failed()));
    }

    @PostMapping("/sync/orders")
    @RequireCapability("ZID_INTEGRATION_MANAGE")
    public ResponseEntity<?> syncOrders(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        ZidOrderSyncService.SyncResult result = facade.syncOrdersForTenant(tenantId);
        return ResponseEntity.ok(Map.of("processed", result.processed(), "failed", result.failed()));
    }

    @PostMapping("/sync/inventory")
    @RequireCapability("ZID_INTEGRATION_MANAGE")
    public ResponseEntity<?> syncInventory(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        ZidInventorySyncService.InventoryResult result = facade.syncInventoryForTenant(tenantId);
        return ResponseEntity.ok(Map.of(
                "stocksMirrored", result.stocksMirrored(),
                "productsProcessed", result.productsProcessed(),
                "failed", result.failed()));
    }

    @PostMapping("/sync/customers")
    @RequireCapability("ZID_INTEGRATION_MANAGE")
    public ResponseEntity<?> syncCustomers(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        String result = facade.syncCustomersForTenant(tenantId);
        return ResponseEntity.ok(Map.of("result", result));
    }

    // ---------------------------------------------------------
    // Observability
    // ---------------------------------------------------------
    @GetMapping("/summary")
    @RequireCapability("ZID_INTEGRATION_VIEW")
    public ResponseEntity<ZidSyncSummaryResponse> summary(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(facade.summary(tenantId));
    }

    @GetMapping("/audit")
    @RequireCapability("ZID_INTEGRATION_VIEW")
    public ResponseEntity<List<Map<String, Object>>> audit(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(facade.recentAudit(tenantId));
    }

    @GetMapping("/webhook-events")
    @RequireCapability("ZID_INTEGRATION_VIEW")
    public ResponseEntity<List<Map<String, Object>>> webhookEvents(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(facade.recentWebhookEvents(tenantId));
    }

    @PostMapping("/reconciliation")
    @RequireCapability("ZID_INTEGRATION_MANAGE")
    public ResponseEntity<?> reconcile(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(facade.reconcileForTenant(tenantId));
    }

    // ---------------------------------------------------------
    // Error mapping
    // ---------------------------------------------------------
    @ExceptionHandler(ZidIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrationError(ZidIntegrationException e) {
        HttpStatus status = switch (e.reason()) {
            case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case STORE_ALREADY_BOUND -> HttpStatus.CONFLICT;
            case INVALID_LINK_STATE, INVALID_AUTHORIZATION_CODE, INVALID_WEBHOOK_PAYLOAD -> HttpStatus.BAD_REQUEST;
            case TRUSTED_BINDING_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TOKEN_REJECTED -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(Map.of(
                "code", e.reason().name(),
                "message", e.getMessage()));
    }
}
