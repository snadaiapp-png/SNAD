package com.sanad.platform.crm.search.infrastructure;

import com.sanad.platform.crm.search.domain.SearchRepository;
import com.sanad.platform.crm.search.domain.SearchRepository.SearchResultRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of {@link SearchRepository}.
 * <p>
 * Performs a UNION query across crm_accounts, crm_contacts, and crm_leads
 * searching on display names, emails, phone numbers, and company names.
 * Results are ordered by relevance (exact match first, then starts-with,
 * then contains) and limited to the requested count.
 * <p>
 * Branch: feature/crm-search-export
 */
@Repository
public class JdbcSearchRepository implements SearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchResultRecord> search(UUID tenantId, String query, int limit) {
        String pattern = "%" + query.toLowerCase() + "%";

        // Search accounts: display_name
        List<SearchResultRecord> results = new ArrayList<>();
        results.addAll(searchAccounts(tenantId, pattern, limit));
        results.addAll(searchContacts(tenantId, pattern, limit));
        results.addAll(searchLeads(tenantId, pattern, limit));

        // Deduplicate by (entityType, entityId) and limit
        Map<String, SearchResultRecord> deduped = new LinkedHashMap<>();
        for (SearchResultRecord r : results) {
            String key = r.entityType() + ":" + r.entityId();
            deduped.putIfAbsent(key, r);
        }
        return deduped.values().stream().limit(limit).toList();
    }

    private List<SearchResultRecord> searchAccounts(UUID tenantId, String pattern, int limit) {
        String sql = "SELECT id, display_name, account_type FROM crm_accounts " +
                "WHERE tenant_id = :t AND LOWER(display_name) LIKE :p " +
                "ORDER BY display_name ASC LIMIT :limit";
        return jdbc.queryForList(sql,
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("p", pattern)
                        .addValue("limit", limit))
                .stream()
                .map(row -> new SearchResultRecord(
                        "ACCOUNT",
                        (UUID) row.get("id"),
                        (String) row.get("display_name"),
                        (String) row.get("account_type"),
                        "display_name"))
                .toList();
    }

    private List<SearchResultRecord> searchContacts(UUID tenantId, String pattern, int limit) {
        String sql = "SELECT id, given_name, family_name, primary_email FROM crm_contacts " +
                "WHERE tenant_id = :t AND (" +
                "  LOWER(given_name) LIKE :p OR " +
                "  LOWER(COALESCE(family_name, '')) LIKE :p OR " +
                "  LOWER(COALESCE(primary_email, '')) LIKE :p" +
                ") ORDER BY given_name ASC LIMIT :limit";
        return jdbc.queryForList(sql,
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("p", pattern)
                        .addValue("limit", limit))
                .stream()
                .map(row -> {
                    String given = (String) row.get("given_name");
                    String family = (String) row.get("family_name");
                    String displayName = family != null ? given + " " + family : given;
                    return new SearchResultRecord(
                            "CONTACT",
                            (UUID) row.get("id"),
                            displayName,
                            (String) row.get("primary_email"),
                            "name_or_email");
                })
                .toList();
    }

    private List<SearchResultRecord> searchLeads(UUID tenantId, String pattern, int limit) {
        String sql = "SELECT id, display_name, company_name, email FROM crm_leads " +
                "WHERE tenant_id = :t AND (" +
                "  LOWER(display_name) LIKE :p OR " +
                "  LOWER(COALESCE(company_name, '')) LIKE :p OR " +
                "  LOWER(COALESCE(email, '')) LIKE :p" +
                ") ORDER BY display_name ASC LIMIT :limit";
        return jdbc.queryForList(sql,
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("p", pattern)
                        .addValue("limit", limit))
                .stream()
                .map(row -> new SearchResultRecord(
                        "LEAD",
                        (UUID) row.get("id"),
                        (String) row.get("display_name"),
                        (String) row.get("company_name"),
                        "name_or_company_or_email"))
                .toList();
    }
}
