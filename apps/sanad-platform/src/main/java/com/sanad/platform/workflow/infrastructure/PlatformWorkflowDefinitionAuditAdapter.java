package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionAuditPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists Workflow definition lifecycle evidence into the platform's
 * append-only audit ledger. Only non-sensitive lifecycle metadata is written.
 */
@Component
public class PlatformWorkflowDefinitionAuditAdapter implements WorkflowDefinitionAuditPort {

    private final PlatformAuditWriter auditWriter;

    public PlatformWorkflowDefinitionAuditAdapter(PlatformAuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    @Override
    public void record(UUID actorUserId,
                       WorkflowDefinition before,
                       WorkflowDefinition after,
                       Action action) {
        WorkflowDefinition subject = after != null ? after : before;
        if (subject == null) {
            throw new IllegalArgumentException("Workflow definition audit requires a subject");
        }

        auditWriter.writeSuccess(
                subject.tenantId(),
                actorUserId,
                subject.tenantId(),
                "WORKFLOW.DEFINITION." + action.name(),
                "WORKFLOW_DEFINITION",
                subject.id().toString(),
                "Workflow definition lifecycle",
                summary(before),
                summary(after),
                UUID.randomUUID().toString(),
                Instant.now());
    }

    private Map<String, Object> summary(WorkflowDefinition definition) {
        if (definition == null) return null;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("definitionId", definition.id().toString());
        state.put("definitionFamilyId", definition.definitionFamilyId() != null
                ? definition.definitionFamilyId().toString() : null);
        state.put("code", definition.code());
        state.put("module", definition.module());
        state.put("version", definition.version());
        state.put("versionLock", definition.versionLock());
        state.put("engineGeneration", definition.engineGeneration() != null
                ? definition.engineGeneration().name() : null);
        state.put("publicationState", definition.publicationState() != null
                ? definition.publicationState().name() : null);
        state.put("status", definition.status() != null ? definition.status().name() : null);
        return state;
    }
}
