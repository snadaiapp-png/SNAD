package com.sanad.platform.hr.api.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * HRM-G0 / WS5 Task 6 — pins the runtime HRM v2 OpenAPI contract to the
 * committed artifact {@code docs/hrm/contracts/openapi/hrm-openapi.json}.
 *
 * <p>The committed artifact is generated from the reviewed runtime contract
 * (run with {@code -Dhrm.openapi.generate=true} to regenerate). The test
 * then compares every {@code /api/v2/hr} path, HTTP method and operationId
 * against the artifact — any unreviewed addition or removal of the public
 * HRM v2 surface fails the build.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrOpenApiContractTest {

    private static final Set<String> METHODS =
            Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void hrmV2SurfaceMatchesCommittedOpenApiArtifact() throws Exception {
        Map<String, Map<String, String>> runtime = extractRuntimeContract();
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
        assertThat(runtime).as("HRM v2 runtime surface must equal the committed OpenAPI artifact").isEqualTo(expected);
    }

    /** Extracts the /api/v2/hr surface as {path: {method: operationId}}. */
    private Map<String, Map<String, String>> extractRuntimeContract() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).path("paths");
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
}
