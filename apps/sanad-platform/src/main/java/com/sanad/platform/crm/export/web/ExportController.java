package com.sanad.platform.crm.export.web;

import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM CSV export.
 * <p>
 * Mounted under {@code /api/v1/crm/export/{entityType}}. Produces CSV
 * files for accounts, contacts, and leads. Uses the appropriate
 * READ capability for each entity type.
 * <p>
 * Branch: feature/crm-search-export
 */
@RestController
@RequestMapping("/api/v1/crm/export")
public class ExportController {

    private final NamedParameterJdbcTemplate jdbc;

    public ExportController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts")
    public void exportAccounts(Authentication authentication,
                                @RequestParam(required = false) String search,
                                HttpServletResponse response) throws IOException {
        UUID tenantId = tenantId(authentication);
        writeCsvHeader(response, "crm-accounts", "id,display_name,account_type,lifecycle_status,primary_currency_code,updated_at");

        StringBuilder sql = new StringBuilder(
                "SELECT id, display_name, account_type, lifecycle_status, primary_currency_code, updated_at " +
                "FROM crm_accounts WHERE tenant_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", tenantId);
        if (search != null && !search.isBlank()) {
            sql.append(" AND LOWER(display_name) LIKE :search");
            params.addValue("search", "%" + search.toLowerCase() + "%");
        }
        sql.append(" ORDER BY display_name ASC LIMIT 5000");

        PrintWriter writer = response.getWriter();
        for (Map<String, Object> row : jdbc.queryForList(sql.toString(), params)) {
            writer.println(csvRow(
                    str(row.get("id")), str(row.get("display_name")), str(row.get("account_type")),
                    str(row.get("lifecycle_status")), str(row.get("primary_currency_code")), str(row.get("updated_at"))));
        }
        writer.flush();
    }

    @RequireCapability("CRM.CONTACT.READ")
    @GetMapping("/contacts")
    public void exportContacts(Authentication authentication,
                                @RequestParam(required = false) String search,
                                HttpServletResponse response) throws IOException {
        UUID tenantId = tenantId(authentication);
        writeCsvHeader(response, "crm-contacts", "id,given_name,family_name,primary_email,primary_phone,lifecycle_status,updated_at");

        StringBuilder sql = new StringBuilder(
                "SELECT id, given_name, family_name, primary_email, primary_phone, lifecycle_status, updated_at " +
                "FROM crm_contacts WHERE tenant_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", tenantId);
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(given_name) LIKE :search OR LOWER(COALESCE(primary_email, '')) LIKE :search)");
            params.addValue("search", "%" + search.toLowerCase() + "%");
        }
        sql.append(" ORDER BY given_name ASC LIMIT 5000");

        PrintWriter writer = response.getWriter();
        for (Map<String, Object> row : jdbc.queryForList(sql.toString(), params)) {
            writer.println(csvRow(
                    str(row.get("id")), str(row.get("given_name")), str(row.get("family_name")),
                    str(row.get("primary_email")), str(row.get("primary_phone")),
                    str(row.get("lifecycle_status")), str(row.get("updated_at"))));
        }
        writer.flush();
    }

    @RequireCapability("CRM.LEAD.READ")
    @GetMapping("/leads")
    public void exportLeads(Authentication authentication,
                             @RequestParam(required = false) String search,
                             HttpServletResponse response) throws IOException {
        UUID tenantId = tenantId(authentication);
        writeCsvHeader(response, "crm-leads", "id,display_name,company_name,email,phone,source,status,score,updated_at");

        StringBuilder sql = new StringBuilder(
                "SELECT id, display_name, company_name, email, phone, source, status, score, updated_at " +
                "FROM crm_leads WHERE tenant_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", tenantId);
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(display_name) LIKE :search OR LOWER(COALESCE(company_name, '')) LIKE :search OR LOWER(COALESCE(email, '')) LIKE :search)");
            params.addValue("search", "%" + search.toLowerCase() + "%");
        }
        sql.append(" ORDER BY display_name ASC LIMIT 5000");

        PrintWriter writer = response.getWriter();
        for (Map<String, Object> row : jdbc.queryForList(sql.toString(), params)) {
            writer.println(csvRow(
                    str(row.get("id")), str(row.get("display_name")), str(row.get("company_name")),
                    str(row.get("email")), str(row.get("phone")), str(row.get("source")),
                    str(row.get("status")), str(row.get("score")), str(row.get("updated_at"))));
        }
        writer.flush();
    }

    private void writeCsvHeader(HttpServletResponse response, String filename, String headerLine) {
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".csv\"");
        try {
            response.getWriter().println(headerLine);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write CSV header", e);
        }
    }

    private static String str(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        return s.equals("null") ? "" : s;
    }

    private String csvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String csvRow(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(csvField(fields[i]));
        }
        return sb.toString();
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
