package com.sanad.platform.idempotency;

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
 * Stage 05 §16 — Verifies that the same Idempotency-Key with the same
 * request payload replays the stored response instead of re-executing
 * the business operation.
 *
 * <p>Calls {@link IdempotencyService#reserveOrReplay} and
 * {@link IdempotencyService#complete} directly (the interceptor is not
 * yet wired into the HTTP layer) and verifies the replay semantics at
 * the service level.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencySameRequestReplayIntegrationTest {

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
    @DisplayName("sameKeySamePayload_replaysResponse: second reserve returns REPLAY with same response body")
    void sameKeySamePayload_replaysResponse() {
        String key = "replay-key-" + java.util.UUID.randomUUID();
        String body = "{\"name\":\"Replay Test Org\"}";
        String operation = "ORGANIZATION.CREATE";
        String route = "/api/v1/organizations";
        String resourceType = "Organization";
        String method = "POST";

        // 1. First reservation → NEW
        setTenantContext();
        IdempotencyService.ReservationResult first;
        try {
            first = idempotencyService.reserveOrReplay(
                    key, operation, route, resourceType, method, body, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(first.type())
                .as("first reservation must return NEW")
                .isEqualTo(IdempotencyService.ReservationType.NEW);
        assertThat(first.record()).isNotNull();

        // 2. Complete the reservation with a response.
        String storedResponseBody = "{\"id\":\"org-123\",\"name\":\"Replay Test Org\"}";
        String storedHeaders = "Location:/api/v1/organizations/org-123\nContent-Type:application/json";
        setTenantContext();
        try {
            idempotencyService.complete(
                    first.record().getId(), 201, storedHeaders, storedResponseBody);
        } finally {
            contextProvider.clear();
        }

        // 3. Second reservation with same key + same body → REPLAY
        setTenantContext();
        IdempotencyService.ReservationResult second;
        try {
            second = idempotencyService.reserveOrReplay(
                    key, operation, route, resourceType, method, body, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(second.type())
                .as("second reservation with same key+payload must return REPLAY")
                .isEqualTo(IdempotencyService.ReservationType.REPLAY);
        assertThat(second.record().getResponseBody())
                .as("replayed response body must match the stored response")
                .isEqualTo(storedResponseBody);
        assertThat(second.record().getResponseStatus()).isEqualTo(201);

        // 4. Verify only 1 idempotency record exists for this key.
        Integer count = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_records " +
                "WHERE tenant_id = ? AND idempotency_key = ?",
                Integer.class, fixture.tenantAId(), key);
        assertThat(count).as("exactly one idempotency record must exist").isEqualTo(1);
    }
}
