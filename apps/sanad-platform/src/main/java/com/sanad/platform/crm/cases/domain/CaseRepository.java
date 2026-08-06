package com.sanad.platform.crm.cases.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Case repository port — bounded context for first-class CRM cases/tickets.
 * <p>
 * Cases are customer support work items with assignee, priority,
 * status lifecycle, and optional linkage to any CRM entity.
 * All methods are tenant-scoped.
 */
public interface CaseRepository {

    CaseRecord findById(UUID tenantId, UUID caseId);

    java.util.List<CaseRecord> findAll(UUID tenantId, int limit, String status,
                                        UUID assigneeUserId, UUID customerId);

    CaseRecord create(UUID tenantId, UUID actorId, CreateCaseCommand command);

    CaseRecord update(UUID tenantId, UUID actorId, UUID caseId,
                      UpdateCaseCommand command, long expectedVersion);

    CaseRecord start(UUID tenantId, UUID actorId, UUID caseId, long expectedVersion);

    CaseRecord resolve(UUID tenantId, UUID actorId, UUID caseId, String resolution, long expectedVersion);

    CaseRecord close(UUID tenantId, UUID actorId, UUID caseId, long expectedVersion);

    CaseRecord reopen(UUID tenantId, UUID actorId, UUID caseId, long expectedVersion);

    CaseRecord assign(UUID tenantId, UUID actorId, UUID caseId, UUID assigneeUserId, long expectedVersion);

    record CaseRecord(UUID id, long version, String subject, String description,
            String caseType, String status, int priority,
            UUID customerId, UUID assigneeUserId, UUID ownerUserId,
            UUID relatedId, OffsetDateTime dueAt,
            OffsetDateTime resolvedAt, OffsetDateTime closedAt,
            Instant createdAt, Instant updatedAt) {}

    record CreateCaseCommand(String subject, String description, String caseType,
            int priority, UUID customerId, UUID assigneeUserId,
            UUID relatedId, OffsetDateTime dueAt) {}

    record UpdateCaseCommand(String subject, String description, String caseType,
            Integer priority, UUID customerId, OffsetDateTime dueAt) {}
}
