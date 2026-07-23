package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.ArchiveBlockedWithActiveMembershipsException;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.TeamCodeConflictException;
import com.sanad.platform.crm.ownership.domain.TeamNotFoundException;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.salesTeamMapper;

/** JDBC implementation of SalesTeamRepository (tenant-scoped). */
@Repository
public class JdbcSalesTeamRepository implements SalesTeamRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSalesTeamRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public SalesTeam save(SalesTeam team) {
        if (team.id() == null || findById(team.tenantId(), team.id()).isEmpty()) {
            return insert(team);
        }
        return update(team);
    }

    private SalesTeam insert(SalesTeam team) {
        UUID id = team.id() != null ? team.id() : UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO crm_sales_teams
                      (id, tenant_id, code, display_name, description, status,
                       manager_user_id, default_queue_id, default_territory_id,
                       created_at, updated_at, created_by, updated_by)
                    VALUES
                      (:id, :tenantId, :code, :displayName, :description, :status,
                       :managerUserId, :defaultQueueId, :defaultTerritoryId,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("tenantId", team.tenantId())
                    .addValue("code", team.code())
                    .addValue("displayName", team.displayName())
                    .addValue("description", team.description())
                    .addValue("status", team.status().name())
                    .addValue("managerUserId", team.managerUserId())
                    .addValue("defaultQueueId", team.defaultQueueId())
                    .addValue("defaultTerritoryId", team.defaultTerritoryId())
                    .addValue("createdBy", team.createdBy())
                    .addValue("updatedBy", team.updatedBy()));
        } catch (DuplicateKeyException conflict) {
            throw new TeamCodeConflictException(team.tenantId(), team.code());
        }
        return findById(team.tenantId(), id).orElseThrow();
    }

    private SalesTeam update(SalesTeam team) {
        int rows = jdbc.update("""
                UPDATE crm_sales_teams
                   SET display_name=:displayName,
                       description=:description,
                       status=:status,
                       manager_user_id=:managerUserId,
                       default_queue_id=:defaultQueueId,
                       default_territory_id=:defaultTerritoryId,
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE id=:id
                   AND tenant_id=:tenantId
                """, new MapSqlParameterSource()
                .addValue("id", team.id())
                .addValue("tenantId", team.tenantId())
                .addValue("displayName", team.displayName())
                .addValue("description", team.description())
                .addValue("status", team.status().name())
                .addValue("managerUserId", team.managerUserId())
                .addValue("defaultQueueId", team.defaultQueueId())
                .addValue("defaultTerritoryId", team.defaultTerritoryId())
                .addValue("updatedBy", team.updatedBy()));
        if (rows != 1) {
            throw new TeamNotFoundException(team.tenantId(), team.id());
        }
        return findById(team.tenantId(), team.id()).orElseThrow();
    }

    @Override
    public Optional<SalesTeam> findById(UUID tenantId, UUID teamId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_sales_teams
                     WHERE tenant_id=:tenantId
                       AND id=:teamId
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("teamId", teamId), salesTeamMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<SalesTeam> findByCode(UUID tenantId, String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_sales_teams
                     WHERE tenant_id=:tenantId
                       AND code=:code
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("code", code), salesTeamMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<SalesTeam> findByTenant(UUID tenantId, TeamStatus status) {
        return jdbc.query("""
                SELECT *
                  FROM crm_sales_teams
                 WHERE tenant_id=:tenantId
                   AND status=:status
                 ORDER BY display_name, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("status", status.name()), salesTeamMapper());
    }

    @Override
    public List<SalesTeam> findByManager(UUID tenantId, UUID managerUserId) {
        return jdbc.query("""
                SELECT *
                  FROM crm_sales_teams
                 WHERE tenant_id=:tenantId
                   AND manager_user_id=:managerUserId
                   AND status='ACTIVE'
                 ORDER BY display_name, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("managerUserId", managerUserId), salesTeamMapper());
    }

    @Override
    @Transactional
    public void archive(UUID tenantId, UUID teamId, UUID updatedBy) {
        long activeCount = countActiveMemberships(tenantId, teamId);
        if (activeCount > 0) {
            throw new ArchiveBlockedWithActiveMembershipsException(
                    tenantId, teamId, activeCount);
        }
        int rows = jdbc.update("""
                UPDATE crm_sales_teams
                   SET status='ARCHIVED',
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE id=:teamId
                   AND tenant_id=:tenantId
                   AND status<>'ARCHIVED'
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("tenantId", tenantId)
                .addValue("updatedBy", updatedBy));
        if (rows != 1) {
            throw new TeamNotFoundException(tenantId, teamId);
        }
    }

    @Override
    public long countActiveMemberships(UUID tenantId, UUID teamId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_team_memberships
                 WHERE tenant_id=:tenantId
                   AND team_id=:teamId
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("teamId", teamId), Long.class);
        return count != null ? count : 0L;
    }
}
