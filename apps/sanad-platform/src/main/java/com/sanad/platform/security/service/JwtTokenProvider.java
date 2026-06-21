package com.sanad.platform.security.service;

import com.sanad.platform.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final SecretKey signingKey;
    private final SecurityProperties.Jwt jwtConfig;

    public JwtTokenProvider(SecurityProperties securityProperties) {
        this.jwtConfig = securityProperties.getJwt();
        // The secret must be at least 256 bits (32 bytes) for HS256.
        // If it's shorter, we pad it — but log a warning.
        byte[] secretBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            log.warn("JWT secret is shorter than 256 bits — this is insecure for production. " +
                    "Set JWT_SECRET to a strong secret of at least 32 bytes.");
            // Pad to 32 bytes using a deterministic derivation (not ideal, but safe for local/dev).
            byte[] padded = new byte[32];
            System.arraycopy(secretBytes, 0, padded, 0, secretBytes.length);
            this.signingKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        }
    }

    /**
     * Mint a new access JWT for the given user.
     *
     * @param userId   the authenticated user's ID
     * @param tenantId the user's tenant ID
     * @param email    the user's email
     * @return the signed JWT string
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
            // Don't log the token value — just the exception type.
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract the user ID from a valid JWT.
     *
     * @param token the raw JWT string
     * @return the user ID, or {@code null} if the token is invalid
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
     * Get the access token TTL (for inclusion in auth responses).
     *
     * @return the TTL as an {@link Instant} offset from now
     */
    public Instant getAccessTokenExpiry() {
        return Instant.now().plus(jwtConfig.getAccessTokenTtl());
    }
}
