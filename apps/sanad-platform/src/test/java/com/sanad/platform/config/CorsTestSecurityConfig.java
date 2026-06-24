package com.sanad.platform.config;

import com.sanad.platform.security.filter.JwtAuthenticationFilter;
import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.user.repository.UserRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for CORS integration tests.
 *
 * <p>Mocks only the JWT provider and user repository so the real
 * {@link com.sanad.platform.security.config.SecurityConfig} is loaded
 * with its CORS configuration intact. This allows MockMvc to exercise
 * the actual Spring Security CORS filter chain.</p>
 */
@TestConfiguration
public class CorsTestSecurityConfig {

    @Bean
    @Primary
    public JwtTokenProvider testJwtTokenProvider() {
        return Mockito.mock(JwtTokenProvider.class);
    }

    @Bean
    @Primary
    public UserRepository testUserRepository() {
        return Mockito.mock(UserRepository.class);
    }
}
