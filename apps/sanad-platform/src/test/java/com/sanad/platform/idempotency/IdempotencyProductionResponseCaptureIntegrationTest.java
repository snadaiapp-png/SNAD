package com.sanad.platform.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.service.JwtTokenProvider;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2 §15 — Verifies real HTTP response capture (not MockMvc) for
 * the idempotency replay feature.
 *
 * <p>Uses {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} and
 * {@link TestRestTemplate} so that the full servlet container processes
 * the request — including the {@link ContentCachingResponseFilter} that
 * wraps the response so {@link IdempotencyCommandInterceptor} can capture
 * the body in {@code afterCompletion}.</p>
 *
 * <p>Two tests:</p>
 * <ol>
 *   <li>{@code realHttpFirstPost_createsAndReplays} — POST to
 *       {@code /api/v1/organizations} with an {@code Idempotency-Key}
 *       header returns 201. A second POST with the same key returns 201
 *       with an {@code Idempotency-Replayed: true} header, the same
 *       response body, and the same organization resource ID.</li>
 *   <li>{@code storedResponseBody_nonEmpty} — after a successful POST,
 *       the {@code response_body} column in {@code idempotency_records}
 *       is non-null and non-empty (proving the
 *       {@link ContentCachingResponseFilter} captured the body).</li>
 * </ol>
 *
 * <p>Stage 05A.1 §13 — All requests carry a real JWT via the
 * {@code Authorization: Bearer} header. The {@code TenantContextFilter}
 * establishes the TenantContext from verified JWT claims.</p>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete
 * tenants (FK RESTRICT from audit_events). Each test uses the fixture's
 * unique tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyProductionResponseCaptureIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private String tokenA;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    /**
     * Builds the Authorization + Idempotency-Key + Content-Type headers
     * for a POST to /api/v1/organizations.
     */
    private HttpHeaders buildHeaders(String idempotencyKey, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        return headers;
    }

    /**
     * Extracts the organization ID from a JSON response body.
     */
    private static String extractOrgId(String body) throws Exception {
        JsonNode root = OM.readTree(body);
        JsonNode idNode = root.get("id");
        assertThat(idNode)
                .as("response body must contain an 'id' field")
                .isNotNull();
        return idNode.asText();
    }

    /**
     * Reads the stored response body from idempotency_records for the
     * given tenant + key. Returns null if no record is found.
     */
    private String readStoredResponseBody(UUID tenantId, String key) throws Exception {
        String sql = "SELECT response_body FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("response_body");
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("realHttpFirstPost_createsAndReplays: POST with Idempotency-Key → 201; POST same key → 201 Idempotency-Replayed=true, same body, same org id")
    void realHttpFirstPost_createsAndReplays() throws Exception {
        String key = "real-http-replay-" + UUID.randomUUID();
        String uniqueName = "Real Http Org " + UUID.randomUUID();
        String body = "{\"name\":\"" + uniqueName + "\",\"description\":\"real http replay\"}";

        // === First POST → 201 ===
        HttpHeaders headers1 = buildHeaders(key, tokenA);
        HttpEntity<String> entity1 = new HttpEntity<>(body, headers1);

        ResponseEntity<String> first = restTemplate.exchange(
                "/api/v1/organizations",
                HttpMethod.POST,
                entity1,
                String.class);

        assertThat(first.getStatusCode())
                .as("first POST must return 201 Created")
                .isEqualTo(HttpStatus.CREATED);
        String firstBody = first.getBody();
        assertThat(firstBody)
                .as("first POST response body must be non-null")
                .isNotNull();
        String firstOrgId = extractOrgId(firstBody);

        // === Second POST with the same Idempotency-Key + body → 201 with Idempotency-Replayed: true ===
        HttpHeaders headers2 = buildHeaders(key, tokenA);
        HttpEntity<String> entity2 = new HttpEntity<>(body, headers2);

        ResponseEntity<String> second = restTemplate.exchange(
                "/api/v1/organizations",
                HttpMethod.POST,
                entity2,
                String.class);

        assertThat(second.getStatusCode())
                .as("second POST with the same key must return 201 Created (replay)")
                .isEqualTo(HttpStatus.CREATED);

        // The Idempotency-Replayed header must be "true".
        String replayedHeader = second.getHeaders()
                .getFirst(IdempotencyCommandInterceptor.IDEMPOTENCY_REPLAYED_HEADER);
        assertThat(replayedHeader)
                .as("second POST response must carry Idempotency-Replayed: true header")
                .isEqualTo("true");

        String secondBody = second.getBody();
        assertThat(secondBody)
                .as("second POST response body must be non-null")
                .isNotNull();

        // The replayed body must match the first body byte-for-byte
        // (the stored response body is replayed verbatim).
        assertThat(secondBody)
                .as("replayed response body must equal the first response body")
                .isEqualTo(firstBody);

        // The replayed response must reference the SAME organization ID.
        String secondOrgId = extractOrgId(secondBody);
        assertThat(secondOrgId)
                .as("replayed response must reference the same organization id as the first response")
                .isEqualTo(firstOrgId);

        // === Verify exactly 1 idempotency record exists in the DB ===
        String countSql = "SELECT COUNT(*) FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                int recordCount = rs.getInt(1);
                assertThat(recordCount)
                        .as("exactly 1 idempotency record must exist for the replayed key")
                        .isEqualTo(1);
            }
        }

        // === Verify exactly 1 organization with the unique name exists ===
        String orgSql = "SELECT COUNT(*) FROM organizations "
                + "WHERE tenant_id = ? AND name = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(orgSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, uniqueName);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                int orgCount = rs.getInt(1);
                assertThat(orgCount)
                        .as("exactly 1 organization must be created (replay does not re-execute)")
                        .isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("storedResponseBody_nonEmpty: after a successful POST, idempotency_records.response_body is non-null and non-empty")
    void storedResponseBody_nonEmpty() throws Exception {
        String key = "real-http-body-" + UUID.randomUUID();
        String uniqueName = "Real Http Body Org " + UUID.randomUUID();
        String body = "{\"name\":\"" + uniqueName + "\",\"description\":\"body capture test\"}";

        HttpHeaders headers = buildHeaders(key, tokenA);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/organizations",
                HttpMethod.POST,
                entity,
                String.class);

        assertThat(response.getStatusCode())
                .as("POST must return 201 Created")
                .isEqualTo(HttpStatus.CREATED);

        // Verify the stored response body is non-null and non-empty.
        // This proves the ContentCachingResponseFilter captured the body
        // and the IdempotencyCommandInterceptor stored it via
        // idempotencyService.complete().
        String storedBody = readStoredResponseBody(fixture.tenantAId(), key);
        assertThat(storedBody)
                .as("stored response_body must be non-null (ContentCachingResponseFilter captured the body)")
                .isNotNull();
        assertThat(storedBody.isEmpty())
                .as("stored response_body must be non-empty")
                .isFalse();

        // The stored body must contain the organization ID (proving it's
        // the real response body, not a placeholder).
        JsonNode stored = OM.readTree(storedBody);
        assertThat(stored.has("id"))
                .as("stored response_body must contain an 'id' field")
                .isTrue();
        assertThat(stored.get("id").asText())
                .as("stored response_body id must be a non-empty UUID")
                .isNotEmpty();
        assertThat(stored.has("name"))
                .as("stored response_body must contain a 'name' field")
                .isTrue();
        assertThat(stored.get("name").asText())
                .as("stored response_body name must match the request")
                .isEqualTo(uniqueName);
    }
}
