package com.sanad.platform.audit;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05 §8 — Verifies actor identity attribution in audit events.
 *
 * <p>For HTTP requests, the actor type, actor user ID, and session ID (JWT
 * jti) must be extracted from the verified TenantContext — never from
 * client-supplied request bodies. For background jobs, the service must
 * accept a caller-supplied {@link AuditActorType#BACKGROUND_JOB} context.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditActorAttributionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private AuditService auditService;
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
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("userActorAttributed: POST /organizations with tokenA → actorType=USER, actorUserId=userA")
    void userActorAttributed() throws Exception {
        String body = "{\"name\":\"Actor Attribution Org\",\"description\":\"attr\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT actor_type, actor_user_id, session_id, jwt_id " +
                "FROM audit_events WHERE tenant_id = ? AND action = 'ORGANIZATION.CREATE' " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId());
        assertThat(rows).as("audit event must exist for org creation").hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("actor_type")).isEqualTo("USER");
        assertThat(row.get("actor_user_id").toString())
                .isEqualTo(fixture.userAId().toString());
        // session_id and jwt_id should both be the JWT jti (non-null).
        assertThat(row.get("session_id"))
                .as("session_id must be populated (JWT jti)").isNotNull();
        assertThat(row.get("jwt_id"))
                .as("jwt_id must be populated").isNotNull();
    }

    @Test
    @DisplayName("requestIdAttributed: audit event requestId matches X-Request-Id header")
    void requestIdAttributed() throws Exception {
        String customRequestId = "req-attribution-" + UUID.randomUUID();
        String body = "{\"name\":\"Request Id Org\",\"description\":\"rid\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("X-Request-Id", customRequestId))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT request_id FROM audit_events " +
                "WHERE tenant_id = ? AND action = 'ORGANIZATION.CREATE' " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("request_id").toString())
                .isEqualTo(customRequestId);
    }

    @Test
    @DisplayName("backgroundJobActorAttributed: AuditService.record with BACKGROUND_JOB type → actorType=BACKGROUND_JOB")
    void backgroundJobActorAttributed() {
        // Set a TEST_FIXTURE TenantContext so RLS binding succeeds.
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), null, null, 0L,
                Set.of(), TenantContext.TenantContextSource.TEST_FIXTURE,
                "bg-job-req-id"));
        try {
            auditService.record(AuditContext.builder(
                            "BACKGROUND.CLEANUP", "AuditEvent", "CLEANUP")
                    .actorType(AuditActorType.BACKGROUND_JOB)
                    .actorService("audit-retention-job")
                    .actorDisplayName("Audit Retention Scheduler")
                    .outcome(AuditOutcome.SUCCESS)
                    .httpStatus(200)
                    .build());
        } finally {
            contextProvider.clear();
        }

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT actor_type, actor_service, actor_display_name " +
                "FROM audit_events WHERE tenant_id = ? AND action = 'BACKGROUND.CLEANUP' " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId());
        assertThat(rows).as("background job audit event must exist").hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("actor_type")).isEqualTo("BACKGROUND_JOB");
        assertThat(row.get("actor_service")).isEqualTo("audit-retention-job");
        assertThat(row.get("actor_display_name")).isEqualTo("Audit Retention Scheduler");
    }
}
