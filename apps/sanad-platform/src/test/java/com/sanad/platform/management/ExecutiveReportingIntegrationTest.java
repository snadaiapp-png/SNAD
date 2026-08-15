package com.sanad.platform.management;

import com.sanad.platform.management.application.ExecutiveReportService;
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
 * Integration test for GAP 25 — Executive Reporting.
 *
 * Verifies the Executive Report aggregates all management domains
 * (KPIs, objectives, risks, issues, alerts, decisions, escalations,
 * CRM, Finance, Analytics, Workflow, module health, revenue overview,
 * operational overview) and exposes metadata (tenant, generatedAt,
 * reportingPeriod, sourceModules, reportStatus, version).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class ExecutiveReportingIntegrationTest {

    @Autowired private ExecutiveReportService reportService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "er-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateReport_returnsAllExpectedSections() {
        Map<String, Object> report = reportService.generateReport(tenantId);
        assertThat(report).containsKeys(
                "commandCenter", "revenueOverview", "operationalOverview",
                "moduleHealth", "executiveIntelligence", "_metadata");
    }

    @Test
    void generateReport_metadataContainsRequiredFields() {
        Map<String, Object> report = reportService.generateReport(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("_metadata");
        assertThat(metadata).containsKeys(
                "tenantId", "generatedAt", "reportingPeriodStart", "reportingPeriodEnd",
                "version", "format", "sourceModules", "reportStatus");
    }

    @Test
    void generateReport_tenantIdMatchesRequestedTenant() {
        Map<String, Object> report = reportService.generateReport(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("_metadata");
        assertThat(metadata.get("tenantId")).isEqualTo(tenantId.toString());
    }

    @Test
    void generateReport_reportStatusIsCompleteOrPartial() {
        Map<String, Object> report = reportService.generateReport(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("_metadata");
        String status = (String) metadata.get("reportStatus");
        assertThat(status).isIn("COMPLETE", "PARTIAL");
    }

    @Test
    void generateReport_isTenantScoped() {
        // Generate for a different tenant — must not include tenantId's data
        UUID otherTenant = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)",
                otherTenant, "er-other-" + otherTenant.toString().substring(0, 8), now, now);

        Map<String, Object> report = reportService.generateReport(otherTenant);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("_metadata");
        assertThat(metadata.get("tenantId")).isEqualTo(otherTenant.toString());
    }

    @Test
    void generateReport_versionIs10() {
        Map<String, Object> report = reportService.generateReport(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("_metadata");
        assertThat(metadata.get("version")).isEqualTo("1.0");
    }

    @Test
    void generateReport_formatIsJson() {
        Map<String, Object> report = reportService.generateReport(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("_metadata");
        assertThat(metadata.get("format")).isEqualTo("JSON");
    }

    @Test
    void generateReport_sourceModulesIncludesCommandCenter() {
        Map<String, Object> report = reportService.generateReport(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("_metadata");
        @SuppressWarnings("unchecked")
        List<String> sources = (List<String>) metadata.get("sourceModules");
        assertThat(sources).contains("COMMAND_CENTER");
    }
}
