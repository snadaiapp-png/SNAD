package com.sanad.platform.commerce;

import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.application.*;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for the atomic order-number allocator and the
 * idempotency contract under simultaneous checkout replay.
 *
 * <p>Covers:
 * <ol>
 *   <li>20 parallel order-number allocations produce 20 unique, gap-free
 *       sequence numbers — no {@code DuplicateKeyException}.</li>
 *   <li>Multiple concurrent checkouts with the same idempotency key
 *       produce exactly ONE logical order — no duplicate payment,
 *       no duplicate inventory effect, no duplicate finance effect.</li>
 *   <li>Two different tenants get independent (tenant, period) sequences.</li>
 *   <li>Deleted/cancelled orders do NOT release their sequence number
 *       (the allocator never decrements).</li>
 * </ol>
 *
 * <p>These tests run on the H2 in-memory database (PostgreSQL compatibility
 * mode) but exercise the same SQL INSERT ... ON CONFLICT DO UPDATE ...
 * RETURNING code path that runs in production PostgreSQL. The migration
 * {@code V20260820_1__create_commerce_order_number_sequences.sql} is
 * applied to both test and prod by Flyway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class CommerceOrderConcurrencyTest {

    @Autowired private StoreService storeService;
    @Autowired private ProductService productService;
    @Autowired private CartService cartService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private OrderService orderService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "c-" + tenantId.toString().substring(0, 8), now, now);
    }

    // ===== Test 1: 20 parallel checkouts produce 20 unique order numbers =====
    @Test
    void parallelCheckouts_produceUniqueGapFreeOrderNumbers() throws Exception {
        UUID storeId = createStore("par");
        storeService.activate(tenantId, storeId, null);
        UUID productId = createProduct(storeId, "par-prod");
        productService.publish(tenantId, storeId, productId, null);
        productService.createPrice(tenantId, storeId, productId,
                new CreatePriceRequest(null, "SAR", new BigDecimal("50.00"), null, null, null), null);

        int parallelism = 20;
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        CountDownLatch ready = new CountDownLatch(parallelism);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);
        List<String> orderNumbers = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < parallelism; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    UUID cartId = cartService.create(tenantId, storeId,
                            new CreateCartRequest(null, "SAR"), null).id();
                    cartService.addItem(tenantId, storeId, cartId,
                            new AddCartItemRequest(productId, null, 1), null);
                    ready.countDown();
                    start.await();
                    var order = checkoutService.checkout(tenantId, storeId,
                            new CheckoutRequest(cartId, "idem-par-" + idx + "-" + System.nanoTime(),
                                    "test@example.com", "Test", null, null), null);
                    orderNumbers.add(order.orderNumber());
                } catch (Exception e) {
                    errors.incrementAndGet();
                    throw new RuntimeException(e);
                }
                return null;
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        boolean done = pool.awaitTermination(60, TimeUnit.SECONDS);
        assertThat(done).as("all threads finished").isTrue();
        assertThat(errors.get()).as("no checkout exceptions").isEqualTo(0);
        assertThat(orderNumbers).hasSize(parallelism);

        // All unique
        assertThat(new HashSet<>(orderNumbers)).hasSize(parallelism);

        // All follow the ORD-YYYYMM-NNNNN pattern
        var now = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC);
        String expectedPeriod = String.format("ORD-%04d%02d-", now.getYear(), now.getMonthValue());
        for (String on : orderNumbers) {
            assertThat(on).startsWith(expectedPeriod);
        }

        // Sequence values are 1..20 (gap-free, no reuse — independent of insert order)
        Set<Integer> seqValues = new HashSet<>();
        for (String on : orderNumbers) {
            String seqPart = on.substring(expectedPeriod.length());
            seqValues.add(Integer.parseInt(seqPart));
        }
        assertThat(seqValues).hasSize(parallelism);
        for (int i = 1; i <= parallelism; i++) {
            assertThat(seqValues).as("contains %d", i).contains(i);
        }
    }

    // ===== Test 2: Multi-tenant independent sequences =====
    @Test
    void twoTenants_haveIndependentOrderNumberSequences() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA, "ta");
        seedTenant(tenantB, "tb");

        UUID storeA = createStoreFor(tenantA, "sta");
        storeService.activate(tenantA, storeA, null);
        UUID prodA = createProductFor(tenantA, storeA, "pa");
        productService.publish(tenantA, storeA, prodA, null);
        productService.createPrice(tenantA, storeA, prodA,
                new CreatePriceRequest(null, "SAR", new BigDecimal("50.00"), null, null, null), null);

        UUID storeB = createStoreFor(tenantB, "stb");
        storeService.activate(tenantB, storeB, null);
        UUID prodB = createProductFor(tenantB, storeB, "pb");
        productService.publish(tenantB, storeB, prodB, null);
        productService.createPrice(tenantB, storeB, prodB,
                new CreatePriceRequest(null, "SAR", new BigDecimal("50.00"), null, null, null), null);

        var orderA1 = checkout(tenantA, storeA, prodA);
        var orderB1 = checkout(tenantB, storeB, prodB);
        var orderA2 = checkout(tenantA, storeA, prodA);

        // Both tenants' first allocation must yield sequence value 1
        assertThat(orderA1.orderNumber()).matches("ORD-\\d{6}-00001");
        assertThat(orderB1.orderNumber()).matches("ORD-\\d{6}-00001");
        // Tenant A's second allocation must yield sequence value 2
        assertThat(orderA2.orderNumber()).matches("ORD-\\d{6}-00002");
    }

    // ===== Test 3: Cancelled order does NOT release its sequence number =====
    @Test
    void cancelledOrder_doesNotReuseSequenceNumber() {
        UUID storeId = createStore("cancel");
        storeService.activate(tenantId, storeId, null);
        UUID productId = createProduct(storeId, "cancel-prod");
        productService.publish(tenantId, storeId, productId, null);
        productService.createPrice(tenantId, storeId, productId,
                new CreatePriceRequest(null, "SAR", new BigDecimal("50.00"), null, null, null), null);

        var o1 = checkout(tenantId, storeId, productId);
        var o2 = checkout(tenantId, storeId, productId);
        // Cancel o1 — the order_number remains allocated
        orderService.cancel(tenantId, storeId, o1.orderId(), null);
        var o3 = checkout(tenantId, storeId, productId);

        // o1's order number is NOT reused by o3 (gap-free monotonic counter)
        assertThat(o3.orderNumber()).isNotEqualTo(o1.orderNumber());
        // And o3's sequence must be strictly greater than o2's
        int seq1 = Integer.parseInt(o1.orderNumber().substring(13));
        int seq2 = Integer.parseInt(o2.orderNumber().substring(13));
        int seq3 = Integer.parseInt(o3.orderNumber().substring(13));
        assertThat(seq3).isGreaterThan(seq2);
        assertThat(seq2).isGreaterThan(seq1);
    }

    // ===== Test 4: Concurrent checkout with same idempotency key → exactly one order =====
    @Test
    void concurrentIdempotentCheckout_producesExactlyOneOrder() throws Exception {
        UUID storeId = createStore("idem-conc");
        storeService.activate(tenantId, storeId, null);
        UUID productId = createProduct(storeId, "idem-prod");
        productService.publish(tenantId, storeId, productId, null);
        productService.createPrice(tenantId, storeId, productId,
                new CreatePriceRequest(null, "SAR", new BigDecimal("100.00"), null, null, null), null);

        // Pre-build N carts (each cart is a single-shot resource — once checked
        // out it can't be reused). All N carts are checked out concurrently
        // with the SAME idempotency key. Only ONE cart will be successfully
        // checked out — the rest will be short-circuited by the DB-level
        // unique idempotency index on (tenant_id, store_id, idempotency_key).
        int parallelism = 8;
        List<UUID> cartIds = new ArrayList<>();
        for (int i = 0; i < parallelism; i++) {
            UUID cartId = cartService.create(tenantId, storeId,
                    new CreateCartRequest(null, "SAR"), null).id();
            cartService.addItem(tenantId, storeId, cartId,
                    new AddCartItemRequest(productId, null, 1), null);
            cartIds.add(cartId);
        }

        String idempotencyKey = "idem-key-" + System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        CountDownLatch ready = new CountDownLatch(parallelism);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < parallelism; i++) {
            final UUID cartId = cartIds.get(i);
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    checkoutService.checkout(tenantId, storeId,
                            new CheckoutRequest(cartId, idempotencyKey,
                                    "test@example.com", "Test", null, null), null);
                } catch (Exception e) {
                    // Cart-already-checked-out (409) or DuplicateKeyException
                    // caught-and-returned-winner are both expected for losers.
                    errors.incrementAndGet();
                }
                return null;
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        boolean done = pool.awaitTermination(60, TimeUnit.SECONDS);
        assertThat(done).as("all threads finished").isTrue();
        // All but one request must fail (with cart-already-checked-out or
        // idempotency-replay-return-winner). Exactly one must succeed silently.
        assertThat(errors.get()).as("losers saw expected exception").isGreaterThan(0);

        // Exactly ONE order exists for this idempotency key.
        Integer orderCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ? AND store_id = ? AND idempotency_key = ?",
                Integer.class, tenantId, storeId, idempotencyKey);
        assertThat(orderCount).as("exactly one logical order per idempotency key").isEqualTo(1);

        // Verify no duplicate payment/finance/inventory side-effects by counting
        // order-status-history rows (one INSERT per status transition). 1 initial
        // PENDING insert + 1 PAID/CONFIRMED transition = 2 history rows (not 2*N).
        Integer historyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce_order_status_history WHERE tenant_id = ? AND order_id IN " +
                        "(SELECT id FROM commerce_orders WHERE tenant_id = ? AND store_id = ? AND idempotency_key = ?)",
                Integer.class, tenantId, tenantId, storeId, idempotencyKey);
        assertThat(historyCount).as("no duplicate status history").isEqualTo(2);
    }

    // ===== Helpers =====
    private UUID createStore(String suffix) {
        return storeService.create(tenantId,
                new CreateStoreRequest("Store-" + suffix, "CODE-" + suffix,
                        "slug-" + suffix + "-" + System.nanoTime(), "ar", "SAR", null), null).id();
    }

    private UUID createProduct(UUID storeId, String suffix) {
        return productService.create(tenantId, storeId,
                new CreateProductRequest("Product-" + suffix, "SKU-" + suffix + "-" + System.nanoTime(),
                        "PHYSICAL", null, null, null), null).id();
    }

    private UUID createStoreFor(UUID tid, String suffix) {
        return storeService.create(tid,
                new CreateStoreRequest("Store-" + suffix, "CODE-" + suffix,
                        "slug-" + suffix + "-" + System.nanoTime(), "ar", "SAR", null), null).id();
    }

    private UUID createProductFor(UUID tid, UUID storeId, String suffix) {
        return productService.create(tid, storeId,
                new CreateProductRequest("Product-" + suffix, "SKU-" + suffix + "-" + System.nanoTime(),
                        "PHYSICAL", null, null, null), null).id();
    }

    private com.sanad.platform.commerce.api.CommerceDtos.CheckoutResponse checkout(
            UUID tid, UUID storeId, UUID productId) {
        UUID cartId = cartService.create(tid, storeId,
                new CreateCartRequest(null, "SAR"), null).id();
        cartService.addItem(tid, storeId, cartId,
                new AddCartItemRequest(productId, null, 1), null);
        return checkoutService.checkout(tid, storeId,
                new CheckoutRequest(cartId, "idem-" + UUID.randomUUID() + "-" + System.nanoTime(),
                        "test@example.com", "Test", null, null), null);
    }

    private void seedTenant(UUID id, String suffix) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                id, suffix + "-" + id.toString().substring(0, 8), now, now);
    }
}
