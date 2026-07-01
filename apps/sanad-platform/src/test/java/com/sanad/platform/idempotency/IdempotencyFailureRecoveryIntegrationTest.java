package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.service.IdempotencyService;
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
 * Stage 05A.1 §19 — Verifies failure recovery semantics for idempotency
 * records.
 *
 * <p>A {@link com.sanad.platform.idempotency.domain.IdempotencyStatus#FAILED_RETRYABLE}
 * record allows a subsequent reservation to re-execute (returns NEW). A
 * {@link com.sanad.platform.idempotency.domain.IdempotencyStatus#FAILED_FINAL}
 * record blocks any retry (returns CONFLICT).</p>
 *
 * <p>Stage 05A.1 §13 — The {@link TenantContext} is established with
 * {@link TenantContext.TenantContextSource#JWT_CLAIM} source (the same
 * source the {@code TenantContextFilter} establishes) and populated with
 * the verified user/tenant IDs from the fixture. No {@code TEST_FIXTURE}
 * source is used.</p>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyFailureRecoveryIntegrationTest {

    @Autowired private IdempotencyService idempotencyService;
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
     * Establishes a JWT_CLAIM-sourced TenantContext (same source the filter
     * chain establishes) with the verified user/tenant IDs from the fixture.
     */
    private void setJwtClaimContext() {
        String token = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(), jti, 0L,
                Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                "test-req-" + UUID.randomUUID()));
    }

    private String reserveOrReplay(String key, String body) {
        setJwtClaimContext();
        try {
            IdempotencyService.ReservationResult result = idempotencyService.reserveOrReplay(
                    key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                    "Organization", "POST", body, null);
            return result.type().name();
        } finally {
            contextProvider.clear();
        }
    }

    private void complete(UUID recordId, int status, String body) {
        setJwtClaimContext();
        try {
            idempotencyService.complete(recordId, status,
                    "Content-Type:application/json", body);
        } finally {
            contextProvider.clear();
        }
    }

    private void fail(UUID recordId, String errorCode, String detail, boolean retryable) {
        setJwtClaimContext();
        try {
            idempotencyService.fail(recordId, errorCode, detail, retryable);
        } finally {
            contextProvider.clear();
        }
    }

    private IdempotencyService.ReservationResult reserveOrReplayFull(String key, String body) {
        setJwtClaimContext();
        try {
            return idempotencyService.reserveOrReplay(
                    key, "ORGANIZATION.CREATE", "/api/v1/organizations",
                    "Organization", "POST", body, null);
        } finally {
            contextProvider.clear();
        }
    }

    @Test
    @DisplayName("retryableFailure_allowsRetry: FAILED_RETRYABLE → next reserve returns NEW (re-execution)")
    void retryableFailure_allowsRetry() throws Exception {
        String key = "retryable-key-" + UUID.randomUUID();
        String body = "{\"name\":\"Retryable Org\"}";

        // 1. Reserve → NEW
        IdempotencyService.ReservationResult first = reserveOrReplayFull(key, body);
        assertThat(first.type())
                .as("first reservation must return NEW")
                .isEqualTo(IdempotencyService.ReservationType.NEW);

        // 2. Mark as FAILED_RETRYABLE (simulates a transient business failure).
        fail(first.record().getId(), "SANAD-TRANSIENT-001",
                "Downstream timeout", true);

        // 3. Reserve again with the same key + body → NEW (re-execution allowed).
        IdempotencyService.ReservationResult retry = reserveOrReplayFull(key, body);
        assertThat(retry.type())
                .as("FAILED_RETRYABLE must allow re-execution (NEW)")
                .isEqualTo(IdempotencyService.ReservationType.NEW);
        assertThat(retry.record().getId())
                .as("re-execution should reuse the same record (now PROCESSING)")
                .isEqualTo(first.record().getId());

        // Verify the record is back to PROCESSING in the DB.
        String sql = "SELECT status FROM idempotency_records WHERE id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, first.record().getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("PROCESSING");
            }
        }
    }

    @Test
    @DisplayName("finalFailure_blocksRetry: FAILED_FINAL → next reserve returns CONFLICT")
    void finalFailure_blocksRetry() throws Exception {
        String key = "final-fail-key-" + UUID.randomUUID();
        String body = "{\"name\":\"Final Fail Org\"}";

        // 1. Reserve → NEW
        IdempotencyService.ReservationResult first = reserveOrReplayFull(key, body);
        assertThat(first.type()).isEqualTo(IdempotencyService.ReservationType.NEW);

        // 2. Mark as FAILED_FINAL (permanent failure — e.g. validation error).
        fail(first.record().getId(), "SANAD-VALIDATION-001",
                "Request payload violates business invariant", false);

        // 3. Reserve again with the same key + body → CONFLICT (retry blocked).
        IdempotencyService.ReservationResult retry = reserveOrReplayFull(key, body);
        assertThat(retry.type())
                .as("FAILED_FINAL must block retry (CONFLICT)")
                .isEqualTo(IdempotencyService.ReservationType.CONFLICT);
        assertThat(retry.message())
                .as("conflict message should indicate permanent failure")
                .contains("permanently");
    }
}
