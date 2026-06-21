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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenConcurrencyPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sanad_refresh_lock")
            .withUsername("sanad_test")
            .withPassword("test_only_database_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private AuthService authService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID tenantId;
    private UUID userId;
    private String email;
    private String credential;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = tenantRepository.save(new Tenant(
                "Refresh Lock Tenant",
                "refresh-lock-" + UUID.randomUUID(),
                TenantStatus.ACTIVE));
        tenantId = tenant.getId();
        email = "refresh-lock@example.com";
        credential = "ConcurrencyTestCredential123!";
        User user = new User(tenantId, email, "Refresh Lock User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(credential));
        userId = userRepository.save(user).getId();
    }

    @Test
    void sameRefreshValueCanBeConsumedOnlyOnce() throws Exception {
        AuthResponse login = authService.login(new LoginRequest(tenantId, email, credential));
        String refreshValue = login.getRefreshToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> rotate(refreshValue, ready, start));
            Future<Boolean> second = executor.submit(() -> rotate(refreshValue, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);

            List<RefreshToken> family = refreshTokenRepository.findAllByTenantIdAndUserId(tenantId, userId);
            assertThat(family.stream().filter(token -> token.getStatus() == RefreshTokenStatus.ACTIVE)).hasSize(1);
            assertThat(family.stream().filter(token -> token.getStatus() == RefreshTokenStatus.USED)).hasSize(1);
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
