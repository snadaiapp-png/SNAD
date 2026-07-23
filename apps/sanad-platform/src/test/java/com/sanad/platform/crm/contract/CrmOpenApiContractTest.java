package com.sanad.platform.crm.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CRM API contract test for the committed, runtime-filtered OpenAPI artifact. */
class CrmOpenApiContractTest {

    private static final int EXPECTED_PATHS = 102;
    private static final int EXPECTED_OPERATIONS = 135;
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Path OPENAPI_PATH =
            Path.of(System.getProperty("user.dir")).getParent().getParent()
                    .resolve("docs/crm/contracts/openapi/crm-openapi.json");

    private JsonNode loadSpec() throws Exception {
        assertTrue(Files.exists(OPENAPI_PATH),
                "OpenAPI artifact must exist at " + OPENAPI_PATH);
        return new ObjectMapper().readTree(Files.readAllBytes(OPENAPI_PATH));
    }

    @Test
    void openApiSpecParsesAndMatchesTheApprovedSurface() throws Exception {
        JsonNode spec = loadSpec();
        assertNotNull(spec);
        assertTrue(spec.path("openapi").asText().startsWith("3."));
        assertEquals(EXPECTED_PATHS, spec.path("paths").size(), "Approved CRM path count changed");

        int operations = 0;
        Iterator<JsonNode> paths = spec.path("paths").elements();
        while (paths.hasNext()) {
            Iterator<String> methods = paths.next().fieldNames();
            while (methods.hasNext()) {
                if (HTTP_METHODS.contains(methods.next())) operations++;
            }
        }
        assertEquals(EXPECTED_OPERATIONS, operations, "Approved CRM operation count changed");
        assertEquals("/api/v2/crm", spec.path("servers").path(0).path("url").asText());
    }

