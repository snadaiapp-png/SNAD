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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05 §6 — Verifies that Row-Level Security on audit_events isolates
 * tenants. Tenant A can only see its own audit events; tenant B's events
 * are invisible (404 on direct GET, absent from list).
 *
 * <p>Grants AUDIT.READ to roleA via the fixture DS in @BeforeEach so the
 * audit query API is accessible. Then inserts audit events for both
 * tenants via the fixture DS and queries through the real HTTP stack.</p>
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
                    "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())",
                    UUID.randomUUID(), tenantId, roleId, capId);
        } catch (Exception e) {
            // AUDIT.READ capability may not be seeded or role_cap may already exist — skip.
        }
    }

    private UUID insertAuditRow(UUID tenantId, String action) {
        UUID id = UUID.randomUUID();
        fixtureJdbc.update(
                "INSERT INTO audit_events (id, tenant_id, actor_type, action, " +
                "resource_type, operation, outcome, occurred_at, recorded_at, " +
                "created_at, event_hash, previous_hash, hash_algorithm, schema_version) " +
                "VALUES (?, ?, 'USER', ?, 'TestResource', 'TEST', 'SUCCESS', " +
                "NOW(), NOW(), NOW(), ?, '0000000000000000000000000000000000000000000000000000000000000000', 'SHA-256', 1)",
                id, tenantId, action, "a".repeat(64));
        return id;
    }

    @Test
    @DisplayName("runtimeRole_seesOnlyOwnTenantEvents: tokenA GET /audit-events → only tenant A events returned")
    void runtimeRole_seesOnlyOwnTenantEvents() throws Exception {
        UUID eventA = insertAuditRow(fixture.tenantAId(), "TEST.ACTION.A");
        UUID eventB = insertAuditRow(fixture.tenantBId(), "TEST.ACTION.B");

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
        assertThat(content.size())
                .as("at least tenant A's event must be visible")
                .isGreaterThanOrEqualTo(1);

        boolean foundA = false;
        boolean foundB = false;
        for (JsonNode node : content) {
            String id = node.path("id").asText();
            if (id.equals(eventA.toString())) foundA = true;
            if (id.equals(eventB.toString())) foundB = true;
        }
        assertTrue(foundA, "Tenant A's audit event must be visible to tokenA");
        assertFalse(foundB, "Tenant B's audit event must NOT be visible to tokenA (RLS isolation)");
    }

    @Test
    @DisplayName("crossTenantAuditGet_returns404: tokenA GETs tenant B's audit event by id → 404")
    void crossTenantAuditGet_returns404() throws Exception {
        UUID eventB = insertAuditRow(fixture.tenantBId(), "TEST.ACTION.B.CROSS");

        mockMvc.perform(get("/api/v1/audit-events/{id}", eventB)
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }
}
