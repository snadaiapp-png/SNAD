package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.ActiveMembershipExistsException;
import com.sanad.platform.crm.ownership.domain.MembershipRole;
import com.sanad.platform.crm.ownership.domain.PrimaryMembershipConflictException;
import com.sanad.platform.crm.ownership.domain.TeamMembership;
import com.sanad.platform.crm.ownership.domain.TeamMembershipNotFoundException;
import com.sanad.platform.crm.ownership.domain.TeamMembershipRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.teamMembershipMapper;

@Repository
public class JdbcTeamMembershipRepository implements TeamMembershipRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTeamMembershipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public TeamMembership save(TeamMembership membership) {
        UUID id = membership.id() != null ? membership.id() : UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO crm_team_memberships
                      (id, tenant_id, team_id, user_id, role, is_primary, status,
                       joined_at, left_at, left_reason, capacity_max, metadata,
                       created_at, updated_at, created_by, updated_by)
                    VALUES
                      (:id, :tenantId, :teamId, :userId, :role, :isPrimary, :status,
                       COALESCE(:joinedAt, CURRENT_TIMESTAMP), :leftAt, :leftReason,
                       :capacityMax, CAST(:metadata AS jsonb),
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("tenantId", membership.tenantId())
                    .addValue("teamId", membership.teamId())
                    .addValue("userId", membership.userId())
                    .addValue("role", membership.role().name())
                    .addValue("isPrimary", membership.isPrimary())
                    .addValue("status", membership.status().name())
                    .addValue("joinedAt", membership.joinedAt() != null
                            ? Timestamp.from(membership.joinedAt()) : null)
                    .addValue("leftAt", membership.leftAt() != null
                            ? Timestamp.from(membership.leftAt()) : null)
                    .addValue("leftReason", membership.leftReason())
                    .addValue("capacityMax", membership.capacityMax())
                    .addValue("metadata", membership.metadata() != null ? membership.metadata() : "{}")
                    .addValue("createdBy", membership.createdBy())
                    .addValue("updatedBy", membership.updatedBy()));
        } catch (DuplicateKeyException conflict) {
            throwMembershipConflict(membership, conflict);
        }
        return findById(membership.tenantId(), id).orElseThrow();
    }

    @Override
    @Transactional
    public TeamMembership updateActive(UUID tenantId,
                                       UUID membershipId,
                                       MembershipRole role,
                                       boolean primary,
                                       int capacityMax,
                                       String metadata,
                                       UUID updatedBy) {
        try {
            int rows = jdbc.update("""
                    UPDATE crm_team_memberships
                       SET role=:role,
                           is_primary=:isPrimary,
                           capacity_max=:capacityMax,
                           metadata=CAST(:metadata AS jsonb),
                           updated_at=CURRENT_TIMESTAMP,
                           updated_by=:updatedBy
                     WHERE tenant_id=:tenantId
                       AND id=:membershipId
                       AND status='ACTIVE'
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("membershipId", membershipId)
                    .addValue("role", role.name())
                    .addValue("isPrimary", primary)
                    .addValue("capacityMax", capacityMax)
                    .addValue("metadata", metadata != null ? metadata : "{}")
                    .addValue("updatedBy", updatedBy));
            if (rows != 1) {
                throw new TeamMembershipNotFoundException(tenantId, membershipId);
            }
        } catch (DuplicateKeyException conflict) {
            if (primary) {
                TeamMembership membership = findById(tenantId, membershipId)
                        .orElseThrow(() -> new TeamMembershipNotFoundException(tenantId, membershipId));
                throw new PrimaryMembershipConflictException(tenantId, membership.userId());
            }
            throw conflict;
        }
        return findById(tenantId, membershipId)
                .orElseThrow(() -> new TeamMembershipNotFoundException(tenantId, membershipId));
    }

    @Override
    public Optional<TeamMembership> findById(UUID tenantId, UUID membershipId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_team_memberships
                     WHERE tenant_id=:tenantId
                       AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", membershipId), teamMembershipMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TeamMembership> findActive(UUID tenantId, UUID teamId, UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_team_memberships
                     WHERE tenant_id=:tenantId
                       AND team_id=:teamId
                       AND user_id=:userId
                       AND status='ACTIVE'
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("teamId", teamId)
                    .addValue("userId", userId), teamMembershipMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public List<TeamMembership> findActiveByTeam(UUID tenantId, UUID teamId) {
        return jdbc.query("""
                SELECT *
                  FROM crm_team_memberships
                 WHERE tenant_id=:tenantId
                   AND team_id=:teamId
                   AND status='ACTIVE'
                 ORDER BY joined_at DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("teamId", teamId), teamMembershipMapper());
    }

    @Override
    public List<TeamMembership> findActiveByUser(UUID tenantId, UUID userId) {
        return jdbc.query("""
                SELECT *
                  FROM crm_team_memberships
                 WHERE tenant_id=:tenantId
                   AND user_id=:userId
                   AND status='ACTIVE'
                 ORDER BY joined_at DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId), teamMembershipMapper());
    }

    @Override
    public Optional<TeamMembership> findPrimaryByUser(UUID tenantId, UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_team_memberships
                     WHERE tenant_id=:tenantId
                       AND user_id=:userId
                       AND status='ACTIVE'
                       AND is_primary=true
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("userId", userId), teamMembershipMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void endMembership(UUID tenantId,
                              UUID membershipId,
                              String leftReason,
                              UUID updatedBy) {
        int rows = jdbc.update("""
                UPDATE crm_team_memberships
                   SET status='ENDED',
                       left_at=CURRENT_TIMESTAMP,
                       left_reason=:reason,
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:id
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", membershipId)
                .addValue("reason", leftReason)
                .addValue("updatedBy", updatedBy));
        if (rows != 1) {
            throw new TeamMembershipNotFoundException(tenantId, membershipId);
        }
    }

    @Override
    public long countActiveByTeam(UUID tenantId, UUID teamId) {
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

    private static void throwMembershipConflict(TeamMembership membership,
                                                DuplicateKeyException conflict) {
        if (membership.isPrimary()) {
            throw new PrimaryMembershipConflictException(
                    membership.tenantId(), membership.userId());
        }
        throw new ActiveMembershipExistsException(
                membership.tenantId(), membership.teamId(), membership.userId());
    }
}
