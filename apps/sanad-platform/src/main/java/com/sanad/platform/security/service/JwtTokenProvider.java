package com.sanad.platform.security.service;

import com.sanad.platform.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Mints and validates short-lived JWT access tokens. */
@Component
public class JwtTokenProvider {

    public static final String ROTATION_REQUIRED_CLAIM = "credential_rotation_required";

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecurityProperties.Jwt jwtConfig;
    private final Environment environment;
    private SecretKey signingKey;

    public JwtTokenProvider(SecurityProperties securityProperties, Environment environment) {
        this.jwtConfig = securityProperties.getJwt();
        this.environment = environment;
    }

    @PostConstruct
    public void validateSecret() {
        String secret = jwtConfig.getSecret();
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));

        if (secret == null || secret.isBlank()) {
            if (production) {
                throw new IllegalStateException(
                        "JWT_SECRET is not set. Production requires at least 32 bytes.");
            }
            byte[] generated = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(generated);
            signingKey = Keys.hmacShaKeyFor(generated);
            log.warn("JWT secret is empty; generated an ephemeral non-production key.");
            return;
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short (" + secretBytes.length
                            + " bytes). At least 32 bytes are required.");
        }

        signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String mintAccessToken(UUID userId, UUID tenantId, String email) {
        return mintAccessToken(userId, tenantId, email, false);
    }

    public String mintAccessToken(
            UUID userId,
            UUID tenantId,
            String email,
            boolean credentialRotationRequired
    ) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAccessTokenTtl());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)
                .claim(ROTATION_REQUIRED_CLAIM, credentialRotationRequired)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtConfig.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    public UUID extractUserId(String token) {
        Claims claims = parseAndValidate(token);
        if (claims == null) {
            return null;
        }
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Instant getAccessTokenExpiry() {
        return Instant.now().plus(jwtConfig.getAccessTokenTtl());
    }
}
