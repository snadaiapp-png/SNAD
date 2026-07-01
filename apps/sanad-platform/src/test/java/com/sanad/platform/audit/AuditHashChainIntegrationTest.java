package com.sanad.platform.audit;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditIntegrityVerificationService;
import com.sanad.platform.audit.service.AuditService;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05 §11 — Verifies the audit hash-chain integrity verification.
 *
 * <p>After recording events via the AuditService, the chain should verify
 * as valid. If a row is inserted with a deliberately-wrong eventHash
 * (simulating tampering), the verification must detect it and report
 * {@code valid=false} with the broken event ID.</p>
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
    @Autowired private AuditService auditService;
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
                    "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())",
                    UUID.randomUUID(), tenantId, roleId, capId);
        } catch (Exception e) {
            // capability not seeded or already granted — skip
        }
    }

    private void setTenantContext() {
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(),
                "test-jti-" + UUID.randomUUID(), 0L,
                Set.of(), TenantContext.TenantContextSource.TEST_FIXTURE,
                "test-request-id"));
    }

    private void recordEvent(String action) {
        setTenantContext();
        try {
            auditService.record(AuditContext.builder(
                            action, "TestResource", "TEST")
                    .actorType(AuditActorType.USER)
                    .outcome(AuditOutcome.SUCCESS)
                    .httpStatus(200)
                    .build());
        } finally {
            contextProvider.clear();
        }
    }

    @Test
    @DisplayName("hashChain_verifiesForSingleEvent: 1 audit event → integrity valid, eventsChecked=1")
    void hashChain_verifiesForSingleEvent() throws Exception {
        recordEvent("TEST.HASH.SINGLE");

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
        recordEvent("TEST.HASH.MULTI.1");
        recordEvent("TEST.HASH.MULTI.2");
        recordEvent("TEST.HASH.MULTI.3");

        String response = mockMvc.perform(get("/api/v1/audit-events/integrity")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
        assertThat(root.path("valid").asBoolean()).isTrue();
        assertThat(root.path("eventsChecked").asInt())
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("hashChain_detectsTampering: row with wrong eventHash → integrity valid=false, firstBrokenEventId set")
    void hashChain_detectsTampering() throws Exception {
        // Record a legitimate event first.
        recordEvent("TEST.HASH.LEGIT");

        // Insert a tampered row directly via fixture DS with a deliberately
        // wrong eventHash (does not match what the hash-chain service would
        // compute). The INSERT trigger does NOT block this — only
        // UPDATE/DELETE are blocked. The integrity verifier will recompute
        // the hash and detect the mismatch.
        UUID tamperedId = UUID.randomUUID();
        fixtureJdbc.update(
                "INSERT INTO audit_events (id, tenant_id, actor_type, action, " +
                "resource_type, operation, outcome, occurred_at, recorded_at, " +
                "created_at, event_hash, previous_hash, hash_algorithm, schema_version) " +
                "VALUES (?, ?, 'USER', 'TEST.HASH.TAMPERED', 'TestResource', 'TEST', " +
                "'SUCCESS', NOW(), NOW(), NOW(), ?, " +
                "'0000000000000000000000000000000000000000000000000000000000000000', 'SHA-256', 1)",
                tamperedId, fixture.tenantAId(), "deadbeef".repeat(8));

        // Invoke the verification service directly (it reads via the
        // repository which is RLS-scoped, so we set the tenant context).
        setTenantContext();
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
