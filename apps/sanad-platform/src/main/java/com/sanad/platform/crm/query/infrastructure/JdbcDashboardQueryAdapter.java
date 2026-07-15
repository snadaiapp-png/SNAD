package com.sanad.platform.crm.query.infrastructure;

import com.sanad.platform.crm.query.domain.DashboardQueryPort;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
public class JdbcDashboardQueryAdapter implements DashboardQueryPort {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcDashboardQueryAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DashboardKpisView getDashboardKpis(UUID tenantId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("t", tenantId);
        long totalAccounts = count("SELECT count(*) FROM crm_accounts WHERE tenant_id=:t", p);
        long activeAccounts = count("SELECT count(*) FROM crm_accounts WHERE tenant_id=:t AND lifecycle_status='ACTIVE'", p);
        long totalContacts = count("SELECT count(*) FROM crm_contacts WHERE tenant_id=:t", p);
        long totalLeads = count("SELECT count(*) FROM crm_leads WHERE tenant_id=:t", p);
        long openLeads = count("SELECT count(*) FROM crm_leads WHERE tenant_id=:t AND status NOT IN ('CONVERTED','DISQUALIFIED','ARCHIVED')", p);
        long totalOpportunities = count("SELECT count(*) FROM crm_opportunities WHERE tenant_id=:t", p);
        long openOpportunities = count("SELECT count(*) FROM crm_opportunities WHERE tenant_id=:t AND status='OPEN'", p);
        long totalActivities = count("SELECT count(*) FROM crm_activities WHERE tenant_id=:t", p);
        long openActivities = count("SELECT count(*) FROM crm_activities WHERE tenant_id=:t AND status='OPEN'", p);
        return new DashboardKpisView(totalAccounts, activeAccounts, totalContacts, totalLeads, openLeads, totalOpportunities, openOpportunities, totalActivities, openActivities, BigDecimal.ZERO);
    }
    private long count(String sql, MapSqlParameterSource p) {
        try { return jdbc.queryForObject(sql, p, Long.class); } catch (Exception e) { return 0; }
    }
}
