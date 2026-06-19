package com.sanad.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PlatformOperationContractTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void openApiContainsMembershipAssignmentOperations() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = objectMapper.readTree(
                result.getResponse().getContentAsString()).path("paths");

        assertOperation(paths, "/api/v1/users", "post");
        assertOperation(paths, "/api/v1/users", "get");
        assertOperation(paths, "/api/v1/users/{userId}", "get");
        assertOperation(paths, "/api/v1/users/{userId}", "put");
        assertOperation(paths, "/api/v1/users/{userId}/activate", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/deactivate", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/suspend", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/archive", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/memberships", "get");
        assertOperation(paths,
                "/api/v1/organizations/{organizationId}/memberships/{membershipId}/assign/{userId}",
                "patch");
        assertOperation(paths,
                "/api/v1/organizations/{organizationId}/memberships/{membershipId}/unassign",
                "patch");
        assertThat(countOperations(paths, "/api/v1/users")).isEqualTo(9);
        assertThat(countOperations(paths, null)).isEqualTo(24);
    }

    private static void assertOperation(JsonNode paths, String path, String method) {
        assertThat(paths.path(path).has(method))
                .as(method.toUpperCase() + " " + path)
                .isTrue();
    }

    private static long countOperations(JsonNode paths, String prefix) {
        long count = 0;
        Iterator<Map.Entry<String, JsonNode>> entries = paths.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            if (prefix != null && !entry.getKey().startsWith(prefix)) continue;
            Iterator<String> fields = entry.getValue().fieldNames();
            while (fields.hasNext()) {
                if (HTTP_METHODS.contains(fields.next())) count++;
            }
        }
        return count;
    }
}
