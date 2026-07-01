package com.sanad.platform.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.audit.service.AuditIntegrityVerificationService;
import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
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
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §8-9 — Verifies the audit hash-chain integrity verification.
 *
 * <p>Stage 05A.1 §13 — HTTP requests use real JWTs through MockMvc. For
 * tamper detection (which requires inserting a row with a wrong eventHash
 * via the fixture DS), the integrity verification service is invoked
 * directly with a {@link TenantContext.TenantContextSource#JWT_CLAIM}
 * context (the same source the filter chain uses).</p>
 *
 * <p>Verifications:</p>
 * <ul>
 *   <li>Single event → chain valid, eventsChecked=1.</li>
 *   <li>Multiple events → chain valid, eventsChecked≥3.</li>
 *   <li>Tampered row (wrong eventHash) → chain invalid, firstBrokenEventId set.</li>
 * </ul>
 *
 * <p>All DB reads use {@link PreparedStatement}. Timestamps use
 * {@link Timestamp#from(java.time.Instant)}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditHashChainIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private AuditIntegrityVerificationService integrityService;
    @Autowired private TenantContextProvider contextProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

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
        grantAuditIntegrityVerify(fixture.tenantAId(), fixture.roleId());
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    private void grantAuditIntegrityVerify(UUID tenantId, UUID roleId) {
        try {
            UUID capId = fixtureJdbc.queryForObject(
                    "SELECT id FROM access_capabilities WHERE code = 'AUDIT.INTEGRITY_VERIFY'",
                    UUID.class);
            fixtureJdbc.update(
                    "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                            + "VALUES (?, ?, ?, ?, NOW())",
                    UUID.randomUUID(), tenantId, roleId, capId);
        } catch (Exception e) {
            // capability not seeded or already granted — skip
        }
    }

    /**
     * Establishes a JWT_CLAIM-sourced TenantContext (same source the filter
     * chain uses) with the verified user/tenant IDs from the fixture.
     */
    private void setJwtClaimContext() {
        String token = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(), jti, 0L,
                Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                "test-request-id-" + UUID.randomUUID()));
    }

    private void postOrganization(String name, String idempotencyKey) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"hash-chain test\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("hashChain_verifiesForSingleEvent: 1 audit event → integrity valid, eventsChecked=1")
    void hashChain_verifiesForSingleEvent() throws Exception {
        postOrganization("Hash Single Org", "hash-single-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/audit-events/integrity")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.eventsChecked").value(1));
    }

    @Test
    @DisplayName("hashChain_verifiesForMultipleEvents: 3 audit events → integrity valid, eventsChecked≥3")
    void hashChain_verifiesForMultipleEvents() throws Exception {
        postOrganization("Hash Multi 1", "hash-multi-1-" + UUID.randomUUID());
        postOrganization("Hash Multi 2", "hash-multi-2-" + UUID.randomUUID());
        postOrganization("Hash Multi 3", "hash-multi-3-" + UUID.randomUUID());

        String response = mockMvc.perform(get("/api/v1/audit-events/integrity")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(response);
        assertThat(root.path("valid").asBoolean()).isTrue();
        assertThat(root.path("eventsChecked").asInt())
                .isGreaterThanOrEqualTo(3);
        // calculatedHeadHash == storedHeadHash (Stage 05A.1 §9)
        assertThat(root.path("calculatedHeadHash").asText())
                .as("calculatedHeadHash must equal storedHeadHash")
                .isEqualTo(root.path("storedHeadHash").asText());
    }

    @Test
    @DisplayName("hashChain_detectsTampering: row with wrong eventHash → integrity valid=false, firstBrokenEventId set")
    void hashChain_detectsTampering() throws Exception {
        // Record a legitimate event first via the real HTTP stack.
        postOrganization("Hash Legit Org", "hash-legit-" + UUID.randomUUID());

        // Insert a tampered row directly via fixture DS with a deliberately
        // wrong eventHash. The INSERT trigger does NOT block this — only
        // UPDATE/DELETE are blocked. The integrity verifier will recompute
        // the hash and detect the mismatch.
        UUID tamperedId = UUID.randomUUID();
        String sql = "INSERT INTO audit_events (id, tenant_id, actor_type, action, "
                + "resource_type, operation, outcome, occurred_at, recorded_at, "
                + "created_at, event_hash, previous_hash, hash_algorithm, "
                + "schema_version, sequence_number) "
                + "VALUES (?, ?, 'USER', 'TEST.HASH.TAMPERED', 'TestResource', "
                + "'TEST', 'SUCCESS', ?, ?, ?, ?, "
                + "'0000000000000000000000000000000000000000000000000000000000000000', "
                + "'SHA-256', 1, ?)";
        Timestamp now = Timestamp.from(java.time.Instant.now());
        fixtureJdbc.update(sql, tamperedId, fixture.tenantAId(), now, now, now,
                "deadbeef".repeat(8), 999_998L);

        // Invoke the verification service directly (it reads via the
        // repository which is RLS-scoped, so we set a JWT_CLAIM-sourced
        // tenant context).
        setJwtClaimContext();
        AuditIntegrityVerificationService.VerificationResult result;
        try {
            result = integrityService.verifyChain(fixture.tenantAId());
        } finally {
            contextProvider.clear();
        }

        assertThat(result.valid())
                .as("integrity must be invalid when a tampered row exists")
                .isFalse();
        assertThat(result.firstBrokenEventId())
                .as("the first broken event ID must be reported")
                .isNotNull();
    }
}
