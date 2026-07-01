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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05 §18 — Verifies that sensitive response headers (Set-Cookie,
 * Authorization) are stripped from the stored response headers when an
 * idempotency record is completed. This prevents credential leakage on
 * replay.
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyResponseRedactionIntegrationTest {

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

    private String completeAndReadStoredHeaders(String headers) {
        String key = "redact-key-" + java.util.UUID.randomUUID();
        String operation = "ORGANIZATION.CREATE";
        String route = "/api/v1/organizations";

        setTenantContext();
        IdempotencyService.ReservationResult reservation;
        try {
            reservation = idempotencyService.reserveOrReplay(
                    key, operation, route, "Organization", "POST",
                    "{\"name\":\"Redact Test\"}", null);
        } finally {
            contextProvider.clear();
        }
        assertThat(reservation.type()).isEqualTo(IdempotencyService.ReservationType.NEW);

        setTenantContext();
        try {
            idempotencyService.complete(
                    reservation.record().getId(), 201, headers,
                    "{\"id\":\"org-x\",\"name\":\"Redact Test\"}");
        } finally {
            contextProvider.clear();
        }

        List<Map<String, Object>> rows = fixtureJdbc.queryForList(
                "SELECT response_headers FROM idempotency_records WHERE id = ?",
                reservation.record().getId());
        assertThat(rows).hasSize(1);
        Object val = rows.get(0).get("response_headers");
        return val == null ? null : val.toString();
    }

    @Test
    @DisplayName("setCookieStrippedFromStoredResponse: Set-Cookie header is removed from stored response")
    void setCookieStrippedFromStoredResponse() {
        String headers = "Content-Type:application/json\n" +
                "Set-Cookie:session=abc123; HttpOnly; Secure\n" +
                "Location:/api/v1/organizations/org-x";

        String stored = completeAndReadStoredHeaders(headers);
        assertThat(stored).isNotNull();
        assertThat(stored).contains("Location:/api/v1/organizations/org-x");
        assertThat(stored).contains("Content-Type:application/json");
        assertThat(stored.toLowerCase())
                .as("Set-Cookie must be stripped from stored response headers")
                .doesNotContain("set-cookie");
        assertThat(stored)
                .as("cookie value must not be present in stored headers")
                .doesNotContain("session=abc123");
    }

    @Test
    @DisplayName("authorizationStrippedFromStoredResponse: Authorization header is removed from stored response")
    void authorizationStrippedFromStoredResponse() {
        String headers = "Content-Type:application/json\n" +
                "Authorization:Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig\n" +
                "Location:/api/v1/organizations/org-y";

        String stored = completeAndReadStoredHeaders(headers);
        assertThat(stored).isNotNull();
        assertThat(stored).contains("Location:/api/v1/organizations/org-y");
        assertThat(stored.toLowerCase())
                .as("Authorization must be stripped from stored response headers")
                .doesNotContain("authorization");
        assertThat(stored)
                .as("bearer token must not be present in stored headers")
                .doesNotContain("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig");
    }
}
