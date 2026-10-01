package com.sanad.platform.hr.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HRM-G3 Task 4 RED/OpenAPI contract.
 *
 * <p>Pins the canonical performance route families and operation IDs against
 * the real Spring runtime OpenAPI document. This deliberately fails before the
 * Task 4 controllers exist.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrG3OpenApiContractTest {

    private static final Map<String, Map<String, String>> REQUIRED = Map.of(
            "/api/v2/hr/performance/goals", Map.of(
                    "get", "hrPerformanceGoalsList",
                    "post", "hrPerformanceGoalCreate"),
            "/api/v2/hr/performance/reviews", Map.of(
                    "get", "hrPerformanceReviewsList",
                    "post", "hrPerformanceReviewCreate"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void runtimeOpenApiExposesCapabilityScopedPerformanceRouteFamilies() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode paths = objectMapper.readTree(body).path("paths");
        REQUIRED.forEach((path, operations) -> {
            JsonNode pathNode = paths.path(path);
            assertThat(pathNode.isMissingNode())
                    .as("runtime OpenAPI must expose %s", path)
                    .isFalse();
            operations.forEach((method, operationId) -> {
                JsonNode operation = pathNode.path(method);
                assertThat(operation.isMissingNode())
                        .as("runtime OpenAPI must expose %s %s", method.toUpperCase(), path)
                        .isFalse();
                assertThat(operation.path("operationId").asText())
                        .as("operationId for %s %s", method.toUpperCase(), path)
                        .isEqualTo(operationId);
            });
        });
    }

    @Test
    void createOperationsDocumentConcreteRequestAndResponseSchemas() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode paths = objectMapper.readTree(body).path("paths");
        for (String path : REQUIRED.keySet()) {
            JsonNode post = paths.path(path).path("post");
            assertThat(post.path("requestBody").isMissingNode())
                    .as("POST %s must document a request body", path)
                    .isFalse();
            assertThat(post.path("responses").size())
                    .as("POST %s must document at least one response", path)
                    .isGreaterThan(0);
        }
    }
}
