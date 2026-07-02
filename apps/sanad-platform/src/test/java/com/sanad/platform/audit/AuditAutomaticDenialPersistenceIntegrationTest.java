package com.sanad.platform.audit;

import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantRuntimeDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.2.9.1 §3/§10 — Tests that security denials are automatically
 * audited with the EXACT classification.
 *
 * <p>Pre-authentication denials (401) are recorded in
 * {@code platform_security_audit_events} with the canonical
 * {@code failure_category} value.</p>
 *
 * <p>Post-authentication denials (403) are recorded in
 * {@code audit_events} (tenant-scoped) with the category name stored
 * in the {@code error_code} column.</p>
 *
 * <p>Stage 05A.2.9.1 §10 — Each denial is verified against
 * {@link PlatformDenialRow} which asserts ALL fields:</p>
 * <ul>
 *   <li>{@code failure_category} matches expected category exactly.</li>
 *   <li>{@code error_code} matches expected code.</li>
 *   <li>{@code request_id} matches the response's {@code X-Request-Id}.</li>
 *   <li>{@code path} matches the request URI.</li>
 *   <li>{@code http_method} matches the request method.</li>
 *   <li>{@code token_fingerprint} present for Bearer denials, absent for MISSING_JWT.</li>
 *   <li>Exactly one new row inserted.</li>
 * </ul>
 *
 * <p>Stage 05A.2.9.1 §6 — Request ID correlation: the same value appears
 * in the HTTP response header, HTTP response body, and the audit row.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditAutomaticDenialPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    // === Pre-authentication denial tests (platform_security_audit_events) ===

    @Test
    @DisplayName("missingJwt_401: category=MISSING_JWT, exactly 1 platform row, request_id correlated")
    void missingJwt_401_deniedAuditPersisted() throws Exception {
        int before = countPlatformDenials();

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org-401\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"))
                .andReturn();

        int after = countPlatformDenials();
        assertThat(after).as("exactly one new platform denial row").isEqualTo(before + 1);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        PlatformDenialRow row = readPlatformDenial(requestId, "/api/v1/organizations");
        assertThat(row.failureCategory()).isEqualTo("MISSING_JWT");
        assertThat(row.errorCode()).isEqualTo("SANAD-AUTH-001");
        assertThat(row.path()).isEqualTo("/api/v1/organizations");
        assertThat(row.httpMethod()).isEqualTo("POST");
        assertThat(row.tokenFingerprint()).isNull();
        assertThat(row.requestId()).isEqualTo(requestId);
        // §6 Request ID correlation: header == body == audit row
        String bodyRequestId = extractRequestIdFromBody(result);
        assertThat(bodyRequestId).isEqualTo(requestId);
        assertThat(row.requestId()).isEqualTo(bodyRequestId);
    }

    @Test
    @DisplayName("malformedJwt_401: category=MALFORMED_JWT, fingerprint present (not raw token)")
    void malformedJwt_401_deniedAuditPersisted() throws Exception {
        int before = countPlatformDenials();

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org-malformed\"}")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"))
                .andReturn();

        int after = countPlatformDenials();
        assertThat(after).isEqualTo(before + 1);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        PlatformDenialRow row = readPlatformDenial(requestId, "/api/v1/organizations");
        assertThat(row.failureCategory()).isEqualTo("MALFORMED_JWT");
        assertThat(row.errorCode()).isEqualTo("SANAD-AUTH-001");
        assertThat(row.httpMethod()).isEqualTo("POST");
        assertThat(row.tokenFingerprint())
                .as("MALFORMED_JWT must persist a SHA-256 fingerprint, not the raw token")
                .isNotNull()
                .hasSize(64)
                .matches("[0-9a-f]{64}");
        assertThat(row.tokenFingerprint())
                .as("fingerprint must NOT contain the raw token")
                .doesNotContain("not-a-jwt");
        assertThat(row.requestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("invalidSignature_401: wrong-key token → category=INVALID_SIGNATURE (not MALFORMED_JWT)")
    void invalidSignature_recordsInvalidSignature() throws Exception {
        // Mint a token signed with a DIFFERENT key (random 32 bytes).
        // The token's structure is valid (3 segments, valid claims) but
        // the signature will not verify against the production key.
        String tokenWithWrongKey = mintTokenWithAlternateKey(
                fixture.userAId(), fixture.tenantAId());

        int before = countPlatformDenials();

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-bad-sig\"}")
                        .header("Authorization", "Bearer " + tokenWithWrongKey))
                .andExpect(status().isUnauthorized())
                .andReturn();

        int after = countPlatformDenials();
        assertThat(after).isEqualTo(before + 1);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        PlatformDenialRow row = readPlatformDenial(requestId, "/api/v1/organizations");
        assertThat(row.failureCategory())
                .as("wrong-signature token must be classified INVALID_SIGNATURE, not MALFORMED_JWT")
                .isEqualTo("INVALID_SIGNATURE");
        assertThat(row.errorCode()).isEqualTo("SANAD-AUTH-001");
        assertThat(row.httpMethod()).isEqualTo("POST");
        assertThat(row.tokenFingerprint()).isNotNull().hasSize(64);
        assertThat(row.requestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("expiredJwt_401: validly-signed but expired token → category=EXPIRED_JWT")
    void expiredJwt_recordsExpiredJwt() throws Exception {
        // Mint a token whose issuedAt and exp are in the past relative
        // to the server's clock. The signature is valid; only the exp
        // is expired. Exercises the real jjwt ExpiredJwtException path.
        Clock pastClock = Clock.offset(Clock.systemUTC(), Duration.ofHours(-2));
        String expiredToken = jwtTokenProvider.mintAccessTokenWithClock(
                fixture.userAId(), fixture.tenantAId(), "expired@example.com",
                false, 0L, pastClock);

        int before = countPlatformDenials();

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-expired\"}")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andReturn();

        int after = countPlatformDenials();
        assertThat(after).isEqualTo(before + 1);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        PlatformDenialRow row = readPlatformDenial(requestId, "/api/v1/organizations");
        assertThat(row.failureCategory())
                .as("expired-but-validly-signed token must be EXPIRED_JWT, not MALFORMED_JWT")
                .isEqualTo("EXPIRED_JWT");
        assertThat(row.errorCode()).isEqualTo("SANAD-AUTH-001");
        assertThat(row.httpMethod()).isEqualTo("POST");
        assertThat(row.tokenFingerprint()).isNotNull().hasSize(64);
        assertThat(row.requestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("invalidSubject_401: validly-signed token with non-UUID subject → category=INVALID_SUBJECT")
    void invalidSubject_recordsInvalidSubject() throws Exception {
        // Mint a token signed by the PRODUCTION key with a non-UUID subject.
        // The signature verifies and the token is not expired — but the
        // filter's UUID.fromString(claims.getSubject()) throws, classifying
        // the denial as INVALID_SUBJECT (not MALFORMED_JWT).
        String tokenWithBadSubject = jwtTokenProvider.mintAccessTokenWithRawSubject(
                "not-a-uuid-subject", fixture.tenantAId(), "bad-subject@example.com");

        int before = countPlatformDenials();

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-bad-subject\"}")
                        .header("Authorization", "Bearer " + tokenWithBadSubject))
                .andExpect(status().isUnauthorized())
                .andReturn();

        int after = countPlatformDenials();
        assertThat(after).isEqualTo(before + 1);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        PlatformDenialRow row = readPlatformDenial(requestId, "/api/v1/organizations");
        assertThat(row.failureCategory())
                .as("non-UUID subject must be INVALID_SUBJECT, not MALFORMED_JWT")
                .isEqualTo("INVALID_SUBJECT");
        assertThat(row.errorCode()).isEqualTo("SANAD-AUTH-001");
        assertThat(row.httpMethod()).isEqualTo("POST");
        assertThat(row.tokenFingerprint()).isNotNull().hasSize(64);
        assertThat(row.requestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("unknownSession_401: valid JWT for non-existent user → category=UNKNOWN_SESSION")
    void unknownSession_recordsUnknownSession() throws Exception {
        // Mint a validly-signed, non-expired token for a user UUID that
        // does NOT exist in the database. Session validation returns
        // Invalid(UNKNOWN_SESSION).
        UUID unknownUserId = UUID.randomUUID();
        String tokenForUnknownUser = jwtTokenProvider.mintAccessToken(
                unknownUserId, fixture.tenantAId(), "ghost@example.com");

        int platformBefore = countPlatformDenials();
        int tenantDeniedBefore = countDeniedAuditForTenant(fixture.tenantAId());

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-unknown-session\"}")
                        .header("Authorization", "Bearer " + tokenForUnknownUser))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"))
                .andReturn();

        int platformAfter = countPlatformDenials();
        int tenantDeniedAfter = countDeniedAuditForTenant(fixture.tenantAId());

        assertThat(platformAfter).as("exactly one new platform denial row")
                .isEqualTo(platformBefore + 1);
        assertThat(tenantDeniedAfter).as("UNKNOWN_SESSION must NOT write to audit_events (pre-auth)")
                .isEqualTo(tenantDeniedBefore);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        PlatformDenialRow row = readPlatformDenial(requestId, "/api/v1/organizations");
        assertThat(row.failureCategory())
                .as("non-existent user must be UNKNOWN_SESSION, not MALFORMED_JWT")
                .isEqualTo("UNKNOWN_SESSION");
        assertThat(row.errorCode()).isEqualTo("SANAD-AUTH-001");
        assertThat(row.httpMethod()).isEqualTo("POST");
        assertThat(row.tokenFingerprint()).isNotNull().hasSize(64);
        assertThat(row.requestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("revokedSession_401: valid JWT with stale session_version → category=REVOKED_SESSION")
    void revokedSession_recordsRevokedSession() throws Exception {
        // User A has session_version=0 in the DB (from the fixture).
        // Mint a token with session_version=5 (higher than DB). The
        // session-validation service detects the mismatch and returns
        // Invalid(REVOKED_SESSION).
        String tokenWithStaleVersion = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice@example.com",
                false, 5L);

        int platformBefore = countPlatformDenials();

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-revoked\"}")
                        .header("Authorization", "Bearer " + tokenWithStaleVersion))
                .andExpect(status().isUnauthorized())
                .andReturn();

        int platformAfter = countPlatformDenials();
        assertThat(platformAfter).isEqualTo(platformBefore + 1);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        PlatformDenialRow row = readPlatformDenial(requestId, "/api/v1/organizations");
        assertThat(row.failureCategory())
                .as("session version mismatch must be REVOKED_SESSION")
                .isEqualTo("REVOKED_SESSION");
        assertThat(row.errorCode()).isEqualTo("SANAD-AUTH-001");
        assertThat(row.httpMethod()).isEqualTo("POST");
        assertThat(row.tokenFingerprint()).isNotNull().hasSize(64);
        assertThat(row.requestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("unverifiedTenant_401: JWT with non-UUID tenant_id claim → category=UNVERIFIED_TENANT")
    void unverifiedTenant_recordsUnverifiedTenant() throws Exception {
        // Mint a token with a valid signature and valid subject UUID,
        // but a non-UUID tenant_id claim. The filter's
        // UUID.fromString(jwtTenantIdStr) throws, classifying the
        // denial as UNVERIFIED_TENANT.
        // We use mintAccessTokenWithRawSubject's signing path but need
        // a different approach: mint a normal token, then re-sign with
        // a custom tenant_id. Since we can't re-sign without the key,
        // we use the production-key mintAccessTokenWithRawSubject helper
        // adapted for tenant override — but that doesn't exist.
        //
        // Simpler: use a token with a UUID subject but a non-UUID
        // tenant_id. We achieve this by building the token with the
        // production key directly via a test helper that accepts a
        // raw tenant_id string.
        String tokenWithBadTenant = mintTokenWithRawTenantId(
                fixture.userAId(), "not-a-uuid-tenant", "bad-tenant@example.com");

        int before = countPlatformDenials();

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-bad-tenant\"}")
                        .header("Authorization", "Bearer " + tokenWithBadTenant))
                .andExpect(status().isUnauthorized())
                .andReturn();

        int after = countPlatformDenials();
        assertThat(after).isEqualTo(before + 1);

        String requestId = result.getResponse().getHeader("X-Request-Id");
        PlatformDenialRow row = readPlatformDenial(requestId, "/api/v1/organizations");
        assertThat(row.failureCategory())
                .as("non-UUID tenant_id claim must be UNVERIFIED_TENANT")
                .isEqualTo("UNVERIFIED_TENANT");
        assertThat(row.errorCode()).isEqualTo("SANAD-AUTH-001");
        assertThat(row.httpMethod()).isEqualTo("POST");
        assertThat(row.tokenFingerprint()).isNotNull().hasSize(64);
        assertThat(row.requestId()).isEqualTo(requestId);
    }

    // === Tenant-scoped denial tests (audit_events) ===

    @Test
    @DisplayName("tenantSelectorMismatch_403: valid JWT, wrong tenantId param → audit_events row")
    void tenantMismatch_recordsTenantSelectorMismatch() throws Exception {
        String tokenForA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice@example.com");

        int platformBefore = countPlatformDenials();
        int tenantDeniedBefore = countDeniedAuditForTenant(fixture.tenantAId());

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-mismatch\"}")
                        .header("Authorization", "Bearer " + tokenForA))
                .andExpect(status().isForbidden())
                .andReturn();

        int platformAfter = countPlatformDenials();
        int tenantDeniedAfter = countDeniedAuditForTenant(fixture.tenantAId());

        assertThat(platformAfter).as("TENANT_SELECTOR_MISMATCH must NOT write to platform_security_audit_events")
                .isEqualTo(platformBefore);
        assertThat(tenantDeniedAfter).as("exactly one new tenant DENIED row")
                .isEqualTo(tenantDeniedBefore + 1);

        String errorCode = readLatestDeniedErrorCode(fixture.tenantAId());
        assertThat(errorCode).isEqualTo("TENANT_SELECTOR_MISMATCH");
    }

    @Test
    @DisplayName("rotationRequired_403: valid session, rotation flag → audit_events row")
    void rotationRequired_recordsRotationRequired() throws Exception {
        String tokenWithRotation = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice@example.com",
                true, 0L);

        int tenantDeniedBefore = countDeniedAuditForTenant(fixture.tenantAId());

        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-rotation\"}")
                        .header("Authorization", "Bearer " + tokenWithRotation))
                .andExpect(status().isForbidden());

        int tenantDeniedAfter = countDeniedAuditForTenant(fixture.tenantAId());
        assertThat(tenantDeniedAfter).isEqualTo(tenantDeniedBefore + 1);

        String errorCode = readLatestDeniedErrorCode(fixture.tenantAId());
        assertThat(errorCode).isEqualTo("ROTATION_REQUIRED");
    }

    @Test
    @DisplayName("missingCapability_403: valid JWT, no ORGANIZATION.CREATE → audit_events row")
    void missingCapability_403_deniedAuditPersisted() throws Exception {
        String noCapToken = jwtTokenProvider.mintAccessToken(
                fixture.userWithoutCapabilityId(), fixture.tenantAId(), "nocap@example.com");

        int before = countDeniedAuditForTenant(fixture.tenantAId());

        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org-403\"}")
                        .header("Authorization", "Bearer " + noCapToken)
                        .header("Idempotency-Key", "denied-403-" + UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));

        int after = countDeniedAuditForTenant(fixture.tenantAId());
        assertThat(after).isGreaterThan(before);

        String errorCode = readLatestDeniedErrorCode(fixture.tenantAId());
        assertThat(errorCode).isEqualTo("CAPABILITY_DENIED");
    }

    // === Helper methods ===

    private int countPlatformDenials() throws Exception {
        String sql = "SELECT COUNT(*) FROM platform_security_audit_events";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Stage 05A.2.9.1 §10 — Reads the latest platform denial row for a
     * given request_id and path, then asserts ALL fields.
     */
    private PlatformDenialRow readPlatformDenial(String requestId, String path) throws Exception {
        String sql = "SELECT failure_category, error_code, request_id, path, http_method, token_fingerprint "
                + "FROM platform_security_audit_events "
                + "WHERE request_id = ? AND path = ? "
                + "ORDER BY occurred_at DESC, created_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, path);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlatformDenialRow(
                            rs.getString("failure_category"),
                            rs.getString("error_code"),
                            rs.getString("request_id"),
                            rs.getString("path"),
                            rs.getString("http_method"),
                            rs.getString("token_fingerprint"));
                }
            }
        }
        return null;
    }

    private int countDeniedAuditForTenant(UUID tenantId) throws Exception {
        String sql = "SELECT COUNT(*) FROM audit_events WHERE tenant_id = ? AND outcome = 'DENIED'";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String readLatestDeniedErrorCode(UUID tenantId) throws Exception {
        String sql = "SELECT error_code FROM audit_events "
                + "WHERE tenant_id = ? AND outcome = 'DENIED' "
                + "ORDER BY occurred_at DESC, created_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("error_code");
                }
            }
        }
        return null;
    }

    private String extractRequestIdFromBody(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        // Extract "requestId":"<value>" from the JSON body
        int idx = body.indexOf("\"requestId\":\"");
        if (idx < 0) {
            return null;
        }
        int start = idx + "\"requestId\":\"".length();
        int end = body.indexOf("\"", start);
        return end > start ? body.substring(start, end) : null;
    }

    // === Test-only token minting helpers ===

    /**
     * Mints a token signed with a different key than the production
     * JwtTokenProvider. The token's structure is valid but the signature
     * will not verify → INVALID_SIGNATURE.
     */
    private String mintTokenWithAlternateKey(UUID userId, UUID tenantId) {
        byte[] altKey = new byte[32];
        new java.security.SecureRandom().nextBytes(altKey);
        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(altKey);
        return io.jsonwebtoken.Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", "wrong-key@example.com")
                .claim(JwtTokenProvider.ROTATION_REQUIRED_CLAIM, false)
                .claim(JwtTokenProvider.SESSION_VERSION_CLAIM, 0L)
                .issuer("sanad-platform")
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    /**
     * Mints a token signed with the PRODUCTION key (extracted from the
     * autowired JwtTokenProvider) but with a raw (non-UUID) tenant_id
     * claim. The signature verifies and the token is not expired — but
     * the filter's UUID.fromString(tenantIdStr) throws → UNVERIFIED_TENANT.
     *
     * <p>We cannot call JwtTokenProvider.mintAccessToken because it
     * requires a UUID tenantId. Instead we re-build the token using
     * jjwt directly, but we need the production signing key. We obtain
     * it by minting a normal token and parsing it — but that doesn't
     * expose the key.</p>
     *
     * <p>The cleanest approach: use the test profile's known JWT secret
     * (application-tenant-postgres-test.yml sets
     * JWT_SECRET=ci-only-non-production-key-1234567890). We sign with
     * that key directly. If the env var JWT_SECRET is overridden, this
     * test would fail — but CI uses the default.</p>
     */
    private String mintTokenWithRawTenantId(UUID userId, String rawTenantId, String email) {
        String secret = System.getProperty("JWT_SECRET",
                System.getenv("JWT_SECRET") != null ? System.getenv("JWT_SECRET")
                        : "ci-only-non-production-key-1234567890");
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);
        return io.jsonwebtoken.Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("tenant_id", rawTenantId)
                .claim("email", email)
                .claim(JwtTokenProvider.ROTATION_REQUIRED_CLAIM, false)
                .claim(JwtTokenProvider.SESSION_VERSION_CLAIM, 0L)
                .issuer("sanad-platform")
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    /**
     * Stage 05A.2.9.1 §10 — Strongly-typed row record. Asserts every
     * column so a misclassified denial cannot hide.
     */
    private record PlatformDenialRow(
            String failureCategory,
            String errorCode,
            String requestId,
            String path,
            String httpMethod,
            String tokenFingerprint
    ) {}
}
