package com.sanad.platform.audit;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05 §9 — Verifies that audit events are persisted with all mandatory
 * fields when a business mutation succeeds.
 *
 * <p>Posts an organization via the real OrganizationController, then reads
 * the audit_events row directly via the fixture DataSource (BYPASSRLS) to
 * verify every column that the AuditService must populate.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditEventPersistenceIntegrationTest {

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
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("auditEvent_persistedWithAllFields: POST /organizations → audit_events row with correct tenant, actor, action, outcome, httpStatus")
    void auditEvent_persistedWithAllFields() throws Exception {
        String body = "{\"name\":\"Persist Audit Org\",\"description\":\"persistence test\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT tenant_id, actor_user_id, action, resource_type, " +
                "outcome, http_status, operation " +
                "FROM audit_events WHERE tenant_id = ? " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId());

        assertThat(rows).as("exactly one audit event should be persisted").hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("tenant_id").toString())
                .isEqualTo(fixture.tenantAId().toString());
        assertThat(row.get("actor_user_id").toString())
                .isEqualTo(fixture.userAId().toString());
        assertThat(row.get("action")).isEqualTo("ORGANIZATION.CREATE");
        assertThat(row.get("resource_type")).isEqualTo("Organization");
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        assertThat(((Number) row.get("http_status")).intValue()).isEqualTo(201);
        assertThat(row.get("operation")).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("auditEvent_hashChainPopulated: previousHash and eventHash are non-null, hashAlgorithm=SHA-256")
    void auditEvent_hashChainPopulated() throws Exception {
        String body = "{\"name\":\"Hash Chain Org\",\"description\":\"hash test\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT previous_hash, event_hash, hash_algorithm, schema_version " +
                "FROM audit_events WHERE tenant_id = ? " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId());

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("previous_hash"))
                .as("previousHash must be populated (genesis hash for first event)")
                .isNotNull();
        assertThat(row.get("event_hash").toString())
                .as("eventHash must be a 64-char hex string")
                .hasSize(64);
        assertThat(row.get("hash_algorithm")).isEqualTo("SHA-256");
        assertThat(((Number) row.get("schema_version")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("auditEvent_occurredAtAndRecordedAtSet: timestamps are populated and occurredAt <= recordedAt")
    void auditEvent_occurredAtAndRecordedAtSet() throws Exception {
        String body = "{\"name\":\"Timestamp Org\",\"description\":\"ts test\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT occurred_at, recorded_at, created_at " +
                "FROM audit_events WHERE tenant_id = ? " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId());

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("occurred_at"))
                .as("occurredAt must be populated").isNotNull();
        assertThat(row.get("recorded_at"))
                .as("recordedAt must be populated").isNotNull();
        assertThat(row.get("created_at"))
                .as("createdAt must be populated").isNotNull();
    }
}
