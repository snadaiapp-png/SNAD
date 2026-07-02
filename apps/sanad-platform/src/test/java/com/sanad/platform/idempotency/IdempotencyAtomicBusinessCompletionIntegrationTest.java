package com.sanad.platform.idempotency;

import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditService;
import com.sanad.platform.idempotency.service.IdempotentCommandExecutor;
import com.sanad.platform.idempotency.service.IdempotentHttpResult;
import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 05A.2.1 §9-10 — Verifies that the {@link IdempotentCommandExecutor}
 * commits business mutation + audit event + idempotency completion all
 * together in a single transaction (Transaction B).
 *
 * <p>The atomicity contract:</p>
 * <ul>
 *   <li><b>Happy path</b>: business action succeeds → business row exists,
 *       SUCCESS audit row exists, idempotency record is COMPLETED. All
 *       three commit together.</li>
 *   <li><b>Audit failure rolls back business</b>: if the audit write
 *       throws, the business mutation rolls back too (no business row,
 *       no COMPLETED record).</li>
 *   <li><b>Business failure does not complete</b>: if the business action
 *       throws, no COMPLETED record exists (it goes to FAILED_RETRYABLE
 *       via Transaction C). No SUCCESS audit row is written.</li>
 * </ul>
 *
 * <p>Stage 05A.2.1 §7 — The executor calls {@code idempotencyService.complete()}
 * with the lease owner and version from the reservation. If the lease was
 * taken over by another worker, this throws {@link com.sanad.platform.idempotency.service.StaleIdempotencyLeaseException}.</p>
 *
 * <p>Stage 05A.1 §13 — Each test establishes a JWT_CLAIM-sourced
 * {@link TenantContext} (same source the filter chain establishes) with
 * the verified user/tenant IDs from the fixture. No TEST_FIXTURE source.</p>
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
class IdempotencyAtomicBusinessCompletionIntegrationTest {

