package com.sanad.platform.ai.application;

import com.sanad.platform.ai.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link AiAgent} lifecycle management.
 *
 * <p>State machine: DRAFT → ACTIVE → INACTIVE → ARCHIVED
 *
 * <p>Controllers should never call repositories directly — they go through
 * this service. Every lifecycle transition is logged via SLF4J.
 */
@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);

    private final AiAgentRepository agentRepo;

    public AiAgentService(AiAgentRepository agentRepo) {
        this.agentRepo = agentRepo;
    }

    @Transactional
    public AiAgent create(AiAgent agent) {
        var saved = agentRepo.save(agent);
        log.info("AiAgent created: tenant={} code={} provider={} actor={}",
                saved.tenantId(), saved.code(), saved.provider(), saved.createdBy());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<AiAgent> findById(UUID tenantId, UUID id) {
        return agentRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<AiAgent> findByTenant(UUID tenantId, int limit) {
        return agentRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<AiAgent> findActive(UUID tenantId, int limit) {
        return agentRepo.findByTenantAndStatus(tenantId, AiAgent.Status.ACTIVE, limit);
    }

    @Transactional
    public AiAgent activate(UUID tenantId, UUID id, UUID actorUserId) {
        var agent = load(tenantId, id);
        var updated = agentRepo.save(agent.activate());
        log.info("AiAgent activated: tenant={} code={} actor={}", tenantId, updated.code(), actorUserId);
        return updated;
    }

    @Transactional
    public AiAgent deactivate(UUID tenantId, UUID id, UUID actorUserId) {
        var agent = load(tenantId, id);
        var updated = agentRepo.save(agent.deactivate());
        log.info("AiAgent deactivated: tenant={} code={} actor={}", tenantId, updated.code(), actorUserId);
        return updated;
    }

    @Transactional
    public AiAgent archive(UUID tenantId, UUID id, UUID actorUserId) {
        var agent = load(tenantId, id);
        var updated = agentRepo.save(agent.archive());
        log.info("AiAgent archived: tenant={} code={} actor={}", tenantId, updated.code(), actorUserId);
        return updated;
    }

    @Transactional
    public void delete(UUID tenantId, UUID id, UUID actorUserId) {
        var agent = load(tenantId, id);
        agentRepo.deleteById(tenantId, id);
        log.info("AiAgent deleted: tenant={} code={} actor={}", tenantId, agent.code(), actorUserId);
    }

    private AiAgent load(UUID tenantId, UUID id) {
        return agentRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("AiAgent not found: " + id));
    }
}
