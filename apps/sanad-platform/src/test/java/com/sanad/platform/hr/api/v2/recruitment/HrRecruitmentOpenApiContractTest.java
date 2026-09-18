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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G1 T10 RED — §13 recruitment/onboarding API surface must be present in
 * the runtime OpenAPI document. Path-variable names are normalized because
 * §13 uses {id} as shorthand while canonical G0 controllers use typed names.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrRecruitmentOpenApiContractTest {

    private static final String HR = "/api/v2/hr";

    private static final List<Route> REQUIRED = List.of(
            route("post", HR + "/recruitment/openings"),
            route("get",  HR + "/recruitment/openings"),
            route("get",  HR + "/recruitment/openings/{id}"),
            route("post", HR + "/recruitment/openings/{id}/submit"),
            route("post", HR + "/recruitment/openings/{id}/approve"),
            route("post", HR + "/recruitment/openings/{id}/reject"),
            route("post", HR + "/recruitment/openings/{id}/pause"),
            route("post", HR + "/recruitment/openings/{id}/resume"),
            route("post", HR + "/recruitment/openings/{id}/close"),
            route("post", HR + "/recruitment/openings/{id}/cancel"),
            route("post", HR + "/recruitment/candidates"),
            route("get",  HR + "/recruitment/candidates"),
            route("get",  HR + "/recruitment/candidates/{id}"),
            route("post", HR + "/recruitment/candidates/{id}/archive"),
            route("post", HR + "/recruitment/applications"),
            route("get",  HR + "/recruitment/applications"),
            route("get",  HR + "/recruitment/applications/{id}"),
            route("post", HR + "/recruitment/applications/{id}/advance"),
            route("post", HR + "/recruitment/applications/{id}/reject"),
            route("post", HR + "/recruitment/applications/{id}/withdraw"),
            route("get",  HR + "/recruitment/applications/{id}/pipeline"),
            route("post", HR + "/recruitment/applications/{id}/interviews"),
            route("get",  HR + "/recruitment/interviews/{id}"),
            route("post", HR + "/recruitment/interviews/{id}/outcome"),
            route("put",  HR + "/recruitment/interviews/{id}/feedback"),
            route("get",  HR + "/recruitment/interviews/{id}/feedback"),
            route("post", HR + "/recruitment/applications/{id}/offers"),
            route("post", HR + "/recruitment/offers/{id}/extend"),
            route("post", HR + "/recruitment/offers/{id}/accept"),
            route("post", HR + "/recruitment/offers/{id}/decline"),
            route("post", HR + "/recruitment/offers/{id}/withdraw"),
            route("post", HR + "/recruitment/offers/{id}/hire-conversion"),
            route("get",  HR + "/onboarding/plans"),
            route("get",  HR + "/onboarding/plans/{id}"),
            route("post", HR + "/onboarding/plans"),
            route("post", HR + "/onboarding/plans/{id}/tasks/{taskId}/complete"),
            route("post", HR + "/onboarding/plans/{id}/tasks/{taskId}/waive"),
            route("post", HR + "/onboarding/plans/{id}/cancel")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void section13RoutesAreDocumentedAtRuntime() throws Exception {
        JsonNode paths = runtimePaths();
        List<String> missing = new ArrayList<>();

        for (Route route : REQUIRED) {
            JsonNode operation = findOperation(paths, route);
            if (operation == null || operation.isMissingNode()) {
                missing.add(route.method().toUpperCase(Locale.ROOT) + " " + route.path());
            }
        }

        assertThat(missing)
                .as("HRM-G1 T10 §13 routes missing from runtime OpenAPI")
                .isEmpty();
    }

    static JsonNode findOperation(JsonNode paths, Route route) {
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

    static String normalize(String path) {
        return path.replaceAll("\\{[^/}]+}", "{}");
    }

    private JsonNode runtimePaths() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("paths");
    }

    private static Route route(String method, String path) {
        return new Route(method, path);
    }

    record Route(String method, String path) {}
}
