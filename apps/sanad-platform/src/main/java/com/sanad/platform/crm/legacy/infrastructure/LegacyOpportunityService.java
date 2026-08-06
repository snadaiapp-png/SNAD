package com.sanad.platform.crm.legacy.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.crm.legacy.infrastructure.LegacySupport.*;

@Service
public class LegacyOpportunityService {

    private final LegacySupport support;

    public LegacyOpportunityService(LegacySupport support) {
        this.support = support;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOpportunity(Authentication authentication, UUID opportunityId) {
        UUID tenantId = support.tenantId(authentication);
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(support.one("crm_opportunities", tenantId, opportunityId,
                        "CRM opportunity not found"));
        result.put("stageHistory", support.jdbc.queryForList(
                "SELECT history.*,from_stage.name AS from_stage_name,to_stage.name AS to_stage_name " +
                        "FROM crm_opportunity_stage_history history " +
                        "LEFT JOIN crm_pipeline_stages from_stage ON from_stage.tenant_id=history.tenant_id AND from_stage.id=history.from_stage_id " +
                        "JOIN crm_pipeline_stages to_stage ON to_stage.tenant_id=history.tenant_id AND to_stage.id=history.to_stage_id " +
                        "WHERE history.tenant_id=:tenantId AND history.opportunity_id=:opportunityId " +
                        "ORDER BY history.changed_at DESC,history.id",
                p().addValue("tenantId", tenantId).addValue("opportunityId", opportunityId)));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPipelineStages(
            Authentication authentication, UUID pipelineId) {
        UUID tenantId = support.tenantId(authentication);
        support.one("crm_pipelines", tenantId, pipelineId, "CRM pipeline not found");
        return support.jdbc.queryForList(
                "SELECT * FROM crm_pipeline_stages WHERE tenant_id=:tenantId AND pipeline_id=:pipelineId AND active=TRUE ORDER BY sequence,id",
                p().addValue("tenantId", tenantId).addValue("pipelineId", pipelineId));
    }

    @Transactional
    public Map<String, Object> updateOpportunity(
            Authentication authentication, UUID opportunityId,
            BigDecimal amount, String name, UUID ownerUserId, long expectedVersion) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", opportunityId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", Timestamp.from(now))
                .addValue("amount", amount)
                .addValue("name", name)
                .addValue("ownerUserId", ownerUserId);
        StringBuilder sql = new StringBuilder("UPDATE crm_opportunities SET version = version + 1, updated_by = :actorId, updated_at = :now");
        if (amount != null) { sql.append(", amount = :amount"); }
        if (name != null) { sql.append(", name = :name"); }
        if (ownerUserId != null) { sql.append(", owner_user_id = :ownerUserId"); }
        sql.append(" WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion");
        int updated = support.jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return support.one("crm_opportunities", tenantId, opportunityId, "CRM opportunity not found");
    }

    @Transactional
    public Map<String, Object> updatePipeline(
            Authentication authentication, UUID pipelineId,
            String name, String currencyCode, long expectedVersion) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", pipelineId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", Timestamp.from(now))
                .addValue("name", name)
                .addValue("currencyCode", currencyCode);
        StringBuilder sql = new StringBuilder("UPDATE crm_pipelines SET version = version + 1, updated_by = :actorId, updated_at = :now");
        if (name != null) { sql.append(", name = :name"); }
        if (currencyCode != null) { sql.append(", currency_code = :currencyCode"); }
        sql.append(" WHERE tenant_id = :tenantId AND id = :id AND version = :expectedVersion");
        int updated = support.jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return support.one("crm_pipelines", tenantId, pipelineId, "CRM pipeline not found");
    }
}
