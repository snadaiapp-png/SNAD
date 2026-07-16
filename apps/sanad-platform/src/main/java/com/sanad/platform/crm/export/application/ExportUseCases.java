package com.sanad.platform.crm.export.application;

import com.sanad.platform.crm.export.domain.ExportRepository;
import com.sanad.platform.crm.export.domain.ExportRepository.AccountExportRow;
import com.sanad.platform.crm.export.domain.ExportRepository.ContactExportRow;
import com.sanad.platform.crm.export.domain.ExportRepository.LeadExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExportUseCases {
    private static final int MAX_EXPORT_ROWS = 5_000;
    private final ExportRepository repository;

    public ExportUseCases(ExportRepository repository) {
        this.repository = repository;
    }

    public List<AccountExportRow> exportAccounts(UUID tenantId, String search) {
        return repository.exportAccounts(tenantId, normalize(search), MAX_EXPORT_ROWS);
    }

    public List<ContactExportRow> exportContacts(UUID tenantId, String search) {
        return repository.exportContacts(tenantId, normalize(search), MAX_EXPORT_ROWS);
    }

    public List<LeadExportRow> exportLeads(UUID tenantId, String search) {
        return repository.exportLeads(tenantId, normalize(search), MAX_EXPORT_ROWS);
    }

    private static String normalize(String search) {
        return search == null || search.isBlank() ? null : search.trim().toLowerCase();
    }
}
