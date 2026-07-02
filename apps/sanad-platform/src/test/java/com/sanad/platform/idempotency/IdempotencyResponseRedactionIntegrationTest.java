package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.service.IdempotencyReservationStore;
import com.sanad.platform.idempotency.service.LeaseGrant;
import com.sanad.platform.idempotency.service.IdempotencyService;
import com.sanad.platform.idempotency.service.RequestFingerprintService;
import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantRuntimeDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.2.1 §15 — Verifies that sensitive fields in the stored
 * idempotency response are redacted.
 *
 * <p>The {@link IdempotencyService#complete} method (and the underlying
 * {@link IdempotencyReservationStore#atomicComplete}) sanitize the
 * response headers (stripping {@code Set-Cookie} and {@code Authorization})
 * and redact the response body via {@link com.sanad.platform.audit.service.AuditRedactionService}
 * before persisting to {@code idempotency_records}.</p>
 *
 * <p>Stage 05A.2.1 §15 — Error-detail redaction (used by
 * {@link IdempotencyService#fail}) handles both JSON and plaintext
 * patterns: {@code password=...}, {@code token=...}, {@code authorization:...},
 * {@code secret=...}, {@code apikey=...}, and {@code Bearer <token>}.</p>
 *
 * <p>Five scenarios:</p>
 * <ol>
 *   <li>{@code setCookieAndAuthorizationStrippedFromStoredHeaders} — POST
 *       that triggers a 201 → stored response_headers must NOT contain
 *       Set-Cookie or Authorization.</li>
 *   <li>{@code nestedJsonRedactedInResponseBody} — direct service call
 *       with a nested JSON body containing a {@code password} field at
 *       the second level → stored body has {@code [REDACTED]}.</li>
 *   <li>{@code plaintextBearerTokenRedactedInErrorDetail} — direct
 *       service call to {@code fail()} with an error detail containing
 *       a {@code Bearer <token>} pattern → stored error_detail has
 *       {@code Bearer [REDACTED]}.</li>
 *   <li>{@code authorizationHeaderPatternRedactedInErrorDetail} — direct
 *       service call to {@code fail()} with an error detail containing
 *       {@code Authorization: Bearer xyz} → stored error_detail has
 *       {@code authorization: [REDACTED]}.</li>
 *   <li>{@code passwordInExceptionMessageRedactedInErrorDetail} — direct
 *       service call to {@code fail()} with an error detail containing
 *       {@code password=hunter2} → stored error_detail has
 *       {@code password=[REDACTED]}.</li>
 * </ol>
 *
 * <p>Stage 05A.1 §13 — HTTP-level tests use real JWTs through MockMvc.
 * Service-level tests establish a JWT_CLAIM-sourced TenantContext (same
 * source the filter chain establishes).</p>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete
 * tenants (FK RESTRICT from audit_events). Each test uses the fixture's
 * unique tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyResponseRedactionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;
    @Autowired private IdempotencyService idempotencyService;
    @Autowired private IdempotencyReservationStore store;
    @Autowired private TenantContextProvider contextProvider;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private String tokenA;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    /**
     * Establishes a JWT_CLAIM-sourced TenantContext (same source the filter
     * chain establishes) with the verified user/tenant IDs from the fixture.
     */
    private void setJwtClaimContext() {
        String token = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(), jti, 0L,
                Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                "redact-req-" + UUID.randomUUID()));
    }

    /**
     * Performs a successful POST and returns the stored response_headers
     * column value for the resulting idempotency record.
     */
    private String performPostAndReadStoredHeaders(String key, String body) throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, key))
                .andExpect(status().isCreated());

        // Look up the stored idempotency record.
        String sql = "SELECT response_headers FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("idempotency record must exist for key %s", key)
                        .isTrue();
                return rs.getString("response_headers");
            }
        }
    }

    /**
     * Reserves a record via the store and returns its ID + lease version.
     * Used by service-level redaction tests that bypass the HTTP layer.
     */
    private ReservedRecord reserveRecord(String key, String body) {
        String fingerprint = new RequestFingerprintService().compute(
                "POST", "/api/v1/organizations", body, null,
                fixture.tenantAId(), "ORGANIZATION.CREATE");
        Instant expiresAt = Instant.now().plusSeconds(3600);
        Instant leaseExpiresAt = Instant.now().plusSeconds(300);
        String ownerRequestId = "redact-owner-" + UUID.randomUUID();
        Optional<LeaseGrant> grant = store.atomicReserve(
                fixture.tenantAId(), key, "ORGANIZATION.CREATE",
                "/api/v1/organizations", "Organization", fingerprint,
                expiresAt, ownerRequestId, leaseExpiresAt);
        assertThat(grant)
                .as("reservation must succeed for key %s", key)
                .isPresent();
        return new ReservedRecord(grant.get().recordId(), grant.get().tenantId(), ownerRequestId, 1L);
    }

    private record ReservedRecord(UUID id, UUID tenantId, String ownerRequestId, long leaseVersion) {}

    /**
     * Reads the stored response_body for the given record ID.
     */
    private String readStoredResponseBody(UUID recordId) throws Exception {
        String sql = "SELECT response_body FROM idempotency_records WHERE id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString("response_body");
            }
        }
    }

    /**
     * Reads the stored error_detail for the given record ID.
     */
    private String readStoredErrorDetail(UUID recordId) throws Exception {
        String sql = "SELECT error_detail FROM idempotency_records WHERE id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString("error_detail");
            }
        }
    }

    @Test
    @DisplayName("setCookieAndAuthorizationStrippedFromStoredHeaders: 201 response → stored headers do not contain Set-Cookie or Authorization")
    void setCookieAndAuthorizationStrippedFromStoredHeaders() throws Exception {
        String key = "redact-headers-" + UUID.randomUUID();
        String body = "{\"name\":\"Redact Headers Org " + UUID.randomUUID()
                + "\",\"description\":\"redact\"}";

        String stored = performPostAndReadStoredHeaders(key, body);
        assertThat(stored).isNotNull();
        // Set-Cookie must NEVER be stored in idempotency records.
        assertThat(stored.toLowerCase())
                .as("Set-Cookie must be stripped from stored response headers")
                .doesNotContain("set-cookie");
        // Authorization must NEVER be stored in idempotency records.
        assertThat(stored.toLowerCase())
                .as("Authorization must be stripped from stored response headers")
                .doesNotContain("authorization");
        assertThat(stored)
                .as("bearer tokens must not be present in stored headers")
                .doesNotContain("Bearer ");
    }

    @Test
    @DisplayName("nestedJsonRedactedInResponseBody: response body with nested password field → stored body has [REDACTED]")
    void nestedJsonRedactedInResponseBody() throws Exception {
        String key = "redact-nested-" + UUID.randomUUID();
        String body = "{\"name\":\"Nested Body Org\"}";
        setJwtClaimContext();
        ReservedRecord reserved;
        try {
            reserved = reserveRecord(key, body);

            // Build a response body with a nested password field at depth 2.
            // The redaction service walks the JSON tree recursively and
            // replaces the value of any field whose name contains "password".
            String nestedJson = "{\"id\":\"" + UUID.randomUUID() + "\","
                    + "\"name\":\"Nested Body Org\","
                    + "\"owner\":{\"email\":\"alice@example.com\","
                    + "\"password\":\"hunter2\"},"
                    + "\"token\":\"should-be-redacted\"}";

            store.atomicComplete(
                    reserved.id(), reserved.tenantId(), reserved.ownerRequestId(), reserved.leaseVersion(),
                    201, "Content-Type: application/json", nestedJson);
        } finally {
            contextProvider.clear();
        }

        String stored = readStoredResponseBody(reserved.id());
        assertThat(stored)
                .as("stored response body must be non-null")
                .isNotNull();
        // The nested password value must be redacted.
        assertThat(stored)
                .as("raw password value must NOT appear in stored response body")
                .doesNotContain("hunter2");
        assertThat(stored)
                .as("[REDACTED] sentinel must appear in place of the redacted password")
                .contains("[REDACTED]");
        // The top-level token field must also be redacted.
        assertThat(stored)
                .as("raw token value must NOT appear in stored response body")
                .doesNotContain("should-be-redacted");
    }

    @Test
    @DisplayName("plaintextBearerTokenRedactedInErrorDetail: error detail containing 'Bearer xyz' → stored error_detail has 'Bearer [REDACTED]'")
    void plaintextBearerTokenRedactedInErrorDetail() throws Exception {
        String key = "redact-bearer-" + UUID.randomUUID();
        String body = "{\"name\":\"Bearer Redact Org\"}";
        setJwtClaimContext();
        ReservedRecord reserved;
        try {
            reserved = reserveRecord(key, body);

            // The error detail contains a plaintext Bearer token. The
            // IdempotencyService.fail() redacts this pattern via the
            // redactErrorDetail() helper (Stage 05A.2.1 §15).
            String errorDetail = "Upstream rejected Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature";

            idempotencyService.fail(reserved.id(), reserved.tenantId(), reserved.ownerRequestId(), reserved.leaseVersion(),
                    "SANAD-IDEMP-EXEC", errorDetail, true);
        } finally {
            contextProvider.clear();
        }

        String stored = readStoredErrorDetail(reserved.id());
        assertThat(stored)
                .as("stored error_detail must be non-null")
                .isNotNull();
        // The raw bearer token must NOT appear.
        assertThat(stored)
                .as("raw Bearer token must NOT appear in stored error_detail")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9.payload.signature");
        assertThat(stored)
                .as("Bearer [REDACTED] sentinel must appear in place of the redacted Bearer token")
                .contains("Bearer [REDACTED]");
    }

    @Test
    @DisplayName("authorizationHeaderPatternRedactedInErrorDetail: error detail containing 'Authorization: Bearer xyz' → stored error_detail redacts the header value")
    void authorizationHeaderPatternRedactedInErrorDetail() throws Exception {
        String key = "redact-authz-pattern-" + UUID.randomUUID();
        String body = "{\"name\":\"Authz Pattern Redact Org\"}";
        setJwtClaimContext();
        ReservedRecord reserved;
        try {
            reserved = reserveRecord(key, body);

            String errorDetail = "Invalid Authorization: Bearer secret-token-12345 in upstream call";

            idempotencyService.fail(reserved.id(), reserved.tenantId(), reserved.ownerRequestId(), reserved.leaseVersion(),
                    "SANAD-IDEMP-EXEC", errorDetail, true);
        } finally {
            contextProvider.clear();
        }

        String stored = readStoredErrorDetail(reserved.id());
        assertThat(stored).isNotNull();
        assertThat(stored)
                .as("raw 'secret-token-12345' must NOT appear in stored error_detail")
                .doesNotContain("secret-token-12345");
        // Either Bearer [REDACTED] or authorization: [REDACTED] must appear.
        assertThat(stored)
                .as("error_detail must contain a [REDACTED] sentinel for the redacted Authorization header")
                .contains("[REDACTED]");
    }

    @Test
    @DisplayName("passwordInExceptionMessageRedactedInErrorDetail: error detail containing 'password=hunter2' → stored error_detail has 'password=[REDACTED]'")
    void passwordInExceptionMessageRedactedInErrorDetail() throws Exception {
        String key = "redact-password-" + UUID.randomUUID();
        String body = "{\"name\":\"Password Redact Org\"}";
        setJwtClaimContext();
        ReservedRecord reserved;
        try {
            reserved = reserveRecord(key, body);

            // The error detail contains a plaintext password= pattern.
            String errorDetail = "Connection failed: password=hunter2; host=db.example.com";

            idempotencyService.fail(reserved.id(), reserved.tenantId(), reserved.ownerRequestId(), reserved.leaseVersion(),
                    "SANAD-IDEMP-EXEC", errorDetail, true);
        } finally {
            contextProvider.clear();
        }

        String stored = readStoredErrorDetail(reserved.id());
        assertThat(stored).isNotNull();
        // The raw password value must NOT appear.
        assertThat(stored)
                .as("raw password value 'hunter2' must NOT appear in stored error_detail")
                .doesNotContain("hunter2");
        assertThat(stored)
                .as("error_detail must contain 'password=[REDACTED]' sentinel")
                .contains("password=[REDACTED]");
    }
}
