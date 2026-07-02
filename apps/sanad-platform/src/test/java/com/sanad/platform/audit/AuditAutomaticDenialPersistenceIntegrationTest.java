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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.2.1 §16 — Verifies that denial audit events are AUTOMATICALLY
 * persisted when an HTTP 401 or 403 occurs.
 *
 * <p>Each test must:</p>
 * <ol>
 *   <li><b>Execute the HTTP request</b> that triggers a denial (missing JWT,
 *       malformed JWT, or missing capability).</li>
 *   <li><b>Verify the HTTP status</b> (401 with {@code SANAD-AUTH-001} or
 *       403 with {@code SANAD-SEC-001}).</li>
 *   <li><b>Query the {@code audit_events} table</b> via the fixture
 *       DataSource to verify that a row with {@code outcome='DENIED'}
 *       was automatically created for the corresponding tenant.</li>
 * </ol>
 *
 * <p>Stage 05A.2.1 §8 — The denial audit is written by the production
 * denial-request audit hook (in REQUIRES_NEW transaction) so that it
 * persists even if the request fails before reaching the controller.
 * It uses {@code AuditService.recordDenied()} internally.</p>
 *
 * <p>Stage 05A.1 §13 — Each HTTP request uses a real JWT (or no JWT, for
 * the 401 case) through the real filter chain via MockMvc.</p>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete
 * tenants (FK RESTRICT from audit_events). Each test uses the fixture's
 * unique tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditAutomaticDenialPersistenceIntegrationTest {

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

    /**
     * Counts DENIED audit events for tenant A.
     */
    private int countDeniedAuditForTenant(UUID tenantId) throws Exception {
        String sql = "SELECT COUNT(*) FROM audit_events "
                + "WHERE tenant_id = ? AND outcome = 'DENIED'";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Reads the most recent DENIED audit event for the given tenant and
     * returns its action and error_code, so the test can assert that the
     * denial was attributed correctly.
     */
    private String[] readLatestDeniedAudit(UUID tenantId) throws Exception {
        String sql = "SELECT action, error_code FROM audit_events "
                + "WHERE tenant_id = ? AND outcome = 'DENIED' "
                + "ORDER BY occurred_at DESC, created_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{rs.getString("action"), rs.getString("error_code")};
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("missingJwt_401_deniedAuditPersisted: no Authorization header → 401 SANAD-AUTH-001 + DENIED audit row")
    void missingJwt_401_deniedAuditPersisted() throws Exception {
        int before = countDeniedAuditForTenant(fixture.tenantAId());

        // (a) Execute HTTP request with no Authorization header.
        // (b) Verify HTTP status is 401 with SANAD-AUTH-001 error code.
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org-401\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));

        // (c) Verify a DENIED audit row was automatically persisted.
        int after = countDeniedAuditForTenant(fixture.tenantAId());
        assertThat(after)
                .as("a DENIED audit event must be automatically persisted on 401 (was %d, now %d)",
                        before, after)
                .isGreaterThan(before);
    }

    @Test
    @DisplayName("malformedJwt_401_deniedAuditPersisted: Bearer not-a-jwt → 401 SANAD-AUTH-001 + DENIED audit row")
    void malformedJwt_401_deniedAuditPersisted() throws Exception {
        int before = countDeniedAuditForTenant(fixture.tenantAId());

        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org-malformed\"}")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));

        int after = countDeniedAuditForTenant(fixture.tenantAId());
        assertThat(after)
                .as("a DENIED audit event must be automatically persisted on malformed JWT (401)")
                .isGreaterThan(before);
    }

    @Test
    @DisplayName("missingCapability_403_deniedAuditPersisted: valid JWT but no ORGANIZATION.CREATE → 403 SANAD-SEC-001 + DENIED audit row")
    void missingCapability_403_deniedAuditPersisted() throws Exception {
        // userWithoutCapId has an ACTIVE membership but NO role grant and therefore
        // no ORGANIZATION.CREATE capability.
        String noCapToken = jwtTokenProvider.mintAccessToken(
                fixture.userWithoutCapabilityId(), fixture.tenantAId(), "nocap@example.com");

        int before = countDeniedAuditForTenant(fixture.tenantAId());

        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org-403\"}")
                        .header("Authorization", "Bearer " + noCapToken)
                        .header("Idempotency-Key", "denied-403-" + UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));

        int after = countDeniedAuditForTenant(fixture.tenantAId());
        assertThat(after)
                .as("a DENIED audit event must be automatically persisted on 403 (capability denied)")
                .isGreaterThan(before);

        // The DENIED audit row should carry the action and error_code.
        String[] latest = readLatestDeniedAudit(fixture.tenantAId());
        assertThat(latest)
                .as("a DENIED audit row must exist with action and error_code")
                .isNotNull();
        assertThat(latest[1])
                .as("DENIED audit row must carry SANAD-SEC-001 error_code")
                .isEqualTo("SANAD-SEC-001");
    }

    @Test
    @DisplayName("deniedAuditIsIndependentOfBusinessTransaction: 401 happens before TenantContextFilter, but DENIED audit still persists")
    void deniedAuditIsIndependentOfBusinessTransaction() throws Exception {
        int before = countDeniedAuditForTenant(fixture.tenantAId());

        // Trigger a 401 by using an unknown user UUID in the JWT.
        String unknownUserToken = jwtTokenProvider.mintAccessToken(
                UUID.randomUUID(), fixture.tenantAId(), "unknown@example.com");
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + unknownUserToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));

        int after = countDeniedAuditForTenant(fixture.tenantAId());
        assertThat(after)
                .as("denial audit must persist independently of the business transaction "
                        + "(REQUIRES_NEW propagation on recordDenied)")
                .isGreaterThan(before);
    }
}
