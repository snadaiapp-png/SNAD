package com.sanad.platform.security.config;

import com.sanad.platform.config.CorsProperties;
import com.sanad.platform.security.denial.SecurityDenialAttributes;
import com.sanad.platform.security.denial.SecurityDenialCategory;
import com.sanad.platform.security.denial.SecurityDenialContext;
import com.sanad.platform.security.denial.SecurityDenialCoordinator;
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
    private final SecurityDenialCoordinator denialCoordinator;
    private final com.sanad.platform.audit.service.SecurityTokenFingerprintService tokenFingerprintService;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider,
                          JwtSessionValidationService sessionValidationService,
                          CorsProperties corsProperties,
                          Environment environment,
                          TenantContextProvider tenantContextProvider,
                          SecurityDenialCoordinator denialCoordinator,
                          com.sanad.platform.audit.service.SecurityTokenFingerprintService tokenFingerprintService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionValidationService = sessionValidationService;
        this.corsProperties = corsProperties;
        this.environment = environment;
        this.tenantContextProvider = tenantContextProvider;
        this.denialCoordinator = denialCoordinator;
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
                            // Stage 05A.2.9.1 §8/§9 — Delegate every 401
                            // path through the SecurityDenialCoordinator.
                            //
                            // The JwtAuthenticationFilter stores a
                            // SecurityDenialContext as a request attribute
                            // when it classifies a JWT failure (MISSING_JWT,
                            // MALFORMED_JWT, INVALID_SIGNATURE, EXPIRED_JWT,
                            // INVALID_SUBJECT, UNVERIFIED_TENANT). The filter
                            // does NOT write the response — it lets the chain
                            // continue so that permitAll endpoints (like
                            // /api/v1/auth/login) proceed normally.
                            //
                            // This AuthenticationEntryPoint fires ONLY for
                            // protected endpoints that require authentication.
                            // It reads the stored denial context (if any) and
                            // delegates to the coordinator. If no context was
                            // stored (edge case), it falls back to MISSING_JWT.
                            Boolean alreadyRecorded = (Boolean) request.getAttribute(
                                    SecurityDenialAttributes.DENIAL_RECORDED);
                            if (Boolean.TRUE.equals(alreadyRecorded)) {
                                return;
                            }
                            // Read the denial context stored by the filter.
                            SecurityDenialContext storedCtx = (SecurityDenialContext)
                                    request.getAttribute(SecurityDenialAttributes.DENIAL_CONTEXT);
                            if (storedCtx != null) {
                                denialCoordinator.deny(request, response, storedCtx);
                            } else {
                                // Fallback: no context stored (e.g. request
                                // bypassed the filter). Classify as
                                // MISSING_JWT with fingerprint if Bearer
                                // header is present.
                                String authHeader = request.getHeader("Authorization");
                                String tokenFp = null;
                                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                    tokenFp = tokenFingerprintService.fingerprint(
                                            authHeader.substring(7));
                                }
                                denialCoordinator.deny(request, response,
                                        SecurityDenialContext.of(
                                                SecurityDenialCategory.MISSING_JWT,
                                                "SANAD-AUTH-001",
                                                401,
                                                tokenFp));
                            }
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            // Stage 05A.2.9.1 §5 — The AccessDeniedHandler
                            // fires when Spring Security's authorization
                            // layer rejects a request. CapabilityDenied
                            // denials are handled separately by the
                            // GlobalExceptionHandler. This handler is
                            // the fallback for any other 403.
                            Boolean alreadyRecorded = (Boolean) request.getAttribute(
                                    SecurityDenialAttributes.DENIAL_RECORDED);
                            if (Boolean.TRUE.equals(alreadyRecorded)) {
                                return;
                            }
                            denialCoordinator.deny(request, response,
                                    SecurityDenialContext.of(
                                            SecurityDenialCategory.CAPABILITY_DENIED,
                                            "SANAD-SEC-001",
                                            403,
                                            null));
                        })
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .securityContext(sc -> sc.requireExplicitSave(false));

        http.addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, sessionValidationService,
                        denialCoordinator, tokenFingerprintService),
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
