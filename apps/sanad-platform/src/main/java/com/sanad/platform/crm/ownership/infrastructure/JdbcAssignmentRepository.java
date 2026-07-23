package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentNotFoundException;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.AssignmentRepository;
import com.sanad.platform.crm.ownership.domain.ChangeType;
import com.sanad.platform.crm.ownership.domain.ConcurrentClaimConflictException;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipHistory;
import com.sanad.platform.crm.ownership.domain.OwnershipHistoryRepository;
import com.sanad.platform.crm.ownership.domain.TriggerSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.assignmentMapper;

@Repository
public class JdbcAssignmentRepository implements AssignmentRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final OwnershipHistoryRepository historyRepo;

    public JdbcAssignmentRepository(NamedParameterJdbcTemplate jdbc,
                                    OwnershipHistoryRepository historyRepo) {
        this.jdbc = jdbc;
        this.historyRepo = historyRepo;
    }

    @Override
    @Transactional
    public Assignment save(Assignment assignment) {
        UUID id = assignment.id() != null ? assignment.id() : UUID.randomUUID();
        try {
            insertAssignment(id, assignment);
        } catch (DataIntegrityViolationException conflict) {
            throw new ConcurrentClaimConflictException(
                    assignment.tenantId(), assignment.recordType(), assignment.recordId());
        }
        return findById(assignment.tenantId(), id).orElseThrow();
    }

    private void insertAssignment(UUID id, Assignment assignment) {
        jdbc.update("""
                INSERT INTO crm_assignments
                  (id, tenant_id, version, subject_type, subject_id, assigned_user_id, assignment_role,
                   status, starts_at, ends_at, reason,
                   owner_type, owner_user_id, owner_team_id, owner_queue_id,
                   record_type, record_id, assigned_by_rule_id, assigned_by_user_id,
                   correlation_id, workflow_result, effective_from, effective_to,
                   created_by, updated_by, created_at, updated_at)
                VALUES
                  (:id, :tenantId, :version, :subjectType, :subjectId, :assignedUserId, :assignmentRole,
                   :status, :startsAt, :endsAt, :reason,
                   :ownerType, :ownerUserId, :ownerTeamId, :ownerQueueId,
                   :recordType, :recordId, :assignedByRuleId, :assignedByUserId,
                   :correlationId, CAST(:workflowResult AS jsonb), :effectiveFrom, :effectiveTo,
                   :createdBy, :updatedBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", assignment.tenantId())
                .addValue("version", assignment.version())
                .addValue("subjectType", assignment.subjectType())
                .addValue("subjectId", assignment.subjectId())
                .addValue("assignedUserId", assignment.assignedUserId())
                .addValue("assignmentRole", assignment.assignmentRole())
                .addValue("status", assignment.status().name())
                .addValue("startsAt", timestamp(assignment.startsAt()))
                .addValue("endsAt", timestamp(assignment.endsAt()))
                .addValue("reason", assignment.reason())
                .addValue("ownerType", assignment.ownerType() != null ? assignment.ownerType().name() : null)
                .addValue("ownerUserId", assignment.ownerUserId())
                .addValue("ownerTeamId", assignment.ownerTeamId())
                .addValue("ownerQueueId", assignment.ownerQueueId())
                .addValue("recordType", assignment.recordType() != null ? assignment.recordType().name() : null)
                .addValue("recordId", assignment.recordId())
                .addValue("assignedByRuleId", assignment.assignedByRuleId())
                .addValue("assignedByUserId", assignment.assignedByUserId())
                .addValue("correlationId", assignment.correlationId())
                .addValue("workflowResult", assignment.workflowResult())
                .addValue("effectiveFrom", timestamp(assignment.effectiveFrom()))
                .addValue("effectiveTo", timestamp(assignment.effectiveTo()))
                .addValue("createdBy", assignment.createdBy())
                .addValue("updatedBy", assignment.updatedBy()));
    }

    private Timestamp timestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    @Override
    public Optional<Assignment> findById(UUID tenantId, UUID assignmentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crm_assignments WHERE tenant_id=:tenantId AND id=:id",
                    new MapSqlParameterSource()
                            .addValue("tenantId", tenantId)
                            .addValue("id", assignmentId),
                    assignmentMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Assignment> findActive(UUID tenantId,
                                           AssignmentRecordType recordType,
                                           UUID recordId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_assignments
                     WHERE tenant_id=:tenantId
                       AND record_type=:recordType
                       AND record_id=:recordId
                       AND status='ACTIVE'
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("recordType", recordType.name())
                    .addValue("recordId", recordId), assignmentMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    private Optional<Assignment> findActiveForUpdate(UUID tenantId,
                                                      AssignmentRecordType recordType,
                                                      UUID recordId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM crm_assignments
                     WHERE tenant_id=:tenantId
                       AND record_type=:recordType
                       AND record_id=:recordId
                       AND status='ACTIVE'
                     FOR UPDATE
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("recordType", recordType.name())
                    .addValue("recordId", recordId), assignmentMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Assignment supersedeAndInsert(UUID tenantId,
                                         AssignmentRecordType recordType,
                                         UUID recordId,
                                         Assignment newAssignment,
                                         UUID actorUserId,
                                         String reason,
                                         ChangeType changeType,
                                         TriggerSource triggerSource,
                                         UUID triggerReferenceId,
                                         OwnerType expectedOwnerType,
                                         UUID expectedOwnerId) {
        Optional<Assignment> current = findActiveForUpdate(tenantId, recordType, recordId);
        validateExpectedOwner(tenantId, recordType, recordId, current, expectedOwnerType, expectedOwnerId);

        ChangeType requestedChange = changeType != null ? changeType : ChangeType.REASSIGN;
        TriggerSource requestedSource = triggerSource != null ? triggerSource : TriggerSource.MANUAL;
        ChangeType effectiveChangeType = current.isPresent()
                ? requestedChange
                : (requestedChange == ChangeType.REASSIGN ? ChangeType.INITIAL : requestedChange);

        if (current.isPresent()) {
            Assignment previous = current.get();
            int ended = jdbc.update("""
                    UPDATE crm_assignments
                       SET status='ENDED',
                           ends_at=CURRENT_TIMESTAMP,
                           effective_to=CURRENT_TIMESTAMP,
                           version=version + 1,
                           updated_at=CURRENT_TIMESTAMP,
                           updated_by=:updatedBy
                     WHERE tenant_id=:tenantId
                       AND id=:id
                       AND status='ACTIVE'
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", previous.id())
                    .addValue("updatedBy", actorUserId));
            if (ended != 1) {
                throw new ConcurrentClaimConflictException(tenantId, recordType, recordId);
            }

            historyRepo.append(new OwnershipHistory(
                    UUID.randomUUID(), tenantId, recordType, recordId,
                    previous.ownerType(), previous.ownerUserId(), previous.ownerTeamId(), previous.ownerQueueId(),
                    newAssignment.ownerType(), newAssignment.ownerUserId(),
                    newAssignment.ownerTeamId(), newAssignment.ownerQueueId(),
                    effectiveChangeType, requestedSource, triggerReferenceId,
                    actorUserId, normalizeReason(reason), newAssignment.correlationId(),
                    Instant.now(), Instant.now()));
        } else {
            historyRepo.append(new OwnershipHistory(
                    UUID.randomUUID(), tenantId, recordType, recordId,
                    null, null, null, null,
                    newAssignment.ownerType(), newAssignment.ownerUserId(),
                    newAssignment.ownerTeamId(), newAssignment.ownerQueueId(),
                    effectiveChangeType, requestedSource, triggerReferenceId,
                    actorUserId, normalizeReason(reason), newAssignment.correlationId(),
                    Instant.now(), Instant.now()));
        }

        return save(newAssignment);
    }

    private void validateExpectedOwner(UUID tenantId,
                                       AssignmentRecordType recordType,
                                       UUID recordId,
                                       Optional<Assignment> current,
                                       OwnerType expectedOwnerType,
                                       UUID expectedOwnerId) {
        if (expectedOwnerType == null) {
            return;
        }
        if (current.isEmpty() || current.get().ownerType() != expectedOwnerType) {
            throw new ConcurrentClaimConflictException(tenantId, recordType, recordId);
        }
        UUID actualOwnerId = switch (expectedOwnerType) {
            case USER -> current.get().ownerUserId();
            case TEAM -> current.get().ownerTeamId();
            case QUEUE -> current.get().ownerQueueId();
        };
        if (!Objects.equals(actualOwnerId, expectedOwnerId)) {
            throw new ConcurrentClaimConflictException(tenantId, recordType, recordId);
        }
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "Ownership assignment changed" : reason;
    }

    @Override
    @Transactional
    public void endAssignment(UUID tenantId,
                              UUID assignmentId,
                              UUID updatedBy,
                              String reason) {
        int rows = jdbc.update("""
                UPDATE crm_assignments
                   SET status='ENDED',
                       ends_at=CURRENT_TIMESTAMP,
                       effective_to=CURRENT_TIMESTAMP,
                       reason=COALESCE(:reason, reason),
                       version=version + 1,
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:id
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", assignmentId)
                .addValue("updatedBy", updatedBy)
                .addValue("reason", reason));
        if (rows != 1) {
            throw new AssignmentNotFoundException(tenantId, null, null);
        }
    }

    @Override
    public long countActiveByOwner(UUID tenantId, OwnerType ownerType, UUID ownerId) {
        String column = switch (ownerType) {
            case USER -> "owner_user_id";
            case TEAM -> "owner_team_id";
            case QUEUE -> "owner_queue_id";
        };
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_assignments WHERE tenant_id=:tenantId AND "
                        + column + "=:ownerId AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("ownerId", ownerId),
                Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public long countActiveByUser(UUID tenantId, UUID userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_assignments
                 WHERE tenant_id=:tenantId
                   AND owner_type='USER'
                   AND owner_user_id=:userId
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId), Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public long countActiveQueueClaims(UUID tenantId, UUID queueId, UUID userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_assignments assignment
                  JOIN LATERAL (
                      SELECT history.change_type,
                             history.trigger_reference_id,
                             history.to_owner_user_id
                        FROM crm_ownership_history history
                       WHERE history.tenant_id=assignment.tenant_id
                         AND history.record_type=assignment.record_type
                         AND history.record_id=assignment.record_id
                       ORDER BY history.recorded_at DESC, history.id DESC
                       LIMIT 1
                  ) latest_history ON TRUE
                 WHERE assignment.tenant_id=:tenantId
                   AND assignment.owner_type='USER'
                   AND assignment.owner_user_id=:userId
                   AND assignment.status='ACTIVE'
                   AND latest_history.change_type='QUEUE_CLAIM'
                   AND latest_history.trigger_reference_id=:queueId
                   AND latest_history.to_owner_user_id=:userId
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("queueId", queueId)
                .addValue("userId", userId), Long.class);
        return count != null ? count : 0L;
    }
}
