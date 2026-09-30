package com.sanad.platform.security.service;

import com.sanad.platform.security.domain.RefreshToken;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.domain.RefreshTokenStatus;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class RefreshTokenConcurrencyPostgresTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"));
        registry.add("spring.datasource.username", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"));
        registry.add("spring.datasource.password", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID userId;
    private UUID planId;
    private String email;
    private String credential;

    @BeforeEach
    void setUp() {
        // This test used to TRUNCATE the shared identity graph (tenants, users,
        // roles, role_capabilities, user_role_assignments, refresh_tokens) plus
        // the whole CRM graph with RESTART IDENTITY CASCADE. That destroyed the
        // canonical migrated control-plane tenant (V20260813_1, tenant
        // 00000000-0000-0000-0000-000000000001) and, through CASCADE, every
        // tenant's rows in ~200 FK-child tables — canonical state that later
        // acceptance tests (G1-G forensic RBAC, permission projection,
        // governance assertions) depend on.
        //
        // Root-cause replacement: fixtures are self-cleaning and residue-tolerant.
        // Every identity this test creates is unique per run (random tenant
        // subdomain, random user email, random plan code), so stale residue from
        // prior runs can never collide or be observed by this test's
        // tenant-scoped assertions, and @AfterEach removes exactly the owned
        // rows in FK-safe order. No global wipe, no canonical destruction.

        Tenant tenant = tenantRepository.save(new Tenant(
                "Refresh Lock Tenant",
                "refresh-lock-" + UUID.randomUUID(),
                TenantStatus.ACTIVE));
        tenantId = tenant.getId();
        // Unique per run: a prior run's user row must never force this run to
        // wipe shared identity tables just to satisfy the users.email unique
        // constraint.
        email = "refresh-lock-" + UUID.randomUUID() + "@example.test";
        credential = UUID.randomUUID().toString();
        User user = new User(tenantId, email, "Refresh Lock User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(credential));
        userId = userRepository.save(user).getId();
        seedLoginEligibleSubscription(tenantId);
    }

    @Test
    void concurrentReuseInvalidatesTheIssuedFamily() throws Exception {
        AuthResponse login = authService.login(new LoginRequest(email, credential));
        String refreshValue = login.getRefreshToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> rotate(refreshValue, ready, start));
            Future<Boolean> second = executor.submit(() -> rotate(refreshValue, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);

            List<RefreshToken> family =
                    refreshTokenRepository.findAllByTenantIdAndUserId(tenantId, userId);
            assertThat(family.stream()
                    .filter(token -> token.getStatus() == RefreshTokenStatus.ACTIVE)).isEmpty();
            assertThat(family.stream()
                    .filter(token -> token.getStatus() == RefreshTokenStatus.USED)).hasSize(1);
            assertThat(family.stream()
                    .filter(token -> token.getStatus() == RefreshTokenStatus.REVOKED)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean rotate(String refreshValue, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await(10, TimeUnit.SECONDS);
            authService.refresh(new RefreshRequest(refreshValue));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Owned-fixture cleanup in FK-safe order, scoped strictly to the rows this
     * run created: refresh_tokens (self-referencing fk_refresh_tokens_replaced_by
     * cleared first), the login-eligibility subscription and plan, then user and
     * tenant. The canonical control-plane tenant and every other tenant's rows
     * are never touched.
     */
    @AfterEach
    void cleanupOwnedFixtures() {
        if (tenantId == null) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET replaced_by_id = NULL WHERE tenant_id = ?",
                tenantId);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM tenant_subscriptions WHERE tenant_id = ?", tenantId);
        if (planId != null) {
            jdbcTemplate.update("DELETE FROM saas_plans WHERE id = ?", planId);
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    /**
     * Canonical login-eligibility fixture: authentication gates require an
     * ACTIVE tenant with exactly one login-eligible effective subscription
     * (fail-closed). Tests seed that subscription alongside the tenant.
     */
    private void seedLoginEligibleSubscription(UUID tenantId) {
        planId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO saas_plans (id, code, name, status, currency_code,
                                                monthly_price_minor, annual_price_minor, trial_days,
                                                max_users, max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, 'Auth Test Plan', 'ACTIVE', 'SAR', 1000, 10000, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                planId, "login-" + tenantId.toString().substring(0, 8));
        jdbc.update("""
                        INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status,
                                                          billing_cycle, seat_quantity, credit_balance_minor,
                                                          started_at, current_period_start, current_period_end,
                                                          cancel_at_period_end, billing_state, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0,
                                NOW(), NOW(), NOW() + INTERVAL '30 days', false, 'CURRENT', NOW(), NOW())
                        """,
                UUID.randomUUID(), tenantId, planId);
    }
}
