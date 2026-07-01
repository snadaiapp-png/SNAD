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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.1 §10 — Verifies that the {@link com.sanad.platform.audit.service.AuditRedactionService}
 * recursively replaces sensitive fields ({@code password}, {@code token},
 * {@code secret}, etc.) with the literal {@code [REDACTED]} sentinel
 * before persisting state-change JSON to {@code audit_events}.
 *
 * <p>Stage 05A.1 §13 — The {@link TenantContext} is established with
 * {@link TenantContext.TenantContextSource#JWT_CLAIM} source (the same
 * source the {@code TenantContextFilter} uses) and populated with the
 * verified user/tenant IDs from the fixture. The actor identity is taken
 * from the verified TenantContext ONLY inside {@link AuditService#record}.</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditRedactionIntegrationTest {

    @Autowired private AuditService auditService;
    @Autowired private TenantContextProvider contextProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

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

    /**
     * Establishes a verified TenantContext using the JWT_CLAIM source (the
     * same source the TenantContextFilter establishes after JWT verification).
     * The actor identity is taken from the verified user/tenant IDs set up
     * by the fixture.
     */
    private void setJwtClaimContext() {
        // Mint a real JWT to extract a real jti for the sessionId field —
        // this is exactly what the TenantContextFilter would do.
        String token = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(), jti, 0L,
                Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                "test-request-id-" + UUID.randomUUID()));
    }

    private void clearTenantContext() {
        contextProvider.clear();
    }

    private String storedMetadataForAction(String action) throws Exception {
        String sql = "SELECT metadata FROM audit_events WHERE tenant_id = ? "
                + "AND action = ? ORDER BY occurred_at DESC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, action);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("audit event for action '%s' must exist", action)
                        .isTrue();
                return rs.getString("metadata");
            }
        }
    }

    private void recordWithMetadata(String action, String metadata) {
        setJwtClaimContext();
        try {
            auditService.record(AuditContext.builder(
                            action, "TestResource", "TEST")
                    .actorType(AuditActorType.USER)
                    .outcome(AuditOutcome.SUCCESS)
                    .httpStatus(200)
                    .metadata(metadata)
                    .build());
        } finally {
            clearTenantContext();
        }
    }

    @Test
    @DisplayName("passwordFieldRedacted: metadata with password field → stored as [REDACTED]")
    void passwordFieldRedacted() throws Exception {
        String metadata = "{\"username\":\"alice\",\"password\":\"super-secret-123\"}";
        recordWithMetadata("TEST.REDACT.PASSWORD", metadata);

        String stored = storedMetadataForAction("TEST.REDACT.PASSWORD");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("[REDACTED]");
        assertThat(stored).doesNotContain("super-secret-123");
    }

    @Test
    @DisplayName("tokenFieldRedacted: metadata with token field → stored as [REDACTED]")
    void tokenFieldRedacted() throws Exception {
        String metadata = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.payload.sig\",\"userId\":\"u1\"}";
        recordWithMetadata("TEST.REDACT.TOKEN", metadata);

        String stored = storedMetadataForAction("TEST.REDACT.TOKEN");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("[REDACTED]");
        assertThat(stored).doesNotContain("eyJhbGciOiJIUzI1NiJ9.payload.sig");
    }

    @Test
    @DisplayName("secretFieldRedacted: metadata with secret field → stored as [REDACTED]")
    void secretFieldRedacted() throws Exception {
        String metadata = "{\"clientSecret\":\"oauth-secret-value\",\"name\":\"app\"}";
        recordWithMetadata("TEST.REDACT.SECRET", metadata);

        String stored = storedMetadataForAction("TEST.REDACT.SECRET");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("[REDACTED]");
        assertThat(stored).doesNotContain("oauth-secret-value");
    }

    @Test
    @DisplayName("nestedJsonRedacted: metadata with nested sensitive fields → recursively redacted")
    void nestedJsonRedacted() throws Exception {
        String metadata = "{\"outer\":{\"inner\":{\"password\":\"deep-secret\"},\"safe\":\"ok\"},\"apiKey\":\"top-level-key\"}";
        recordWithMetadata("TEST.REDACT.NESTED", metadata);

        String stored = storedMetadataForAction("TEST.REDACT.NESTED");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("[REDACTED]");
        assertThat(stored).doesNotContain("deep-secret");
        assertThat(stored).doesNotContain("top-level-key");
        // The non-sensitive nested field should remain.
        assertThat(stored).contains("ok");
    }
}
