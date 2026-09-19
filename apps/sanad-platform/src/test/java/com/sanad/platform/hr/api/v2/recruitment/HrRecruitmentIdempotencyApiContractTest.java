package com.sanad.platform.hr.api.v2.recruitment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G1 T10 RED — all §13 mutations must expose G0's Idempotency-Key
 * contract, and §7 hire conversion must explicitly document replay => HTTP 200
 * with the original result.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrRecruitmentIdempotencyApiContractTest {

    private static final String HR = "/api/v2/hr";

    private static final List<Route> MUTATIONS = List.of(
            post(HR + "/recruitment/openings"),
            post(HR + "/recruitment/openings/{id}/submit"),
            post(HR + "/recruitment/openings/{id}/approve"),
            post(HR + "/recruitment/openings/{id}/reject"),
            post(HR + "/recruitment/openings/{id}/pause"),
            post(HR + "/recruitment/openings/{id}/resume"),
            post(HR + "/recruitment/openings/{id}/close"),
            post(HR + "/recruitment/openings/{id}/cancel"),
            post(HR + "/recruitment/candidates"),
            post(HR + "/recruitment/candidates/{id}/archive"),
            post(HR + "/recruitment/applications"),
            post(HR + "/recruitment/applications/{id}/advance"),
            post(HR + "/recruitment/applications/{id}/reject"),
            post(HR + "/recruitment/applications/{id}/withdraw"),
            post(HR + "/recruitment/applications/{id}/interviews"),
            post(HR + "/recruitment/interviews/{id}/outcome"),
            put(HR + "/recruitment/interviews/{id}/feedback"),
            post(HR + "/recruitment/applications/{id}/offers"),
            post(HR + "/recruitment/offers/{id}/extend"),
            post(HR + "/recruitment/offers/{id}/accept"),
            post(HR + "/recruitment/offers/{id}/decline"),
            post(HR + "/recruitment/offers/{id}/withdraw"),
            post(HR + "/recruitment/offers/{id}/hire-conversion"),
            post(HR + "/onboarding/plans"),
            post(HR + "/onboarding/plans/{id}/tasks/{taskId}/complete"),
            post(HR + "/onboarding/plans/{id}/tasks/{taskId}/waive"),
            post(HR + "/onboarding/plans/{id}/cancel")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void everyMutationDocumentsRequiredIdempotencyKey() throws Exception {
        JsonNode paths = runtimePaths();
        List<String> missing = new ArrayList<>();

        for (Route mutation : MUTATIONS) {
            JsonNode operation = findOperation(paths, mutation);
            if (!hasRequiredIdempotencyHeader(operation)) {
                missing.add(mutation.method().toUpperCase(Locale.ROOT) + " " + mutation.path());
            }
        }

        assertThat(missing)
                .as("Every HRM-G1 mutation must document required Idempotency-Key")
                .isEmpty();
    }

    @Test
    void hireConversionDocumentsReplayAs200OriginalResult() throws Exception {
        Route conversion = post(HR + "/recruitment/offers/{id}/hire-conversion");
        JsonNode operation = findOperation(runtimePaths(), conversion);

        assertThat(operation)
                .as("hire-conversion operation must exist")
                .isNotNull();
        assertThat(operation.path("responses").path("200").isMissingNode())
                .as("hire-conversion replay must return HTTP 200")
                .isFalse();

        String docs = (operation.path("summary").asText("") + " "
                + operation.path("description").asText("")).toLowerCase(Locale.ROOT);
        assertThat(docs).contains("replay");
        assertThat(docs).contains("original");
    }

    private static boolean hasRequiredIdempotencyHeader(JsonNode operation) {
        if (operation == null || operation.isMissingNode()) {
            return false;
        }
        return StreamSupport.stream(operation.path("parameters").spliterator(), false)
                .anyMatch(parameter ->
                        "header".equals(parameter.path("in").asText())
                                && "Idempotency-Key".equalsIgnoreCase(parameter.path("name").asText())
                                && parameter.path("required").asBoolean(false));
    }

    private static JsonNode findOperation(JsonNode paths, Route route) {
        String expectedPath = normalize(route.path());
        var fields = paths.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (normalize(entry.getKey()).equals(expectedPath)) {
                return entry.getValue().path(route.method());
            }
        }
        return null;
    }

    private static String normalize(String path) {
        return path.replaceAll("\\{[^/}]+}", "{}");
    }

    private JsonNode runtimePaths() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("paths");
    }

    private static Route post(String path) {
        return new Route("post", path);
    }

    private static Route put(String path) {
        return new Route("put", path);
    }

    private record Route(String method, String path) {}
}
