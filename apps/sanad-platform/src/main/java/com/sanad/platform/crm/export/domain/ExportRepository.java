package com.sanad.platform.crm.export.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only persistence port for bounded CRM exports. */
public interface ExportRepository {
    List<AccountExportRow> exportAccounts(UUID tenantId, String search, int limit);
    List<ContactExportRow> exportContacts(UUID tenantId, String search, int limit);
    List<LeadExportRow> exportLeads(UUID tenantId, String search, int limit);

    record AccountExportRow(UUID id, String displayName, String accountType,
                            String lifecycleStatus, String primaryCurrencyCode,
                            Instant updatedAt) {}

    record ContactExportRow(UUID id, String givenName, String familyName,
                            String primaryEmail, String primaryPhone,
                            String lifecycleStatus, Instant updatedAt) {}

    record LeadExportRow(UUID id, String displayName, String companyName,
                         String email, String phone, String source, String status,
                         BigDecimal score, Instant updatedAt) {}
}
