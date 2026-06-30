package com.sanad.platform.api.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 03A — Runtime OpenAPI contract export.
 *
 * <p>Fetches {@code /v3/api-docs} from the running Spring context (springdoc
 * auto-generates the document from the live {@code @RestController} classes
 * and their {@code @Operation/@ApiResponse} annotations). The output is
 * written verbatim to {@code build/api-contract/openapi-current.json}.</p>
 *
 * <p>This file is the ONLY authority for the application's API contract —
 * hand-written YAMLs in {@code docs/api-contracts/} are reference docs
 * only and must be reconciled against this runtime artifact.</p>
 *
 * <p>The compatibility gate ({@code scripts/ci/check-api-contract-compatibility.sh})
 * consumes the produced file and diffs it against the committed baseline
 * {@code docs/api-contracts/openapi-v1-baseline.yaml}.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OpenApiContractExportTest {

    /** Output path — must match the path used by the CI compatibility script. */
    static final Path OUTPUT_PATH =
            Paths.get("build/api-contract/openapi-current.json");

    /** Minimum number of paths expected in the live API. */
    private static final int MIN_PATHS = 15;

    /** Minimum number of schema components expected. */
    private static final int MIN_SCHEMAS = 10;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Export runtime OpenAPI to build/api-contract/openapi-current.json")
    void exportRuntimeOpenApi() throws Exception {
        // 1. Fetch the live document.
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 2. Validate it is structurally an OpenAPI 3 document.
        JsonNode root = objectMapper.readTree(body);
        assertThat(root.path("openapi").asText())
                .as("openapi version field must be present")
                .startsWith("3.");
        JsonNode paths = root.path("paths");
        assertThat(paths.isObject())
                .as("paths must be a JSON object")
                .isTrue();
        assertThat(paths.size())
                .as("paths must contain at least %d entries", MIN_PATHS)
                .isGreaterThanOrEqualTo(MIN_PATHS);

        JsonNode schemas = root.path("components").path("schemas");
        assertThat(schemas.isObject())
                .as("components.schemas must be a JSON object")
                .isTrue();
        assertThat(schemas.size())
                .as("schemas must contain at least %d entries", MIN_SCHEMAS)
                .isGreaterThanOrEqualTo(MIN_SCHEMAS);

        // 3. Sanity check: every controller path begins with /api/v1 or /actuator.
        Iterator<Map.Entry<String, JsonNode>> it = paths.fields();
        while (it.hasNext()) {
            String p = it.next().getKey();
            assertThat(p.startsWith("/api/v1/") || p.startsWith("/api/v1")
                    || p.startsWith("/actuator") || p.equals("/v3/api-docs"))
                    .as("unexpected path prefix: %s", p)
                    .isTrue();
        }

        // 4. Write to disk, pretty-printed for stable diffs.
        Files.createDirectories(OUTPUT_PATH.getParent());
        ObjectMapper pretty = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        Files.writeString(OUTPUT_PATH, pretty.writeValueAsString(root));

        // 5. Final assertion: file exists and is non-empty.
        assertThat(Files.exists(OUTPUT_PATH))
                .as("openapi-current.json must exist after export")
                .isTrue();
        assertThat(Files.size(OUTPUT_PATH))
                .as("openapi-current.json must be non-empty")
                .isGreaterThan(0L);
    }
}
