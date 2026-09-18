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
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrRecruitmentIdempotencyApiContractTest {

    private static final String PATH =
            "/hr/api/v2/recruitment/offers/{id}/hire-conversion";

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

        JsonNode ok = operation.path("responses").path("200");
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

        assertThat(ok.toString())
                .as("ConversionResult schema must expose replayed:boolean")
                .contains("\"replayed\"");
    }

    private JsonNode runtimeOpenApi() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
