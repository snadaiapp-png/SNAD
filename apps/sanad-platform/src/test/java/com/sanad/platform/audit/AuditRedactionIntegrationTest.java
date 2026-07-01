package com.sanad.platform.audit;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditService;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05 §10 — Verifies that the {@link com.sanad.platform.audit.service.AuditRedactionService}
 * recursively replaces sensitive fields ({@code password}, {@code token},
 * {@code secret}, etc.) with the literal {@code [REDACTED]} sentinel before
 * persisting state-change JSON to {@code audit_events}.
 *
 * <p>Calls {@link AuditService#record(AuditContext)} directly with
 * metadata containing sensitive fields, then reads the stored row via
 * the fixture DataSource to verify redaction.</p>
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

    private void setTenantContext(UUID tenantId, UUID userId) {
        contextProvider.setContext(new TenantContext(
                tenantId, userId, "test-jti-" + UUID.randomUUID(), 0L,
                Set.of(), TenantContext.TenantContextSource.TEST_FIXTURE,
                "test-request-id"));
    }

    private void clearTenantContext() {
        contextProvider.clear();
    }

    private String storedMetadataForAction(String action) {
        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT metadata FROM audit_events WHERE tenant_id = ? AND action = ? " +
                "ORDER BY occurred_at DESC LIMIT 1",
                fixture.tenantAId(), action);
        assertThat(rows).as("audit event for action '%s' must exist", action).hasSize(1);
        Object val = rows.get(0).get("metadata");
        return val == null ? null : val.toString();
    }

    @Test
    @DisplayName("passwordFieldRedacted: metadata with password field → stored as [REDACTED]")
    void passwordFieldRedacted() {
        setTenantContext(fixture.tenantAId(), fixture.userAId());
        try {
            String metadata = "{\"username\":\"alice\",\"password\":\"super-secret-123\"}";
            auditService.record(AuditContext.builder(
                            "TEST.REDACT.PASSWORD", "TestResource", "TEST")
                    .outcome(AuditOutcome.SUCCESS)
                    .httpStatus(200)
                    .metadata(metadata)
                    .build());
        } finally {
            clearTenantContext();
        }

        String stored = storedMetadataForAction("TEST.REDACT.PASSWORD");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("[REDACTED]");
        assertThat(stored).doesNotContain("super-secret-123");
    }

    @Test
    @DisplayName("tokenFieldRedacted: metadata with token field → stored as [REDACTED]")
    void tokenFieldRedacted() {
        setTenantContext(fixture.tenantAId(), fixture.userAId());
        try {
            String metadata = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.payload.sig\",\"userId\":\"u1\"}";
            auditService.record(AuditContext.builder(
                            "TEST.REDACT.TOKEN", "TestResource", "TEST")
                    .outcome(AuditOutcome.SUCCESS)
                    .httpStatus(200)
                    .metadata(metadata)
                    .build());
        } finally {
            clearTenantContext();
        }

        String stored = storedMetadataForAction("TEST.REDACT.TOKEN");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("[REDACTED]");
        assertThat(stored).doesNotContain("eyJhbGciOiJIUzI1NiJ9.payload.sig");
    }

    @Test
    @DisplayName("secretFieldRedacted: metadata with secret field → stored as [REDACTED]")
    void secretFieldRedacted() {
        setTenantContext(fixture.tenantAId(), fixture.userAId());
        try {
            String metadata = "{\"clientSecret\":\"oauth-secret-value\",\"name\":\"app\"}";
            auditService.record(AuditContext.builder(
                            "TEST.REDACT.SECRET", "TestResource", "TEST")
                    .outcome(AuditOutcome.SUCCESS)
                    .httpStatus(200)
                    .metadata(metadata)
                    .build());
        } finally {
            clearTenantContext();
        }

        String stored = storedMetadataForAction("TEST.REDACT.SECRET");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("[REDACTED]");
        assertThat(stored).doesNotContain("oauth-secret-value");
    }

    @Test
    @DisplayName("nestedJsonRedacted: metadata with nested sensitive fields → recursively redacted")
    void nestedJsonRedacted() {
        setTenantContext(fixture.tenantAId(), fixture.userAId());
        try {
            String metadata = "{\"outer\":{\"inner\":{\"password\":\"deep-secret\"},\"safe\":\"ok\"},\"apiKey\":\"top-level-key\"}";
            auditService.record(AuditContext.builder(
                            "TEST.REDACT.NESTED", "TestResource", "TEST")
                    .actorType(AuditActorType.USER)
                    .outcome(AuditOutcome.SUCCESS)
                    .httpStatus(200)
                    .metadata(metadata)
                    .build());
        } finally {
            clearTenantContext();
        }

        String stored = storedMetadataForAction("TEST.REDACT.NESTED");
        assertThat(stored).isNotNull();
        assertThat(stored).contains("[REDACTED]");
        assertThat(stored).doesNotContain("deep-secret");
        assertThat(stored).doesNotContain("top-level-key");
        // The non-sensitive nested field should remain.
        assertThat(stored).contains("ok");
    }
}
