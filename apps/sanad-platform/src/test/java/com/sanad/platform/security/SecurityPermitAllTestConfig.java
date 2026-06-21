package com.sanad.platform.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration that permits all requests.
 *
 * <p>This is used by existing integration tests (OrganizationApiIntegrationTest,
 * UserApiPersistenceIntegrationTest, etc.) that were written before Spring Security
 * was added. These tests call /api/v1/** endpoints directly without authentication
 * and would otherwise get 401.</p>
 *
 * <p>Tests that NEED to verify authentication behavior (AuthApiIntegrationTest)
 * do NOT use this configuration — they use the real SecurityConfig.</p>
 *
 * <p>This configuration is activated via @Import(SecurityPermitAllTestConfig.class)
 * on tests that need it, or via the @ActiveProfiles("test") profile which can
 * import it.</p>
 */
@TestConfiguration
public class SecurityPermitAllTestConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
