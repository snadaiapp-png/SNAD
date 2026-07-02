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
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).path("paths");

        assertThat(count(paths, "/api/v1/users")).isEqualTo(9);
        assertThat(count(paths, "/api/v1/access")).isEqualTo(20);
        assertThat(count(paths, "/api/v1/control-plane")).isEqualTo(32);
        assertThat(count(paths, "/api/v1/crm")).isEqualTo(35);
        assertThat(count(paths, null)).isEqualTo(123);
        assertThat(paths.path("/api/v1/auth/change-credential").has("post")).isTrue();
        assertThat(paths.path("/api/v1/access/evaluation").has("get")).isTrue();
        assertThat(paths.path("/api/v1/control-plane/dashboard").has("get")).isTrue();
        assertThat(paths.path("/api/v1/control-plane/tenants").has("post")).isTrue();
        assertThat(paths.path("/api/v1/control-plane/plans").has("post")).isTrue();
        assertThat(paths.path("/api/v1/control-plane/subscriptions/{subscriptionId}/change-plan").has("patch")).isTrue();
        assertThat(paths.path("/api/v1/control-plane/billing/invoices/{invoiceId}/mark-paid").has("post")).isTrue();
        assertThat(paths.path("/api/v1/control-plane/tenants/{tenantId}/organizations/{organizationId}/memberships").has("post")).isTrue();
        assertThat(paths.path("/api/v1/crm/dashboard").has("get")).isTrue();
        assertThat(paths.path("/api/v1/crm/accounts/{accountId}/customer-360").has("get")).isTrue();
        assertThat(paths.path("/api/v1/crm/contacts/{contactId}/restore").has("patch")).isTrue();
        assertThat(paths.path("/api/v1/crm/leads/{leadId}/convert").has("post")).isTrue();
        assertThat(paths.path("/api/v1/crm/opportunities/{opportunityId}/stage").has("patch")).isTrue();
        assertThat(paths.path("/api/v1/crm/activities/{activityId}/complete").has("patch")).isTrue();
        assertThat(paths.path("/api/v1/crm/imports").has("post")).isTrue();
        assertThat(paths.path("/api/v1/crm/custom-fields").has("post")).isTrue();
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
