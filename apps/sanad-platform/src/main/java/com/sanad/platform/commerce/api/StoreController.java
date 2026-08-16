package com.sanad.platform.commerce.api;

import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.application.CartService;
import com.sanad.platform.commerce.application.CheckoutService;
import com.sanad.platform.commerce.application.OrderService;
import com.sanad.platform.commerce.application.ProductService;
import com.sanad.platform.commerce.application.StoreDomainService;
import com.sanad.platform.commerce.application.StoreService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;

/**
 * Stores/E-Commerce Management API (v20260816.5).
 *
 * <p>All management endpoints are tenant-scoped (resolved via
 * {@link com.sanad.platform.security.SecurityContextUtils#tenantId(Authentication)})
 * and protected by {@code @RequireCapability} ({@code ECOMMERCE.VIEW / WRITE /
 * PUBLISH / ADMIN / ORDER_MANAGE}). Mirrors the {@code WebsiteController}
 * conventions.
 */
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;
    private final ProductService productService;
    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final StoreDomainService domainService;

    public StoreController(StoreService storeService, ProductService productService,
                           CartService cartService, CheckoutService checkoutService,
                           OrderService orderService, StoreDomainService domainService) {
        this.storeService = storeService;
        this.productService = productService;
        this.cartService = cartService;
        this.checkoutService = checkoutService;
        this.orderService = orderService;
        this.domainService = domainService;
    }

    // ===== Stores =====
    @PostMapping
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<StoreResponse> createStore(Authentication auth, @RequestBody CreateStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeService.create(tenantId(auth), request, auth));
    }

    @GetMapping
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<List<StoreResponse>> listStores(Authentication auth) {
        return ResponseEntity.ok(storeService.list(tenantId(auth)));
    }

    @GetMapping("/{id}")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<StoreResponse> getStore(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(storeService.get(tenantId(auth), id));
    }

    @PutMapping("/{id}")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<StoreResponse> updateStore(Authentication auth, @PathVariable UUID id,
                                                     @RequestBody UpdateStoreRequest request) {
        return ResponseEntity.ok(storeService.update(tenantId(auth), id, request, auth));
    }

    @PostMapping("/{id}/activate")
    @RequireCapability("ECOMMERCE.ADMIN")
    public ResponseEntity<StoreResponse> activateStore(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(storeService.activate(tenantId(auth), id, auth));
    }

    @PostMapping("/{id}/suspend")
    @RequireCapability("ECOMMERCE.ADMIN")
    public ResponseEntity<StoreResponse> suspendStore(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(storeService.suspend(tenantId(auth), id, auth));
    }

    @PostMapping("/{id}/archive")
    @RequireCapability("ECOMMERCE.ADMIN")
    public ResponseEntity<StoreResponse> archiveStore(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(storeService.archive(tenantId(auth), id, auth));
    }

    @GetMapping("/{id}/summary")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<StoreSummary> summarizeStore(Authentication auth, @PathVariable UUID id) {
        // Summary is tenant-wide (not per-store); id is validated implicitly by storeService.get
        storeService.get(tenantId(auth), id);
        return ResponseEntity.ok(storeService.summarize(tenantId(auth)));
    }

    // ===== Products =====
    @GetMapping("/{id}/products")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<List<ProductResponse>> listProducts(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(productService.list(tenantId(auth), id));
    }

    @PostMapping("/{id}/products")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<ProductResponse> createProduct(Authentication auth, @PathVariable UUID id,
                                                          @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.create(tenantId(auth), id, request, auth));
    }

    @GetMapping("/{id}/products/{pid}")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<ProductResponse> getProduct(Authentication auth, @PathVariable UUID id,
                                                       @PathVariable UUID pid) {
        return ResponseEntity.ok(productService.get(tenantId(auth), id, pid));
    }

    @PutMapping("/{id}/products/{pid}")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<ProductResponse> updateProduct(Authentication auth, @PathVariable UUID id,
                                                          @PathVariable UUID pid,
                                                          @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(tenantId(auth), id, pid, request, auth));
    }

    @PostMapping("/{id}/products/{pid}/publish")
    @RequireCapability("ECOMMERCE.PUBLISH")
    public ResponseEntity<ProductResponse> publishProduct(Authentication auth, @PathVariable UUID id,
                                                            @PathVariable UUID pid) {
        return ResponseEntity.ok(productService.publish(tenantId(auth), id, pid, auth));
    }

    @PostMapping("/{id}/products/{pid}/unpublish")
    @RequireCapability("ECOMMERCE.PUBLISH")
    public ResponseEntity<ProductResponse> unpublishProduct(Authentication auth, @PathVariable UUID id,
                                                              @PathVariable UUID pid) {
        return ResponseEntity.ok(productService.unpublish(tenantId(auth), id, pid, auth));
    }

    @PostMapping("/{id}/products/{pid}/variants")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<VariantResponse> createVariant(Authentication auth, @PathVariable UUID id,
                                                          @PathVariable UUID pid,
                                                          @RequestBody CreateVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createVariant(tenantId(auth), id, pid, request, auth));
    }

    @GetMapping("/{id}/products/{pid}/variants")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<List<VariantResponse>> listVariants(Authentication auth, @PathVariable UUID id,
                                                               @PathVariable UUID pid) {
        return ResponseEntity.ok(productService.listVariants(tenantId(auth), id, pid));
    }

    // ===== Collections =====
    @GetMapping("/{id}/collections")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<List<CollectionResponse>> listCollections(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(productService.listCollections(tenantId(auth), id));
    }

    @PostMapping("/{id}/collections")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<CollectionResponse> createCollection(Authentication auth, @PathVariable UUID id,
                                                                @RequestBody CreateCollectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createCollection(tenantId(auth), id, request, auth));
    }

    // ===== Prices =====
    @PostMapping("/{id}/products/{pid}/prices")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<PriceResponse> createPrice(Authentication auth, @PathVariable UUID id,
                                                       @PathVariable UUID pid,
                                                       @RequestBody CreatePriceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createPrice(tenantId(auth), id, pid, request, auth));
    }

    @GetMapping("/{id}/products/{pid}/prices")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<List<PriceResponse>> listPrices(Authentication auth, @PathVariable UUID id,
                                                            @PathVariable UUID pid) {
        return ResponseEntity.ok(productService.listPrices(tenantId(auth), id, pid));
    }

    // ===== Carts =====
    @PostMapping("/{id}/carts")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<CartResponse> createCart(Authentication auth, @PathVariable UUID id,
                                                    @RequestBody CreateCartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartService.create(tenantId(auth), id, request, auth));
    }

    @GetMapping("/{id}/carts/{cartId}")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<CartResponse> getCart(Authentication auth, @PathVariable UUID id,
                                                 @PathVariable UUID cartId) {
        return ResponseEntity.ok(cartService.calculateTotals(tenantId(auth), id, cartId));
    }

    @PostMapping("/{id}/carts/{cartId}/items")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<CartResponse> addCartItem(Authentication auth, @PathVariable UUID id,
                                                      @PathVariable UUID cartId,
                                                      @RequestBody AddCartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(tenantId(auth), id, cartId, request, auth));
    }

    @PutMapping("/{id}/carts/{cartId}/items/{itemId}")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<CartResponse> updateCartItem(Authentication auth, @PathVariable UUID id,
                                                         @PathVariable UUID cartId,
                                                         @PathVariable UUID itemId,
                                                         @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(tenantId(auth), id, cartId, itemId, request, auth));
    }

    @DeleteMapping("/{id}/carts/{cartId}/items/{itemId}")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<CartResponse> removeCartItem(Authentication auth, @PathVariable UUID id,
                                                         @PathVariable UUID cartId,
                                                         @PathVariable UUID itemId) {
        return ResponseEntity.ok(cartService.removeItem(tenantId(auth), id, cartId, itemId, auth));
    }

    @DeleteMapping("/{id}/carts/{cartId}")
    @RequireCapability("ECOMMERCE.WRITE")
    public ResponseEntity<CartResponse> clearCart(Authentication auth, @PathVariable UUID id,
                                                    @PathVariable UUID cartId) {
        return ResponseEntity.ok(cartService.clear(tenantId(auth), id, cartId, auth));
    }

    // ===== Checkout =====
    @PostMapping("/{id}/checkout")
    @RequireCapability("ECOMMERCE.ORDER_MANAGE")
    public ResponseEntity<CheckoutResponse> checkout(Authentication auth, @PathVariable UUID id,
                                                     @RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checkoutService.checkout(tenantId(auth), id, request, auth));
    }

    // ===== Orders =====
    @GetMapping("/{id}/orders")
    @RequireCapability("ECOMMERCE.ORDER_MANAGE")
    public ResponseEntity<List<OrderResponse>> listOrders(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.list(tenantId(auth), id));
    }

    @GetMapping("/{id}/orders/{orderId}")
    @RequireCapability("ECOMMERCE.ORDER_MANAGE")
    public ResponseEntity<OrderResponse> getOrder(Authentication auth, @PathVariable UUID id,
                                                    @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.get(tenantId(auth), id, orderId));
    }

    @GetMapping("/{id}/orders/{orderId}/items")
    @RequireCapability("ECOMMERCE.ORDER_MANAGE")
    public ResponseEntity<List<OrderItemResponse>> listOrderItems(Authentication auth, @PathVariable UUID id,
                                                                    @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getItems(tenantId(auth), id, orderId));
    }

    @PostMapping("/{id}/orders/{orderId}/cancel")
    @RequireCapability("ECOMMERCE.ORDER_MANAGE")
    public ResponseEntity<OrderResponse> cancelOrder(Authentication auth, @PathVariable UUID id,
                                                       @PathVariable UUID orderId,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(orderService.cancel(tenantId(auth), id, orderId, auth));
    }

    // ===== Domains =====
    @GetMapping("/{id}/domains")
    @RequireCapability("ECOMMERCE.VIEW")
    public ResponseEntity<List<DomainResponse>> listDomains(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(domainService.list(tenantId(auth), id));
    }

    @PostMapping("/{id}/domains")
    @RequireCapability("ECOMMERCE.ADMIN")
    public ResponseEntity<DomainResponse> registerDomain(Authentication auth, @PathVariable UUID id,
                                                          @RequestBody CreateDomainRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.registerCustomDomain(tenantId(auth), id, request, auth));
    }

    @PostMapping("/{id}/domains/{domainId}/verify")
    @RequireCapability("ECOMMERCE.ADMIN")
    public ResponseEntity<DomainResponse> verifyDomain(Authentication auth, @PathVariable UUID id,
                                                        @PathVariable UUID domainId,
                                                        @RequestBody Map<String, String> body) {
        String token = body != null ? body.get("verificationToken") : null;
        return ResponseEntity.ok(domainService.verify(tenantId(auth), id, domainId, token, auth));
    }

    @PostMapping("/{id}/domains/{domainId}/activate")
    @RequireCapability("ECOMMERCE.ADMIN")
    public ResponseEntity<DomainResponse> activateDomain(Authentication auth, @PathVariable UUID id,
                                                          @PathVariable UUID domainId) {
        return ResponseEntity.ok(domainService.activate(tenantId(auth), id, domainId, auth));
    }
}
