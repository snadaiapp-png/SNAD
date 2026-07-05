package com.sanad.platform.controlplane;

import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.admin.service.AdminPlatformService;
import com.sanad.platform.health.api.HealthDtos.HealthActionRequest;
import com.sanad.platform.health.api.HealthDtos.HealthActionResult;
import com.sanad.platform.health.api.HealthDtos.PlatformHealthResponse;
import com.sanad.platform.health.service.HealthIntelligenceService;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
@Transactional
class HealthIntelligenceIntegrationTest {

    @Autowired private HealthIntelligenceService healthService;
    @Autowired private AdminPlatformService platformService;

    @Test
    void producesPlatformServiceTenantPressureAndForecastSignals() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        platformService.createTenant(
                new CreateTenantRequest(
                        "Health Tenant " + suffix,
                        "Health Tenant Legal " + suffix,
                        "health-" + suffix,
                        "billing-" + suffix + "@example.test",
                        "admin-" + suffix + "@example.test",
                        "Health Administrator",
                        "SA",
                        "ar-SA",
                        "Asia/Riyadh",
                        "SAR",
                        14
                ),
                authentication()
        );

        PlatformHealthResponse snapshot = healthService.snapshot();

        assertThat(snapshot.generatedAt()).isNotNull();
        assertThat(snapshot.healthScore()).isBetween(0, 100);
        assertThat(snapshot.runtime().memoryUsagePercent()).isBetween(0.0, 100.0);
        assertThat(snapshot.dataPressure().pressureScore()).isBetween(0, 100);
        assertThat(snapshot.services()).isNotEmpty();
        assertThat(snapshot.services()).allSatisfy(service -> {
            assertThat(service.healthScore()).isBetween(0, 100);
            assertThat(service.pressureScore()).isBetween(0, 100);
        });
        assertThat(snapshot.tenants()).isNotEmpty();
        assertThat(snapshot.tenants()).allSatisfy(tenant -> {
            assertThat(tenant.healthScore()).isBetween(0, 100);
            assertThat(tenant.pressureScore()).isBetween(0, 100);
            assertThat(tenant.trackedRecords()).isGreaterThanOrEqualTo(0);
        });
        assertThat(snapshot.forecast()).extracting(point -> point.horizonMinutes())
                .containsExactly(0, 15, 30, 60);
        assertThat(snapshot.partial()).isFalse();
        assertThat(snapshot.dataCompletenessScore()).isEqualTo(100);
        assertThat(snapshot.degradedComponents()).isEmpty();
        assertThat(snapshot.collectionErrors()).isEmpty();
        assertThat(snapshot.availableActions()).extracting(action -> action.code())
                .contains("RUN_DIAGNOSTICS", "AUTO_HEAL", "REFRESH_TENANT_HEALTH");
    }

    @Test
    void executesAllowListedDiagnosticAndReturnsAuditedFreshSnapshot() {
        HealthActionResult result = healthService.execute(
                new HealthActionRequest(
                        "PLATFORM",
                        null,
                        "RUN_DIAGNOSTICS",
                        "Integration test controlled diagnostic"
                ),
                authentication()
        );

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.message()).contains("database latency");
        assertThat(result.snapshot()).isNotNull();
        assertThat(result.snapshot().healthScore()).isBetween(0, 100);
    }

    private static UsernamePasswordAuthenticationToken authentication() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", UUID.randomUUID().toString(),
                "user_id", UUID.randomUUID().toString()
        ));
        return authentication;
    }
}
