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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform API count and contract test.
 *
 * <p>Verifies that the runtime OpenAPI ({@code /v3/api-docs}) matches the
 * committed CRM contract ({@code docs/crm/contracts/openapi/crm-openapi.json})
 * and that CRM-008B ownership and CRM-009 integration operations are counted,
 * secured, and scoped correctly.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PlatformApiCountTest {
    private static final Set<String> METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    private static final Set<String> OWNERSHIP_PREFIXES = Set.of(
            "/teams", "/queues", "/territories", "/assignment-rules",
            "/assignments", "/ownership-history", "/transfers", "/my-work");

    private static final long EXPECTED_CRM_V1_OPS = 84;
    /** 95 CRM baseline + 38 ownership + 7 CRM-009 public integration operations. */
    private static final long EXPECTED_CRM_V2_OPS = 140;
    /** Previous total 312 + 3 public workflow operations + 1 internal callback. */
    private static final long EXPECTED_TOTAL_OPS = 316;
    private static final long EXPECTED_OWNERSHIP_PATHS = 28;
    private static final long EXPECTED_OWNERSHIP_OPS = 38;
    private static final long EXPECTED_COMMITTED_CRM_PATHS = 107;
    private static final long EXPECTED_COMMITTED_CRM_OPS = 140;

    private static final Path COMMITTED_OPENAPI =
            Path.of(System.getProperty("user.dir")).getParent().getParent()
                    .resolve("docs/crm/contracts/openapi/crm-openapi.json");

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
        assertThat(count(paths, "/api/v1/crm")).isEqualTo(EXPECTED_CRM_V1_OPS);
        assertThat(count(paths, "/api/v2/crm")).isEqualTo(EXPECTED_CRM_V2_OPS);
        assertThat(count(paths, "/api/v1/business-process-e2e")).isEqualTo(2);
        assertThat(count(paths, null)).isEqualTo(EXPECTED_TOTAL_OPS);

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
        assertThat(has(paths, "/api/v2/crm/accounts/{accountId}/addresses", "get")).isTrue();
        assertThat(has(paths, "/api/v2/crm/contacts/{contactId}/communication-methods", "get")).isTrue();
        assertThat(has(paths, "/api/v2/crm/integrations/workflows", "post")).isTrue();
        assertThat(has(paths, "/api/v2/crm/integrations/workflows/{requestId}", "get")).isTrue();
        assertThat(has(paths, "/api/v2/crm/integrations/workflows/{requestId}/cancel", "post")).isTrue();
        assertThat(has(paths, "/internal/crm/integrations/workflows/callback", "post")).isTrue();
        assertThat(has(paths, "/api/v1/business-process-e2e/{processCode}/execute", "post")).isTrue();
        assertThat(has(paths, "/api/v1/business-process-e2e/runs/{runId}", "get")).isTrue();
    }

    @Test
    void ownershipApiSurfaceMatchesApprovedContract() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).path("paths");

        long ownershipPaths = 0;
        long ownershipOps = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String path = entry.getKey();
            if (!path.startsWith("/api/v2/crm")) continue;
            String relative = path.substring("/api/v2/crm".length());
            if (!isOwnershipPath(relative)) continue;
            ownershipPaths++;
            Iterator<String> fields = entry.getValue().fieldNames();
            while (fields.hasNext()) {
                if (METHODS.contains(fields.next())) ownershipOps++;
            }
        }

        assertThat(ownershipPaths)
                .as("CRM-008B ownership path count (expected %d)", EXPECTED_OWNERSHIP_PATHS)
                .isEqualTo(EXPECTED_OWNERSHIP_PATHS);
        assertThat(ownershipOps)
                .as("CRM-008B ownership operation count (expected %d)", EXPECTED_OWNERSHIP_OPS)
                .isEqualTo(EXPECTED_OWNERSHIP_OPS);
    }

    @Test
    void transferExecuteIsNotPubliclyExposed() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).path("paths");

        for (Iterator<String> it = paths.fieldNames(); it.hasNext(); ) {
            String path = it.next();
            if (!path.startsWith("/api/v2/crm/transfers")) continue;
            assertThat(path.toLowerCase().endsWith("/execute"))
                    .as("No public CRM.TRANSFER.EXECUTE endpoint: %s", path)
                    .isFalse();
        }

        JsonNode committed = objectMapper.readTree(Files.readAllBytes(COMMITTED_OPENAPI));
        JsonNode committedPaths = committed.path("paths");
        for (Iterator<String> it = committedPaths.fieldNames(); it.hasNext(); ) {
            String path = it.next();
            if (!path.startsWith("/transfers")) continue;
            assertThat(path.toLowerCase().endsWith("/execute"))
                    .as("No public CRM.TRANSFER.EXECUTE endpoint in committed OpenAPI: %s", path)
                    .isFalse();
        }
    }

    @Test
    void runtimeMatchesCommittedOwnershipContract() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode runtimePaths = objectMapper.readTree(body).path("paths");

        JsonNode committed = objectMapper.readTree(Files.readAllBytes(COMMITTED_OPENAPI));
        JsonNode committedPaths = committed.path("paths");

        for (Iterator<String> it = committedPaths.fieldNames(); it.hasNext(); ) {
            String committedPath = it.next();
            if (!isOwnershipPath(committedPath)) continue;
            String runtimePath = "/api/v2/crm" + committedPath;
            assertThat(runtimePaths.has(runtimePath))
                    .as("Runtime missing committed ownership path: %s", runtimePath)
                    .isTrue();
        }

        int committedPathCount = 0;
        int committedOpCount = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = committedPaths.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            committedPathCount++;
            Iterator<String> fields = entry.getValue().fieldNames();
            while (fields.hasNext()) {
                if (METHODS.contains(fields.next())) committedOpCount++;
            }
        }
        assertThat(committedPathCount)
                .as("Committed CRM OpenAPI path count")
                .isEqualTo(EXPECTED_COMMITTED_CRM_PATHS);
        assertThat(committedOpCount)
                .as("Committed CRM OpenAPI operation count")
                .isEqualTo(EXPECTED_COMMITTED_CRM_OPS);
    }

    private static boolean isOwnershipPath(String relativePath) {
        for (String prefix : OWNERSHIP_PREFIXES) {
            if (relativePath.equals(prefix) || relativePath.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
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
