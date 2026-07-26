package com.sanad.platform.crm.pagination;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runtime OpenAPI corrections for fail-closed CRM contract semantics. */
@Configuration
public class CrmOpenApiSemanticParityConfiguration {

    @Bean
    OpenApiCustomizer crmIdempotencyHeaderRequirednessCustomizer() {
        return openApi -> {
            if (openApi == null || openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, item) -> {
                if (path == null || !path.startsWith("/api/v2/crm") || item == null) return;
                markRequired(item.getPost());
            });
        };
    }

    private static void markRequired(Operation operation) {
        if (operation == null || operation.getParameters() == null) return;
        for (Parameter parameter : operation.getParameters()) {
            if (parameter != null
                    && "header".equalsIgnoreCase(parameter.getIn())
                    && "Idempotency-Key".equalsIgnoreCase(parameter.getName())) {
                parameter.setRequired(true);
            }
        }
    }
}
