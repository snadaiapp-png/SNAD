package com.sanad.platform.crm.integration.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Mints and validates short-lived service-to-service JWTs for CRM integration
 * calls. Tokens are tenant-bound and correlation-bound and are never used as
 * browser access tokens.
 */
@Component
public class ServiceJwtProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final String issuer;
    private final String serviceName;
    private final Duration ttl;
    private final boolean configured;

    public ServiceJwtProvider(
            @Value("${sanad.service-auth.jwt-secret:}") String secret,
            @Value("${sanad.service-auth.issuer:sanad-platform}") String issuer,
            @Value("${sanad.service-auth.service-name:sanad-crm}") String serviceName,
            @Value("${sanad.service-auth.ttl-seconds:60}") long ttlSeconds) {
        String normalizedSecret = secret == null ? "" : secret.strip();
        this.configured = !normalizedSecret.isBlank();
        byte[] keyBytes;
        if (configured) {
            keyBytes = normalizedSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("sanad.service-auth.jwt-secret must be at least 32 bytes");
            }
        } else {
            keyBytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(keyBytes);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = required(issuer, "issuer");
        this.serviceName = required(serviceName, "serviceName");
        this.ttl = Duration.ofSeconds(Math.max(15, Math.min(ttlSeconds, 300)));
    }

    public boolean isConfigured() {
        return configured;
    }

    public String mint(UUID tenantId, String correlationId, String contractVersion, String audience) {
        Objects.requireNonNull(tenantId, "tenantId");
        String normalizedCorrelationId = required(correlationId, "correlationId");
        String normalizedContractVersion = required(contractVersion, "contractVersion");
        String normalizedAudience = required(audience, "audience");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .issuer(issuer)
                .subject(serviceName)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("aud", normalizedAudience)
                .claim("service_name", serviceName)
                .claim("tenant_id", tenantId.toString())
                .claim("correlation_id", normalizedCorrelationId)
                .claim("contract_version", normalizedContractVersion)
                .signWith(signingKey)
                .compact();
    }

    public ValidatedServiceToken validate(String token, String expectedAudience) {
        if (token == null || token.isBlank()) {
            throw new ServiceAuthenticationException("SERVICE_TOKEN_MISSING");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String audience = String.valueOf(claims.get("aud"));
            if (!required(expectedAudience, "expectedAudience").equals(audience)) {
                throw new ServiceAuthenticationException("SERVICE_TOKEN_AUDIENCE_INVALID");
            }
            String tokenService = claims.get("service_name", String.class);
            if (tokenService == null || tokenService.isBlank() || !tokenService.equals(claims.getSubject())) {
                throw new ServiceAuthenticationException("SERVICE_TOKEN_SUBJECT_INVALID");
            }
            UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
            String correlationId = required(claims.get("correlation_id", String.class), "correlation_id");
            String contractVersion = required(claims.get("contract_version", String.class), "contract_version");
            String jti = required(claims.getId(), "jti");
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();
            return new ValidatedServiceToken(
                    tokenService, tenantId, correlationId, contractVersion,
                    audience, jti, issuedAt, expiresAt);
        } catch (ServiceAuthenticationException error) {
            throw error;
        } catch (JwtException | IllegalArgumentException | NullPointerException error) {
            throw new ServiceAuthenticationException("SERVICE_TOKEN_INVALID", error);
        }
    }

    public record ValidatedServiceToken(
            String serviceName,
            UUID tenantId,
            String correlationId,
            String contractVersion,
            String audience,
            String jti,
            Instant issuedAt,
            Instant expiresAt) {
    }

    public static final class ServiceAuthenticationException extends RuntimeException {
        private final String code;

        public ServiceAuthenticationException(String code) {
            super(code);
            this.code = code;
        }

        public ServiceAuthenticationException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }
}
