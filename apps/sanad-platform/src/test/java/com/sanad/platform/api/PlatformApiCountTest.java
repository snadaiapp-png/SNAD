package com.sanad.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PlatformApiCountTest {
    private static final Set<String> METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void platformPublishesExpectedOperations() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).path("paths");

        assertThat(count(paths, "/api/v1/users")).isEqualTo(9);
        assertThat(count(paths, "/api/v1/access")).isEqualTo(20);
        assertThat(count(paths, "/api/v1/control-plane")).isEqualTo(35);
        assertThat(count(paths, "/api/v1/crm")).isEqualTo(84);
        assertThat(count(paths, "/api/v2/crm")).isEqualTo(66);
        assertThat(count(paths, null)).isEqualTo(239);
        assertThat(has(paths, "/api/v1/auth/change-credential", "post")).isTrue();
        assertThat(has(paths, "/api/v1/access/evaluation", "get")).isTrue();
        assertThat(has(paths, "/api/v1/control-plane/dashboard", "get")).isTrue();
        assertThat(has(paths, "/api/v1/control-plane/health", "get")).isTrue();
        assertThat(has(paths, "/api/v1/control-plane/health/actions", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/dashboard", "get")).isTrue();
        assertThat(has(paths, "/api/v1/crm/accounts/{accountId}/customer-360", "get")).isTrue();
        assertThat(has(paths, "/api/v1/crm/accounts/{accountId}/master", "get")).isTrue();
        assertThat(has(paths, "/api/v1/crm/accounts/{accountId}/master", "patch")).isTrue();
        assertThat(has(paths, "/api/v1/crm/accounts/{accountId}/addresses", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/accounts/{accountId}/identifiers", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/accounts/{accountId}/relationships", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/accounts/{accountId}/duplicates", "get")).isTrue();
        assertThat(has(paths, "/api/v1/crm/accounts/{sourceAccountId}/merge/{targetAccountId}", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/leads/{leadId}/convert", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/opportunities/{opportunityId}/stage", "patch")).isTrue();
        assertThat(has(paths, "/api/v1/crm/imports/upload", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/imports/{jobId}/run", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/imports/{jobId}/errors.csv", "get")).isTrue();
        assertThat(has(paths, "/api/v1/crm/custom-fields", "post")).isTrue();
        assertThat(has(paths, "/api/v1/crm/custom-fields/values/{entityType}/{entityId}", "put")).isTrue();
        assertThat(has(paths, "/api/v1/crm/custom-fields/search", "get")).isTrue();
        assertThat(has(paths, "/api/v2/crm/accounts", "post")).isTrue();
        assertThat(has(paths, "/api/v2/crm/opportunities/{opportunityId}", "patch")).isTrue();
        assertThat(has(paths, "/api/v2/crm/imports/upload", "post")).isTrue();
        assertThat(has(paths, "/api/v2/crm/contacts/{contactId}/relationships", "post")).isTrue();
        assertThat(has(paths, "/api/v2/crm/accounts/{accountId}/contact-relationships", "get")).isTrue();
    }

    private static boolean has(JsonNode paths, String path, String method) {
        return paths.path(path).has(method);
    }

    private static long count(JsonNode paths, String prefix) {
        long total = 0;
        Iterator<Map.Entry<String, JsonNode>> entries = paths.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            if (prefix != null && !entry.getKey().startsWith(prefix)) continue;
            Iterator<String> fields = entry.getValue().fieldNames();
            while (fields.hasNext()) if (METHODS.contains(fields.next())) total++;
        }
        return total;
    }
}
