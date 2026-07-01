package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.domain.IdempotencyStatus;
import com.sanad.platform.idempotency.service.IdempotencyService;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05 §19 — Verifies failure recovery semantics for idempotency
 * records.
 *
 * <p>A {@link IdempotencyStatus#FAILED_RETRYABLE} record allows a
 * subsequent reservation to re-execute (returns NEW). A
 * {@link IdempotencyStatus#FAILED_FINAL} record blocks any retry
 * (returns CONFLICT).</p>
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

    private void setTenantContext() {
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(),
                "test-jti-" + java.util.UUID.randomUUID(), 0L,
                Set.of(), TenantContext.TenantContextSource.TEST_FIXTURE,
                "test-req-" + java.util.UUID.randomUUID()));
    }

    @Test
    @DisplayName("retryableFailure_allowsRetry: FAILED_RETRYABLE → next reserve returns NEW (re-execution)")
    void retryableFailure_allowsRetry() {
        String key = "retryable-key-" + java.util.UUID.randomUUID();
        String body = "{\"name\":\"Retryable Org\"}";
        String operation = "ORGANIZATION.CREATE";
        String route = "/api/v1/organizations";
        String resourceType = "Organization";
        String method = "POST";

        // 1. Reserve → NEW
        setTenantContext();
        IdempotencyService.ReservationResult first;
        try {
            first = idempotencyService.reserveOrReplay(
                    key, operation, route, resourceType, method, body, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(first.type()).isEqualTo(IdempotencyService.ReservationType.NEW);

        // 2. Mark as FAILED_RETRYABLE (simulates a transient business failure).
        setTenantContext();
        try {
            idempotencyService.fail(
                    first.record().getId(), "SANAD-TRANSIENT-001",
                    "Downstream timeout", true);
        } finally {
            contextProvider.clear();
        }

        // 3. Reserve again with the same key + body → NEW (re-execution allowed).
        setTenantContext();
        IdempotencyService.ReservationResult retry;
        try {
            retry = idempotencyService.reserveOrReplay(
                    key, operation, route, resourceType, method, body, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(retry.type())
                .as("FAILED_RETRYABLE must allow re-execution (NEW)")
                .isEqualTo(IdempotencyService.ReservationType.NEW);
        assertThat(retry.record().getId())
                .as("re-execution should reuse the same record (now PROCESSING)")
                .isEqualTo(first.record().getId());

        // Verify the record is back to PROCESSING in the DB.
        String status = fixtureJdbc.queryForObject(
                "SELECT status FROM idempotency_records WHERE id = ?",
                String.class, first.record().getId());
        assertThat(status).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("finalFailure_blocksRetry: FAILED_FINAL → next reserve returns CONFLICT")
    void finalFailure_blocksRetry() {
        String key = "final-fail-key-" + java.util.UUID.randomUUID();
        String body = "{\"name\":\"Final Fail Org\"}";
        String operation = "ORGANIZATION.CREATE";
        String route = "/api/v1/organizations";
        String resourceType = "Organization";
        String method = "POST";

        // 1. Reserve → NEW
        setTenantContext();
        IdempotencyService.ReservationResult first;
        try {
            first = idempotencyService.reserveOrReplay(
                    key, operation, route, resourceType, method, body, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(first.type()).isEqualTo(IdempotencyService.ReservationType.NEW);

        // 2. Mark as FAILED_FINAL (permanent failure — e.g. validation error).
        setTenantContext();
        try {
            idempotencyService.fail(
                    first.record().getId(), "SANAD-VALIDATION-001",
                    "Request payload violates business invariant", false);
        } finally {
            contextProvider.clear();
        }

        // 3. Reserve again with the same key + body → CONFLICT (retry blocked).
        setTenantContext();
        IdempotencyService.ReservationResult retry;
        try {
            retry = idempotencyService.reserveOrReplay(
                    key, operation, route, resourceType, method, body, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(retry.type())
                .as("FAILED_FINAL must block retry (CONFLICT)")
                .isEqualTo(IdempotencyService.ReservationType.CONFLICT);
        assertThat(retry.message())
                .as("conflict message should indicate permanent failure")
                .contains("permanently");
    }
}