    @Test
    void specCoversEveryCrmDomain() throws Exception {
        JsonNode paths = loadSpec().path("paths");
        String[] domainPrefixes = {
                "/accounts", "/contacts", "/leads", "/opportunities",
                "/activities", "/pipelines", "/imports", "/custom-fields",
                "/timeline", "/addresses", "/communication-methods",
                "/teams", "/queues", "/territories", "/assignment-rules",
                "/assignments", "/ownership-history", "/transfers", "/my-work"
        };
        for (String prefix : domainPrefixes) {
            boolean found = false;
            Iterator<String> names = paths.fieldNames();
            while (names.hasNext()) {
                if (names.next().startsWith(prefix)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "OpenAPI spec is missing paths for domain: " + prefix);
        }
    }

    @Test
    void everyDeclaredLimitIsBounded() throws Exception {
        JsonNode paths = loadSpec().path("paths");
        int boundedLimits = 0;
        Iterator<Map.Entry<String, JsonNode>> pathIterator = paths.fields();
        while (pathIterator.hasNext()) {
            Map.Entry<String, JsonNode> path = pathIterator.next();
            Iterator<Map.Entry<String, JsonNode>> operations = path.getValue().fields();
            while (operations.hasNext()) {
                Map.Entry<String, JsonNode> operation = operations.next();
                if (!HTTP_METHODS.contains(operation.getKey())) continue;
                for (JsonNode parameter : operation.getValue().path("parameters")) {
                    if (!"limit".equals(parameter.path("name").asText())
                            || !"query".equals(parameter.path("in").asText())) continue;
                    JsonNode schema = parameter.path("schema");
                    assertEquals(1, schema.path("minimum").asInt(),
                            path.getKey() + " limit minimum must be 1");
                    assertEquals(200, schema.path("maximum").asInt(),
                            path.getKey() + " limit maximum must be 200");
                    assertEquals(50, schema.path("default").asInt(),
                            path.getKey() + " limit default must be 50");
                    boundedLimits++;
                }
            }
        }
        assertTrue(boundedLimits >= 14, "Expected pagination on the CRM list operations");
    }

    @Test
    void specDefinesCoreAndCrm007GeneratedSchemas() throws Exception {
        JsonNode schemas = loadSpec().path("components").path("schemas");
        for (String name : new String[]{
                "CreateAccountRequest", "AccountResponse", "SingleResponseAccountResponse",
                "CreateContactRequest", "ContactResponse", "ListResponseContactResponse",
                "CreateAddressRequest", "AddressResponse", "SingleResponseAddressResponse",
                "CreateCommunicationMethodRequest", "CommunicationMethodResponse",
                "SingleResponseCommunicationMethodResponse"}) {
            assertNotNull(schemas.get(name), "OpenAPI spec is missing generated schema: " + name);
        }
    }

    @Test
    void everyPathOperationHasAtLeastOneResponse() throws Exception {
        JsonNode paths = loadSpec().path("paths");
        Iterator<Map.Entry<String, JsonNode>> pathIterator = paths.fields();
        while (pathIterator.hasNext()) {
            Map.Entry<String, JsonNode> path = pathIterator.next();
            Iterator<Map.Entry<String, JsonNode>> operations = path.getValue().fields();
            while (operations.hasNext()) {
                Map.Entry<String, JsonNode> operation = operations.next();
                if (!HTTP_METHODS.contains(operation.getKey())) continue;
                JsonNode responses = operation.getValue().path("responses");
                assertTrue(responses.isObject() && !responses.isEmpty(),
                        path.getKey() + " " + operation.getKey() + " must define a response");
            }
        }
    }

    @Test
    void everyOperationRequiresBearerAuthentication() throws Exception {
        JsonNode spec = loadSpec();
        JsonNode scheme = spec.path("components").path("securitySchemes").path("BearerAuth");
        assertEquals("http", scheme.path("type").asText());
        assertEquals("bearer", scheme.path("scheme").asText());

        Iterator<Map.Entry<String, JsonNode>> pathIterator = spec.path("paths").fields();
        while (pathIterator.hasNext()) {
            Map.Entry<String, JsonNode> path = pathIterator.next();
            Iterator<Map.Entry<String, JsonNode>> operations = path.getValue().fields();
            while (operations.hasNext()) {
                Map.Entry<String, JsonNode> operation = operations.next();
                if (!HTTP_METHODS.contains(operation.getKey())) continue;
                boolean bearer = false;
                for (JsonNode requirement : operation.getValue().path("security")) {
                    if (requirement.has("BearerAuth")) {
                        bearer = true;
                        break;
                    }
                }
                assertTrue(bearer,
                        path.getKey() + " " + operation.getKey() + " must require BearerAuth");
            }
        }
    }

    @Test
    void createResponsesUse201() throws Exception {
        JsonNode paths = loadSpec().path("paths");
        for (String path : new String[]{
                "/accounts", "/accounts/{accountId}/addresses", "/contacts/{contactId}/addresses",
                "/accounts/{accountId}/communication-methods", "/contacts/{contactId}/communication-methods",
                "/addresses/import", "/communication-methods/import",
                "/teams", "/queues", "/territories", "/assignment-rules", "/transfers"}) {
            JsonNode responses = paths.path(path).path("post").path("responses");
            assertNotNull(responses.get("201"), "POST " + path + " must declare 201 Created");
            assertTrue(responses.get("200") == null,
                    "POST " + path + " must not advertise 200 when runtime returns 201");
        }
    }

    @Test
    void versionedCrm007MutationsRequireIfMatch() throws Exception {
        JsonNode paths = loadSpec().path("paths");
        String[] mutationPaths = {
                "/addresses/{addressId}", "/addresses/{addressId}/primary",
                "/addresses/{addressId}/archive", "/addresses/{addressId}/reactivate",
                "/communication-methods/{communicationMethodId}",
                "/communication-methods/{communicationMethodId}/preferred",
                "/communication-methods/{communicationMethodId}/verification",
                "/communication-methods/{communicationMethodId}/archive",
                "/communication-methods/{communicationMethodId}/reactivate"
        };
        for (String path : mutationPaths) {
            assertRequiredHeader(paths.path(path).path("patch"), "If-Match", path);
        }
    }

    @Test
    void idempotencyKeysAreRequiredOnGovernedCreates() throws Exception {
        JsonNode paths = loadSpec().path("paths");
        int protectedOperations = 0;
        Iterator<JsonNode> pathIterator = paths.elements();
        while (pathIterator.hasNext()) {
            Iterator<Map.Entry<String, JsonNode>> operations = pathIterator.next().fields();
            while (operations.hasNext()) {
                Map.Entry<String, JsonNode> operation = operations.next();
                if (!HTTP_METHODS.contains(operation.getKey())) continue;
                for (JsonNode parameter : operation.getValue().path("parameters")) {
                    if ("Idempotency-Key".equals(parameter.path("name").asText())) {
                        assertTrue(parameter.path("required").asBoolean(),
                                "Idempotency-Key must be required where declared");
                        protectedOperations++;
                    }
                }
            }
        }
        assertTrue(protectedOperations >= 20,
                "Expected idempotency protection on CRM create/action operations");
        for (String path : new String[]{
                "/accounts/{accountId}/addresses", "/contacts/{contactId}/addresses",
                "/accounts/{accountId}/communication-methods", "/contacts/{contactId}/communication-methods",
                "/addresses/import", "/communication-methods/import",
                "/teams", "/queues", "/territories", "/assignment-rules", "/transfers",
                "/assignments/reassign", "/assignments/bulk-reassign"}) {
            assertRequiredHeader(paths.path(path).path("post"), "Idempotency-Key", path);
        }
    }

    private static void assertRequiredHeader(JsonNode operation, String header, String path) {
        boolean found = false;
        for (JsonNode parameter : operation.path("parameters")) {
            if (header.equals(parameter.path("name").asText())
                    && "header".equals(parameter.path("in").asText())
                    && parameter.path("required").asBoolean()) {
                found = true;
                break;
            }
        }
        assertTrue(found, operation.path("operationId").asText() + " on " + path
                + " must require " + header);
    }
}
