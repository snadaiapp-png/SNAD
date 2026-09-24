package com.sanad.platform.ai.api;

import com.sanad.platform.ai.application.AiAgentService;
import com.sanad.platform.ai.application.AiExecutionService;
import com.sanad.platform.ai.domain.AiAgent;
import com.sanad.platform.ai.domain.AiInference;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;
import static com.sanad.platform.security.SecurityContextUtils.userId;

/**
 * AI Module REST API — agents, inferences, and execution.
 *
 * <p>All endpoints are tenant-scoped via {@link com.sanad.platform.security.SecurityContextUtils#tenantId(Authentication)}
 * and require a {@link RequireCapability AI.*} capability.
 *
 * <p>Base path: {@code /api/v1/ai}
 *
 * <p><strong>AI Safety</strong>: ALL inferences are advisory-only. No endpoint
 * mutates business state based on AI output.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiAgentService agentService;
    private final AiExecutionService executionService;

    public AiController(AiAgentService agentService, AiExecutionService executionService) {
        this.agentService = agentService;
        this.executionService = executionService;
    }

    // ===== Agents =====

    @PostMapping("/agents")
    @RequireCapability("AI.WRITE")
    public ResponseEntity<Map<String, Object>> createAgent(
            Authentication auth, @RequestBody CreateAgentRequest req) {
        var agent = AiAgent.create(
                tenantId(auth), req.code(), req.name(), req.description(),
                req.provider() != null
                        ? AiAgent.Provider.valueOf(req.provider())
                        : AiAgent.Provider.DETERMINISTIC,
                req.modelName(), req.systemPrompt(), req.configuration(),
                req.maxTokens(), req.temperature(),
                userId(auth)
        );
        var saved = agentService.create(agent);
        return ResponseEntity.ok(toAgentMap(saved));
    }

    @GetMapping("/agents")
    @RequireCapability("AI.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listAgents(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var agents = agentService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(agents.stream().map(this::toAgentMap).toList());
    }

    @GetMapping("/agents/{id}")
    @RequireCapability("AI.VIEW")
    public ResponseEntity<Map<String, Object>> getAgent(
            Authentication auth, @PathVariable UUID id) {
        return agentService.findById(tenantId(auth), id)
                .map(a -> ResponseEntity.ok(toAgentMap(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/agents/{id}/activate")
    @RequireCapability("AI.WRITE")
    public ResponseEntity<Map<String, Object>> activateAgent(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toAgentMap(
                agentService.activate(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/agents/{id}/deactivate")
    @RequireCapability("AI.ADMIN")
    public ResponseEntity<Map<String, Object>> deactivateAgent(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toAgentMap(
                agentService.deactivate(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/agents/{id}/archive")
    @RequireCapability("AI.ADMIN")
    public ResponseEntity<Map<String, Object>> archiveAgent(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toAgentMap(
                agentService.archive(tenantId(auth), id, userId(auth))));
    }

    // ===== Inferences =====

    @GetMapping("/inferences")
    @RequireCapability("AI.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listInferences(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var inferences = executionService.findInferencesByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(inferences.stream().map(this::toInferenceMap).toList());
    }

    @GetMapping("/inferences/{id}")
    @RequireCapability("AI.VIEW")
    public ResponseEntity<Map<String, Object>> getInference(
            Authentication auth, @PathVariable UUID id) {
        return executionService.findInferenceById(tenantId(auth), id)
                .map(i -> ResponseEntity.ok(toInferenceMap(i)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/agents/{id}/inferences")
    @RequireCapability("AI.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listAgentInferences(
            Authentication auth, @PathVariable UUID id,
            @RequestParam(defaultValue = "50") int limit) {
        var inferences = executionService.findInferencesByAgent(tenantId(auth), id, limit);
        return ResponseEntity.ok(inferences.stream().map(this::toInferenceMap).toList());
    }

    // ===== Execution =====

    @PostMapping("/execute")
    @RequireCapability("AI.EXECUTE")
    public ResponseEntity<Map<String, Object>> execute(
            Authentication auth, @RequestBody ExecuteRequest req) {
        var inference = executionService.execute(
                tenantId(auth), req.agentId(), userId(auth),
                req.input(), req.correlationId(),
                req.businessEntityType(), req.businessEntityId()
        );
        return ResponseEntity.ok(toInferenceMap(inference));
    }

    @GetMapping("/quota")
    @RequireCapability("AI.VIEW")
    public ResponseEntity<Map<String, Object>> getQuota(Authentication auth) {
        var tenant = tenantId(auth);
        var used = executionService.countThisMonth(tenant);
        return ResponseEntity.ok(Map.of(
                "tenantId", tenant,
                "usedThisMonth", used,
                "advisoryOnly", true
        ));
    }

    // ===== Request DTOs =====

    public record CreateAgentRequest(
            String code, String name, String description,
            String provider, String modelName, String systemPrompt,
            String configuration, Integer maxTokens, Double temperature
    ) {}

    public record ExecuteRequest(
            UUID agentId,
            String input,
            UUID correlationId,
            String businessEntityType,
            UUID businessEntityId
    ) {}

    // ===== Response helpers =====

    private Map<String, Object> toAgentMap(AiAgent a) {
        return Map.of(
                "id", a.id(),
                "code", a.code(),
                "name", a.name(),
                "status", a.status().name(),
                "provider", a.provider().name(),
                "modelName", a.modelName() != null ? a.modelName() : "",
                "version", a.version(),
                "versionLock", a.versionLock(),
                "createdBy", a.createdBy(),
                "advisoryOnly", true
        );
    }

    private Map<String, Object> toInferenceMap(AiInference i) {
        return Map.of(
                "id", i.id(),
                "agentId", i.agentId(),
                "invokedBy", i.invokedBy(),
                "status", i.status().name(),
                "advisory", i.advisory(),
                "tokensInput", i.tokensInput() != null ? i.tokensInput() : 0,
                "tokensOutput", i.tokensOutput() != null ? i.tokensOutput() : 0,
                "latencyMs", i.latencyMs() != null ? i.latencyMs() : 0,
                "costCents", i.costCents(),
                "createdAt", i.createdAt().toString()
        );
    }
}
