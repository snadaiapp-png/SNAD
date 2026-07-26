package com.sanad.platform.crm.pagination;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrmOpenApiRuntimeSemanticParityTest {

    @Test
    void marksEveryDeclaredCrmPostIdempotencyHeaderAsRequired() {
        Operation createAccount = new Operation().parameters(List.of(
                new Parameter().name("Idempotency-Key").in("header").required(false),
                new Parameter().name("X-Request-ID").in("header").required(false)));
        Operation readAccount = new Operation().parameters(List.of(
                new Parameter().name("X-Request-ID").in("header").required(false)));
        Operation unrelatedCreate = new Operation().parameters(List.of(
                new Parameter().name("Idempotency-Key").in("header").required(false)));

        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v2/crm/accounts", new PathItem().post(createAccount).get(readAccount))
                .addPathItem("/api/v1/orders", new PathItem().post(unrelatedCreate)));

        new CrmOpenApiSemanticParityConfiguration()
                .crmIdempotencyHeaderRequirednessCustomizer()
                .customise(openApi);

        assertThat(required(createAccount, "Idempotency-Key")).isTrue();
        assertThat(required(createAccount, "X-Request-ID")).isFalse();
        assertThat(required(readAccount, "Idempotency-Key")).isNull();
        assertThat(required(unrelatedCreate, "Idempotency-Key")).isFalse();
    }

    private Boolean required(Operation operation, String name) {
        if (operation == null || operation.getParameters() == null) return null;
        return operation.getParameters().stream()
                .filter(parameter -> name.equals(parameter.getName()))
                .map(Parameter::getRequired)
                .findFirst()
                .orElse(null);
    }
}
