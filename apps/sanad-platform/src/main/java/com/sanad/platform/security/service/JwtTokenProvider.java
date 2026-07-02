package com.sanad.platform.security.service;

import com.sanad.platform.security.config.SecurityProperties;
import com.sanad.platform.security.denial.JwtValidationResult;
import com.sanad.platform.security.denial.SecurityDenialCategory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Mints and validates short-lived JWT access tokens. */
@Component
public class JwtTokenProvider {

    public static final String ROTATION_REQUIRED_CLAIM = "credential_rotation_required";
    public static final String SESSION_VERSION_CLAIM = "session_version";

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecurityProperties.Jwt jwtConfig;
    private final Environment environment;
    private final Clock clock;
    private SecretKey signingKey;

    @Autowired
    public JwtTokenProvider(SecurityProperties securityProperties, Environment environment) {
        this(securityProperties, environment, Clock.systemUTC());
    }

    /**
     * Stage 04A.3.6.2 — Clock-injected constructor for testability.
     *
     * <p>Production wiring uses {@link Clock#systemUTC()}. Tests can inject a
     * fixed {@link Clock} to mint tokens with deterministic issued-at and
     * expiration timestamps — e.g. an expired-but-validly-signed token that
     * exercises the real {@code JwtAuthenticationFilter} expiry path without
     * sleeping or faking the signature.</p>
     */
    public JwtTokenProvider(SecurityProperties securityProperties,
                             Environment environment,
                             Clock clock) {
        this.jwtConfig = securityProperties.getJwt();
        this.environment = environment;
        this.clock = clock == null ? Clock.systemUTC() : clock;
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
        return mintAccessToken(userId, tenantId, email, false, 0L);
    }

    public String mintAccessToken(
            UUID userId,
            UUID tenantId,
            String email,
            boolean credentialRotationRequired
    ) {
        return mintAccessToken(userId, tenantId, email, credentialRotationRequired, 0L);
    }

    public String mintAccessToken(
            UUID userId,
            UUID tenantId,
            String email,
            boolean credentialRotationRequired,
            long sessionVersion
    ) {
        Instant now = clock.instant();
        Instant expiry = now.plus(jwtConfig.getAccessTokenTtl());

        // Stage 04A §6 — jti (JWT ID) is the real session identifier.
        // It is unique per token and used as the sessionId in TenantContext.
        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId)
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)
                .claim(ROTATION_REQUIRED_CLAIM, credentialRotationRequired)
                .claim(SESSION_VERSION_CLAIM, sessionVersion)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Stage 04A.3.6.2 — Mint a token whose issuedAt and expiration are derived
     * from the supplied {@link Clock} rather than the provider's configured
     * clock.
     *
     * <p>This overload exists so that integration tests can mint a token that
     * is <strong>validly signed by the production signing key</strong> but
     * whose {@code exp} claim is already in the past relative to the running
     * server. The resulting token flows through the real
     * {@code JwtAuthenticationFilter} → {@code JwtTokenProvider.parseAndValidate}
     * path and is rejected by {@code io.jsonwebtoken} exactly as a production
     * expired token would be. This eliminates the previous "fake expired JWT
     * with an invalid signature" workaround (CD-04-P1-018).</p>
     *
     * @param overrideClock the clock used to compute issuedAt and exp
     */
    public String mintAccessTokenWithClock(
            UUID userId,
            UUID tenantId,
            String email,
            boolean credentialRotationRequired,
            long sessionVersion,
            Clock overrideClock
    ) {
        Clock effective = overrideClock == null ? clock : overrideClock;
        Instant now = effective.instant();
        Instant expiry = now.plus(jwtConfig.getAccessTokenTtl());

        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId)
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)
                .claim(ROTATION_REQUIRED_CLAIM, credentialRotationRequired)
                .claim(SESSION_VERSION_CLAIM, sessionVersion)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Stage 05A.2.9.1 §2 — Mint a token signed by the PRODUCTION signing
     * key but with a raw (non-UUID) subject string.
     *
     * <p>This overload exists so that integration tests can mint a token
     * that is <strong>validly signed and not expired</strong> but whose
     * {@code sub} claim is not a UUID — exercising the real
     * {@code JwtAuthenticationFilter} INVALID_SUBJECT classification path
     * without sleeping, faking the signature, or hardcoding a test-only
     * secret that would diverge from the production key.</p>
     *
     * <p>The token flows through the real filter chain:
     * {@code JwtTokenProvider.validateAndClassify} returns
     * {@code Valid(claims)} (signature OK, not expired), then the filter
     * attempts {@code UUID.fromString(claims.getSubject())} which throws
     * {@code IllegalArgumentException}, classifying the denial as
     * INVALID_SUBJECT.</p>
     *
     * @param rawSubject  the raw subject string (NOT a UUID — e.g. "not-a-uuid")
     * @param tenantId    the tenant ID claim
     * @param email       the email claim
     */
    public String mintAccessTokenWithRawSubject(
            String rawSubject,
            UUID tenantId,
            String email
    ) {
        Instant now = clock.instant();
        Instant expiry = now.plus(jwtConfig.getAccessTokenTtl());
        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(rawSubject)
                .id(tokenId)
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)
                .claim(ROTATION_REQUIRED_CLAIM, false)
                .claim(SESSION_VERSION_CLAIM, 0L)
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

    /**
     * Stage 05A.2.9.1 §6 — Typed replacement for {@link #parseAndValidate}.
     *
     * <p>Classifies the failure into one of {@link SecurityDenialCategory#MALFORMED_JWT},
     * {@link SecurityDenialCategory#INVALID_SIGNATURE}, or
     * {@link SecurityDenialCategory#EXPIRED_JWT} so the denial audit row
     * carries the exact reason rather than collapsing every Bearer
     * failure to {@code MALFORMED_JWT}.</p>
     *
     * <p>Never leaks the underlying exception type, message, or signer
     * diagnostics to the caller. The returned {@link JwtValidationResult}
     * is the only value used downstream by the
     * {@link com.sanad.platform.security.denial.SecurityDenialCoordinator}.</p>
     *
     * @param token raw JWT string (must not be null)
     * @return {@link JwtValidationResult.Valid} on success, or
     *         {@link JwtValidationResult.Invalid} carrying the canonical
     *         denial category
     */
    public JwtValidationResult validateAndClassify(String token) {
        if (token == null || token.isBlank()) {
            return new JwtValidationResult.Invalid(SecurityDenialCategory.MALFORMED_JWT);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtConfig.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new JwtValidationResult.Valid(claims);
        } catch (ExpiredJwtException e) {
            log.debug("JWT validation failed: ExpiredJwtException");
            return new JwtValidationResult.Invalid(SecurityDenialCategory.EXPIRED_JWT);
        } catch (SignatureException e) {
            log.debug("JWT validation failed: SignatureException");
            return new JwtValidationResult.Invalid(SecurityDenialCategory.INVALID_SIGNATURE);
        } catch (MalformedJwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getClass().getSimpleName());
            return new JwtValidationResult.Invalid(SecurityDenialCategory.MALFORMED_JWT);
        } catch (JwtException e) {
            // Any other JWT-layer failure (wrong issuer, unsupported, etc.)
            // is reported as MALFORMED — we do not distinguish further to
            // avoid leaking parser internals.
            log.debug("JWT validation failed: {}", e.getClass().getSimpleName());
            return new JwtValidationResult.Invalid(SecurityDenialCategory.MALFORMED_JWT);
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
        return clock.instant().plus(jwtConfig.getAccessTokenTtl());
    }
}
