package com.sanad.platform.hr.api.v2.recruitment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G1 T10 RED — §7/§13 hire-conversion replay API contract.
 *
 * <p>The conversion endpoint is explicitly idempotent: every request carries
 * Idempotency-Key, and a replay returns HTTP 200 with the original result and
 * replayed=true. OpenAPI must document those semantics rather than leaving
 * them as implementation-only behavior.</p>
 *
 * <p>The assertions are deliberately OpenAPI-reference aware: Springdoc may
 * represent parameters/responses/schemas inline or through component $refs.
 * The contract cares about semantics, not serialization shape.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrRecruitmentIdempotencyApiContractTest {

    private static final String PATH =
            "/api/v2/hr/recruitment/offers/{id}/hire-conversion";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void hireConversion_openApiDocumentsRequiredIdempotencyKeyAndReplay200() throws Exception {
        JsonNode root = runtimeOpenApi();
        JsonNode operation = root.path("paths").path(PATH).path("post");

        assertThat(operation.isMissingNode())
                .as("§13 hire-conversion POST must exist")
                .isFalse();

        JsonNode parameters = operation.path("parameters");
        JsonNode idempotency = StreamSupport.stream(parameters.spliterator(), false)
                .map(p -> resolveRef(root, p))
                .filter(p -> "header".equalsIgnoreCase(p.path("in").asText()))
                .filter(p -> "Idempotency-Key".equalsIgnoreCase(p.path("name").asText()))
                .findFirst()
                .orElse(null);

        assertThat(idempotency)
                .as("hire-conversion must declare the canonical Idempotency-Key header")
                .isNotNull();
        assertThat(idempotency.path("required").asBoolean())
                .as("Idempotency-Key is mandatory for the governed conversion command")
                .isTrue();

        JsonNode ok = resolveRef(root, operation.path("responses").path("200"));
        assertThat(ok.isMissingNode())
                .as("first execution and replay both return the successful 200 contract")
                .isFalse();

        String documentation = (operation.path("summary").asText("") + " "
                + operation.path("description").asText("") + " "
                + ok.path("description").asText("")).toLowerCase(Locale.ROOT);

        assertThat(documentation)
                .as("OpenAPI must explicitly document idempotent replay semantics")
                .contains("replay")
                .contains("idempot");

        JsonNode schema = ok.path("content").path("application/json").path("schema");
        JsonNode replayed = findProperty(root, schema, "replayed");
        assertThat(replayed)
                .as("ConversionResult schema must expose replayed:boolean, inline or via $ref")
                .isNotNull();
        assertThat(resolveRef(root, replayed).path("type").asText())
                .as("ConversionResult.replayed must be boolean")
                .isEqualTo("boolean");
    }

    private JsonNode findProperty(JsonNode root, JsonNode schema, String propertyName) {
        JsonNode resolved = resolveRef(root, schema);
        JsonNode property = resolved.path("properties").path(propertyName);
        if (!property.isMissingNode()) {
            return property;
        }
        for (String composition : new String[]{"allOf", "oneOf", "anyOf"}) {
            JsonNode variants = resolved.path(composition);
            if (variants.isArray()) {
                for (JsonNode variant : variants) {
                    JsonNode nested = findProperty(root, variant, propertyName);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }
        return null;
    }

    private JsonNode resolveRef(JsonNode root, JsonNode node) {
        if (node != null && node.hasNonNull("$ref")) {
            String ref = node.path("$ref").asText();
            if (ref.startsWith("#/")) {
                return root.at(ref.substring(1));
            }
        }
        return node == null ? objectMapper.getNodeFactory().missingNode() : node;
    }

    private JsonNode runtimeOpenApi() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
