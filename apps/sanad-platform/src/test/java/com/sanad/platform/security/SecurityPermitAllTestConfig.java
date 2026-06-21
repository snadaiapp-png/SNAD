package com.sanad.platform.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only chain for pre-authentication integration suites.
 *
 * <p>The negative order is intentional: Spring selects the first matching
 * SecurityFilterChain, so this chain must precede the production chain.
 * Authentication-specific tests do not import this configuration.</p>
 */
@TestConfiguration
public class SecurityPermitAllTestConfig {

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
