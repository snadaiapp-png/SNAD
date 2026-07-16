package com.sanad.platform.crm.export.web;

import com.sanad.platform.crm.export.application.ExportUseCases;
import com.sanad.platform.crm.export.domain.ExportRepository.AccountExportRow;
import com.sanad.platform.crm.export.domain.ExportRepository.ContactExportRow;
import com.sanad.platform.crm.export.domain.ExportRepository.LeadExportRow;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

/** V1 REST controller for bounded, tenant-scoped CRM CSV exports. */
@RestController
@RequestMapping("/api/v1/crm/export")
public class ExportController {
    private final ExportUseCases exports;

    public ExportController(ExportUseCases exports) {
        this.exports = exports;
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts")
    public void exportAccounts(Authentication authentication,
                               @RequestParam(required = false) String search,
                               HttpServletResponse response) throws IOException {
        writeCsvHeader(response, "crm-accounts",
                "id,display_name,account_type,lifecycle_status,primary_currency_code,updated_at");
        PrintWriter writer = response.getWriter();
        for (AccountExportRow row : exports.exportAccounts(tenantId(authentication), search)) {
            writer.println(csvRow(str(row.id()), row.displayName(), row.accountType(),
                    row.lifecycleStatus(), row.primaryCurrencyCode(), str(row.updatedAt())));
        }
        writer.flush();
    }

    @RequireCapability("CRM.CONTACT.READ")
    @GetMapping("/contacts")
    public void exportContacts(Authentication authentication,
                               @RequestParam(required = false) String search,
                               HttpServletResponse response) throws IOException {
        writeCsvHeader(response, "crm-contacts",
                "id,given_name,family_name,primary_email,primary_phone,lifecycle_status,updated_at");
        PrintWriter writer = response.getWriter();
        for (ContactExportRow row : exports.exportContacts(tenantId(authentication), search)) {
            writer.println(csvRow(str(row.id()), row.givenName(), row.familyName(),
                    row.primaryEmail(), row.primaryPhone(), row.lifecycleStatus(), str(row.updatedAt())));
        }
        writer.flush();
    }

    @RequireCapability("CRM.LEAD.READ")
    @GetMapping("/leads")
    public void exportLeads(Authentication authentication,
                            @RequestParam(required = false) String search,
                            HttpServletResponse response) throws IOException {
        writeCsvHeader(response, "crm-leads",
                "id,display_name,company_name,email,phone,source,status,score,updated_at");
        PrintWriter writer = response.getWriter();
        for (LeadExportRow row : exports.exportLeads(tenantId(authentication), search)) {
            writer.println(csvRow(str(row.id()), row.displayName(), row.companyName(), row.email(),
                    row.phone(), row.source(), row.status(), str(row.score()), str(row.updatedAt())));
        }
        writer.flush();
    }

    private void writeCsvHeader(HttpServletResponse response, String filename, String headerLine) {
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + ".csv\"");
        try {
            response.getWriter().println(headerLine);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to write CSV header", exception);
        }
    }

    private static String str(Object value) {
        if (value == null) return "";
        String string = String.valueOf(value);
        return "null".equals(string) ? "" : string;
    }

    private String csvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String csvRow(String... fields) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) result.append(',');
            result.append(csvField(fields[index]));
        }
        return result.toString();
    }

    private static UUID tenantId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get("tenant_id") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get("tenant_id").toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated CRM context", exception);
        }
    }
}
