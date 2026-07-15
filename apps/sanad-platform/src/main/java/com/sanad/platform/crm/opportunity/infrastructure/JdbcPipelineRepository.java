package com.sanad.platform.crm.opportunity.infrastructure;

import com.sanad.platform.crm.opportunity.domain.PipelineRepository;
import com.sanad.platform.crm.error.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcPipelineRepository implements PipelineRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcPipelineRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public PipelineRecord findById(UUID t, UUID id) {
        try { return mapRow(jdbc.queryForMap("SELECT * FROM crm_pipelines WHERE tenant_id=:t AND id=:id", new MapSqlParameterSource().addValue("t",t).addValue("id",id))); }
        catch (org.springframework.dao.EmptyResultDataAccessException e) { throw new CrmContractException(CrmErrorCode.CRM_PIPELINE_NOT_FOUND); }
    }
    public List<PipelineRecord> findAll(UUID t) {
        return jdbc.queryForList("SELECT * FROM crm_pipelines WHERE tenant_id=:t ORDER BY name", new MapSqlParameterSource().addValue("t",t)).stream().map(this::mapRow).toList();
    }
    public PipelineRecord create(UUID t, UUID actorId, CreatePipelineCommand cmd) {
        UUID id = UUID.randomUUID(); Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_pipelines (id,tenant_id,version,name,currency_code,active,created_by,created_at,updated_at) VALUES (:id,:t,0,:name,:currencyCode,TRUE,:actorId,:now,:now)",
                new MapSqlParameterSource().addValue("id",id).addValue("t",t).addValue("name",cmd.name()).addValue("currencyCode",cmd.currencyCode()).addValue("actorId",actorId).addValue("now",Timestamp.from(now)));
        List<String> stages = cmd.stages();
        for (int i = 0; i < stages.size(); i++) {
            UUID stageId = UUID.randomUUID();
            String stageName = stages.get(i);
            // Compute terminal_state and probability to match V1 semantics:
            //   "Won" -> terminal WON, probability 100
            //   "Lost" -> terminal LOST, probability 0
            //   other -> non-terminal, probability scaled by position
            String terminal = stageName.equalsIgnoreCase("Won") ? "WON"
                    : stageName.equalsIgnoreCase("Lost") ? "LOST" : null;
            java.math.BigDecimal probability;
            if (terminal == null) {
                int probPct = Math.min(90, Math.round((i * 100f) / Math.max(1, stages.size() - 1)));
                probability = java.math.BigDecimal.valueOf(probPct);
            } else {
                probability = "WON".equals(terminal)
                        ? java.math.BigDecimal.valueOf(100)
                        : java.math.BigDecimal.ZERO;
            }
            jdbc.update("INSERT INTO crm_pipeline_stages (id,tenant_id,pipeline_id,name,sequence,probability,terminal_state,active) VALUES (:id,:t,:pipelineId,:name,:seq,:prob,:terminal,TRUE)",
                    new MapSqlParameterSource().addValue("id",stageId).addValue("t",t).addValue("pipelineId",id).addValue("name",stageName).addValue("seq",i+1).addValue("prob",probability).addValue("terminal",terminal));
        }
        return findById(t, id);
    }
    public PipelineRecord update(UUID t, UUID actorId, UUID id, String name, String currencyCode, long expectedVersion) {
        StringBuilder sql = new StringBuilder("UPDATE crm_pipelines SET version=version+1,updated_by=:actorId,updated_at=:now");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t",t).addValue("id",id).addValue("expectedVersion",expectedVersion).addValue("actorId",actorId).addValue("now",Timestamp.from(Instant.now()));
        if (name != null) { sql.append(",name=:name"); params.addValue("name", name); }
        if (currencyCode != null) { sql.append(",currency_code=:currencyCode"); params.addValue("currencyCode", currencyCode); }
        sql.append(" WHERE tenant_id=:t AND id=:id AND version=:expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(t, id);
    }
    public List<StageRecord> findStages(UUID t, UUID pipelineId) {
        return jdbc.queryForList("SELECT * FROM crm_pipeline_stages WHERE tenant_id=:t AND pipeline_id=:pipelineId AND active=TRUE ORDER BY sequence",
                new MapSqlParameterSource().addValue("t",t).addValue("pipelineId",pipelineId)).stream().map(r -> new StageRecord((UUID)r.get("id"),(UUID)r.get("pipeline_id"),(String)r.get("name"),((Number)r.get("sequence")).intValue(),(java.math.BigDecimal)r.get("probability"),(String)r.get("terminal_state"),true)).toList();
    }
    private PipelineRecord mapRow(Map<String,Object> r) {
        return new PipelineRecord((UUID)r.get("id"),asLong(r.get("version")),(String)r.get("name"),(String)r.get("currency_code"),r.get("active")!=null&&((Boolean)r.get("active")),asInstant(r.get("created_at")),asInstant(r.get("updated_at")));
    }
    private static long asLong(Object v) { if(v==null)return 0L; if(v instanceof Number n)return n.longValue(); try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return 0L;} }
    private static Instant asInstant(Object v) { if(v==null)return null; if(v instanceof Timestamp t)return t.toInstant(); if(v instanceof Instant i)return i; return null; }
}
