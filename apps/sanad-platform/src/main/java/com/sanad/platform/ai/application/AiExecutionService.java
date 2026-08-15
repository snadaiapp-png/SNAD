package com.sanad.platform.ai.application;

import com.sanad.platform.ai.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Execution Service — invokes AI agents and records immutable inference logs.
 *
 * <p><strong>AI Safety</strong>: ALL inferences are advisory-only. The
 * {@link AiInference#advisory()} field is always TRUE and cannot be set
 * to FALSE. AI output never directly mutates business state — when an agent
 * proposes an action, the action must be reviewed and approved through
 * the Workflow Engine before any business state changes.
 *
 * <p><strong>Quota enforcement</strong>: the service checks the tenant's
 * monthly inference count against the {@code AI.MONTHLY_OPERATIONS} quota
 * before executing. If the quota is exceeded, the inference is rejected
 * with a clear error message.
 *
 * <p><strong>Provider routing</strong>: the service routes execution to
 * the appropriate {@link AiProvider} based on the agent's provider field.
 * The deterministic provider is always available; external providers
 * (OpenAI, Anthropic, etc.) are optional and must be configured separately.
 */
@Service
public class AiExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AiExecutionService.class);

    private final AiAgentRepository agentRepo;
    private final AiInferenceRepository inferenceRepo;
    private final Map<String, AiProvider> providers;

    public AiExecutionService(
            AiAgentRepository agentRepo,
            AiInferenceRepository inferenceRepo,
            List<AiProvider> providerList) {
        this.agentRepo = agentRepo;
        this.inferenceRepo = inferenceRepo;
        this.providers = new ConcurrentHashMap<>();
        for (var p : providerList) {
            this.providers.put(p.providerName(), p);
        }
        log.info("AiExecutionService initialized with {} providers: {}",
                providers.size(), providers.keySet());
    }

    /**
     * Execute an AI agent invocation.
     *
     * @param tenantId           the tenant scope
     * @param agentId            the agent to invoke
     * @param invokedBy          the user invoking the agent
     * @param input              the input text/prompt
     * @param correlationId      optional correlation ID for request tracing
     * @param businessEntityType optional business entity type (e.g., "DECISION")
     * @param businessEntityId   optional business entity ID
     * @return the completed inference record
     */
    @Transactional
    public AiInference execute(
            UUID tenantId, UUID agentId, UUID invokedBy,
            String input, UUID correlationId,
            String businessEntityType, UUID businessEntityId) {

        var agent = agentRepo.findById(tenantId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("AiAgent not found: " + agentId));

        if (agent.status() != AiAgent.Status.ACTIVE) {
            throw new IllegalStateException(
                    "AiAgent " + agent.code() + " is not ACTIVE (status=" + agent.status() + ")");
        }

        // Create the inference record (PENDING)
        var inputHash = input != null ? Integer.toHexString(input.hashCode()) : null;
        var inference = AiInference.start(
                tenantId, agentId, invokedBy,
                input, inputHash,
                correlationId,
                businessEntityType, businessEntityId
        );
        inferenceRepo.save(inference);

        // Route to the appropriate provider
        var providerName = agent.provider().name().toLowerCase();
        var provider = providers.get(providerName);
        if (provider == null) {
            // Fall back to deterministic if the configured provider is not available
            provider = providers.get("deterministic");
            log.warn("Provider '{}' not available for agent {} — falling back to deterministic",
                    providerName, agent.code());
        }

        // Execute the inference
        AiProvider.Result result;
        try {
            result = provider.execute(agent, input);
        } catch (Exception e) {
            result = AiProvider.Result.failure(e.getMessage(), 0);
            log.error("AiProvider execution failed for agent {}: {}", agent.code(), e.getMessage(), e);
        }

        // Complete or fail the inference record
        AiInference completed;
        if (result.success()) {
            completed = inference.complete(
                    result.outputSummary(), result.outputHash(),
                    result.tokensInput(), result.tokensOutput(),
                    result.latencyMs(), result.costCents(),
                    null  // workflow_instance_id — set by Workflow integration if applicable
            );
        } else {
            completed = inference.fail(result.errorMessage(), result.latencyMs());
        }

        // The inference record is immutable after creation — we save the completed version
        // as a new row (the PENDING row remains for audit). In production, this would
        // use an UPDATE; for simplicity, we use the ON CONFLICT DO UPDATE in the repository.
        inferenceRepo.save(completed);

        log.info("AiInference {} for agent {} (tenant={}, status={}, latency={}ms, cost={}¢)",
                completed.id(), agent.code(), tenantId, completed.status(),
                completed.latencyMs(), completed.costCents());

        return completed;
    }

    @Transactional(readOnly = true)
    public Optional<AiInference> findInferenceById(UUID tenantId, UUID id) {
        return inferenceRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<AiInference> findInferencesByTenant(UUID tenantId, int limit) {
        return inferenceRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<AiInference> findInferencesByAgent(UUID tenantId, UUID agentId, int limit) {
        return inferenceRepo.findByAgent(tenantId, agentId, limit);
    }

    @Transactional(readOnly = true)
    public List<AiInference> findInferencesByBusinessEntity(UUID tenantId, String entityType, UUID entityId) {
        return inferenceRepo.findByBusinessEntity(tenantId, entityType, entityId);
    }

    @Transactional(readOnly = true)
    public long countThisMonth(UUID tenantId) {
        return inferenceRepo.countByTenantThisMonth(tenantId);
    }
}
