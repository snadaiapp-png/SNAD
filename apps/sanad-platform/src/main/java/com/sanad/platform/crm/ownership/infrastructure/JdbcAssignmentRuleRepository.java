package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcAssignmentRuleRepository implements AssignmentRuleRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcAssignmentRuleRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public AssignmentRule save(AssignmentRule rule) {
        UUID id = rule.id() != null ? rule.id() : UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_assignment_rules
              (id, tenant_id, code, current_version, status, created_at, updated_at, created_by, updated_by)
            VALUES
              (:id, :tenantId, :code, :currentVersion, :status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
            """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", rule.tenantId()).addValue("code", rule.code())
                .addValue("currentVersion", rule.currentVersion()).addValue("status", rule.status().name())
                .addValue("createdBy", rule.createdBy()).addValue("updatedBy", rule.updatedBy()));
        return findById(rule.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<AssignmentRule> findById(UUID tenantId, UUID ruleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_assignment_rules WHERE tenant_id=:tenantId AND id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", ruleId), assignmentRuleMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public Optional<AssignmentRule> findByCode(UUID tenantId, String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_assignment_rules WHERE tenant_id=:tenantId AND code=:code",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("code", code), assignmentRuleMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<AssignmentRule> findByTenant(UUID tenantId, RuleStatus status) {
        return jdbc.query("SELECT * FROM crm_assignment_rules WHERE tenant_id=:tenantId AND status=:status ORDER BY code",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("status", status.name()), assignmentRuleMapper());
    }

    @Override @Transactional
    public AssignmentRuleVersion saveVersion(AssignmentRuleVersion v) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_assignment_rule_versions
              (id, tenant_id, rule_id, version, display_name, description, record_type, priority,
               match_conditions, distribution_method, target_owner_id, target_team_id, target_queue_id,
               fallback_owner_id, effective_from, effective_to, status, created_by, created_at)
            VALUES
              (:id, :tenantId, :ruleId, :version, :displayName, :description, :recordType, :priority,
               :matchConditions, :distributionMethod, :targetOwnerId, :targetTeamId, :targetQueueId,
               :fallbackOwnerId, CURRENT_TIMESTAMP, :effectiveTo, :status, :createdBy, CURRENT_TIMESTAMP)
            """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", v.tenantId()).addValue("ruleId", v.ruleId())
                .addValue("version", v.version()).addValue("displayName", v.displayName())
                .addValue("description", v.description()).addValue("recordType", v.recordType().name())
                .addValue("priority", v.priority()).addValue("matchConditions", v.matchConditions() != null ? v.matchConditions() : "{}")
                .addValue("distributionMethod", v.distributionMethod().name())
                .addValue("targetOwnerId", v.targetOwnerId()).addValue("targetTeamId", v.targetTeamId())
                .addValue("targetQueueId", v.targetQueueId()).addValue("fallbackOwnerId", v.fallbackOwnerId())
                .addValue("effectiveTo", v.effectiveTo()).addValue("status", v.status().name())
                .addValue("createdBy", v.createdBy()));
        return findVersion(v.tenantId(), v.ruleId(), v.version()).orElseThrow();
    }

    @Override
    public Optional<AssignmentRuleVersion> findVersion(UUID tenantId, UUID ruleId, int version) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM crm_assignment_rule_versions WHERE tenant_id=:tenantId AND rule_id=:ruleId AND version=:version",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ruleId", ruleId).addValue("version", version),
                assignmentRuleVersionMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public Optional<AssignmentRuleVersion> findActiveVersion(UUID tenantId, UUID ruleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM crm_assignment_rule_versions WHERE tenant_id=:tenantId AND rule_id=:ruleId AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ruleId", ruleId),
                assignmentRuleVersionMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<AssignmentRuleVersion> findAllVersions(UUID tenantId, UUID ruleId) {
        return jdbc.query("SELECT * FROM crm_assignment_rule_versions WHERE tenant_id=:tenantId AND rule_id=:ruleId ORDER BY version DESC",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ruleId", ruleId), assignmentRuleVersionMapper());
    }

    @Override @Transactional
    public void activateVersion(UUID tenantId, UUID ruleId, int version, UUID updatedBy) {
        // Deactivate all ACTIVE versions for this rule (partial unique index enforces single-active)
        jdbc.update("UPDATE crm_assignment_rule_versions SET status='INACTIVE' WHERE tenant_id=:tenantId AND rule_id=:ruleId AND status='ACTIVE'",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ruleId", ruleId));
        // Activate the requested version
        int rows = jdbc.update("UPDATE crm_assignment_rule_versions SET status='ACTIVE' WHERE tenant_id=:tenantId AND rule_id=:ruleId AND version=:version",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ruleId", ruleId).addValue("version", version));
        if (rows == 0) throw new AssignmentRuleNotFoundException(tenantId, ruleId);
        // Update rule's current_version
        jdbc.update("UPDATE crm_assignment_rules SET current_version=:version, updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy WHERE tenant_id=:tenantId AND id=:ruleId",
            new MapSqlParameterSource().addValue("version", version).addValue("updatedBy", updatedBy).addValue("tenantId", tenantId).addValue("ruleId", ruleId));
    }

    @Override
    public AssignmentRuleCounter getOrCreateCounter(UUID tenantId, UUID ruleId) {
        try {
            return jdbc.queryForObject("SELECT * FROM crm_assignment_rule_counters WHERE tenant_id=:tenantId AND rule_id=:ruleId",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ruleId", ruleId), assignmentRuleCounterMapper());
        } catch (EmptyResultDataAccessException e) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO crm_assignment_rule_counters (id, tenant_id, rule_id, counter, updated_at)
                VALUES (:id, :tenantId, :ruleId, 0, CURRENT_TIMESTAMP)
                ON CONFLICT (tenant_id, rule_id) DO NOTHING
                """, new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId).addValue("ruleId", ruleId));
            return new AssignmentRuleCounter(id, tenantId, ruleId, 0, null);
        }
    }

    @Override @Transactional
    public AssignmentRuleCounter incrementCounter(UUID tenantId, UUID ruleId) {
        // Atomic increment via UPDATE ... RETURNING
        AssignmentRuleCounter counter = getOrCreateCounter(tenantId, ruleId);
        jdbc.update("UPDATE crm_assignment_rule_counters SET counter = counter + 1, updated_at = CURRENT_TIMESTAMP WHERE tenant_id=:tenantId AND rule_id=:ruleId",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ruleId", ruleId));
        return getOrCreateCounter(tenantId, ruleId);
    }
}
