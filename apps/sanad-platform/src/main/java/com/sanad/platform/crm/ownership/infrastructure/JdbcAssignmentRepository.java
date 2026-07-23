package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcAssignmentRepository implements AssignmentRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final OwnershipHistoryRepository historyRepo;
    public JdbcAssignmentRepository(NamedParameterJdbcTemplate jdbc, OwnershipHistoryRepository historyRepo) {
        this.jdbc = jdbc; this.historyRepo = historyRepo;
    }

    @Override @Transactional
    public Assignment save(Assignment a) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_assignments
              (id, tenant_id, version, subject_type, subject_id, assigned_user_id, assignment_role,
               status, starts_at, ends_at, reason,
               owner_type, owner_user_id, owner_team_id, owner_queue_id,
               record_type, record_id, assigned_by_rule_id, assigned_by_user_id,
               correlation_id, workflow_result, effective_from, effective_to,
               created_by, updated_by, created_at, updated_at)
            VALUES
              (:id, :tenantId, 0, :subjectType, :subjectId, :assignedUserId, :assignmentRole,
               :status, :startsAt, :endsAt, :reason,
               :ownerType, :ownerUserId, :ownerTeamId, :ownerQueueId,
               :recordType, :recordId, :assignedByRuleId, :assignedByUserId,
               :correlationId, :workflowResult, :effectiveFrom, :effectiveTo,
               :createdBy, :updatedBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", a.tenantId())
                .addValue("subjectType", a.subjectType()).addValue("subjectId", a.subjectId())
                .addValue("assignedUserId", a.assignedUserId()).addValue("assignmentRole", a.assignmentRole())
                .addValue("status", a.status().name()).addValue("startsAt", a.startsAt()).addValue("endsAt", a.endsAt())
                .addValue("reason", a.reason())
                .addValue("ownerType", a.ownerType() != null ? a.ownerType().name() : null)
                .addValue("ownerUserId", a.ownerUserId()).addValue("ownerTeamId", a.ownerTeamId())
                .addValue("ownerQueueId", a.ownerQueueId())
                .addValue("recordType", a.recordType() != null ? a.recordType().name() : null)
                .addValue("recordId", a.recordId()).addValue("assignedByRuleId", a.assignedByRuleId())
                .addValue("assignedByUserId", a.assignedByUserId()).addValue("correlationId", a.correlationId())
                .addValue("workflowResult", a.workflowResult()).addValue("effectiveFrom", a.effectiveFrom())
                .addValue("effectiveTo", a.effectiveTo()).addValue("createdBy", a.createdBy()).addValue("updatedBy", a.updatedBy()));
        return findById(a.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<Assignment> findById(UUID tenantId, UUID assignmentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_assignments WHERE tenant_id=:tenantId AND id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", assignmentId), assignmentMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public Optional<Assignment> findActive(UUID tenantId, AssignmentRecordType recordType, UUID recordId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM crm_assignments WHERE tenant_id=:tenantId AND record_type=:recordType AND record_id=:recordId AND status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("recordType", recordType.name()).addValue("recordId", recordId),
                assignmentMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override @Transactional
    public Assignment supersedeAndInsert(UUID tenantId, AssignmentRecordType recordType, UUID recordId,
                                          Assignment newAssignment, UUID actorUserId, String reason) {
        // 1. Find current active assignment
        Optional<Assignment> current = findActive(tenantId, recordType, recordId);

        // 2. End current active assignment (if exists) — partial unique index enforces single-active
        if (current.isPresent()) {
            Assignment prev = current.get();
            jdbc.update("""
                UPDATE crm_assignments SET status='ENDED', ends_at=CURRENT_TIMESTAMP, effective_to=CURRENT_TIMESTAMP,
                  updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy
                WHERE tenant_id=:tenantId AND id=:id AND status='ACTIVE'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", prev.id()).addValue("updatedBy", actorUserId));

            // 3. Append ownership history for the supersede
            historyRepo.append(new OwnershipHistory(
                UUID.randomUUID(), tenantId, recordType, recordId,
                prev.ownerType(), prev.ownerUserId(), prev.ownerTeamId(), prev.ownerQueueId(),
                newAssignment.ownerType(), newAssignment.ownerUserId(), newAssignment.ownerTeamId(), newAssignment.ownerQueueId(),
                ChangeType.REASSIGN, TriggerSource.MANUAL, null, actorUserId, reason,
                newAssignment.correlationId(), Instant.now(), Instant.now()
            ));
        } else {
            // No prior assignment — record INITIAL
            historyRepo.append(new OwnershipHistory(
                UUID.randomUUID(), tenantId, recordType, recordId,
                null, null, null, null,
                newAssignment.ownerType(), newAssignment.ownerUserId(), newAssignment.ownerTeamId(), newAssignment.ownerQueueId(),
                ChangeType.INITIAL, TriggerSource.MANUAL, null, actorUserId, reason,
                newAssignment.correlationId(), Instant.now(), Instant.now()
            ));
        }

        // 4. Insert new active assignment
        return save(newAssignment);
    }

    @Override @Transactional
    public void endAssignment(UUID tenantId, UUID assignmentId, UUID updatedBy, String reason) {
        int rows = jdbc.update("""
            UPDATE crm_assignments SET status='ENDED', ends_at=CURRENT_TIMESTAMP, effective_to=CURRENT_TIMESTAMP,
              updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy
            WHERE tenant_id=:tenantId AND id=:id AND status='ACTIVE'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", assignmentId).addValue("updatedBy", updatedBy));
        if (rows == 0) throw new AssignmentNotFoundException(tenantId, null, null);
    }

    @Override
    public long countActiveByOwner(UUID tenantId, OwnerType ownerType, UUID ownerId) {
        String column = switch (ownerType) {
            case USER -> "owner_user_id"; case TEAM -> "owner_team_id"; case QUEUE -> "owner_queue_id";
        };
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM crm_assignments WHERE tenant_id=:tenantId AND " + column + "=:ownerId AND status='ACTIVE'",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ownerId", ownerId), Long.class);
        return c != null ? c : 0L;
    }

    @Override
    public long countActiveByUser(UUID tenantId, UUID userId) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM crm_assignments WHERE tenant_id=:tenantId AND owner_user_id=:userId AND status='ACTIVE'",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("userId", userId), Long.class);
        return c != null ? c : 0L;
    }
}
