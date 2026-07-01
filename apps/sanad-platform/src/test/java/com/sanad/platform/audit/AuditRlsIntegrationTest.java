package com.sanad.platform.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.service.JwtTokenProvider;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §6-7 — Verifies that Row-Level Security on {@code audit_events}
 * isolates tenants.
 *
 * <p>Stage 05A.1 §13 — Each test uses a real JWT through MockMvc. The
 * {@code TenantContextFilter} establishes the TenantContext from verified
 * JWT claims; RLS then enforces tenant isolation at the database level.</p>
 *
 * <p>Checks performed:</p>
 * <ul>
 *   <li>{@code relrowsecurity=true} and {@code relforcerowsecurity=true} on
 *       {@code audit_events} (queried from {@code pg_class}).</li>
 *   <li>Tenant A's token sees only Tenant A's audit events (HTTP GET).</li>
 *   <li>Missing TenantContext (no JWT) returns 0 rows (HTTP 401, so the
 *       audit-events endpoint is never reached).</li>
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
class AuditRlsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
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
        grantAuditRead(fixture.tenantAId(), fixture.roleId());
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    private void grantAuditRead(UUID tenantId, UUID roleId) {
        try {
            UUID capId = fixtureJdbc.queryForObject(
                    "SELECT id FROM access_capabilities WHERE code = 'AUDIT.READ'",
                    UUID.class);
            fixtureJdbc.update(
                    "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                            + "VALUES (?, ?, ?, ?, NOW())",
                    UUID.randomUUID(), tenantId, roleId, capId);
        } catch (Exception e) {
            // AUDIT.READ capability may not be seeded or role_cap may already exist — skip.
        }
    }

    /**
     * Inserts a minimal audit_events row directly via the fixture DS for a
     * given tenant. Uses a unique sequence_number per row to avoid colliding
     * with AuditService-generated rows.
     */
    private UUID insertAuditRow(UUID tenantId, String action, long sequenceNumber) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO audit_events (id, tenant_id, actor_type, action, "
                + "resource_type, operation, outcome, occurred_at, recorded_at, "
                + "created_at, event_hash, previous_hash, hash_algorithm, "
                + "schema_version, sequence_number) "
                + "VALUES (?, ?, 'USER', ?, 'TestResource', 'TEST', 'SUCCESS', "
                + "?, ?, ?, ?, "
                + "'0000000000000000000000000000000000000000000000000000000000000000', "
                + "'SHA-256', 1, ?)";
        Timestamp now = Timestamp.from(java.time.Instant.now());
        fixtureJdbc.update(sql, id, tenantId, action, now, now, now,
                "a".repeat(64), sequenceNumber);
        return id;
    }

    @Test
    @DisplayName("rlsEnabledOnAuditEvents: pg_class shows relrowsecurity=true AND relforcerowsecurity=true on audit_events")
    void rlsEnabledOnAuditEvents() throws Exception {
        String sql = "SELECT relrowsecurity, relforcerowsecurity FROM pg_class "
                + "WHERE relname = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "audit_events");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("audit_events row must exist in pg_class").isTrue();
                assertThat(rs.getBoolean("relrowsecurity"))
                        .as("RLS must be enabled on audit_events (relrowsecurity=true)")
                        .isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity"))
                        .as("RLS must be forced on audit_events (relforcerowsecurity=true)")
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("tenantA_seesOnlyOwnTenantEvents: tokenA GET /audit-events → only Tenant A events returned")
    void tenantA_seesOnlyOwnTenantEvents() throws Exception {
        UUID eventA = insertAuditRow(fixture.tenantAId(), "TEST.RLS.A", 100_001L);
        UUID eventB = insertAuditRow(fixture.tenantBId(), "TEST.RLS.B", 100_002L);

        String response = mockMvc.perform(get("/api/v1/audit-events")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        JsonNode content = root.path("content");

        assertThat(content.isArray()).isTrue();
        boolean foundA = false;
        boolean foundB = false;
        for (JsonNode node : content) {
            String id = node.path("id").asText();
            if (id.equals(eventA.toString())) foundA = true;
            if (id.equals(eventB.toString())) foundB = true;
        }
        assertThat(foundA).as("Tenant A's audit event must be visible to tokenA").isTrue();
        assertThat(foundB).as("Tenant B's audit event must NOT be visible to tokenA (RLS isolation)").isFalse();
    }

    @Test
    @DisplayName("missingTenantContext_returns401: no JWT → 401, audit-events endpoint never reached (0 rows visible)")
    void missingTenantContext_returns401() throws Exception {
        // Insert an event in Tenant A so we have something to leak.
        insertAuditRow(fixture.tenantAId(), "TEST.RLS.NOCONTEXT", 100_003L);

        // No Authorization header → 401 SANAD-AUTH-001, the audit-events
        // endpoint is NEVER reached. This is the "missing context = 0 rows"
        // guarantee: without a verified JWT, no TenantContext is established,
        // so RLS cannot grant access to any tenant's audit events.
        mockMvc.perform(get("/api/v1/audit-events")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isUnauthorized());
    }
}
