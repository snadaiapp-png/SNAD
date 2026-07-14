package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.party.domain.AccountHierarchyPort;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class JdbcAccountHierarchyAdapter implements AccountHierarchyPort {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcAccountHierarchyAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean parentExists(UUID tenantId, UUID parentAccountId) {
        if (parentAccountId == null) return true;
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM crm_accounts WHERE tenant_id = :t AND id = :id AND lifecycle_status = 'ACTIVE'",
                new MapSqlParameterSource().addValue("t", tenantId).addValue("id", parentAccountId),
                Integer.class);
        return count != null && count > 0;
    }

    public boolean wouldCreateCycle(UUID tenantId, UUID accountId, UUID parentAccountId) {
        if (parentAccountId == null) return false;
        // Walk up the hierarchy to detect cycles (max 10 levels)
        UUID current = parentAccountId;
        for (int i = 0; i < 10; i++) {
            if (current.equals(accountId)) return true;
            try {
                UUID next = jdbc.queryForObject(
                        "SELECT parent_account_id FROM crm_accounts WHERE tenant_id = :t AND id = :id",
                        new MapSqlParameterSource().addValue("t", tenantId).addValue("id", current),
                        UUID.class);
                if (next == null) return false;
                current = next;
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                return false;
            }
        }
        return false;
    }

    public boolean hasActiveChildren(UUID tenantId, UUID accountId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM crm_accounts WHERE tenant_id = :t AND parent_account_id = :id AND lifecycle_status = 'ACTIVE'",
                new MapSqlParameterSource().addValue("t", tenantId).addValue("id", accountId),
                Integer.class);
        return count != null && count > 0;
    }
}