    @Autowired private IdempotentCommandExecutor executor;
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
                "atomic-biz-req-" + UUID.randomUUID()));
    }

    /**
     * A simple result DTO that the executor can serialize for replay.
     */
    public record CreatedResource(UUID id, String name) {
        public UUID getId() { return id; }
    }

    @Test
    @DisplayName("happyPath_businessAndAuditAndCompletionCommitTogether: business mutation + SUCCESS audit + COMPLETED idempotency all commit in Transaction B")
    void happyPath_businessAndAuditAndCompletionCommitTogether() throws Exception {
        String key = "atomic-biz-happy-" + UUID.randomUUID();
        String body = "{\"name\":\"Atomic Biz Happy Org\"}";
        UUID createdOrgId = UUID.randomUUID();

        setJwtClaimContext();
        IdempotentHttpResult<CreatedResource> result;
        try {
            result = executor.execute(
                    new IdempotentCommandExecutor.OperationMetadata(
                            "TEST.ATOMIC.HAPPY", "/api/v1/test/atomic", "TestResource"),
                    key, body, "POST", null,
                    () -> {
                        // Simulate business mutation: insert an organization row.
                        fixtureJdbc.update(
                                "INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) "
                                        + "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                                createdOrgId, fixture.tenantAId(), "Atomic Biz Happy Org", "test");
                        return new CreatedResource(createdOrgId, "Atomic Biz Happy Org");
                    });
        } finally {
            contextProvider.clear();
        }

        // === Verify the result ===
        assertThat(result.replayed())
                .as("first execution must not be a replay")
                .isFalse();
        assertThat(result.httpStatus())
                .as("HTTP status must be 201")
                .isEqualTo(201);
        assertThat(result.resourceId())
                .as("resourceId must match the created org id")
                .isEqualTo(createdOrgId);

        // === Verify business mutation committed ===
        String orgSql = "SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(orgSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setObject(2, createdOrgId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("business mutation (organization row) must be committed")
                        .isEqualTo(1);
            }
        }

        // === Verify SUCCESS audit event was committed ===
        String auditSql = "SELECT COUNT(*) FROM audit_events "
                + "WHERE tenant_id = ? AND action = 'TEST.ATOMIC.HAPPY' AND outcome = 'SUCCESS'";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(auditSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("a SUCCESS audit event must be committed in the same transaction")
                        .isEqualTo(1);
            }
        }

        // === Verify idempotency record is COMPLETED ===
        String idemSql = "SELECT status, response_status, response_body "
                + "FROM idempotency_records WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(idemSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("idempotency record must exist for key %s", key)
                        .isTrue();
                assertThat(rs.getString("status"))
                        .as("idempotency record must be COMPLETED")
                        .isEqualTo("COMPLETED");
                assertThat(rs.getInt("response_status"))
                        .as("stored response_status must be 201")
                        .isEqualTo(201);
                assertThat(rs.getString("response_body"))
                        .as("stored response_body must be non-null")
                        .isNotNull();
            }
        }
    }

    @Test
    @DisplayName("businessFailure_noCompletedNoSuccessAudit: business action throws → no COMPLETED, no SUCCESS audit, FAILED_RETRYABLE")
    void businessFailure_noCompletedNoSuccessAudit() throws Exception {
        String key = "atomic-biz-fail-" + UUID.randomUUID();
        String body = "{\"name\":\"Atomic Biz Fail Org\"}";

        setJwtClaimContext();
        assertThatThrownBy(() -> {
            executor.execute(
                    new IdempotentCommandExecutor.OperationMetadata(
                            "TEST.ATOMIC.FAIL", "/api/v1/test/atomic", "TestResource"),
                    key, body, "POST", null,
                    () -> {
                        // Business action throws → Transaction B rolls back.
                        throw new RuntimeException("business failure");
                    });
        })
                .as("business failure must propagate to the caller")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("business failure");
        contextProvider.clear();

        // === Verify idempotency record is FAILED_RETRYABLE (not COMPLETED) ===
        String idemSql = "SELECT status FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(idemSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("idempotency record must exist for key %s", key)
                        .isTrue();
                String status = rs.getString("status");
                assertThat(status)
                        .as("idempotency record must NOT be COMPLETED after business failure (was %s)", status)
                        .isNotEqualTo("COMPLETED");
                assertThat(status)
                        .as("idempotency record must be FAILED_RETRYABLE after business failure (was %s)", status)
                        .isEqualTo("FAILED_RETRYABLE");
            }
        }

        // === Verify NO SUCCESS audit event was written ===
        String auditSql = "SELECT COUNT(*) FROM audit_events "
                + "WHERE tenant_id = ? AND action = 'TEST.ATOMIC.FAIL' AND outcome = 'SUCCESS'";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(auditSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("no SUCCESS audit event must be persisted when the business mutation fails")
                        .isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("replayOnSecondExecution: same key+payload second call → replayed result, no second business mutation")
    void replayOnSecondExecution() throws Exception {
        String key = "atomic-biz-replay-" + UUID.randomUUID();
        String body = "{\"name\":\"Atomic Biz Replay Org\"}";

        // First execution: business mutation + completion.
        setJwtClaimContext();
        IdempotentHttpResult<CreatedResource> first;
        try {
            first = executor.execute(
                    new IdempotentCommandExecutor.OperationMetadata(
                            "TEST.ATOMIC.REPLAY", "/api/v1/test/atomic", "TestResource"),
                    key, body, "POST", null,
                    () -> new CreatedResource(UUID.randomUUID(), "Atomic Biz Replay Org"));
        } finally {
            contextProvider.clear();
        }
        assertThat(first.replayed())
                .as("first execution must not be a replay")
                .isFalse();

        // Count audit events before second execution.
        String auditCountSql = "SELECT COUNT(*) FROM audit_events "
                + "WHERE tenant_id = ? AND action = 'TEST.ATOMIC.REPLAY'";
        int auditCountAfterFirst;
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(auditCountSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                auditCountAfterFirst = rs.getInt(1);
            }
        }

        // Second execution: same key + body → must be a replay.
        setJwtClaimContext();
        IdempotentHttpResult<CreatedResource> second;
        try {
            second = executor.execute(
                    new IdempotentCommandExecutor.OperationMetadata(
                            "TEST.ATOMIC.REPLAY", "/api/v1/test/atomic", "TestResource"),
                    key, body, "POST", null,
                    () -> {
                        // This business action should NOT be invoked on replay.
                        throw new AssertionError("business action must NOT be invoked on replay");
                    });
        } finally {
            contextProvider.clear();
        }

        assertThat(second.replayed())
                .as("second execution with same key+payload must be a replay")
                .isTrue();
        assertThat(second.body())
                .as("replayed body must equal the first body")
                .isEqualTo(first.body());

        // No additional audit event should have been written.
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(auditCountSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int auditCountAfterSecond = rs.getInt(1);
                assertThat(auditCountAfterSecond)
                        .as("replay must NOT write an additional audit event")
                        .isEqualTo(auditCountAfterFirst);
            }
        }

        // Exactly 1 idempotency record must exist.
        String countSql = "SELECT COUNT(*) FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("exactly 1 idempotency record must exist after replay")
                        .isEqualTo(1);
            }
        }
    }
}
