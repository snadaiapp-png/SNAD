package com.sanad.platform.executive.api;

import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformOperationsTenantManagementContractTest {

    @Test
    void tenantUpdateEndpointMustExistAndRequireExecutiveManage() {
        Method update = Arrays.stream(PlatformOperationsCommandController.class.getDeclaredMethods())
                .filter(method -> {
                    PatchMapping mapping = method.getAnnotation(PatchMapping.class);
                    return mapping != null && Arrays.asList(mapping.value()).contains("/tenants/{tenantId}");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("PATCH /tenants/{tenantId} update endpoint is missing"));

        RequireCapability capability = update.getAnnotation(RequireCapability.class);
        assertThat(capability).isNotNull();
        assertThat(capability.value()).isEqualTo("EXECUTIVE_MANAGE");
    }
}
