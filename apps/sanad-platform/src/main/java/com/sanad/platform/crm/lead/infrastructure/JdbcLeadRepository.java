package com.sanad.platform.crm.lead.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.lead.domain.LeadRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcLeadRepository implements LeadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcLeadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LeadRecord findById(UUID tenantId, UUID leadId) {
        try {
            return mapRow(jdbc.queryForMap(
                    "SELECT * FROM crm_leads WHERE tenant_id=:tenantId AND id=:id",
                    p(tenantId, leadId)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_LEAD_NOT_FOUND);
        }
    }

    public List<LeadRecord> findAll(UUID tenantId, int limit, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_leads WHERE tenant_id=:tenantId");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=:status");
            params.addValue("status", status.toUpperCase());
        }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT :limit");
        params.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapRow).toList();
    }

    public LeadRecord create(UUID tenantId, UUID actorId, CreateLeadCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_leads (id,tenant_id,version,display_name,normalized_name,company_name,email,normalized_email,phone,source,status,owner_user_id,score,created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,0,:displayName,LOWER(:displayName),:companyName,:email,LOWER(:email),:phone,:source,'NEW',:ownerUserId,:score,:actorId,:actorId,:now,:now)",
                new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId)
                        .addValue("displayName", cmd.displayName()).addValue("companyName", cmd.companyName())
                        .addValue("email", cmd.email()).addValue("phone", cmd.phone())
                        .addValue("source", cmd.source()).addValue("ownerUserId", cmd.ownerUserId())
                        .addValue("score", cmd.score()).addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        return findById(tenantId, id);
    }

    public LeadRecord changeStatus(UUID tenantId, UUID actorId, UUID leadId, String status,
                                   String reason, long expectedVersion) {
        int updated = jdbc.update("UPDATE crm_leads SET status=:status,updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", leadId)
                        .addValue("expectedVersion", expectedVersion).addValue("status", status.toUpperCase())
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, leadId);
    }

    public LeadConversionRecord convert(UUID tenantId, UUID actorId, UUID leadId,
                                        ConvertLeadCommand cmd, long expectedVersion) {
        LeadRecord lead = findById(tenantId, leadId);
        if ("CONVERTED".equals(lead.status())) {
            return new LeadConversionRecord(lead, lead.convertedAccountId(), lead.convertedContactId(),
                    lead.convertedOpportunityId(), true);
        }
        int updated = jdbc.update("UPDATE crm_leads SET status='CONVERTED',converted_account_id=:accountId,converted_contact_id=:contactId,converted_opportunity_id=:opportunityId,updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", leadId)
                        .addValue("expectedVersion", expectedVersion).addValue("accountId", cmd.accountId())
                        .addValue("contactId", cmd.contactId()).addValue("opportunityId", cmd.opportunityId())
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        LeadRecord updatedLead = findById(tenantId, leadId);
        return new LeadConversionRecord(updatedLead, cmd.accountId(), cmd.contactId(),
                cmd.opportunityId(), false);
    }

    private LeadRecord mapRow(Map<String, Object> r) {
        return new LeadRecord((UUID) r.get("id"), asLong(r.get("version")),
                (String) r.get("display_name"), (String) r.get("company_name"),
                (String) r.get("email"), (String) r.get("phone"), (String) r.get("source"),
                (String) r.get("status"), (UUID) r.get("owner_user_id"), (BigDecimal) r.get("score"),
                (UUID) r.get("converted_account_id"), (UUID) r.get("converted_contact_id"),
                (UUID) r.get("converted_opportunity_id"), asInstant(r.get("created_at")),
                asInstant(r.get("updated_at")));
    }

    private MapSqlParameterSource p(UUID tenantId, UUID id) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id);
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static Instant asInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp t) return t.toInstant();
        if (v instanceof Instant i) return i;
        return null;
    }
}
