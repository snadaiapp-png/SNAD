package com.sanad.platform.security.api;

import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCredentialReconciliationContractTest {

    @Test
    void exposesUserWriteGatedAdministrativeCredentialReconciliationEndpoint() {
        Method endpoint = Arrays.stream(AuthController.class.getDeclaredMethods())
                .filter(method -> {
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping != null
                            && Arrays.asList(mapping.value())
                            .contains("/admin-reconcile-credential/{userId}");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "AuthController must expose /admin-reconcile-credential/{userId}"));

        RequireCapability capability = endpoint.getAnnotation(RequireCapability.class);
        assertThat(capability)
                .as("credential reconciliation must remain capability-gated")
                .isNotNull();
        assertThat(capability.value()).isEqualTo("USER.WRITE");
    }
}
