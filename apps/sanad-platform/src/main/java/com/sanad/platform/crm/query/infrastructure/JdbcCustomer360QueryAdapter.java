package com.sanad.platform.crm.query.infrastructure;

import com.sanad.platform.crm.query.domain.Customer360QueryPort;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
public class JdbcCustomer360QueryAdapter implements Customer360QueryPort {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcCustomer360QueryAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Customer360View getCustomer360(UUID tenantId, UUID accountId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("t", tenantId).addValue("accountId", accountId);
        List<ContactSummary> contacts = jdbc.queryForList("SELECT id, display_name, primary_email, lifecycle_status FROM crm_contacts WHERE tenant_id=:t AND account_id=:accountId", p)
                .stream().map(r -> new ContactSummary((UUID)r.get("id"), (String)r.get("display_name"), (String)r.get("primary_email"), (String)r.get("lifecycle_status"))).toList();
        List<OpportunitySummary> opportunities = jdbc.queryForList("SELECT id, name, amount, currency_code, status FROM crm_opportunities WHERE tenant_id=:t AND account_id=:accountId", p)
                .stream().map(r -> new OpportunitySummary((UUID)r.get("id"), (String)r.get("name"), (BigDecimal)r.get("amount"), (String)r.get("currency_code"), (String)r.get("status"))).toList();
        List<ActivitySummary> activities = jdbc.queryForList("SELECT id, activity_type, subject, status FROM crm_activities WHERE tenant_id=:t AND related_type='ACCOUNT' AND related_id=:accountId", p)
                .stream().map(r -> new ActivitySummary((UUID)r.get("id"), (String)r.get("activity_type"), (String)r.get("subject"), (String)r.get("status"))).toList();
        return new Customer360View(accountId, null, null, null, contacts.size(), opportunities.size(), activities.size(), 0, contacts, opportunities, activities);
    }
}
