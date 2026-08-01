package com.sanad.platform.crm.legacy.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.crm.legacy.infrastructure.LegacySupport.*;

@Service
public class LegacyDashboardService {

    private final LegacySupport support;
    private final LegacyCustomFieldService customFieldService;

    public LegacyDashboardService(LegacySupport support, LegacyCustomFieldService customFieldService) {
        this.support = support;
        this.customFieldService = customFieldService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(Authentication authentication) {
        UUID tenantId = support.tenantId(authentication);
        MapSqlParameterSource params = p().addValue("tenantId", tenantId);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", support.scalarLong(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id=:tenantId AND lifecycle_status<>'ARCHIVED'",
                params));
        result.put("contacts", support.scalarLong(
                "SELECT COUNT(*) FROM crm_contacts WHERE tenant_id=:tenantId AND lifecycle_status<>'ARCHIVED'",
                params));
        result.put("openLeads", support.scalarLong(
                "SELECT COUNT(*) FROM crm_leads WHERE tenant_id=:tenantId AND status NOT IN ('CONVERTED','DISQUALIFIED','ARCHIVED')",
                params));
        result.put("openOpportunities", support.scalarLong(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id=:tenantId AND status='OPEN'",
                params));
        BigDecimal weighted = support.jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(amount,0) * probability / 100),0) FROM crm_opportunities WHERE tenant_id=:tenantId AND status='OPEN'",
                params, BigDecimal.class);
        result.put("weightedPipeline", weighted == null ? BigDecimal.ZERO : weighted);
        result.put("overdueActivities", support.scalarLong(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id=:tenantId AND status IN ('OPEN','IN_PROGRESS') AND due_at IS NOT NULL AND due_at<CURRENT_TIMESTAMP",
                params));
        result.put("recentActivity", support.jdbc.queryForList(
                "SELECT * FROM crm_timeline_events WHERE tenant_id=:tenantId ORDER BY occurred_at DESC,id LIMIT 10",
                params));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customer360(Authentication authentication, UUID accountId) {
        UUID tenantId = support.tenantId(authentication);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("account", support.one("crm_accounts", tenantId, accountId, "CRM account not found"));
        result.put("customFields", customFieldService.readCustomFieldValuesInternal(tenantId, "ACCOUNT", accountId, false));
        MapSqlParameterSource params = p().addValue("tenantId", tenantId).addValue("accountId", accountId);
        result.put("contacts", support.jdbc.queryForList(
                "SELECT * FROM crm_contacts WHERE tenant_id=:tenantId AND account_id=:accountId AND lifecycle_status<>'ARCHIVED' ORDER BY updated_at DESC,id",
                params));
        result.put("opportunities", support.jdbc.queryForList(
                "SELECT opportunity.*,pipeline.name AS pipeline_name,stage.name AS stage_name " +
                        "FROM crm_opportunities opportunity " +
                        "JOIN crm_pipelines pipeline ON pipeline.tenant_id=opportunity.tenant_id AND pipeline.id=opportunity.pipeline_id " +
                        "JOIN crm_pipeline_stages stage ON stage.tenant_id=opportunity.tenant_id AND stage.id=opportunity.stage_id " +
                        "WHERE opportunity.tenant_id=:tenantId AND opportunity.account_id=:accountId " +
                        "ORDER BY opportunity.updated_at DESC,opportunity.id",
                params));
        result.put("activities", support.jdbc.queryForList(
                "SELECT * FROM crm_activities WHERE tenant_id=:tenantId AND related_type='ACCOUNT' AND related_id=:accountId ORDER BY created_at DESC,id LIMIT 100",
                params));
        result.put("timeline", support.jdbc.queryForList(
                "SELECT * FROM crm_timeline_events WHERE tenant_id=:tenantId AND subject_type='ACCOUNT' AND subject_id=:accountId ORDER BY occurred_at DESC,id LIMIT 200",
                params));
        return result;
    }

    @Transactional
    public Map<String, Object> restoreAccount(Authentication authentication, UUID accountId) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        Instant now = Instant.now();
        int changed = support.jdbc.update(
                "UPDATE crm_accounts SET lifecycle_status='ACTIVE',archived_at=NULL,updated_at=:now,updated_by=:actorId,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND lifecycle_status='ARCHIVED'",
                support.context(tenantId, actorId, accountId, now));
        if (changed != 1) throw conflict("CRM account is not archived or does not exist");
        support.timeline(tenantId, "ACCOUNT", accountId, "crm.account.restored",
                "Account restored", "CRM_ACCOUNT", accountId, actorId, now);
        return support.one("crm_accounts", tenantId, accountId, "CRM account not found");
    }
}
