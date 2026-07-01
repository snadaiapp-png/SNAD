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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §6 — Verifies that audit events are persisted with all
 * mandatory fields, including the new {@code sequence_number}, when a
 * business mutation succeeds through the real HTTP filter chain.
 *
 * <p>Stage 05A.1 §13 — Each test POSTs to {@code /api/v1/organizations}
 * with a real JWT (no manual TenantContext setup, no TEST_FIXTURE source).
 * The {@code TenantContextFilter} establishes the TenantContext from
 * verified JWT claims; the {@code AuditService} takes actor identity from
 * that context only.</p>
 *
 * <p>Stage 05A.1 §22 — Each POST carries an {@code Idempotency-Key} header
 * because the endpoint is annotated with {@code @IdempotentOperation}.</p>
 *
 * <p>All DB reads use {@link PreparedStatement} (no string concatenation).
 * Timestamps use {@link java.sql.Timestamp#from(java.time.Instant)} when
 * needed (not used in this class — only read-side queries here).</p>
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

    private void postOrganization(String name, String idempotencyKey) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"persistence test\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("auditEvent_persistedWithAllFields: POST /organizations → audit_events row with correct tenant, actor, action, outcome, httpStatus, sequence_number")
    void auditEvent_persistedWithAllFields() throws Exception {
        postOrganization("Persist Audit Org", "persistence-key-" + UUID.randomUUID());

        String sql = "SELECT tenant_id, actor_user_id, action, resource_type, "
                + "outcome, http_status, operation, sequence_number "
                + "FROM audit_events WHERE tenant_id = ? "
                + "ORDER BY occurred_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("exactly one audit event should be persisted").isTrue();
                assertThat(rs.getObject("tenant_id").toString())
                        .isEqualTo(fixture.tenantAId().toString());
                assertThat(rs.getObject("actor_user_id").toString())
                        .isEqualTo(fixture.userAId().toString());
                assertThat(rs.getString("action")).isEqualTo("ORGANIZATION.CREATE");
                assertThat(rs.getString("resource_type")).isEqualTo("Organization");
                assertThat(rs.getString("outcome")).isEqualTo("SUCCESS");
                assertThat(rs.getInt("http_status")).isEqualTo(201);
                assertThat(rs.getString("operation")).isEqualTo("CREATE");
                // Stage 05A.1 §8 — sequence_number must be populated.
                assertThat(rs.getObject("sequence_number"))
                        .as("sequence_number must be populated (Stage 05A.1 §8)")
                        .isNotNull();
                assertThat(rs.getLong("sequence_number"))
                        .as("sequence_number must be >= 1")
                        .isGreaterThanOrEqualTo(1L);
            }
        }
    }

    @Test
    @DisplayName("auditEvent_hashChainPopulated: previousHash and eventHash are non-null, hashAlgorithm=SHA-256")
    void auditEvent_hashChainPopulated() throws Exception {
        postOrganization("Hash Chain Org", "hashchain-key-" + UUID.randomUUID());

        String sql = "SELECT previous_hash, event_hash, hash_algorithm, schema_version "
                + "FROM audit_events WHERE tenant_id = ? "
                + "ORDER BY occurred_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("previous_hash"))
                        .as("previousHash must be populated (genesis hash for first event)")
                        .isNotNull();
                String eventHash = rs.getString("event_hash");
                assertThat(eventHash)
                        .as("eventHash must be a 64-char hex string")
                        .hasSize(64);
                assertThat(rs.getString("hash_algorithm")).isEqualTo("SHA-256");
                assertThat(rs.getInt("schema_version")).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("auditEvent_occurredAtAndRecordedAtSet: timestamps are populated and occurredAt <= recordedAt")
    void auditEvent_occurredAtAndRecordedAtSet() throws Exception {
        postOrganization("Timestamp Org", "ts-key-" + UUID.randomUUID());

        String sql = "SELECT occurred_at, recorded_at, created_at "
                + "FROM audit_events WHERE tenant_id = ? "
                + "ORDER BY occurred_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                java.sql.Timestamp occurredAt = rs.getTimestamp("occurred_at");
                java.sql.Timestamp recordedAt = rs.getTimestamp("recorded_at");
                java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                assertThat(occurredAt).as("occurredAt must be populated").isNotNull();
                assertThat(recordedAt).as("recordedAt must be populated").isNotNull();
                assertThat(createdAt).as("createdAt must be populated").isNotNull();
                assertThat(occurredAt)
                        .as("occurredAt must be <= recordedAt")
                        .isBeforeOrEqualTo(recordedAt);
            }
        }
    }
}
