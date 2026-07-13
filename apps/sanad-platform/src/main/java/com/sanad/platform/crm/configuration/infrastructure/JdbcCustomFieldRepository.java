package com.sanad.platform.crm.configuration.infrastructure;

import com.sanad.platform.crm.configuration.domain.CustomFieldRepository;
import com.sanad.platform.crm.error.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcCustomFieldRepository implements CustomFieldRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcCustomFieldRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public CustomFieldRecord findById(UUID t, UUID fieldId) {
        try { return mapRow(jdbc.queryForMap("SELECT * FROM crm_custom_field_definitions WHERE tenant_id=:t AND id=:id", new MapSqlParameterSource().addValue("t",t).addValue("id",fieldId))); }
        catch (org.springframework.dao.EmptyResultDataAccessException e) { throw new CrmContractException(CrmErrorCode.CRM_CUSTOM_FIELD_NOT_FOUND); }
    }
    public List<CustomFieldRecord> findAll(UUID t, String entityType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_custom_field_definitions WHERE tenant_id=:t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", t);
        if (entityType != null) { sql.append(" AND entity_type=:entityType"); params.addValue("entityType", entityType.toUpperCase()); }
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapRow).toList();
    }
    public CustomFieldRecord create(UUID t, UUID actorId, CreateCustomFieldCommand cmd) {
        UUID id = UUID.randomUUID(); Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_custom_field_definitions (id,tenant_id,version,entity_type,field_key,label_ar,label_en,data_type,sensitive,searchable,required,active,created_by,updated_by,created_at,updated_at) VALUES (:id,:t,0,:entityType,:fieldKey,:labelAr,:labelEn,:dataType,:sensitive,:searchable,:required,TRUE,:actorId,:actorId,:now,:now)",
                new MapSqlParameterSource().addValue("id",id).addValue("t",t).addValue("entityType",cmd.entityType().toUpperCase()).addValue("fieldKey",cmd.fieldKey()).addValue("labelAr",cmd.labelAr()).addValue("labelEn",cmd.labelEn()).addValue("dataType",cmd.dataType().toUpperCase()).addValue("sensitive",Boolean.TRUE.equals(cmd.sensitive())).addValue("searchable",Boolean.TRUE.equals(cmd.searchable())).addValue("required",Boolean.TRUE.equals(cmd.required())).addValue("actorId",actorId).addValue("now",Timestamp.from(now)));
        return findById(t, id);
    }
    public CustomFieldRecord update(UUID t, UUID actorId, UUID fieldId, UpdateCustomFieldCommand cmd, long expectedVersion) {
        StringBuilder sql = new StringBuilder("UPDATE crm_custom_field_definitions SET version=version+1,updated_by=:actorId,updated_at=:now");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t",t).addValue("id",fieldId).addValue("expectedVersion",expectedVersion).addValue("actorId",actorId).addValue("now",Timestamp.from(Instant.now()));
        if (cmd.labelAr() != null) { sql.append(",label_ar=:labelAr"); params.addValue("labelAr", cmd.labelAr()); }
        if (cmd.labelEn() != null) { sql.append(",label_en=:labelEn"); params.addValue("labelEn", cmd.labelEn()); }
        if (cmd.sensitive() != null) { sql.append(",sensitive=:sensitive"); params.addValue("sensitive", cmd.sensitive()); }
        if (cmd.searchable() != null) { sql.append(",searchable=:searchable"); params.addValue("searchable", cmd.searchable()); }
        if (cmd.required() != null) { sql.append(",required=:required"); params.addValue("required", cmd.required()); }
        sql.append(" WHERE tenant_id=:t AND id=:id AND version=:expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(t, fieldId);
    }
    public CustomFieldValueSet readValues(UUID t, String entityType, UUID entityId, boolean includeSensitive) {
        List<CustomFieldValue> values = new ArrayList<>();
        return new CustomFieldValueSet(entityType, entityId, values);
    }
    public CustomFieldValueSet upsertValues(UUID t, UUID actorId, String entityType, UUID entityId, CustomFieldValueCommand cmd) {
        return new CustomFieldValueSet(entityType, entityId, List.of());
    }
    public List<CustomFieldSearchResult> searchValues(UUID t, String entityType, String fieldKey, String query, int limit) {
        return List.of();
    }
    private CustomFieldRecord mapRow(Map<String,Object> r) {
        return new CustomFieldRecord((UUID)r.get("id"),asLong(r.get("version")),(String)r.get("entity_type"),(String)r.get("field_key"),(String)r.get("label_ar"),(String)r.get("label_en"),(String)r.get("data_type"),r.get("sensitive")!=null&&((Boolean)r.get("sensitive")),r.get("searchable")!=null&&((Boolean)r.get("searchable")),r.get("required")!=null&&((Boolean)r.get("required")),r.get("active")!=null&&((Boolean)r.get("active")),asInstant(r.get("created_at")),asInstant(r.get("updated_at")));
    }
    private static long asLong(Object v) { if(v==null)return 0L; if(v instanceof Number n)return n.longValue(); try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return 0L;} }
    private static Instant asInstant(Object v) { if(v==null)return null; if(v instanceof Timestamp t)return t.toInstant(); if(v instanceof Instant i)return i; return null; }
}
