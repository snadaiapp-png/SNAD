package com.sanad.platform.security;

import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.security.tenant.ThreadLocalTenantContextProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.Mockito;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/** Test-only boundary for integration suites that predate authentication. */
@TestConfiguration
public class SecurityPermitAllTestConfig {

    @Bean
    public static BeanDefinitionRegistryPostProcessor removeRealJwtProvider() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
                if (registry.containsBeanDefinition("jwtTokenProvider")) {
                    registry.removeBeanDefinition("jwtTokenProvider");
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                // No-op.
            }
        };
    }

    @Bean
    @Primary
    public JwtTokenProvider testJwtTokenProvider() {
        return Mockito.mock(JwtTokenProvider.class);
    }

    @Bean
    @Order(-100)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Stage 04A — Test-only TenantContextProvider that allows integration tests
     * to use TenantResolver without real JWT authentication.
     */
    @Bean
    @Primary
    public TenantContextProvider testTenantContextProvider() {
        return new ThreadLocalTenantContextProvider();
    }

    /**
     * Stage 04A — Test-only filter that establishes a TenantContext from the
     * tenantId query parameter. This simulates what JwtAuthenticationFilter +
     * TenantContextFilter would do in production, but without JWT verification.
     * ONLY for tests that use SecurityPermitAllTestConfig.
     */
    @Bean
    @Order(-99)
    public OncePerRequestFilter testTenantContextFilter(TenantContextProvider provider) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                             HttpServletResponse response,
                                             FilterChain filterChain) throws ServletException, IOException {
                String tenantIdStr = request.getParameter("tenantId");
                if (tenantIdStr != null && !tenantIdStr.isBlank()) {
                    try {
                        UUID tenantId = UUID.fromString(tenantIdStr);
                        TenantContext ctx = new TenantContext(
                                tenantId,
                                UUID.randomUUID(),
                                "test-session",
                                0L,
                                Set.of(),
                                TenantContext.TenantContextSource.TEST_FIXTURE,
                                "test-request");
                        provider.setContext(ctx);
                    } catch (IllegalArgumentException ignored) {
                        // Invalid UUID — no context set
                    }
                }
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    provider.clear();
                }
            }
        };
    }
}
