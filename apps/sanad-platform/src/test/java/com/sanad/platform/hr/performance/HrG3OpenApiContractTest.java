package com.sanad.platform.hr.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrG3OpenApiContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void performanceGoalRouteExposesExactBaseOperationsAndTypedBodies() throws Exception {
        JsonNode paths = runtimePaths();
        JsonNode goals = paths.path("/api/v2/hr/performance/goals");

        assertThat(goals.isMissingNode()).as("G3 goals route must exist").isFalse();
        assertOperation(goals, "get", "hrPerformanceGoalsList", false);
        assertOperation(goals, "post", "hrPerformanceGoalCreate", true);
    }

    @Test
    void performanceReviewRouteExposesExactBaseOperationsAndTypedBodies() throws Exception {
        JsonNode paths = runtimePaths();
        JsonNode reviews = paths.path("/api/v2/hr/performance/reviews");

        assertThat(reviews.isMissingNode()).as("G3 reviews route must exist").isFalse();
        assertOperation(reviews, "get", "hrPerformanceReviewsList", false);
        assertOperation(reviews, "post", "hrPerformanceReviewCreate", true);
    }

    private JsonNode runtimePaths() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("paths");
    }

    private void assertOperation(JsonNode path, String method, String operationId, boolean requestBodyRequired) {
        JsonNode operation = path.path(method);
        assertThat(operation.isMissingNode()).as("%s operation must exist", method).isFalse();
        assertThat(operation.path("operationId").asText()).isEqualTo(operationId);

        if (requestBodyRequired) {
            assertThat(operation.path("requestBody").isMissingNode())
                    .as("%s must document a request body", operationId).isFalse();
            assertThat(operation.path("requestBody").path("content").has("application/json"))
                    .as("%s request body must be JSON", operationId).isTrue();
        }

        JsonNode responses = operation.path("responses");
        assertThat(responses.size()).as("%s must document responses", operationId).isGreaterThan(0);
    }
}
