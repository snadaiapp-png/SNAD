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
 * Stage 05 §16 — Verifies that reusing an Idempotency-Key with a DIFFERENT
 * request payload returns a CONFLICT result (HTTP 409 SANAD-IDEMP-002 in
 * production) instead of replaying or re-executing.
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyPayloadMismatchIntegrationTest {

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
    @DisplayName("sameKeyDifferentPayload_returns409: same key + different body → CONFLICT")
    void sameKeyDifferentPayload_returns409() {
        String key = "mismatch-key-" + java.util.UUID.randomUUID();
        String body1 = "{\"name\":\"Mismatch Org A\"}";
        String body2 = "{\"name\":\"Mismatch Org B DIFFERENT\"}";
        String operation = "ORGANIZATION.CREATE";
        String route = "/api/v1/organizations";
        String resourceType = "Organization";
        String method = "POST";

        // 1. First reservation with body1 → NEW
        setTenantContext();
        IdempotencyService.ReservationResult first;
        try {
            first = idempotencyService.reserveOrReplay(
                    key, operation, route, resourceType, method, body1, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(first.type())
                .as("first reservation must return NEW")
                .isEqualTo(IdempotencyService.ReservationType.NEW);

        // Complete the first reservation.
        setTenantContext();
        try {
            idempotencyService.complete(first.record().getId(), 201,
                    "Content-Type:application/json",
                    "{\"id\":\"org-a\",\"name\":\"Mismatch Org A\"}");
        } finally {
            contextProvider.clear();
        }

        // 2. Second reservation with the SAME key but DIFFERENT body → CONFLICT
        setTenantContext();
        IdempotencyService.ReservationResult second;
        try {
            second = idempotencyService.reserveOrReplay(
                    key, operation, route, resourceType, method, body2, null);
        } finally {
            contextProvider.clear();
        }
        assertThat(second.type())
                .as("same key + different payload must return CONFLICT (HTTP 409 SANAD-IDEMP-002)")
                .isEqualTo(IdempotencyService.ReservationType.CONFLICT);
        assertThat(second.message())
                .as("conflict message should indicate payload mismatch")
                .contains("different payload");
    }
}
