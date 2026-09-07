package com.sanad.platform.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

/**
 * Namespace-safe typed workflow context (design decision S3).
 *
 * <p>Context is not a free scratchpad: step outputs are committed to their
 * own {@code stepOutputs.<stepKey>} namespace exactly once, and arbitrary
 * cross-step overwrites are rejected. Historical instance payloads stay
 * interpretable by their pinned {@code contextSchemaVersion}.</p>
 */
@Service
public class WorkflowContextService {

    private final ObjectMapper objectMapper;

    public WorkflowContextService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Commits one step's output to its namespace. The namespace is immutable
     * once committed — a second write for the same step key is a defect, not
     * an update.
     */
    public String writeStepOutput(String contextJson, String stepKey, JsonNode output) {
        try {
            ObjectNode context = contextJson == null || contextJson.isBlank()
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(contextJson);
            ObjectNode stepOutputs = context.withObjectProperty("stepOutputs");
            if (stepOutputs.has(stepKey)) {
                throw new IllegalStateException("Step output namespace is immutable once committed: " + stepKey);
            }
            stepOutputs.set(stepKey, output.deepCopy());
            return objectMapper.writeValueAsString(context);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow context document", e);
        }
    }

    /** Reads a JSON pointer from the context, failing closed on parse errors. */
    public JsonNode read(String contextJson, String jsonPointer) {
        try {
            JsonNode context = contextJson == null || contextJson.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(contextJson);
            return context.at(jsonPointer);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow context document", e);
        }
    }
}
