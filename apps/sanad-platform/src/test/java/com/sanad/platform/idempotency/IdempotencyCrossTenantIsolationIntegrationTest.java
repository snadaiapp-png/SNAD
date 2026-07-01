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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05 §16 — Verifies that the same Idempotency-Key is independent
 * across tenants. Tenant A and Tenant B can each use the same key for
 * their own operations without conflict.
 *
 * <p>The unique constraint on {@code (tenant_id, operation, route,
 * idempotency_key)} includes {@code tenant_id}, so the same key in two
 * different tenants produces two independent records.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyCrossTenantIsolationIntegrationTest {

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

    private void setTenantContext(UUID tenantId, UUID userId) {
        contextProvider.setContext(new TenantContext(
                tenantId, userId,
                "test-jti-" + java.util.UUID.randomUUID(), 0L,
                Set.of(), TenantContext.TenantContextSource.TEST_FIXTURE,
                "test-req-" + java.util.UUID.randomUUID()));
    }

    @Test
    @DisplayName("sameKeyDifferentTenants_independent: key K1 in Tenant A and Tenant B → two independent records")
    void sameKeyDifferentTenants_independent() {
        String sharedKey = "cross-tenant-key-" + java.util.UUID.randomUUID();
        String operation = "ORGANIZATION.CREATE";
        String route = "/api/v1/organizations";
        String resourceType = "Organization";
        String method = "POST";

        // 1. Tenant A reserves the key → NEW
        setTenantContext(fixture.tenantAId(), fixture.userAId());
        IdempotencyService.ReservationResult aFirst;
        try {
            aFirst = idempotencyService.reserveOrReplay(
                    sharedKey, operation, route, resourceType, method,
                    "{\"name\":\"Org A\"}", null);
        } finally {
            contextProvider.clear();
        }
        assertThat(aFirst.type())
                .as("Tenant A first reservation must return NEW")
                .isEqualTo(IdempotencyService.ReservationType.NEW);

        // Complete tenant A's reservation.
        setTenantContext(fixture.tenantAId(), fixture.userAId());
        try {
            idempotencyService.complete(aFirst.record().getId(), 201,
                    "Content-Type:application/json",
                    "{\"id\":\"org-a-uuid\",\"name\":\"Org A\"}");
        } finally {
            contextProvider.clear();
        }

        // 2. Tenant B reserves the SAME key → must also be NEW (not REPLAY).
        setTenantContext(fixture.tenantBId(), fixture.userBId());
        IdempotencyService.ReservationResult bFirst;
        try {
            bFirst = idempotencyService.reserveOrReplay(
                    sharedKey, operation, route, resourceType, method,
                    "{\"name\":\"Org B\"}", null);
        } finally {
            contextProvider.clear();
        }
        assertThat(bFirst.type())
                .as("Tenant B reservation with the same key must return NEW (independent)")
                .isEqualTo(IdempotencyService.ReservationType.NEW);
        assertThat(bFirst.record().getTenantId())
                .as("Tenant B record must belong to Tenant B")
                .isEqualTo(fixture.tenantBId());

        // 3. Verify: two independent idempotency records exist for the same key.
        Integer count = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key = ?",
                Integer.class, sharedKey);
        assertThat(count)
                .as("two independent records must exist for the same key across tenants")
                .isEqualTo(2);

        // 4. Verify: the records have different tenant IDs.
        java.util.List<java.util.UUID> tenantIds = fixtureJdbc.queryForList(
                "SELECT tenant_id FROM idempotency_records WHERE idempotency_key = ?",
                java.util.UUID.class, sharedKey);
        assertThat(tenantIds)
                .containsExactlyInAnyOrder(fixture.tenantAId(), fixture.tenantBId());
    }
}
