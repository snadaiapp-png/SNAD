package com.sanad.platform.hr.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.api.v2.performance.HrPerformanceGoalV2Controller;
import com.sanad.platform.hr.api.v2.performance.HrPerformanceReviewV2Controller;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.Arrays;

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
        JsonNode goals = runtimePaths().path("/api/v2/hr/performance/goals");

        assertThat(goals.isMissingNode()).as("G3 goals route must exist").isFalse();
        assertTypedOperation(goals, "get", "hrPerformanceGoalsList", false);
        assertTypedOperation(goals, "post", "hrPerformanceGoalCreate", true);
    }

    @Test
    void performanceReviewRouteExposesExactBaseOperationsAndTypedBodies() throws Exception {
        JsonNode reviews = runtimePaths().path("/api/v2/hr/performance/reviews");

        assertThat(reviews.isMissingNode()).as("G3 reviews route must exist").isFalse();
        assertTypedOperation(reviews, "get", "hrPerformanceReviewsList", false);
        assertTypedOperation(reviews, "post", "hrPerformanceReviewCreate", true);
    }

    @Test
    void everyG3ControllerOperationUsesTheExactCanonicalCapability() {
        assertCapability(HrPerformanceGoalV2Controller.class, "listGoals", PerformanceCapabilities.GOAL_SELF_VIEW);
        assertCapability(HrPerformanceGoalV2Controller.class, "getGoal", PerformanceCapabilities.GOAL_SELF_VIEW);
        assertCapability(HrPerformanceGoalV2Controller.class, "createGoal", PerformanceCapabilities.GOAL_SELF_UPDATE);
        assertCapability(HrPerformanceGoalV2Controller.class, "updateGoal", PerformanceCapabilities.GOAL_SELF_UPDATE);
        assertCapability(HrPerformanceGoalV2Controller.class, "updateGoalProgress", PerformanceCapabilities.GOAL_SELF_UPDATE);
        assertCapability(HrPerformanceGoalV2Controller.class, "listTeamGoals", PerformanceCapabilities.GOAL_TEAM_MANAGE);
        assertCapability(HrPerformanceGoalV2Controller.class, "createTeamGoal", PerformanceCapabilities.GOAL_TEAM_MANAGE);
        assertCapability(HrPerformanceGoalV2Controller.class, "updateTeamGoal", PerformanceCapabilities.GOAL_TEAM_MANAGE);
        assertCapability(HrPerformanceGoalV2Controller.class, "updateTeamGoalProgress", PerformanceCapabilities.GOAL_TEAM_MANAGE);

        assertCapability(HrPerformanceReviewV2Controller.class, "listReviews", PerformanceCapabilities.REVIEW_SELF_VIEW);
        assertCapability(HrPerformanceReviewV2Controller.class, "getReview", PerformanceCapabilities.REVIEW_SELF_VIEW);
        assertCapability(HrPerformanceReviewV2Controller.class, "createSelfReview", PerformanceCapabilities.REVIEW_SELF_SUBMIT);
        assertCapability(HrPerformanceReviewV2Controller.class, "submitReview", PerformanceCapabilities.REVIEW_SELF_SUBMIT);
        assertCapability(HrPerformanceReviewV2Controller.class, "acknowledgeReview", PerformanceCapabilities.REVIEW_SELF_SUBMIT);
        assertCapability(HrPerformanceReviewV2Controller.class, "listTeamReviews", PerformanceCapabilities.REVIEW_TEAM_MANAGE);
        assertCapability(HrPerformanceReviewV2Controller.class, "getTeamReview", PerformanceCapabilities.REVIEW_TEAM_MANAGE);
        assertCapability(HrPerformanceReviewV2Controller.class, "createTeamReview", PerformanceCapabilities.REVIEW_TEAM_MANAGE);
        assertCapability(HrPerformanceReviewV2Controller.class, "cancelTeamReview", PerformanceCapabilities.REVIEW_TEAM_MANAGE);
    }

    private JsonNode runtimePaths() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("paths");
    }

    private void assertTypedOperation(
            JsonNode path,
            String method,
            String operationId,
            boolean requestBodyRequired) {
        JsonNode operation = path.path(method);
        assertThat(operation.isMissingNode()).as("%s operation must exist", method).isFalse();
        assertThat(operation.path("operationId").asText()).isEqualTo(operationId);

        if (requestBodyRequired) {
            JsonNode requestSchema = operation.path("requestBody")
                    .path("content").path("application/json").path("schema");
            assertThat(requestSchema.isMissingNode() || requestSchema.isEmpty())
                    .as("%s must document a typed JSON request body", operationId)
                    .isFalse();
        }

        JsonNode responseSchema = firstSuccessJsonSchema(operation.path("responses"));
        assertThat(responseSchema.isMissingNode() || responseSchema.isEmpty())
                .as("%s must document a typed success response", operationId)
                .isFalse();
    }

    private JsonNode firstSuccessJsonSchema(JsonNode responses) {
        for (var it = responses.fields(); it.hasNext();) {
            var response = it.next();
            if (response.getKey().startsWith("2")) {
                JsonNode schema = response.getValue()
                        .path("content").path("application/json").path("schema");
                if (!schema.isMissingNode() && !schema.isEmpty()) {
                    return schema;
                }
            }
        }
        return objectMapper.missingNode();
    }

    private static void assertCapability(
            Class<?> controllerType,
            String methodName,
            String expectedCapability) {
        Method method = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing controller method " + methodName));

        RequireCapability requirement = method.getAnnotation(RequireCapability.class);
        assertThat(requirement)
                .as("%s.%s must fail closed behind @RequireCapability",
                        controllerType.getSimpleName(), methodName)
                .isNotNull();
        assertThat(requirement.value())
                .as("%s.%s must use the exact canonical G3 capability",
                        controllerType.getSimpleName(), methodName)
                .isEqualTo(expectedCapability);
    }
}
