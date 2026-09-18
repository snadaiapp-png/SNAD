package com.sanad.platform.hr.api.v2.recruitment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G1 T10 RED — authoritative §13 recruitment/onboarding OpenAPI surface.
 *
 * <p>This test intentionally lands before controller implementation. It must
 * fail until every §13 route exists under the repository-canonical /api/v2/hr family.
 * The source design spells the URL prefix as /hr/api/v2, but the executable G0 contract,\n * web client and API-count gate all establish /api/v2/hr; this test preserves\n * that single canonical family and prevents a parallel namespace. It also fails\n * on undocumented extra operations inside the G1 recruitment
 * and onboarding namespaces.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrRecruitmentOpenApiContractTest {

    private static final Set<String> METHODS =
            Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    private static final Set<Operation> EXPECTED = Set.of(
            op("post", "/api/v2/hr/recruitment/openings"),
            op("get", "/api/v2/hr/recruitment/openings"),
            op("get", "/api/v2/hr/recruitment/openings/{id}"),
            op("post", "/api/v2/hr/recruitment/openings/{id}/submit"),
            op("post", "/api/v2/hr/recruitment/openings/{id}/approve"),
            op("post", "/api/v2/hr/recruitment/openings/{id}/reject"),
            op("post", "/api/v2/hr/recruitment/openings/{id}/pause"),
            op("post", "/api/v2/hr/recruitment/openings/{id}/resume"),
            op("post", "/api/v2/hr/recruitment/openings/{id}/close"),
            op("post", "/api/v2/hr/recruitment/openings/{id}/cancel"),

            op("post", "/api/v2/hr/recruitment/candidates"),
            op("get", "/api/v2/hr/recruitment/candidates"),
            op("get", "/api/v2/hr/recruitment/candidates/{id}"),
            op("post", "/api/v2/hr/recruitment/candidates/{id}/archive"),

            op("post", "/api/v2/hr/recruitment/applications"),
            op("get", "/api/v2/hr/recruitment/applications"),
            op("get", "/api/v2/hr/recruitment/applications/{id}"),
            op("post", "/api/v2/hr/recruitment/applications/{id}/advance"),
            op("post", "/api/v2/hr/recruitment/applications/{id}/reject"),
            op("post", "/api/v2/hr/recruitment/applications/{id}/withdraw"),
            op("get", "/api/v2/hr/recruitment/applications/{id}/pipeline"),
            op("post", "/api/v2/hr/recruitment/applications/{id}/interviews"),

            op("get", "/api/v2/hr/recruitment/interviews/{id}"),
            op("post", "/api/v2/hr/recruitment/interviews/{id}/outcome"),
            op("put", "/api/v2/hr/recruitment/interviews/{id}/feedback"),
            op("get", "/api/v2/hr/recruitment/interviews/{id}/feedback"),

            op("post", "/api/v2/hr/recruitment/applications/{id}/offers"),
            op("post", "/api/v2/hr/recruitment/offers/{id}/extend"),
            op("post", "/api/v2/hr/recruitment/offers/{id}/accept"),
            op("post", "/api/v2/hr/recruitment/offers/{id}/decline"),
            op("post", "/api/v2/hr/recruitment/offers/{id}/withdraw"),
            op("post", "/api/v2/hr/recruitment/offers/{id}/hire-conversion"),

            op("get", "/api/v2/hr/onboarding/plans"),
            op("get", "/api/v2/hr/onboarding/plans/{id}"),
            op("post", "/api/v2/hr/onboarding/plans"),
            op("post", "/api/v2/hr/onboarding/plans/{id}/tasks/{taskId}/complete"),
            op("post", "/api/v2/hr/onboarding/plans/{id}/tasks/{taskId}/waive"),
            op("post", "/api/v2/hr/onboarding/plans/{id}/cancel")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void section13Surface_isCompleteAndHasNoUndocumentedOperations() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode paths = objectMapper.readTree(body).path("paths");
        Set<Operation> runtime = new LinkedHashSet<>();

        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            if (!path.startsWith("/api/v2/hr/recruitment")
                    && !path.startsWith("/api/v2/hr/onboarding")) {
                return;
            }
            pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey().toLowerCase(java.util.Locale.ROOT);
                if (METHODS.contains(method)) {
                    runtime.add(op(method, path));
                }
            });
        });

        assertThat(runtime)
                .as("HRM G1 T10 §13 runtime surface must exactly match the authoritative API design")
                .containsExactlyInAnyOrderElementsOf(EXPECTED);
        assertThat(runtime).hasSize(38);
    }

    private static Operation op(String method, String path) {
        return new Operation(method, path);
    }

    private record Operation(String method, String path) {}
}
