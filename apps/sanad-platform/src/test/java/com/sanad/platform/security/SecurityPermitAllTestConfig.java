package com.sanad.platform.security;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.security.authorization.CapabilityAuthorizationBypass;
import com.sanad.platform.security.service.JwtTokenProvider;
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

    /**
     * Always-allow RBAC evaluation so that {@code @RequireCapability} methods
     * are reachable in integration tests without seeding roles/capabilities.
     */
    @Bean
    @Primary
    public CapabilityEvaluationService testCapabilityEvaluationService() {
        CapabilityEvaluationService mock = Mockito.mock(CapabilityEvaluationService.class);
        Mockito.when(mock.evaluate(Mockito.any(UUID.class), Mockito.any(UUID.class),
                        Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> new AccessDecisionResponse(
                        invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(3), invocation.getArgument(2),
                        true, "ALLOW", UUID.randomUUID(), "TEST_ROLE"));
        return mock;
    }

    /** Exists only in test sources; no production bean can silently enable it. */
    @Bean
    public CapabilityAuthorizationBypass capabilityAuthorizationBypass() {
        return () -> true;
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
}
