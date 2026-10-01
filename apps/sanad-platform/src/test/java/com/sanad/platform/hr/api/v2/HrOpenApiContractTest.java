package com.sanad.platform.hr.api.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pins the runtime HRM v2 OpenAPI contract to the committed artifact.
 *
 * <p>The artifact is generated from the live {@code /v3/api-docs} document.
 * Besides the historical path/method/operationId surface, G3 performance
 * operations retain their runtime request/response schema shapes so Task 4
 * cannot be closed through a count-only edit.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrOpenApiContractTest {

    private static final Set<String> METHODS =
            Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final String G3_PREFIX = "/api/v2/hr/performance/";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void hrmV2SurfaceMatchesCommittedOpenApiArtifact() throws Exception {
        JsonNode runtimeDocument = runtimeDocument();
        Map<String, Map<String, String>> runtime = extractRuntimeContract(runtimeDocument);
        ObjectNode g3TypedRuntime = extractG3TypedContract(runtimeDocument);
        Path artifact = Path.of(System.getProperty("user.dir"))
                .getParent().getParent().resolve("docs/hrm/contracts/openapi/hrm-openapi.json");

        if (Boolean.getBoolean("hrm.openapi.generate")) {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("generatedFrom", "runtime platform contract export");
            out.put("hrmV2Operations", runtime.values().stream().mapToInt(Map::size).sum());
            ObjectNode paths = out.putObject("paths");
            runtime.forEach((path, ops) -> {
                ObjectNode pathNode = paths.putObject(path);
                ops.forEach(pathNode::put);
            });
            out.set("g3TypedOperations", g3TypedRuntime);
            Files.createDirectories(artifact.getParent());
            Files.writeString(artifact, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
            System.out.println("HRM OpenAPI artifact regenerated at " + artifact);
            return;
        }

        assertThat(artifact).exists();
        JsonNode committed = objectMapper.readTree(Files.readString(artifact));
        Map<String, Map<String, String>> expected = new TreeMap<>();
        committed.path("paths").fields().forEachRemaining(e -> {
            Map<String, String> ops = new TreeMap<>();
            e.getValue().fields().forEachRemaining(op -> {
                if (METHODS.contains(op.getKey())) {
                    ops.put(op.getKey(), op.getValue().asText());
                }
            });
            expected.put(e.getKey(), ops);
        });
        assertThat(runtime)
                .as("HRM v2 runtime surface must equal the committed OpenAPI artifact")
                .isEqualTo(expected);
        assertThat(committed.path("g3TypedOperations"))
                .as("G3 request/response schema contract must come from the runtime OpenAPI document")
                .isEqualTo(g3TypedRuntime);
    }

    private JsonNode runtimeDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** Extracts the /api/v2/hr surface as {path: {method: operationId}}. */
    private Map<String, Map<String, String>> extractRuntimeContract(JsonNode document) {
        JsonNode paths = document.path("paths");
        Map<String, Map<String, String>> result = new TreeMap<>();
        paths.fields().forEachRemaining(pathEntry -> {
            if (!pathEntry.getKey().startsWith("/api/v2/hr")) {
                return;
            }
            Map<String, String> ops = new TreeMap<>();
            pathEntry.getValue().fields().forEachRemaining(opEntry -> {
                if (METHODS.contains(opEntry.getKey())) {
                    ops.put(opEntry.getKey(), opEntry.getValue().path("operationId").asText());
                }
            });
            result.put(pathEntry.getKey(), ops);
        });
        return result;
    }

    /**
     * Preserves the G3 operationId plus the exact runtime JSON request/success
     * response schema fragments. This is intentionally generated rather than
     * hand-authored so request/response type drift is review-visible.
     */
    private ObjectNode extractG3TypedContract(JsonNode document) {
        ObjectNode result = objectMapper.createObjectNode();
        document.path("paths").fields().forEachRemaining(pathEntry -> {
            if (!pathEntry.getKey().startsWith(G3_PREFIX)) {
                return;
            }
            ObjectNode pathOut = result.putObject(pathEntry.getKey());
            pathEntry.getValue().fields().forEachRemaining(opEntry -> {
                if (!METHODS.contains(opEntry.getKey())) {
                    return;
                }
                JsonNode operation = opEntry.getValue();
                ObjectNode operationOut = pathOut.putObject(opEntry.getKey());
                operationOut.put("operationId", operation.path("operationId").asText());

                JsonNode requestSchema = operation.path("requestBody")
                        .path("content").path("application/json").path("schema");
                if (!requestSchema.isMissingNode() && !requestSchema.isEmpty()) {
                    operationOut.set("requestSchema", requestSchema.deepCopy());
                }

                ObjectNode responsesOut = operationOut.putObject("successResponses");
                operation.path("responses").fields().forEachRemaining(response -> {
                    if (!response.getKey().startsWith("2")) {
                        return;
                    }
                    JsonNode schema = response.getValue()
                            .path("content").path("application/json").path("schema");
                    if (!schema.isMissingNode() && !schema.isEmpty()) {
                        responsesOut.set(response.getKey(), schema.deepCopy());
                    }
                });
            });
        });
        return result;
    }
}
