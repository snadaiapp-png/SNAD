package com.sanad.platform.security.filter;

import com.sanad.platform.security.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT authentication filter.
 *
 * <p>Extracts the Bearer token from the {@code Authorization} header,
 * validates it, and populates the {@code SecurityContext} with the
 * authenticated user's identity. If no token is present or the token
 * is invalid, the filter does nothing — the security chain will
 * reject the request with 401 if the endpoint requires authentication.</p>
 *
 * <p>Note: This class is NOT annotated with {@code @Component} — it is
 * registered as a bean in {@link com.sanad.platform.security.config.SecurityConfig}
 * so that {@code @WebMvcTest} tests (which exclude SecurityConfig) don't
 * try to load it and its dependencies.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        Claims claims = jwtTokenProvider.parseAndValidate(token);

        if (claims == null) {
            // Invalid token — let the security chain handle the 401
            log.debug("Invalid JWT token presented to {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Build the authentication object with claims as details
        Map<String, Object> details = new HashMap<>();
        details.put("user_id", claims.getSubject());
        details.put("tenant_id", claims.get("tenant_id", String.class));
        details.put("email", claims.get("email", String.class));

        // For this stage, all authenticated users get a single authority.
        // Future stages will add role-based authorities from the JWT or a DB lookup.
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER")
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        authentication.setDetails(details);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
