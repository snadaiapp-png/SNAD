package com.sanad.platform.security.service;

import com.sanad.platform.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints and validates JWT access tokens.
 *
 * <p>Tokens contain the following claims:</p>
 * <ul>
 *   <li>{@code sub} — user ID (UUID string)</li>
 *   <li>{@code tenant_id} — tenant ID (UUID string)</li>
 *   <li>{@code email} — user email</li>
 *   <li>{@code iat} — issued at</li>
 *   <li>{@code exp} — expiration</li>
 *   <li>{@code iss} — issuer (from config)</li>
 * </ul>
 *
 * <p>Tokens are signed with HMAC-SHA256 using the secret from
 * {@link SecurityProperties.Jwt#getSecret()}.</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecurityProperties.Jwt jwtConfig;
    private final String activeProfile;

    private SecretKey signingKey;

    public JwtTokenProvider(
            SecurityProperties securityProperties,
            @Value("${spring.profiles.active:local}") String activeProfile
    ) {
        this.jwtConfig = securityProperties.getJwt();
        this.activeProfile = activeProfile;
    }

    /**
     * Validate the JWT secret at startup.
     *
     * <p>In production (when {@code RENDER_DATABASE_URL} is set, indicating
     * a real Render deployment), the secret must be at least 32 bytes.
     * If it's empty or too short, the application fails fast.</p>
     *
     * <p>In CI environments (where {@code RENDER_DATABASE_URL} is NOT set
     * but the profile is {@code prod}), a warning is logged and a random
     * key is generated. This allows CI to run without requiring
     * {@code JWT_SECRET} to be set in every workflow.</p>
     */
    @PostConstruct
    public void validateSecret() {
        String secret = jwtConfig.getSecret();
        boolean isRealProduction = System.getenv("RENDER_DATABASE_URL") != null;

        if (secret == null || secret.isBlank()) {
            if (isProdProfile() && isRealProduction) {
                throw new IllegalStateException(
                        "JWT_SECRET is not set. Production requires a strong secret of at least 32 bytes. " +
                        "Set the JWT_SECRET environment variable."
                );
            }
            log.warn("JWT secret is empty — using a generated test key. DO NOT use in production.");
            byte[] testKey = new byte[32];
            new java.security.SecureRandom().nextBytes(testKey);
            this.signingKey = Keys.hmacShaKeyFor(testKey);
            return;
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            if (isProdProfile() && isRealProduction) {
                throw new IllegalStateException(
                        "JWT_SECRET is too short (" + secretBytes.length + " bytes). " +
                        "Production requires at least 32 bytes (256 bits)."
                );
            }
            log.warn("JWT secret is shorter than 256 bits — padding for local/dev. DO NOT use in production.");
            byte[] padded = new byte[32];
            System.arraycopy(secretBytes, 0, padded, 0, secretBytes.length);
            this.signingKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        }
    }

    private boolean isProdProfile() {
        return "prod".equals(activeProfile) || "production".equals(activeProfile);
    }

    /**
     * Mint a new access JWT for the given user.
     */
    public String mintAccessToken(UUID userId, UUID tenantId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAccessTokenTtl());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate a JWT and extract its claims.
     *
     * @param token the raw JWT string
     * @return the parsed claims, or {@code null} if the token is invalid
     */
    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtConfig.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract the user ID from a valid JWT.
     */
    public UUID extractUserId(String token) {
        Claims claims = parseAndValidate(token);
        if (claims == null) return null;
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get the access token expiry (for inclusion in auth responses).
     */
    public Instant getAccessTokenExpiry() {
        return Instant.now().plus(jwtConfig.getAccessTokenTtl());
    }
}
