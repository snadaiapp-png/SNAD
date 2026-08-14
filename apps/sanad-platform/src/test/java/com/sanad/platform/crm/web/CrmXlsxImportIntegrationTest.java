package com.sanad.platform.crm.web;

import com.sanad.platform.crm.legacy.infrastructure.LegacyCrmInfrastructureService;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
class CrmXlsxImportIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LegacyCrmInfrastructureService extended;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,subdomain,name,status,locale,timezone,currency_code,created_at,updated_at) VALUES (?,?,?,'ACTIVE','ar-SA','Asia/Riyadh','SAR',?,?)",
                tenantId, "xlsx-" + tenantId.toString().substring(0, 8), "XLSX Tenant", now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)",
                userId, tenantId, userId + "@example.test", "XLSX User", now, now);
    }

    @AfterEach
    void cleanup() {
        if (tenantId == null) return;
        for (String table : List.of(
                "crm_import_errors", "crm_import_files", "crm_custom_field_values",
                "crm_timeline_events", "crm_activities", "crm_opportunity_stage_history",
                "crm_leads", "crm_opportunities", "crm_contacts", "crm_pipeline_stages",
                "crm_pipelines", "crm_accounts", "crm_import_jobs",
                "crm_custom_field_definitions")) {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", tenantId);
        }
        jdbc.update("DELETE FROM role_capabilities WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM roles WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM users WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM tenants WHERE id=?", tenantId);
    }

    @Test
    void importsFirstWorksheetUsingInlineStrings() throws Exception {
        byte[] xlsx = workbook(List.of(
                List.of("displayName", "companyName"),
                List.of("Noura Alharbi", "Noura Labs")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "leads.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        Map<String, Object> job = extended.uploadImport(auth(), "LEAD", null, file);
        assertThat(job.get("status")).isEqualTo("READY");
        assertThat(extended.processNextImportNow()).isTrue();

        UUID jobId = (UUID) job.get("id");
        Map<String, Object> completed = extended.getImportJob(auth(), jobId);
        assertThat(completed.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) completed.get("succeeded_rows")).longValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_leads WHERE tenant_id=? AND display_name='Noura Alharbi' AND company_name='Noura Labs'",
                Long.class, tenantId)).isEqualTo(1);
    }

    private Authentication auth() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId.toString(), "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return authentication;
    }

    private byte[] workbook(List<List<String>> rows) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int row = 0; row < rows.size(); row++) {
            xml.append("<row r=\"").append(row + 1).append("\">");
            for (int column = 0; column < rows.get(row).size(); column++) {
                xml.append("<c r=\"").append(column(column)).append(row + 1)
                        .append("\" t=\"inlineStr\"><is><t>")
                        .append(escape(rows.get(row).get(column)))
                        .append("</t></is></c>");
            }
            xml.append("</row>");
        }
        xml.append("</sheetData></worksheet>");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(xml.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private String column(int index) {
        StringBuilder value = new StringBuilder();
        int current = index + 1;
        while (current > 0) {
            value.insert(0, (char) ('A' + (current - 1) % 26));
            current = (current - 1) / 26;
        }
        return value.toString();
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
