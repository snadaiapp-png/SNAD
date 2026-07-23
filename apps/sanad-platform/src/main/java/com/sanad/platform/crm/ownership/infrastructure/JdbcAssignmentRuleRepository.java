package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.ActiveRuleVersionExistsException;
import com.sanad.platform.crm.ownership.domain.AssignmentRule;
import com.sanad.platform.crm.ownership.domain.AssignmentRuleCounter;
import com.sanad.platform.crm.ownership.domain.AssignmentRuleNotFoundException;
import com.sanad.platform.crm.ownership.domain.AssignmentRuleRepository;
import com.sanad.platform.crm.ownership.domain.AssignmentRuleVersion;
import com.sanad.platform.crm.ownership.domain.ConcurrentRuleActivationConflictException;
import com.sanad.platform.crm.ownership.domain.RuleStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.assignmentRuleCounterMapper;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.assignmentRuleMapper;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.assignmentRuleVersionMapper;

@Repository
public class JdbcAssignmentRuleRepository implements AssignmentRuleRepository {
    private static final String POSTGRES_LOCK_NOT_AVAILABLE = "55P03";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAssignmentRuleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public AssignmentRule save(AssignmentRule rule) {
        UUID id = rule.id() != null ? rule.id() : UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_assignment_rules
                  (id, tenant_id, code, current_version, status,
                   created_at, updated_at, created_by, updated_by)
                VALUES
                  (:id, :tenantId, :code, :currentVersion, :status,
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", rule.tenantId())
                .addValue("code", rule.code())
                .addValue("currentVersion", rule.currentVersion())
                .addValue("status", rule.status().name())
                .addValue("createdBy", rule.createdBy())
                .addValue("updatedBy", rule.updatedBy()));
        return findById(rule.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<AssignmentRule> findById(UUID tenantId, UUID ruleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_assignment_rules WHERE tenant_id=:tenantId AND id=:id",
                    new MapSqlParameterSource()
                            .addValue("tenantId", tenantId)
                            .addValue("id", ruleId), assignmentRuleMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<AssignmentRule> findByCode(UUID tenantId, String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_assignment_rules WHERE tenant_id=:tenantId AND code=:code",
                    new MapSqlParameterSource()
                            .addValue("tenantId", tenantId)
                            .addValue("code", code), assignmentRuleMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<AssignmentRule> findByTenant(UUID tenantId, RuleStatus status) {
        return jdbc.query(
                "SELECT * FROM crm_assignment_rules WHERE tenant_id=:tenantId AND status=:status ORDER BY code",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("status", status.name()), assignmentRuleMapper());
    }

    @Override
    @Transactional
    public AssignmentRuleVersion saveVersion(AssignmentRuleVersion version) {
        UUID id = version.id() != null ? version.id() : UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO crm_assignment_rule_versions
                      (id, tenant_id, rule_id, version, display_name, description, record_type, priority,
                       match_conditions, distribution_method, target_owner_id, target_team_id,
                       target_queue_id, fallback_owner_id, effective_from, effective_to,
                       status, created_by, created_at)
                    VALUES
                      (:id, :tenantId, :ruleId, :version, :displayName, :description, :recordType, :priority,
                       CAST(:matchConditions AS jsonb), :distributionMethod, :targetOwnerId, :targetTeamId,
                       :targetQueueId, :fallbackOwnerId, :effectiveFrom, :effectiveTo,
                       :status, :createdBy, CURRENT_TIMESTAMP)
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("tenantId", version.tenantId())
                    .addValue("ruleId", version.ruleId())
                    .addValue("version", version.version())
                    .addValue("displayName", version.displayName())
                    .addValue("description", version.description())
                    .addValue("recordType", version.recordType().name())
                    .addValue("priority", version.priority())
                    .addValue("matchConditions", version.matchConditions() != null
                            ? version.matchConditions() : "{}")
                    .addValue("distributionMethod", version.distributionMethod().name())
                    .addValue("targetOwnerId", version.targetOwnerId())
                    .addValue("targetTeamId", version.targetTeamId())
                    .addValue("targetQueueId", version.targetQueueId())
                    .addValue("fallbackOwnerId", version.fallbackOwnerId())
                    .addValue("effectiveFrom", version.effectiveFrom() != null
                            ? Timestamp.from(version.effectiveFrom()) : null)
                    .addValue("effectiveTo", version.effectiveTo() != null
                            ? Timestamp.from(version.effectiveTo()) : null)
                    .addValue("status", version.status().name())
                    .addValue("createdBy", version.createdBy()));
        } catch (DataIntegrityViolationException conflict) {
            throw new ActiveRuleVersionExistsException(version.tenantId(), version.ruleId());
        }
        return findVersion(version.tenantId(), version.ruleId(), version.version()).orElseThrow();
    }

    @Override
    public Optional<AssignmentRuleVersion> findVersion(UUID tenantId, UUID ruleId, int version) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_assignment_rule_versions
                     WHERE tenant_id=:tenantId
                       AND rule_id=:ruleId
                       AND version=:version
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("ruleId", ruleId)
                    .addValue("version", version), assignmentRuleVersionMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<AssignmentRuleVersion> findActiveVersion(UUID tenantId, UUID ruleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_assignment_rule_versions
                     WHERE tenant_id=:tenantId
                       AND rule_id=:ruleId
                       AND status='ACTIVE'
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("ruleId", ruleId), assignmentRuleVersionMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<AssignmentRuleVersion> findAllVersions(UUID tenantId, UUID ruleId) {
        return jdbc.query("""
                SELECT *
                  FROM crm_assignment_rule_versions
                 WHERE tenant_id=:tenantId
                   AND rule_id=:ruleId
                 ORDER BY version DESC
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("ruleId", ruleId), assignmentRuleVersionMapper());
    }

    @Override
    @Transactional
    public void activateVersion(UUID tenantId, UUID ruleId, int version, UUID updatedBy) {
        Integer lockedRuleVersion;
        try {
            lockedRuleVersion = jdbc.queryForObject("""
                    SELECT current_version
                      FROM crm_assignment_rules
                     WHERE tenant_id=:tenantId
                       AND id=:ruleId
                     FOR UPDATE NOWAIT
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("ruleId", ruleId), Integer.class);
        } catch (EmptyResultDataAccessException missing) {
            throw new AssignmentRuleNotFoundException(tenantId, ruleId);
        } catch (DataAccessException databaseFailure) {
            if (hasSqlState(databaseFailure, POSTGRES_LOCK_NOT_AVAILABLE)) {
                throw new ConcurrentRuleActivationConflictException(tenantId, ruleId);
            }
            throw databaseFailure;
        }

        Integer targetExists = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_assignment_rule_versions
                 WHERE tenant_id=:tenantId
                   AND rule_id=:ruleId
                   AND version=:version
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("ruleId", ruleId)
                .addValue("version", version), Integer.class);
        if (targetExists == null || targetExists != 1) {
            throw new AssignmentRuleNotFoundException(tenantId, ruleId);
        }

        if (lockedRuleVersion != null && lockedRuleVersion == version
                && findActiveVersion(tenantId, ruleId)
                .map(active -> active.version() == version)
                .orElse(false)) {
            return;
        }

        jdbc.update("""
                UPDATE crm_assignment_rule_versions
                   SET status='INACTIVE'
                 WHERE tenant_id=:tenantId
                   AND rule_id=:ruleId
                   AND status='ACTIVE'
                   AND version<>:version
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("ruleId", ruleId)
                .addValue("version", version));

        int activated;
        try {
            activated = jdbc.update("""
                    UPDATE crm_assignment_rule_versions
                       SET status='ACTIVE'
                     WHERE tenant_id=:tenantId
                       AND rule_id=:ruleId
                       AND version=:version
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("ruleId", ruleId)
                    .addValue("version", version));
        } catch (DataIntegrityViolationException conflict) {
            throw new ActiveRuleVersionExistsException(tenantId, ruleId);
        }
        if (activated != 1) {
            throw new AssignmentRuleNotFoundException(tenantId, ruleId);
        }

        int ruleUpdated = jdbc.update("""
                UPDATE crm_assignment_rules
                   SET current_version=:version,
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:ruleId
                """, new MapSqlParameterSource()
                .addValue("version", version)
                .addValue("updatedBy", updatedBy)
                .addValue("tenantId", tenantId)
                .addValue("ruleId", ruleId));
        if (ruleUpdated != 1) {
            throw new AssignmentRuleNotFoundException(tenantId, ruleId);
        }
    }

