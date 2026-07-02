package com.sanad.platform.security.config;

import com.sanad.platform.config.CorsProperties;
import com.sanad.platform.security.filter.JwtAuthenticationFilter;
import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.JwtSessionValidationService;
import com.sanad.platform.security.tenant.TenantContextFilter;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless API security. Browser session cookies are owned by the Next.js
 * same-origin BFF; the Render backend does not create browser cookies.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtSessionValidationService sessionValidationService;
    private final CorsProperties corsProperties;
    private final Environment environment;
    private final TenantContextProvider tenantContextProvider;
    private final com.sanad.platform.audit.service.PlatformSecurityDenialAuditService platformDenialAuditService;
    private final com.sanad.platform.audit.service.SecurityTokenFingerprintService tokenFingerprintService;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider,
                          JwtSessionValidationService sessionValidationService,
                          CorsProperties corsProperties,
                          Environment environment,
                          TenantContextProvider tenantContextProvider,
                          com.sanad.platform.audit.service.PlatformSecurityDenialAuditService platformDenialAuditService,
                          com.sanad.platform.audit.service.SecurityTokenFingerprintService tokenFingerprintService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionValidationService = sessionValidationService;
        this.corsProperties = corsProperties;
        this.environment = environment;
        this.tenantContextProvider = tenantContextProvider;
        this.platformDenialAuditService = platformDenialAuditService;
        this.tokenFingerprintService = tokenFingerprintService;
    }

    /**
     * Validate and parse CORS origins after property binding.
     * In production profile, enforces HTTPS-only, no wildcards, no empty list.
     */
    @PostConstruct
    void initializeCorsOrigins() {
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        corsProperties.validateAndParse(isProduction);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> {
                            // Stage 05A.2.9: Record platform security denial with safe fingerprint
                            try {
                                String reqId = org.slf4j.MDC.get("requestId");
                                String tokenFp = null;
                                String authHeader = request.getHeader("Authorization");
                                String failureCat = "MISSING_JWT";
                                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                    String rawToken = authHeader.substring(7);
                                    // SHA-256 fingerprint — never store raw token or prefix
                                    tokenFp = tokenFingerprintService.fingerprint(rawToken);
                                    failureCat = "MALFORMED_JWT";
                                }
                                platformDenialAuditService.recordDenial(
                                        failureCat, "SANAD-AUTH-001", reqId,
                                        request.getRequestURI(), request.getMethod(),
                                        request.getRemoteAddr(), request.getHeader("User-Agent"),
                                        tokenFp, null);
                            } catch (Exception auditEx) {
                                // Log ERROR but don't prevent 401 response
                                org.slf4j.LoggerFactory.getLogger("SecurityConfig")
                                    .error("Failed to record platform denial audit: requestId={} path={} exception={}",
                                        org.slf4j.MDC.get("requestId"), request.getRequestURI(),
                                        auditEx.getClass().getSimpleName());
                            }
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/problem+json");
                            response.getWriter().write(
                                    "{\"type\":\"https://snad.ai/errors/auth-001\","
                                    + "\"title\":\"Unauthorized\","
                                    + "\"status\":401,"
                                    + "\"detail\":\"Authentication is required\","
                                    + "\"instance\":\"" + request.getRequestURI() + "\","
                                    + "\"code\":\"SANAD-AUTH-001\","
                                    + "\"timestamp\":\"" + java.time.Instant.now() + "\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/problem+json");
                            response.getWriter().write(
                                    "{\"type\":\"https://snad.ai/errors/sec-001\","
                                    + "\"title\":\"Access denied\","
                                    + "\"status\":403,"
                                    + "\"detail\":\"Access is denied for this resource\","
                                    + "\"instance\":\"" + request.getRequestURI() + "\","
                                    + "\"code\":\"SANAD-SEC-001\","
                                    + "\"timestamp\":\"" + java.time.Instant.now() + "\"}"
                            );
                        })
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .securityContext(sc -> sc.requireExplicitSave(false));

        http.addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, sessionValidationService),
                UsernamePasswordAuthenticationFilter.class
        );
        // Stage 04 — TenantContextFilter runs AFTER JwtAuthenticationFilter
        // to build the TenantContext from verified JWT claims.
        http.addFilterAfter(
                new TenantContextFilter(tenantContextProvider),
                JwtAuthenticationFilter.class
        );
        return http.build();
    }

    @Bean
    @Order(0)
    @Profile("local")
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

    /**
     * CORS configuration source using exact-origin allowlist.
     *
     * <p>Only explicitly configured origins are accepted. No wildcards,
     * no pattern matching, no Vercel subdomain inference. Origins are
     * validated at startup via {@link CorsProperties}.</p>
     *
     * <p>Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS —
     * the methods used by the SANAD REST API.</p>
     *
     * <p>Allowed request headers: Authorization (JWT), Content-Type,
     * Accept, X-Requested-With, X-SANAD-Refresh-Token (refresh flow).</p>
     *
     * <p>Exposed response headers: X-SANAD-Refresh-Token (required for
     * the refresh-token cookie header on login/refresh responses), Location
     * (for redirect scenarios).</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Use allowedOrigins (exact match) — NOT allowedOriginPatterns
        List<String> origins = corsProperties.getParsedOrigins();
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept",
                        "X-Requested-With", "X-SANAD-Refresh-Token"));

        configuration.setExposedHeaders(
                List.of("X-SANAD-Refresh-Token", "Location"));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
