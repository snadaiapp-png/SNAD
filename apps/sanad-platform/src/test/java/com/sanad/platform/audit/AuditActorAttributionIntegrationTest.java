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
 * Stage 05A.1 §13 — Verifies actor identity attribution in audit events.
 *
 * <p>Stage 05A.1 §13 — Actor trust boundary: actor identity is taken from
 * the verified {@link com.sanad.platform.security.tenant.TenantContext}
 * ONLY. The {@code TenantContextFilter} establishes the context from
 * verified JWT claims (no {@code TEST_FIXTURE} source); the
 * {@link com.sanad.platform.audit.service.AuditService} ignores any
 * caller-supplied actor identity.</p>
 *
 * <p>Verifications:</p>
 * <ul>
 *   <li>POST with tokenA → actorType=USER, actorUserId=userA,
 *       session_id+jwt_id populated from the JWT jti.</li>
 *   <li>X-Request-Id header → audit event requestId matches.</li>
 *   <li>BACKGROUND_JOB actor (via direct AuditService call with a
 *       JWT_CLAIM-sourced TenantContext) → actorType=BACKGROUND_JOB,
 *       actorService+actorDisplayName stored.</li>
 * </ul>
 *
 * <p>Stage 05A.1 §22 — Each POST carries an {@code Idempotency-Key} header.</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
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
    @Autowired private com.sanad.platform.audit.service.AuditService auditService;
    @Autowired private com.sanad.platform.security.tenant.TenantContextProvider contextProvider;
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
    @DisplayName("userActorAttributed: POST /organizations with tokenA → actorType=USER, actorUserId=userA, session_id+jwt_id populated")
    void userActorAttributed() throws Exception {
        String body = "{\"name\":\"Actor Attribution Org\",\"description\":\"attr\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", "actor-user-" + UUID.randomUUID()))
                .andExpect(status().isCreated());

        String sql = "SELECT actor_type, actor_user_id, session_id, jwt_id "
                + "FROM audit_events WHERE tenant_id = ? "
                + "AND action = 'ORGANIZATION.CREATE' "
                + "ORDER BY occurred_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("audit event must exist for org creation").isTrue();
                assertThat(rs.getString("actor_type")).isEqualTo("USER");
                assertThat(rs.getObject("actor_user_id").toString())
                        .isEqualTo(fixture.userAId().toString());
                // Stage 05A.1 §13 — session_id and jwt_id must be populated
                // from the verified JWT jti (not from the caller-supplied
                // AuditContext).
                assertThat(rs.getString("session_id"))
                        .as("session_id must be populated (JWT jti)").isNotNull();
                assertThat(rs.getString("jwt_id"))
                        .as("jwt_id must be populated").isNotNull();
            }
        }
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
                        .header("Idempotency-Key", "actor-rid-" + UUID.randomUUID())
                        .header("X-Request-Id", customRequestId))
                .andExpect(status().isCreated());

        String sql = "SELECT request_id FROM audit_events "
                + "WHERE tenant_id = ? AND action = 'ORGANIZATION.CREATE' "
                + "ORDER BY occurred_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("request_id"))
                        .isEqualTo(customRequestId);
            }
        }
    }

    @Test
    @DisplayName("backgroundJobActorAttributed: AuditService.record with BACKGROUND_JOB type → actorType=BACKGROUND_JOB")
    void backgroundJobActorAttributed() throws Exception {
        // Establish a JWT_CLAIM-sourced TenantContext (the same source the
        // filter chain establishes for HTTP requests). Background jobs
        // typically run with a BACKGROUND_JOB source, but the actor
        // attribution logic in AuditService is source-agnostic — it takes
        // the userId from the verified TenantContext and the actorType
        // from the AuditContext.
        String token = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        String jti = claims != null ? claims.getId() : "jti-bg-" + UUID.randomUUID();
        contextProvider.setContext(new com.sanad.platform.security.tenant.TenantContext(
                fixture.tenantAId(), fixture.userAId(), jti, 0L,
                java.util.Set.of(),
                com.sanad.platform.security.tenant.TenantContext.TenantContextSource.JWT_CLAIM,
                "bg-job-req-" + UUID.randomUUID()));
        try {
            auditService.record(com.sanad.platform.audit.service.AuditContext.builder(
                            "BACKGROUND.CLEANUP", "AuditEvent", "CLEANUP")
                    .actorType(com.sanad.platform.audit.domain.AuditActorType.BACKGROUND_JOB)
                    .actorService("audit-retention-job")
                    .actorDisplayName("Audit Retention Scheduler")
                    .outcome(com.sanad.platform.audit.domain.AuditOutcome.SUCCESS)
                    .httpStatus(200)
                    .build());
        } finally {
            contextProvider.clear();
        }

        String sql = "SELECT actor_type, actor_service, actor_display_name "
                + "FROM audit_events WHERE tenant_id = ? "
                + "AND action = 'BACKGROUND.CLEANUP' "
                + "ORDER BY occurred_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("background job audit event must exist").isTrue();
                assertThat(rs.getString("actor_type")).isEqualTo("BACKGROUND_JOB");
                assertThat(rs.getString("actor_service")).isEqualTo("audit-retention-job");
                assertThat(rs.getString("actor_display_name")).isEqualTo("Audit Retention Scheduler");
            }
        }
    }
}
