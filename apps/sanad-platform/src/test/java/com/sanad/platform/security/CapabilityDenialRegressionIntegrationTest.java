package com.sanad.platform.security;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.2.3 — Capability denial regression test.
 *
 * <p>Verifies the full capability-denial contract end-to-end via MockMvc
 * with real JWTs through the production filter chain (NO
 * {@code SecurityPermitAllTestConfig}):</p>
 * <ol>
 *   <li><b>HTTP 403</b> — the request is rejected with {@code SANAD-SEC-001}.</li>
 *   <li><b>Controller does not execute</b> — the
 *       {@code @RequireCapability("ORGANIZATION.CREATE")} aspect short-circuits
 *       before the controller method body runs, so no business logic is
 *       invoked.</li>
 *   <li><b>No organization row is created</b> — the database is unchanged;
 *       no phantom organization appears in the tenant's table.</li>
 *   <li><b>A DENIED audit row is persisted</b> — the denial-request audit
 *       hook (REQUIRES_NEW transaction) writes an
 *       {@code outcome='DENIED'} audit event so the denial is attributable.</li>
 * </ol>
 *
 * <p>This is the regression gate for the capability-denial path. It must
 * never be weakened or skipped. If any of the four assertions fails, the
 * platform has lost its authorization enforcement boundary.</p>
 *
 * <p>Stage 05A.1 §13 — Each test uses a real JWT through the real filter
 * chain via MockMvc (no permit-all security configuration).</p>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete
 * tenants (FK RESTRICT from audit_events). Each test uses the fixture's
 * unique tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class CapabilityDenialRegressionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private String noCapToken;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        // userWithoutCapabilityId has an ACTIVE membership (passes session
        // validation) but NO role grant → no ORGANIZATION.CREATE capability.
        noCapToken = jwtTokenProvider.mintAccessToken(
                fixture.userWithoutCapabilityId(), fixture.tenantAId(), "nocap@example.com");
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    /**
     * Counts organizations for a tenant via the fixture DataSource
     * (BYPASSRLS — can see all rows regardless of tenant context).
     */
    private int countOrgsForTenant(UUID tenantId) throws Exception {
        String sql = "SELECT COUNT(*) FROM organizations WHERE tenant_id = ?";
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
     * Returns true if an organization with the given name exists under the
     * given tenant (via fixture DataSource — BYPASSRLS).
     */
    private boolean orgNameExists(UUID tenantId, String name) throws Exception {
        String sql = "SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND name = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Counts DENIED audit events for a tenant via the fixture DataSource.
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
     * Returns the action and error_code of the most recent DENIED audit
     * event for the given tenant.
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

    /**
     * Regression gate for the capability-denial path.
     *
     * <p>A user with an ACTIVE membership but NO role grant (and therefore
     * no {@code ORGANIZATION.CREATE} capability) attempts to create an
     * organization. The platform must:</p>
     * <ul>
     *   <li>Return HTTP 403 with {@code SANAD-SEC-001}.</li>
     *   <li>NOT execute the controller's create logic (no organization row).</li>
     *   <li>Persist a DENIED audit event for attribution.</li>
     * </ul>
     */
    @Test
    @DisplayName("userWithoutCreateCapability_gets403_controllerDoesNotExecute_noOrgCreated_deniedAuditPersisted")
    void userWithoutCreateCapability_gets403_controllerDoesNotExecute_noOrgCreated_deniedAuditPersisted()
            throws Exception {
        int orgsBefore = countOrgsForTenant(fixture.tenantAId());
        int deniedBefore = countDeniedAuditForTenant(fixture.tenantAId());

        // A unique org name so we can prove no row was inserted with it.
        String deniedOrgName = "denied-cap-org-" + UUID.randomUUID();

        // === Act: POST /api/v1/organizations with a valid JWT but no capability ===
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + deniedOrgName + "\",\"description\":\"should not persist\"}")
                        .header("Authorization", "Bearer " + noCapToken)
                        .header("Idempotency-Key", "denied-cap-" + UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));

        // === Assert 1: HTTP 403 returned (verified by andExpect above) ===

        // === Assert 2: controller did not execute — no new organization row ===
        int orgsAfter = countOrgsForTenant(fixture.tenantAId());
        assertThat(orgsAfter)
                .as("no organization row must be created when ORGANIZATION.CREATE is denied "
                        + "(controller must not execute; was %d, now %d)", orgsBefore, orgsAfter)
                .isEqualTo(orgsBefore);

        // === Assert 3: the denied org name is NOT in the database ===
        assertThat(orgNameExists(fixture.tenantAId(), deniedOrgName))
                .as("the denied organization name '%s' must not appear in the organizations table",
                        deniedOrgName)
                .isFalse();

        // === Assert 4: a DENIED audit row was persisted ===
        int deniedAfter = countDeniedAuditForTenant(fixture.tenantAId());
        assertThat(deniedAfter)
                .as("a DENIED audit event must be automatically persisted on capability denial "
                        + "(was %d, now %d)", deniedBefore, deniedAfter)
                .isGreaterThan(deniedBefore);

        // === Assert 5: the DENIED audit row carries the SANAD-SEC-001 error code ===
        String[] latest = readLatestDeniedAudit(fixture.tenantAId());
        assertThat(latest)
                .as("a DENIED audit row must exist with action and error_code")
                .isNotNull();
        assertThat(latest[1])
                .as("DENIED audit row must carry SANAD-SEC-001 error_code")
                .isEqualTo("SANAD-SEC-001");
    }

    /**
     * Regression gate for runtime revocation: a user who HAD the capability
     * but whose role grant was REVOKED at runtime must be denied on the next
     * request, with the same four contracts as the never-had-capability case.
     *
     * <p>This guards against capability caching bugs and stale-grant
     * vulnerabilities.</p>
     */
    @Test
    @DisplayName("revokedGrant_gets403_noOrgCreated_deniedAuditPersisted: runtime grant revocation denies subsequent request")
    void revokedGrant_gets403_noOrgCreated_deniedAuditPersisted() throws Exception {
        // User A starts WITH the ORGANIZATION.CREATE capability.
        String tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");

        // Revoke the role grant at runtime.
        fixtureSeeder.revokeRoleGrant(fixture.tenantAId(), fixture.roleGrantId());

        int orgsBefore = countOrgsForTenant(fixture.tenantAId());
        int deniedBefore = countDeniedAuditForTenant(fixture.tenantAId());

        String deniedOrgName = "denied-revoked-org-" + UUID.randomUUID();

        // === Act: POST with the (now-revoked) JWT ===
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + deniedOrgName + "\",\"description\":\"should not persist\"}")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", "denied-revoked-" + UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));

        // === Assert: no new org row ===
        int orgsAfter = countOrgsForTenant(fixture.tenantAId());
        assertThat(orgsAfter)
                .as("no organization row must be created after grant revocation "
                        + "(was %d, now %d)", orgsBefore, orgsAfter)
                .isEqualTo(orgsBefore);

        assertThat(orgNameExists(fixture.tenantAId(), deniedOrgName))
                .as("the denied organization name '%s' must not appear in the organizations table",
                        deniedOrgName)
                .isFalse();

        // === Assert: a DENIED audit row was persisted ===
        int deniedAfter = countDeniedAuditForTenant(fixture.tenantAId());
        assertThat(deniedAfter)
                .as("a DENIED audit event must be persisted after runtime grant revocation "
                        + "(was %d, now %d)", deniedBefore, deniedAfter)
                .isGreaterThan(deniedBefore);
    }
}
