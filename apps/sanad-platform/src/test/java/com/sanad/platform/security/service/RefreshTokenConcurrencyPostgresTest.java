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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID userId;
    private String email;
    private String credential;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        // Delete child rows first to avoid PostgreSQL FK constraint violations.
        // PostgreSQL strictly enforces FK constraints (unlike H2 in local dev).
        // Order: deepest children first, then parents.
        jdbcTemplate.update("DELETE FROM crm_opportunity_stage_history");
        jdbcTemplate.update("DELETE FROM crm_opportunities");
        jdbcTemplate.update("DELETE FROM crm_pipeline_stages");
        jdbcTemplate.update("DELETE FROM crm_pipelines");
        jdbcTemplate.update("DELETE FROM crm_tasks");
        jdbcTemplate.update("DELETE FROM crm_notes");
        jdbcTemplate.update("DELETE FROM crm_tags");
        jdbcTemplate.update("DELETE FROM crm_activities");
        jdbcTemplate.update("DELETE FROM crm_contacts");
        jdbcTemplate.update("DELETE FROM crm_leads");
        jdbcTemplate.update("DELETE FROM crm_accounts");
        jdbcTemplate.update("DELETE FROM user_role_assignments");
        jdbcTemplate.update("DELETE FROM role_capabilities");
        jdbcTemplate.update("DELETE FROM roles");
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = tenantRepository.save(new Tenant(
                "Refresh Lock Tenant",
                "refresh-lock-" + UUID.randomUUID(),
                TenantStatus.ACTIVE));
        tenantId = tenant.getId();
        email = "refresh-lock@example.com";
        credential = UUID.randomUUID().toString();
        User user = new User(tenantId, email, "Refresh Lock User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(credential));
        userId = userRepository.save(user).getId();
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
}
