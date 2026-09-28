package com.sanad.platform.security.api;

import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCredentialReconciliationContractTest {

    @Test
    void exposesUserWriteGatedAdministrativeCredentialReconciliationEndpoint() {
        Method endpoint = Arrays.stream(AdminCredentialReconciliationController.class.getDeclaredMethods())
                .filter(method -> {
                    PostMapping mapping = method.getAnnotation(PostMapping.class);
                    return mapping != null
                            && Arrays.asList(mapping.value())
                            .contains("/admin-reconcile-credential/{userId}");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "AdminCredentialReconciliationController must expose /admin-reconcile-credential/{userId}"));

        RequireCapability capability = endpoint.getAnnotation(RequireCapability.class);
        assertThat(capability)
                .as("credential reconciliation must remain capability-gated")
                .isNotNull();
        assertThat(capability.value()).isEqualTo("USER.WRITE");
    }

    @Test
    void endpointIsDisabledUnlessRecoveryFlagIsExplicitlyTrue() {
        ConditionalOnProperty condition = AdminCredentialReconciliationController.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("sanad.security.credential-reconciliation");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
