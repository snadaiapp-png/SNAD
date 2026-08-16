package com.sanad.platform.commerce;

import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.application.*;
import com.sanad.platform.commerce.domain.CommerceDomain;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class StoreModuleIntegrationTest {

    @Autowired private StoreService storeService;
    @Autowired private ProductService productService;
    @Autowired private CartService cartService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private OrderService orderService;
    @Autowired private StoreDomainService domainService;
    @Autowired private JdbcTemplate jdbc;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "se-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void createStore_persistsWithDraftStatus() {
        var s = storeService.create(tenantId, new CreateStoreRequest("Store", "CODE", "s1", "ar", "SAR", null), null);
        assertThat(s.status()).isEqualTo(CommerceDomain.StoreStatus.DRAFT);
    }

    @Test
    void activateStore_transitionsToActive() {
        var s = createStore("act");
        assertThat(storeService.activate(tenantId, s.id(), null).status()).isEqualTo(CommerceDomain.StoreStatus.ACTIVE);
    }

    @Test
    void createProduct_persistsWithDraftStatus() {
        var s = createStore("prod");
        var p = createProduct(s.id(), "p1");
        assertThat(p.status()).isEqualTo(CommerceDomain.ProductStatus.DRAFT);
    }

    @Test
    void publishProduct_transitionsToPublished() {
        var s = createStore("pub");
        var p = createProduct(s.id(), "pubp");
        assertThat(productService.publish(tenantId, s.id(), p.id(), null).status()).isEqualTo(CommerceDomain.ProductStatus.PUBLISHED);
    }

    @Test
    void createCart_returnsEmpty() {
        var s = createStore("cart");
        var c = cartService.create(tenantId, s.id(), new CreateCartRequest(null, "SAR"), null);
        assertThat(c.status()).isEqualTo(CommerceDomain.CartStatus.ACTIVE);
    }

    @Test
    void checkout_createsOrder() {
        var s = createStore("co");
        storeService.activate(tenantId, s.id(), null);
        var p = createProduct(s.id(), "cop");
        productService.publish(tenantId, s.id(), p.id(), null);
        productService.createPrice(tenantId, s.id(), p.id(), new CreatePriceRequest(null, "SAR", new BigDecimal("50.00"), null, null, null), null);
        var c = cartService.create(tenantId, s.id(), new CreateCartRequest(null, "SAR"), null);
        cartService.addItem(tenantId, s.id(), c.id(), new AddCartItemRequest(p.id(), null, 1), null);
        var order = checkoutService.checkout(tenantId, s.id(),
                new CheckoutRequest(c.id(), "idem-" + System.nanoTime(), "test@example.com", "Test", null, null), null);
        assertThat(order.orderNumber()).isNotBlank();
        assertThat(order.grandTotal()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void orderItems_containImmutableSnapshot() {
        var s = createStore("snap");
        storeService.activate(tenantId, s.id(), null);
        var p = createProduct(s.id(), "sp");
        productService.publish(tenantId, s.id(), p.id(), null);
        productService.createPrice(tenantId, s.id(), p.id(), new CreatePriceRequest(null, "SAR", new BigDecimal("75.00"), null, null, null), null);
        var c = cartService.create(tenantId, s.id(), new CreateCartRequest(null, "SAR"), null);
        cartService.addItem(tenantId, s.id(), c.id(), new AddCartItemRequest(p.id(), null, 3), null);
        var order = checkoutService.checkout(tenantId, s.id(),
                new CheckoutRequest(c.id(), "idem-snap-" + System.nanoTime(), "test@example.com", "Test", null, null), null);
        var items = orderService.getItems(tenantId, s.id(), order.orderId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(items.get(0).quantity()).isEqualTo(3);
    }

    @Test
    void registerCustomDomain_startsAsPending() {
        var s = createStore("dom");
        var d = domainService.registerCustomDomain(tenantId, s.id(), new CreateDomainRequest("shop.example.com"), null);
        assertThat(d.verificationStatus()).isEqualTo(CommerceDomain.VerificationStatus.PENDING);
        assertThat(d.activationStatus()).isEqualTo(CommerceDomain.ActivationStatus.INACTIVE);
    }

    @Test
    void activateDomain_requiresVerificationFirst() {
        var s = createStore("domact");
        var d = domainService.registerCustomDomain(tenantId, s.id(), new CreateDomainRequest("act.example.com"), null);
        assertThatThrownBy(() -> domainService.activate(tenantId, s.id(), d.id(), null))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void crossTenantStoreAccess_denied() {
        var s = createStore("xt");
        UUID other = createOtherTenant();
        assertThatThrownBy(() -> storeService.get(other, s.id()))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void noErpImplementation() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'erp_%'", Integer.class);
        assertThat(c).as("ERP_BUSINESS_IMPLEMENTATION_ADDED").isEqualTo(0);
    }

    @Test
    void noPosImplementation() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'pos_%'", Integer.class);
        assertThat(c).as("POS_BUSINESS_IMPLEMENTATION_ADDED").isEqualTo(0);
    }

    @Test
    void noContractImplementation() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'contracts_%'", Integer.class);
        assertThat(c).as("CONTRACT_BUSINESS_IMPLEMENTATION_ADDED").isEqualTo(0);
    }

    private StoreResponse createStore(String slug) {
        return storeService.create(tenantId, new CreateStoreRequest("Store " + slug, slug.toUpperCase(), slug, "ar", "SAR", null), null);
    }

    private ProductResponse createProduct(UUID storeId, String slug) {
        return productService.create(tenantId, storeId,
                new CreateProductRequest("Product " + slug, slug, "SKU-" + slug, "Desc", CommerceDomain.ProductType.PHYSICAL, null), null);
    }

    private UUID createOtherTenant() {
        UUID ot = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)",
                ot, "se-ot-" + ot.toString().substring(0, 8), now, now);
        return ot;
    }
}
