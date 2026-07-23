package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcTeamMembershipRepository implements TeamMembershipRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTeamMembershipRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public TeamMembership save(TeamMembership m) {
        UUID id = UUID.randomUUID();
        String sql = """
            INSERT INTO crm_team_memberships
              (id, tenant_id, team_id, user_id, role, is_primary, status,
               joined_at, left_at, left_reason, capacity_max, metadata,
               created_at, updated_at, created_by, updated_by)
            VALUES
              (:id, :tenantId, :teamId, :userId, :role, :isPrimary, :status,
               CURRENT_TIMESTAMP, :leftAt, :leftReason, :capacityMax, :metadata,
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
            """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("tenantId", m.tenantId())
                    .addValue("teamId", m.teamId())
                    .addValue("userId", m.userId())
                    .addValue("role", m.role().name())
                    .addValue("isPrimary", m.isPrimary())
                    .addValue("status", m.status().name())
                    .addValue("leftAt", m.leftAt())
                    .addValue("leftReason", m.leftReason())
                    .addValue("capacityMax", m.capacityMax())
                    .addValue("metadata", m.metadata() != null ? m.metadata() : "{}")
                    .addValue("createdBy", m.createdBy())
                    .addValue("updatedBy", m.updatedBy()));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            if (m.isPrimary()) {
                throw new PrimaryMembershipConflictException(m.tenantId(), m.userId());
            }
            throw new ActiveMembershipExistsException(m.tenantId(), m.teamId(), m.userId());
        }
        return findById(m.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<TeamMembership> findById(UUID tenantId, UUID membershipId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_team_memberships WHERE tenant_id=:tenantId AND id=:id",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", membershipId),
                    teamMembershipMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public Optional<TeamMembership> findActive(UUID tenantId, UUID teamId, UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_team_memberships WHERE tenant_id=:tenantId AND team_id=:teamId AND user_id=:userId AND status='ACTIVE'",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("teamId", teamId).addValue("userId", userId),
                    teamMembershipMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<TeamMembership> findActiveByTeam(UUID tenantId, UUID teamId) {
        return jdbc.query(
                "SELECT * FROM crm_team_memberships WHERE tenant_id=:tenantId AND team_id=:teamId AND status='ACTIVE' ORDER BY joined_at DESC",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("teamId", teamId),
                teamMembershipMapper());
    }

    @Override
    public List<TeamMembership> findActiveByUser(UUID tenantId, UUID userId) {
        return jdbc.query(
                "SELECT * FROM crm_team_memberships WHERE tenant_id=:tenantId AND user_id=:userId AND status='ACTIVE' ORDER BY joined_at DESC",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId),
                teamMembershipMapper());
    }

    @Override
    public Optional<TeamMembership> findPrimaryByUser(UUID tenantId, UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_team_memberships WHERE tenant_id=:tenantId AND user_id=:userId AND status='ACTIVE' AND is_primary=true",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId),
                    teamMembershipMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    @Transactional
    public void endMembership(UUID tenantId, UUID membershipId, String leftReason, UUID updatedBy) {
        int rows = jdbc.update(
                "UPDATE crm_team_memberships SET status='ENDED', left_at=CURRENT_TIMESTAMP, left_reason=:reason, updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy WHERE tenant_id=:tenantId AND id=:id AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", membershipId).addValue("reason", leftReason).addValue("updatedBy", updatedBy));
        if (rows == 0) throw new OwnershipDomainException("Membership not found or already ended: " + membershipId);
    }

    @Override
    public long countActiveByTeam(UUID tenantId, UUID teamId) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM crm_team_memberships WHERE tenant_id=:tenantId AND team_id=:teamId AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("teamId", teamId), Long.class);
        return c != null ? c : 0L;
    }
}
