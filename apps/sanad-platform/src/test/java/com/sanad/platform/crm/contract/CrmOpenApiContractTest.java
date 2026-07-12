package com.sanad.platform.crm.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRM-G2 Contract Test — OpenAPI Conformity (AC-11).
 * <p>
 * Verifies that the committed OpenAPI artifact at
 * {@code docs/crm/contracts/openapi/crm-openapi.json}:
 *   - Parses as valid JSON.
 *   - Has OpenAPI version 3.x.
 *   - Has at least one path per CRM domain (accounts, contacts, leads,
 *     opportunities, activities, pipelines, imports, custom-fields,
 *     timeline).
 *   - Has the standard reusable parameters (Limit, Cursor, Sort,
 *     Direction, IfMatch, IdempotencyKey).
 *   - Has the standard schemas (Meta, Page, ErrorResponse, FieldError).
 *   - Every path operation references at least one response.
 *   - Has a security scheme (BearerAuth).
 * <p>
 * The OpenAPI drift check (regenerate → diff) is performed by the
 * {@code crm-api-contract-validation.yml} workflow on CI.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmOpenApiContractTest {

    private static final Path OPENAPI_PATH =
            Path.of("docs/crm/contracts/openapi/crm-openapi.json");

    private JsonNode loadSpec() throws Exception {
        assertTrue(Files.exists(OPENAPI_PATH),
                "OpenAPI artifact must exist at " + OPENAPI_PATH);
        return new ObjectMapper().readTree(Files.readAllBytes(OPENAPI_PATH));
    }

    @Test
    void openApiSpecParsesAsValidJson() throws Exception {
        JsonNode spec = loadSpec();
        assertNotNull(spec);
        assertNotNull(spec.get("openapi"));
        assertTrue(spec.get("openapi").asText().startsWith("3."),
                "OpenAPI version must be 3.x; got: " + spec.get("openapi"));
    }

    @Test
    void specCoversEveryCrmDomain() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode paths = spec.get("paths");
        assertNotNull(paths);
        // Each domain must have at least one path.
        String[] domainPrefixes = {
                "/accounts", "/contacts", "/leads", "/opportunities",
                "/activities", "/pipelines", "/imports", "/custom-fields",
                "/timeline"
        };
        for (String prefix : domainPrefixes) {
            boolean found = false;
            Iterator<String> it = paths.fieldNames();
            while (it.hasNext()) {
                if (it.next().startsWith(prefix)) { found = true; break; }
            }
            assertTrue(found, "OpenAPI spec is missing paths for domain: " + prefix);
        }
    }

    @Test
    void specDefinesReusablePaginationParameters() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode params = spec.path("components").path("parameters");
        for (String name : new String[]{"Limit", "Cursor", "Sort", "Direction", "IfMatch", "IdempotencyKey"}) {
            assertNotNull(params.get(name),
                    "OpenAPI spec is missing reusable parameter: " + name);
        }
    }

    @Test
    void specDefinesStandardSchemas() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode schemas = spec.path("components").path("schemas");
        for (String name : new String[]{"Meta", "Page", "ErrorResponse", "FieldError", "CreateAccountRequest", "AccountResponse"}) {
            assertNotNull(schemas.get(name),
                    "OpenAPI spec is missing schema: " + name);
        }
    }

    @Test
    void everyPathOperationHasAtLeastOneResponse() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode paths = spec.get("paths");
        Iterator<Map.Entry<String, JsonNode>> it = paths.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> path = it.next();
            Iterator<Map.Entry<String, JsonNode>> ops = path.getValue().fields();
            while (ops.hasNext()) {
                Map.Entry<String, JsonNode> op = ops.next();
                String method = op.getKey();
                // Skip non-HTTP-method keys like "parameters" or "summary".
                if (!method.equals("get") && !method.equals("post") && !method.equals("patch")
                        && !method.equals("put") && !method.equals("delete")) continue;
                JsonNode responses = op.getValue().get("responses");
                assertNotNull(responses, "Path " + path.getKey() + " " + method + " has no responses");
                assertTrue(responses.size() > 0,
                        "Path " + path.getKey() + " " + method + " must define at least one response");
            }
        }
    }

    @Test
    void specDefinesBearerAuthSecurityScheme() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode schemes = spec.path("components").path("securitySchemes");
        assertNotNull(schemes.get("BearerAuth"),
                "OpenAPI spec must define the BearerAuth security scheme");
        assertEquals("http", schemes.get("BearerAuth").get("type").asText());
        assertEquals("bearer", schemes.get("BearerAuth").get("scheme").asText());
    }

    @Test
    void accountCreateResponseIs201() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode postResponses = spec.path("paths").path("/accounts").path("post").path("responses");
        assertNotNull(postResponses.get("201"),
                "POST /accounts must declare a 201 response (HTTP success status for create)");
    }

    @Test
    void accountPatchRequiresIfMatchHeader() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode ifMatch = spec.path("paths").path("/accounts/{accountId}").path("patch")
                .path("parameters");
        boolean foundIfMatchRequired = false;
        for (JsonNode param : ifMatch) {
            if ("If-Match".equals(param.path("name").asText())
                    && "header".equals(param.path("in").asText())
                    && param.path("required").asBoolean(false)) {
                foundIfMatchRequired = true;
                break;
            }
        }
        assertTrue(foundIfMatchRequired,
                "PATCH /accounts/{accountId} must declare If-Match as a required header");
    }

    @Test
    void paginationParametersAreBounded() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode limit = spec.path("components").path("parameters").path("Limit").path("schema");
        assertEquals(1, limit.path("minimum").asInt(), "Limit minimum must be 1");
        assertEquals(200, limit.path("maximum").asInt(), "Limit maximum must be 200");
        assertEquals(50, limit.path("default").asInt(), "Limit default must be 50");
    }
}
