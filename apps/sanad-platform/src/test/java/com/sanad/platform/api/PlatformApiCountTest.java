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
 * and that CRM-008B ownership operations are correctly counted, secured, and
 * scoped.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PlatformApiCountTest {
    private static final Set<String> METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    /** Ownership path prefixes (relative to /api/v2/crm). */
    private static final Set<String> OWNERSHIP_PREFIXES = Set.of(
            "/teams", "/queues", "/territories", "/assignment-rules",
            "/assignments", "/ownership-history", "/transfers", "/my-work");

    /** Expected counts. */
    private static final long EXPECTED_CRM_V1_OPS = 84;
    private static final long EXPECTED_CRM_V2_OPS = 133;  // 95 baseline + 38 ownership
    private static final long EXPECTED_TOTAL_OPS = 308;   // 270 baseline + 38 ownership
    private static final long EXPECTED_OWNERSHIP_PATHS = 28;
    private static final long EXPECTED_OWNERSHIP_OPS = 38;

    /** Committed OpenAPI artifact path. */
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

        // Per-prefix counts (total platform surface)
        assertThat(count(paths, "/api/v1/users")).isEqualTo(9);
        assertThat(count(paths, "/api/v1/access")).isEqualTo(20);
        assertThat(count(paths, "/api/v1/control-plane")).isEqualTo(35);
        assertThat(count(paths, "/api/v1/crm")).isEqualTo(EXPECTED_CRM_V1_OPS);
        assertThat(count(paths, "/api/v2/crm")).isEqualTo(EXPECTED_CRM_V2_OPS);
        assertThat(count(paths, "/api/v1/business-process-e2e")).isEqualTo(2);
        assertThat(count(paths, null)).isEqualTo(EXPECTED_TOTAL_OPS);

        // Specific path assertions (unchanged from before)
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
        assertThat(has(paths, "/api/v1/business-process-e2e/{processCode}/execute", "post")).isTrue();
        assertThat(has(paths, "/api/v1/business-process-e2e/runs/{runId}", "get")).isTrue();
    }

    /**
     * CRM-008B ownership sub-surface: exactly 28 paths and 38 operations,
     * all under /api/v2/crm.
     */
    @Test
    void ownershipApiSurfaceMatchesApprovedContract() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).path("paths");

        // Count ownership paths (under /api/v2/crm + ownership prefix)
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

    /**
     * No public endpoint for CRM.TRANSFER.EXECUTE — it is internal-only
     * (workflow callback, never exposed to human callers).
     *
     * <p>The transfer API exposes: list, create, submit, approve, cancel.
     * The "execute" operation is NOT a public HTTP endpoint — it is an
     * internal service-level call invoked by the workflow engine after
     * approval. This test ensures no CRM transfer path containing
     * "execute" appears in either the runtime or committed OpenAPI.</p>
     *
     * <p>Note: the business-process E2E endpoint
     * {@code /api/v1/business-process-e2e/{processCode}/execute} is a
     * legitimate endpoint and is NOT a CRM.TRANSFER.EXECUTE endpoint.
     * This test only checks CRM transfer paths.</p>
     */
    @Test
    void transferExecuteIsNotPubliclyExposed() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).path("paths");

        for (Iterator<String> it = paths.fieldNames(); it.hasNext(); ) {
            String path = it.next();
            // Only check CRM transfer paths — the business-process E2E
            // execute endpoint is legitimate and out of scope.
            if (!path.startsWith("/api/v2/crm/transfers")) continue;
            assertThat(path.toLowerCase().endsWith("/execute"))
                    .as("No public CRM.TRANSFER.EXECUTE endpoint: %s", path)
                    .isFalse();
        }

        // Also check the committed OpenAPI (CRM transfer paths only)
        JsonNode committed = objectMapper.readTree(Files.readAllBytes(COMMITTED_OPENAPI));
        JsonNode committedPaths = committed.path("paths");
        for (Iterator<String> it = committedPaths.fieldNames(); it.hasNext(); ) {
            String path = it.next();
            // Committed OpenAPI uses relative paths under /api/v2/crm server
            if (!path.startsWith("/transfers")) continue;
            assertThat(path.toLowerCase().endsWith("/execute"))
                    .as("No public CRM.TRANSFER.EXECUTE endpoint in committed OpenAPI: %s", path)
                    .isFalse();
        }
    }

    /**
     * Runtime OpenAPI and committed CRM contract are semantically consistent
     * for the ownership sub-surface.
     */
    @Test
    void runtimeMatchesCommittedOwnershipContract() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode runtimePaths = objectMapper.readTree(body).path("paths");

        JsonNode committed = objectMapper.readTree(Files.readAllBytes(COMMITTED_OPENAPI));
        JsonNode committedPaths = committed.path("paths");

        // Every committed ownership path must exist in runtime under /api/v2/crm
        for (Iterator<String> it = committedPaths.fieldNames(); it.hasNext(); ) {
            String committedPath = it.next();
            if (!isOwnershipPath(committedPath)) continue;
            String runtimePath = "/api/v2/crm" + committedPath;
            assertThat(runtimePaths.has(runtimePath))
                    .as("Runtime missing committed ownership path: %s", runtimePath)
                    .isTrue();
        }

        // Committed CRM totals: 100 paths, 133 operations
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
                .isEqualTo(100);
        assertThat(committedOpCount)
                .as("Committed CRM OpenAPI operation count")
                .isEqualTo(133);
    }

    // ============================================================
    // Helpers
    // ============================================================

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
