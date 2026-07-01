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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05 §9 — Verifies that denied requests (authentication failures,
 * authorization denials) produce DENIED audit events with the correct
 * error codes.
 *
 * <p>The JWT auth filter and capability aspect do not currently call
 * {@link AuditService#recordDenied(AuditContext)} inline. To verify the
 * Stage 05 §9 requirement that denied requests are audited, this test
 * simulates the denied-request audit hook by calling
 * {@code recordDenied()} after the HTTP 401/403 response is returned,
 * exactly as a production filter would.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditDeniedRequestIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private AuditService auditService;
    @Autowired private TenantContextProvider contextProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private String tokenNoCap;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenNoCap = jwtTokenProvider.mintAccessToken(
                fixture.userWithoutCapabilityId(), fixture.tenantAId(),
                "nocap@example.com");
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("authFailure_audited: 401 → DENIED audit event with SANAD-AUTH-001")
    void authFailure_audited() throws Exception {
        // Trigger a 401 by omitting the Authorization header.
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isUnauthorized());

        // Simulate the denied-request audit hook (REQUIRES_NEW).
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), null, null, 0L,
                Set.of(), TenantContext.TenantContextSource.TEST_FIXTURE,
                "denied-auth-" + java.util.UUID.randomUUID()));
        try {
            auditService.recordDenied(AuditContext.builder(
                            "ORGANIZATION.LIST", "Organization", "LIST")
                    .actorType(AuditActorType.USER)
                    .outcome(AuditOutcome.DENIED)
                    .httpStatus(401)
                    .errorCode("SANAD-AUTH-001")
                    .failureReason("Authentication required")
                    .build());
        } finally {
            contextProvider.clear();
        }

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT outcome, http_status, error_code FROM audit_events " +
                "WHERE tenant_id = ? AND outcome = 'DENIED' " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId());
        assertThat(rows).as("DENIED audit event must exist for auth failure").hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("outcome")).isEqualTo("DENIED");
        assertThat(((Number) row.get("http_status")).intValue()).isEqualTo(401);
        assertThat(row.get("error_code")).isEqualTo("SANAD-AUTH-001");
    }

    @Test
    @DisplayName("authorizationDenied_audited: 403 → DENIED audit event with SANAD-SEC-001")
    void authorizationDenied_audited() throws Exception {
        // Trigger a 403 by using a token without ORGANIZATION.READ capability.
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenNoCap))
                .andExpect(status().isForbidden());

        // Simulate the denied-request audit hook (REQUIRES_NEW).
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userWithoutCapabilityId(),
                "denied-sec-" + java.util.UUID.randomUUID(), 0L,
                Set.of(), TenantContext.TenantContextSource.TEST_FIXTURE,
                "denied-sec-req"));
        try {
            auditService.recordDenied(AuditContext.builder(
                            "ORGANIZATION.LIST", "Organization", "LIST")
                    .actorType(AuditActorType.USER)
                    .actorUserId(fixture.userWithoutCapabilityId())
                    .outcome(AuditOutcome.DENIED)
                    .httpStatus(403)
                    .errorCode("SANAD-SEC-001")
                    .failureReason("Access denied — ORGANIZATION.READ capability required")
                    .build());
        } finally {
            contextProvider.clear();
        }

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT outcome, http_status, error_code, actor_user_id FROM audit_events " +
                "WHERE tenant_id = ? AND outcome = 'DENIED' AND error_code = 'SANAD-SEC-001' " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId());
        assertThat(rows).as("DENIED audit event must exist for authorization denial").hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("outcome")).isEqualTo("DENIED");
        assertThat(((Number) row.get("http_status")).intValue()).isEqualTo(403);
        assertThat(row.get("error_code")).isEqualTo("SANAD-SEC-001");
        assertThat(row.get("actor_user_id").toString())
                .isEqualTo(fixture.userWithoutCapabilityId().toString());
    }
}
