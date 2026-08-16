package com.sanad.platform.commerce.api;

import com.sanad.platform.commerce.domain.CommerceDomain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for the Commerce (Stores/E-Commerce) platform (v20260816.5).
 *
 * <p>Records are immutable and used as the request/response payloads for
 * {@link com.sanad.platform.commerce.api.StoreController} and
 * {@link com.sanad.platform.commerce.api.PublicStoreController}.
 */
public final class CommerceDtos {

    private CommerceDtos() {}

    // ===== Store =====
    public record CreateStoreRequest(String name, String code, String slug,
                                      String defaultLocale, String defaultCurrency,
                                      Map<String, Object> settings) {}
    public record UpdateStoreRequest(String name, String defaultLocale,
                                      String defaultCurrency, Map<String, Object> settings) {}
    public record StoreResponse(UUID id, UUID tenantId, String name, String code, String slug,
                                 StoreStatus status, String defaultLocale, String defaultCurrency,
                                 boolean isPrimary, Map<String, Object> settings,
                                 long version, Instant createdAt, Instant updatedAt) {}
    public record StoreSummary(int totalStores, int activeStores, int draftStores,
                                int suspendedStores, int archivedStores, int totalProducts,
                                int publishedProducts, int totalCollections, int activeCarts,
                                int totalOrders, int paidOrders) {}

    // ===== Product =====
    public record CreateProductRequest(String name, String slug, String sku, String description,
                                        ProductType productType, Map<String, Object> attributes) {}
    public record UpdateProductRequest(String name, String sku, String description,
                                        ProductType productType, Map<String, Object> attributes,
                                        Long expectedVersion) {}
    public record ProductResponse(UUID id, UUID tenantId, UUID storeId, String name, String slug,
                                    String sku, String description, ProductStatus status,
                                    ProductType productType, Instant publishedAt,
                                    long version, Instant createdAt, Instant updatedAt) {}

    // ===== Variant =====
    public record CreateVariantRequest(String sku, String name, Map<String, Object> options) {}
    public record VariantResponse(UUID id, UUID tenantId, UUID productId, String sku, String name,
                                    Map<String, Object> options, VariantStatus status,
                                    long version, Instant createdAt, Instant updatedAt) {}

    // ===== Collection =====
    public record CreateCollectionRequest(String name, String slug, String description,
                                            Integer sortOrder, List<UUID> productIds) {}
    public record CollectionResponse(UUID id, UUID tenantId, UUID storeId, String name, String slug,
                                       String description, CollectionStatus status, int sortOrder,
                                       List<UUID> productIds, long version,
                                       Instant createdAt, Instant updatedAt) {}

    // ===== Price =====
    public record CreatePriceRequest(UUID variantId, String currency, BigDecimal amount,
                                       BigDecimal compareAtAmount, Instant validFrom, Instant validTo) {}
    public record PriceResponse(UUID id, UUID tenantId, UUID storeId, UUID productId, UUID variantId,
                                  String currency, BigDecimal amount, BigDecimal compareAtAmount,
                                  Instant validFrom, Instant validTo, PriceStatus status,
                                  long version, Instant createdAt, Instant updatedAt) {}

    // ===== Cart =====
    public record CreateCartRequest(String customerRef, String currency) {}
    public record CartResponse(UUID id, UUID tenantId, UUID storeId, String customerRef,
                                String currency, CartStatus status, BigDecimal subtotal,
                                Instant expiresAt, List<CartItemResponse> items,
                                long version, Instant createdAt, Instant updatedAt) {}
    public record CartItemResponse(UUID id, UUID cartId, UUID productId, UUID variantId,
                                     int quantity, BigDecimal unitPrice, String currency,
                                     BigDecimal lineTotal, Instant createdAt, Instant updatedAt) {}
    public record AddCartItemRequest(UUID productId, UUID variantId, int quantity) {}
    public record UpdateCartItemRequest(int quantity) {}

    // ===== Checkout =====
    public record CheckoutRequest(UUID cartId, String idempotencyKey,
                                    String customerEmail, String customerName,
                                    UUID customerContactId, Map<String, Object> metadata) {}
    public record CheckoutResponse(UUID orderId, String orderNumber, String paymentRef,
                                     String customerRef, String currency, BigDecimal subtotal,
                                     BigDecimal discountTotal, BigDecimal taxTotal,
                                     BigDecimal shippingTotal, BigDecimal grandTotal,
                                     String paymentStatus, String fulfillmentStatus,
                                     String orderStatus, Instant createdAt) {}

    // ===== Order =====
    public record OrderResponse(UUID id, UUID tenantId, UUID storeId, String orderNumber,
                                  UUID cartId, String customerReference, Map<String, Object> customerSnapshot,
                                  String currency, BigDecimal subtotal, BigDecimal discountTotal,
                                  BigDecimal taxTotal, BigDecimal shippingTotal, BigDecimal grandTotal,
                                  PaymentStatus paymentStatus, FulfillmentStatus fulfillmentStatus,
                                  OrderStatus status, String idempotencyKey,
                                  long version, Instant createdAt, Instant updatedAt) {}
    public record OrderItemResponse(UUID id, UUID orderId, UUID productId, UUID variantId,
                                      String productName, String productSku,
                                      Map<String, Object> variantOptions, int quantity,
                                      BigDecimal unitPrice, BigDecimal discount, BigDecimal tax,
                                      BigDecimal lineTotal, Instant createdAt) {}

    // ===== Domain =====
    public record CreateDomainRequest(String hostname) {}
    public record DomainResponse(UUID id, UUID tenantId, UUID storeId, String hostname,
                                   DomainType domainType, VerificationStatus verificationStatus,
                                   ActivationStatus activationStatus, boolean isPrimary,
                                   String verificationToken, Instant verifiedAt,
                                   String failureReason, long version,
                                   Instant createdAt, Instant updatedAt) {}

    // ===== Public Resolution =====
    public record PublicStoreResponse(UUID storeId, UUID tenantId, String name, String slug,
                                        String defaultLocale, String defaultCurrency,
                                        List<PublicCollectionResponse> collections) {}
    public record PublicProductResponse(UUID id, String name, String slug, String sku,
                                          String description, ProductType productType,
                                          String currency, BigDecimal price, BigDecimal compareAtPrice) {}
    public record PublicCollectionResponse(UUID id, String name, String slug, String description,
                                             int sortOrder, List<PublicProductResponse> products) {}
}
