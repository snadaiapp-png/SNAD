package com.sanad.platform.crm.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes the generated platform OpenAPI document reflect the CRM HTTP contract
 * that is already enforced at runtime by the controller and contract services.
 *
 * <p>The platform publishes one aggregate OpenAPI document. The committed CRM
 * contract is deterministically filtered from {@code /api/v2/crm}; therefore
 * these rules are applied only to CRM operations and do not change other
 * platform modules.</p>
 */
@Configuration
public class CrmOpenApiConfiguration {

    static final String CRM_PREFIX = "/api/v2/crm";
    static final String BEARER_AUTH = "BearerAuth";
    private static final Set<String> CREATED_COLLECTION_PATHS = Set.of(
            CRM_PREFIX + "/accounts",
            CRM_PREFIX + "/contacts",
            CRM_PREFIX + "/leads",
            CRM_PREFIX + "/opportunities",
            CRM_PREFIX + "/activities",
            CRM_PREFIX + "/accounts/{accountId}/addresses",
            CRM_PREFIX + "/contacts/{contactId}/addresses",
            CRM_PREFIX + "/accounts/{accountId}/communication-methods",
            CRM_PREFIX + "/contacts/{contactId}/communication-methods",
            CRM_PREFIX + "/addresses/import",
            CRM_PREFIX + "/communication-methods/import");

    @Bean
    OpenAPI crmOpenApiComponents() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("SANAD access token issued by the platform identity service.");
        return new OpenAPI().components(
                new Components().addSecuritySchemes(BEARER_AUTH, bearer));
    }

    @Bean
    OpenApiCustomizer crmContractOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (!path.equals(CRM_PREFIX) && !path.startsWith(CRM_PREFIX + "/")) {
                    return;
                }
                operations(pathItem).forEach((method, operation) -> {
                    requireBearerAuthentication(operation);
                    normalizeContractParameters(operation);
                    normalizeCreationResponse(path, method, operation);
                });
            });
        };
    }

    private static Map<PathItem.HttpMethod, Operation> operations(PathItem pathItem) {
        Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
        return operations == null ? Map.of() : operations;
    }

    private static void requireBearerAuthentication(Operation operation) {
        if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
        }
    }

    private static void normalizeContractParameters(Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            String name = parameter.getName();
            if ("If-Match".equalsIgnoreCase(name)
                    || "Idempotency-Key".equalsIgnoreCase(name)) {
                parameter.setRequired(true);
            }
            if ("limit".equals(name) && "query".equals(parameter.getIn())) {
                @SuppressWarnings("rawtypes")
                Schema schema = parameter.getSchema();
                if (schema == null) {
                    schema = new Schema<>().type("integer").format("int32");
                    parameter.setSchema(schema);
                }
                schema.setMinimum(BigDecimal.ONE);
                schema.setMaximum(BigDecimal.valueOf(200));
                schema.setDefault(50);
            }
        }
    }

    private static void normalizeCreationResponse(
            String path,
            PathItem.HttpMethod method,
            Operation operation) {
        if (method != PathItem.HttpMethod.POST
                || !CREATED_COLLECTION_PATHS.contains(path)
                || operation.getResponses() == null) {
            return;
        }
        // These collection endpoints return 201 through ResponseEntity and
        // idempotency support, which Springdoc cannot infer from the generic
        // ResponseEntity signature alone.
        ApiResponse success = operation.getResponses().get("201");
        if (success == null) {
            success = operation.getResponses().get("200");
        }
        if (success != null) {
            success.setDescription("Created");
            operation.getResponses().addApiResponse("201", success);
            operation.getResponses().remove("200");
        }
    }
}
