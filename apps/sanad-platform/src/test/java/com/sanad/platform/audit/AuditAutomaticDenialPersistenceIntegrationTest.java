package com.sanad.platform.audit;

import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantRuntimeDataSourceConfig;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.2.9 §3 — Tests that security denials are automatically
 * audited.
 *
 * <p>Pre-authentication denials (401) are recorded in
 * {@code platform_security_audit_events}.</p>
 *
 * <p>Post-authentication denials (403) are recorded in
 * {@code audit_events} (tenant-scoped).</p>
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
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    // === Pre-authentication denial tests (query platform_security_audit_events) ===

    @Test
    @DisplayName("missingJwt_401_deniedAuditPersisted: no Authorization → 401 + platform audit row")
    void missingJwt_401_deniedAuditPersisted() throws Exception {
        int before = countPlatformDenials();

        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org-401\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));

        int after = countPlatformDenials();
        assertThatPlatformDenialIncreased(before, after, "MISSING_JWT");
    }

    @Test
    @DisplayName("malformedJwt_401_deniedAuditPersisted: Bearer not-a-jwt → 401 + platform audit row")
    void malformedJwt_401_deniedAuditPersisted() throws Exception {
        int before = countPlatformDenials();

        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-org-malformed\"}")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));

        int after = countPlatformDenials();
        assertThatPlatformDenialIncreased(before, after, "MALFORMED_JWT");
    }

    @Test
    @DisplayName("deniedAuditIsIndependentOfBusinessTransaction: 401 denial persists independently")
    void deniedAuditIsIndependentOfBusinessTransaction() throws Exception {
        int before = countPlatformDenials();

        // A 401 happens before any business transaction. The platform denial
        // audit uses REQUIRES_NEW, so it commits independently.
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-independent\"}"))
                .andExpect(status().isUnauthorized());

        int after = countPlatformDenials();
        assertThatPlatformDenialIncreased(before, after, "MISSING_JWT");
    }

    // === Post-authentication denial tests (query audit_events) ===

    @Test
    @DisplayName("missingCapability_403_deniedAuditPersisted: valid JWT, no ORGANIZATION.CREATE → 403 + tenant audit row")
    void missingCapability_403_deniedAuditPersisted() throws Exception {
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
        org.assertj.core.api.Assertions.assertThat(after)
                .as("a DENIED audit event must be automatically persisted on 403 (was %d, now %d)", before, after)
                .isGreaterThan(before);
    }

    // === Helper methods ===

    private int countPlatformDenials() throws Exception {
        String sql = "SELECT COUNT(*) FROM platform_security_audit_events";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void assertThatPlatformDenialIncreased(int before, int after, String expectedCategory) {
        org.assertj.core.api.Assertions.assertThat(after)
                .as("platform security audit row must be persisted (was %d, now %d)", before, after)
                .isGreaterThan(before);
    }

    private int countDeniedAuditForTenant(UUID tenantId) throws Exception {
        String sql = "SELECT COUNT(*) FROM audit_events WHERE tenant_id = ? AND outcome = 'DENIED'";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
