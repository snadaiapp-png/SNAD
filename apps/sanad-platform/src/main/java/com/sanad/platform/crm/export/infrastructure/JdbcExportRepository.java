package com.sanad.platform.crm.export.infrastructure;

import com.sanad.platform.crm.export.domain.ExportRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcExportRepository implements ExportRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcExportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AccountExportRow> exportAccounts(UUID tenantId, String search, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,display_name,account_type,lifecycle_status,primary_currency_code,updated_at "
                        + "FROM crm_accounts WHERE tenant_id=:tenantId");
        MapSqlParameterSource params = parameters(tenantId, search, limit);
        if (search != null) sql.append(" AND LOWER(display_name) LIKE :search");
        sql.append(" ORDER BY display_name ASC LIMIT :limit");
        return jdbc.queryForList(sql.toString(), params).stream().map(this::accountRow).toList();
    }

    @Override
    public List<ContactExportRow> exportContacts(UUID tenantId, String search, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,given_name,family_name,primary_email,primary_phone,lifecycle_status,updated_at "
                        + "FROM crm_contacts WHERE tenant_id=:tenantId");
        MapSqlParameterSource params = parameters(tenantId, search, limit);
        if (search != null) {
            sql.append(" AND (LOWER(given_name) LIKE :search "
                    + "OR LOWER(COALESCE(primary_email,'')) LIKE :search)");
        }
        sql.append(" ORDER BY given_name ASC LIMIT :limit");
        return jdbc.queryForList(sql.toString(), params).stream().map(this::contactRow).toList();
    }

    @Override
    public List<LeadExportRow> exportLeads(UUID tenantId, String search, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,display_name,company_name,email,phone,source,status,score,updated_at "
                        + "FROM crm_leads WHERE tenant_id=:tenantId");
        MapSqlParameterSource params = parameters(tenantId, search, limit);
        if (search != null) {
            sql.append(" AND (LOWER(display_name) LIKE :search "
                    + "OR LOWER(COALESCE(company_name,'')) LIKE :search "
                    + "OR LOWER(COALESCE(email,'')) LIKE :search)");
        }
        sql.append(" ORDER BY display_name ASC LIMIT :limit");
        return jdbc.queryForList(sql.toString(), params).stream().map(this::leadRow).toList();
    }

    private static MapSqlParameterSource parameters(UUID tenantId, String search, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", limit);
        if (search != null) params.addValue("search", "%" + search + "%");
        return params;
    }

    private AccountExportRow accountRow(Map<String, Object> row) {
        return new AccountExportRow(
                (UUID) row.get("id"), string(row, "display_name"), string(row, "account_type"),
                string(row, "lifecycle_status"), string(row, "primary_currency_code"),
                instant(row.get("updated_at")));
    }

    private ContactExportRow contactRow(Map<String, Object> row) {
        return new ContactExportRow(
                (UUID) row.get("id"), string(row, "given_name"), string(row, "family_name"),
                string(row, "primary_email"), string(row, "primary_phone"),
                string(row, "lifecycle_status"), instant(row.get("updated_at")));
    }

    private LeadExportRow leadRow(Map<String, Object> row) {
        Object score = row.get("score");
        return new LeadExportRow(
                (UUID) row.get("id"), string(row, "display_name"), string(row, "company_name"),
                string(row, "email"), string(row, "phone"), string(row, "source"),
                string(row, "status"), score instanceof BigDecimal value ? value : null,
                instant(row.get("updated_at")));
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        throw new IllegalStateException("Unsupported CRM export timestamp type: " + value.getClass());
    }
}
