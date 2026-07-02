package com.sanad.platform.audit;

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
 * Stage 05A.1 §9 — Verifies the transactional consistency between business
 * mutations and audit events through the real HTTP filter chain.
 *
 * <p>Stage 05A.1 §13 — All HTTP requests use real JWTs through MockMvc. The
 * {@code TenantContextFilter} establishes the verified TenantContext; the
 * {@code OrganizationService.createOrganization} calls {@link com.sanad.platform.audit.service.AuditService#record}
 * in the SAME transaction as the business mutation.</p>
 *
 * <p>Stage 05A.1 §22 — Each POST carries an {@code Idempotency-Key} header.</p>
 *
 * <p>Verifications:</p>
 * <ul>
 *   <li>Business success → audit committed in the same transaction.</li>
 *   <li>Business failure (duplicate name 409) → no SUCCESS audit recorded
 *       (rolled back with the business transaction).</li>
 *   <li>Denial (401 from missing JWT) → no SUCCESS audit recorded for the
 *       denied request — the JwtAuthenticationFilter rejects the request
 *       before the TenantContextFilter runs, so no TenantContext is
 *       established and no business audit event is written.</li>
 * </ul>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditTransactionBoundaryIntegrationTest {

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

    private int countSuccessAuditForOrgCreate() throws Exception {
        String sql = "SELECT COUNT(*) FROM audit_events "
                + "WHERE tenant_id = ? AND action = 'ORGANIZATION.CREATE' "
                + "AND outcome = 'SUCCESS'";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void postOrganization(String name, String idempotencyKey, int expectedStatus) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"txn test\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("businessSuccess_auditCommitted: POST /organizations succeeds → both org and audit committed")
    void businessSuccess_auditCommitted() throws Exception {
        int before = countSuccessAuditForOrgCreate();
        postOrganization("Txn Success Org", "txn-success-" + UUID.randomUUID(), 201);

        int after = countSuccessAuditForOrgCreate();
        assertThat(after)
                .as("a SUCCESS audit event must be committed when the business mutation commits")
                .isGreaterThan(before);
    }

    @Test
    @DisplayName("businessFailure_auditRolledBack: duplicate org name → 409 → no SUCCESS audit recorded")
    void businessFailure_auditRolledBack() throws Exception {
        // First create succeeds.
        String sharedName = "Txn Fail Org " + UUID.randomUUID();
        postOrganization(sharedName, "txn-fail-1-" + UUID.randomUUID(), 201);

        int afterFirst = countSuccessAuditForOrgCreate();

        // Second create with the same name must fail with 409.
        postOrganization(sharedName, "txn-fail-2-" + UUID.randomUUID(), 409);

        int afterSecond = countSuccessAuditForOrgCreate();

        // No additional SUCCESS audit should exist — the failed operation
        // either never wrote one or its transaction (and audit) rolled back.
        assertThat(afterSecond)
                .as("no SUCCESS audit should be persisted when the business mutation fails")
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("denialRequest_noSuccessAuditRecorded: 401 → JwtAuthFilter rejects before TenantContextFilter → no SUCCESS audit (denial audit independent)")
    void denialRequest_noSuccessAuditRecorded() throws Exception {
        int before = countSuccessAuditForOrgCreate();

        // Trigger a real 401 by omitting the Authorization header. The
        // JwtAuthenticationFilter rejects the request before the
        // TenantContextFilter runs, so no TenantContext is established
        // and no business audit event is written for this request.
        //
        // Stage 05A.2.1 §8 — The denial audit (if any) is written via
        // AuditService.recordDenied() in a REQUIRES_NEW transaction,
        // so it commits independently of the business transaction. This
        // test verifies that no SUCCESS audit is recorded for the denied
        // request — the denial audit (if produced) is tracked separately
        // under outcome='DENIED', not under outcome='SUCCESS'.
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org\"}"))
                .andExpect(status().isUnauthorized());

        int after = countSuccessAuditForOrgCreate();
        assertThat(after)
                .as("no SUCCESS audit should be recorded for a denied request "
                        + "(denial audit is independent — outcome='DENIED', not 'SUCCESS')")
                .isEqualTo(before);
    }
}