    @Override
    @Transactional
    public AssignmentRuleCounter getOrCreateCounter(UUID tenantId, UUID ruleId) {
        UUID candidateId = UUID.randomUUID();
        return jdbc.queryForObject("""
                INSERT INTO crm_assignment_rule_counters
                    (id, tenant_id, rule_id, counter, updated_at)
                VALUES
                    (:id, :tenantId, :ruleId, 0, CURRENT_TIMESTAMP)
                ON CONFLICT (tenant_id, rule_id)
                DO UPDATE SET updated_at = crm_assignment_rule_counters.updated_at
                RETURNING id, tenant_id, rule_id, counter, updated_at
                """, new MapSqlParameterSource()
                .addValue("id", candidateId)
                .addValue("tenantId", tenantId)
                .addValue("ruleId", ruleId), assignmentRuleCounterMapper());
    }

    @Override
    @Transactional
    public AssignmentRuleCounter incrementCounter(UUID tenantId, UUID ruleId) {
        UUID candidateId = UUID.randomUUID();
        return jdbc.queryForObject("""
                INSERT INTO crm_assignment_rule_counters
                    (id, tenant_id, rule_id, counter, updated_at)
                VALUES
                    (:id, :tenantId, :ruleId, 1, CURRENT_TIMESTAMP)
                ON CONFLICT (tenant_id, rule_id)
                DO UPDATE SET
                    counter = crm_assignment_rule_counters.counter + 1,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id, tenant_id, rule_id, counter, updated_at
                """, new MapSqlParameterSource()
                .addValue("id", candidateId)
                .addValue("tenantId", tenantId)
                .addValue("ruleId", ruleId), assignmentRuleCounterMapper());
    }

    private boolean hasSqlState(Throwable error, String expectedSqlState) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && expectedSqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
