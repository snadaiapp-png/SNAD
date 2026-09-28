package com.sanad.platform.security.api;

import com.sanad.platform.security.authorization.RequireCapability;
import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCredentialReconciliationContractTest {

    @Test
    void exposesHiddenUserWriteGatedCredentialReconciliationEndpoint() {
        assertThat(AdminCredentialReconciliationController.class.getAnnotation(Hidden.class))
                .as("Recovery-only controller must stay out of the public OpenAPI surface")
                .isNotNull();

        Method endpoint = Arrays.stream(AdminCredentialReconciliationController.class.getDeclaredMethods())
                .filter(method -> {
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping != null
                            && Arrays.asList(mapping.value())
                            .contains("/admin-reconcile-credential/{userId}");
                })
                .findFirst()
                .orElse(null);

        assertThat(endpoint)
                .as("Recovery controller must expose the governed credential reconciliation endpoint")
                .isNotNull();

        RequireCapability capability = endpoint.getAnnotation(RequireCapability.class);
        assertThat(capability)
                .as("Credential reconciliation must remain capability-gated")
                .isNotNull();
        assertThat(capability.value()).isEqualTo("USER.WRITE");
    }
}
