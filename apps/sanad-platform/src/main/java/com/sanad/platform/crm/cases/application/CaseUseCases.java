package com.sanad.platform.crm.cases.application;

import com.sanad.platform.crm.cases.domain.CaseRepository;
import com.sanad.platform.crm.cases.domain.CaseRepository.CreateCaseCommand;
import com.sanad.platform.crm.cases.domain.CaseRepository.CaseRecord;
import com.sanad.platform.crm.cases.domain.CaseRepository.UpdateCaseCommand;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Use-case facade for the Case bounded context.
 * <p>
 * Thin orchestration layer — applies {@code @Transactional} boundaries
 * and delegates to {@link CaseRepository}. Domain policies (state machine
 * enforcement) live in the repository layer; this facade may evolve to
 * host cross-cutting concerns (timeline events, audit, notifications).
 */
public class CaseUseCases {
    private final CaseRepository repo;

    public CaseUseCases(CaseRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public CaseRecord create(UUID tenantId, UUID actorId, CreateCaseCommand cmd) {
        return repo.create(tenantId, actorId, cmd);
    }

    public CaseRecord getById(UUID tenantId, UUID id) {
        return repo.findById(tenantId, id);
    }

    public List<CaseRecord> list(UUID tenantId, int limit, String status,
                                  UUID assigneeUserId, UUID customerId) {
        return repo.findAll(tenantId, limit, status, assigneeUserId, customerId);
    }

    @Transactional
    public CaseRecord update(UUID tenantId, UUID actorId, UUID id,
                              UpdateCaseCommand cmd, long expectedVersion) {
        return repo.update(tenantId, actorId, id, cmd, expectedVersion);
    }

    @Transactional
    public CaseRecord start(UUID tenantId, UUID actorId, UUID id, long expectedVersion) {
        return repo.start(tenantId, actorId, id, expectedVersion);
    }

    @Transactional
    public CaseRecord resolve(UUID tenantId, UUID actorId, UUID id,
                               String resolution, long expectedVersion) {
        return repo.resolve(tenantId, actorId, id, resolution, expectedVersion);
    }

    @Transactional
    public CaseRecord close(UUID tenantId, UUID actorId, UUID id, long expectedVersion) {
        return repo.close(tenantId, actorId, id, expectedVersion);
    }

    @Transactional
    public CaseRecord reopen(UUID tenantId, UUID actorId, UUID id, long expectedVersion) {
        return repo.reopen(tenantId, actorId, id, expectedVersion);
    }

    @Transactional
    public CaseRecord assign(UUID tenantId, UUID actorId, UUID id,
                              UUID assigneeUserId, long expectedVersion) {
        return repo.assign(tenantId, actorId, id, assigneeUserId, expectedVersion);
    }
}
