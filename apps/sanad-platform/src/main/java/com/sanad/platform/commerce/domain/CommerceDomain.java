package com.sanad.platform.commerce.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Commerce (Stores/E-Commerce) platform domain types (v20260816.5).
 *
 * <p>Defines the canonical status / type enums used by the commerce platform
 * (commerce_stores, commerce_products, commerce_product_variants,
 * commerce_collections, commerce_prices, commerce_carts, commerce_orders,
 * commerce_store_domains).
 *
 * <p>The {@link #isReservedHostname(String)} helper mirrors the Website
 * platform's {@code WebsiteDomain.isReservedHostname} logic so that the
 * same reserved-prefix rules apply to commerce storefront custom domains.
 */
public final class CommerceDomain {

    private CommerceDomain() {}

    /** Lifecycle status of a {@code commerce_stores} row. */
    public enum StoreStatus { DRAFT, ACTIVE, SUSPENDED, ARCHIVED }

    /** Lifecycle status of a {@code commerce_products} row. */
    public enum ProductStatus { DRAFT, PUBLISHED, UNPUBLISHED, ARCHIVED }

    /** Type of a product (drives fulfillment model). */
    public enum ProductType { PHYSICAL, DIGITAL, SERVICE, BUNDLE }

    /** Lifecycle status of a {@code commerce_product_variants} row. */
    public enum VariantStatus { DRAFT, ACTIVE, ARCHIVED }

    /** Lifecycle status of a {@code commerce_collections} row. */
    public enum CollectionStatus { DRAFT, PUBLISHED, UNPUBLISHED, ARCHIVED }

    /** Lifecycle status of a {@code commerce_prices} row. */
    public enum PriceStatus { ACTIVE, INACTIVE, EXPIRED }

    /** Lifecycle status of a {@code commerce_carts} row. */
    public enum CartStatus { ACTIVE, CHECKED_OUT, EXPIRED, ABANDONED }

    /** Lifecycle status of a {@code commerce_orders} row. */
    public enum OrderStatus { PENDING, CONFIRMED, PAID, PROCESSING, COMPLETED, CANCELLED }

    /** Payment status of a {@code commerce_orders} row. */
    public enum PaymentStatus { PENDING, AUTHORIZED, PAID, PARTIALLY_REFUNDED, REFUNDED, FAILED }

    /** Fulfillment status of a {@code commerce_orders} row. */
    public enum FulfillmentStatus { UNFULFILLED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, RETURNED }

    /** Domain type for {@code commerce_store_domains}. */
    public enum DomainType { CUSTOM, DEFAULT_GENERATED }

    /** Verification status for {@code commerce_store_domains}. */
    public enum VerificationStatus { PENDING, VERIFYING, VERIFIED, FAILED }

    /** Activation status for {@code commerce_store_domains}. */
    public enum ActivationStatus { INACTIVE, ACTIVE, DISABLED }

    /**
     * Reserved hostname prefixes that cannot be claimed as custom storefront
     * domains. Reuses the same list as {@code WebsiteDomain} so that the
     * platform has a single, consistent reserved-name policy.
     */
    public static final Set<String> RESERVED_HOSTNAME_PREFIXES = Set.of(
            "www", "admin", "api", "app", "mail", "smtp", "ftp", "localhost",
            "snad", "sanad", "platform", "manage", "management", "system",
            "health", "actuator", "v1", "v2",
            "shop", "store", "stores", "ecom", "ecommerce", "checkout",
            "cart", "billing", "pay", "orders"
    );

    /**
     * Check if a hostname is reserved or protected. Mirrors
     * {@code WebsiteDomain.isReservedHostname} semantics.
     */
    public static boolean isReservedHostname(String hostname) {
        if (hostname == null) return true;
        String lower = hostname.toLowerCase(Locale.ROOT).trim();
        for (String prefix : RESERVED_HOSTNAME_PREFIXES) {
            if (lower.equals(prefix) || lower.startsWith(prefix + ".")
                    || lower.endsWith("." + prefix)) {
                return true;
            }
        }
        // Protect the platform's own deployment domains
        if (lower.contains("vercel.app") || lower.contains("onrender.com")
                || lower.contains("snad.ai") || lower.contains("sanad.ai")) {
            return true;
        }
        return false;
    }
}
