package com.sanad.platform.security.config;

import com.sanad.platform.security.filter.JwtAuthenticationFilter;
import com.sanad.platform.security.service.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for the SANAD platform.
 *
 * <p>Configures stateless JWT authentication, CORS integration,
 * password encoding, and the security filter chain.</p>
 *
 * <p>H2 console and frame-options disabling are restricted to the
 * {@code local} profile only. In production, frame options are enabled
 * (SAMEORIGIN) and H2 console is not accessible.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final String activeProfile;

    @Value("${cors.allowed-origins:https://snad-app.vercel.app}")
    private String corsAllowedOrigins;

    public SecurityConfig(
            JwtTokenProvider jwtTokenProvider,
            @Value("${spring.profiles.active:local}") String activeProfile
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.activeProfile = activeProfile;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh"
                        ).permitAll()
                        // Actuator health (public — for monitoring)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        // OpenAPI / Swagger UI (public — disabled in prod anyway)
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // All other /api/** endpoints require authentication
                        .requestMatchers("/api/**").authenticated()
                        // Everything else is permitted (static resources, etc.)
                        // H2 console is handled by a separate @Profile("local") filter chain below
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.debug("Unauthorized request to {}: {}", request.getRequestURI(),
                                    authException.getMessage());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"timestamp\":\"" + java.time.Instant.now() + "\"," +
                                            "\"status\":401," +
                                            "\"error\":\"Unauthorized\"," +
                                            "\"message\":\"المصادقة مطلوبة\"," +
                                            "\"path\":\"" + request.getRequestURI() + "\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.debug("Access denied for {}: {}", request.getRequestURI(),
                                    accessDeniedException.getMessage());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"timestamp\":\"" + java.time.Instant.now() + "\"," +
                                            "\"status\":403," +
                                            "\"error\":\"Forbidden\"," +
                                            "\"message\":\"تم رفض الوصول\"," +
                                            "\"path\":\"" + request.getRequestURI() + "\"}"
                            );
                        })
                );

        // CSRF: enabled in production (double-submit cookie pattern),
        // disabled in local/dev (for testing convenience).
        if (isProdProfile()) {
            http.csrf(csrf -> csrf
                    .ignoringRequestMatchers("/api/v1/auth/login")
                    .csrfTokenRepository(csrfTokenRepository())
            );
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        // Frame options: SAMEORIGIN for all profiles (clickjacking protection)
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        http.addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class
        );

        // Order 1 — runs before the main chain for /h2-console/** in local profile only
        http.securityContext(sc -> sc.requireExplicitSave(false));

        return http.build();
    }

    /**
     * Separate SecurityFilterChain for H2 console — local profile ONLY.
     * This chain runs at order 0 (before the main chain) and only matches
     * /h2-console/**. It disables frame options and CSRF for H2 console
     * to work correctly.
     *
     * <p>In production, this bean is not created (no H2 console).</p>
     */
    @Bean
    @org.springframework.core.annotation.Order(0)
    @org.springframework.context.annotation.Profile("local")
    public SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(corsAllowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * CSRF token repository using the double-submit cookie pattern.
     *
     * <p>The CSRF token is stored in a cookie named {@code XSRF-TOKEN}
     * (readable by JavaScript). The frontend must send it back via the
     * {@code X-XSRF-TOKEN} header on state-changing requests (POST, PUT,
     * PATCH, DELETE).</p>
     *
     * <p>This protects against CSRF attacks on cookie-based endpoints
     * (refresh, logout) while remaining compatible with stateless JWT
     * authentication.</p>
     */
    private boolean isProdProfile() {
        return "prod".equals(activeProfile) || "production".equals(activeProfile);
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }
}
