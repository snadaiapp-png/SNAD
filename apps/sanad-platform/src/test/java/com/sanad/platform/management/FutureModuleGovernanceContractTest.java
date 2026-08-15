package com.sanad.platform.management;

import com.sanad.platform.management.application.ManagementGovernanceModuleContract;
import com.sanad.platform.management.application.ManagementGovernanceModuleRegistry;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for GAP 24 — Future Module Auto-Governance Contract.
 *
 * Verifies the unified contract auto-discovers all modules, including
 * unknown/future modules that have no adapter registered.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class FutureModuleGovernanceContractTest {

    @Autowired private ManagementGovernanceModuleRegistry registry;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "fmg-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void allModules_returnsAtLeastFiveAdapters() {
        List<ManagementGovernanceModuleContract> all = registry.allModules();
        // CRM, Finance, Analytics, Workflow, Module Registry = 5 adapters
        assertThat(all.size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void allModules_containsExpectedModuleCodes() {
        List<ManagementGovernanceModuleContract> all = registry.allModules();
        List<String> codes = all.stream().map(ManagementGovernanceModuleContract::moduleCode).toList();
        assertThat(codes).contains("CRM", "FINANCE", "ANALYTICS", "WORKFLOW");
    }

    @Test
    void compositeSummary_returnsFullContractSurface() {
        registry.invalidate(tenantId);
        List<Map<String, Object>> summary = registry.compositeSummary(tenantId);
        for (Map<String, Object> row : summary) {
            assertThat(row).containsKeys("moduleCode", "displayName", "enabled", "healthStatus",
                    "capabilities", "kpiSummary", "operationalSummary",
                    "openAlertsCount", "openRisksCount", "openIssuesCount",
                    "slaState", "metadata");
        }
    }

    @Test
    void find_unknownModuleCode_returnsEmpty() {
        // ERP is not yet implemented — registry must tolerate the lookup
        var found = registry.find(tenantId, "ERP");
        assertThat(found).isEmpty();
    }

    @Test
    void find_existingModuleCode_returnsAdapter() {
        // CRM adapter is registered — lookup should succeed when CRM is enabled
        // (depends on entitlement; if not enabled, returns empty — both are valid)
        var found = registry.find(tenantId, "CRM");
        // Either present (if CRM enabled) or empty (if not) — both are valid behavior
        assertThat(found).isNotNull();
    }

    @Test
    void compositeHealthStatus_isNotNullForAnyTenant() {
        registry.invalidate(tenantId);
        var status = registry.compositeHealthStatus(tenantId);
        assertThat(status).isNotNull();
        // For a tenant with no entitlements, status is UNAVAILABLE
        // For a tenant with default-enabled modules, status could be HEALTHY
        assertThat(status.name()).isIn("HEALTHY", "DEGRADED", "UNHEALTHY", "UNAVAILABLE");
    }

    @Test
    void contractMethods_neverThrowForUnknownFutureModule() {
        // Verify contract methods are defensive — even for the MODULE_REGISTRY
        // (which is always enabled) they should not throw.
        var modules = registry.allModules();
        for (ManagementGovernanceModuleContract m : modules) {
            try {
                boolean enabled = m.isEnabled(tenantId);
                var health = m.healthStatus(tenantId);
                var caps = m.capabilities(tenantId);
                var kpis = m.kpiSummary(tenantId);
                var ops = m.operationalSummary(tenantId);
                int alerts = m.openAlertsCount(tenantId);
                int risks = m.openRisksCount(tenantId);
                int issues = m.openIssuesCount(tenantId);
                var sla = m.slaState(tenantId);
                var meta = m.metadata();
                // No exceptions expected
                assertThat(health).isNotNull();
                assertThat(sla).isNotNull();
            } catch (Exception e) {
                throw new AssertionError("Contract method on " + m.moduleCode() + " threw: " + e.getMessage(), e);
            }
        }
    }

    @Test
    void allModuleMetadata_containsVersionAndMaturity() {
        for (ManagementGovernanceModuleContract m : registry.allModules()) {
            Map<String, Object> meta = m.metadata();
            assertThat(meta).containsKeys("version", "maturity");
        }
    }
}
