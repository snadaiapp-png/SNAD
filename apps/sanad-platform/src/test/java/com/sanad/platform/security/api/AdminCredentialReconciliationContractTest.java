package com.sanad.platform.security.api;

import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCredentialReconciliationContractTest {

    @Test
    void exposesTenantScopedAdminCredentialReconciliationEndpointGuardedByUserWrite() {
        Method method = Arrays.stream(AuthController.class.getDeclaredMethods())
                .filter(candidate -> {
                    PostMapping mapping = candidate.getAnnotation(PostMapping.class);
                    return mapping != null && Arrays.asList(mapping.value())
                            .contains("/admin-reconcile-credential/{userId}");
                })
                .findFirst()
                .orElse(null);

        assertThat(method)
                .as("AuthController must expose the bounded production-recovery reconciliation endpoint")
                .isNotNull();

        RequireCapability capability = Objects.requireNonNull(method).getAnnotation(RequireCapability.class);
        assertThat(capability)
                .as("Credential reconciliation must remain capability-gated")
                .isNotNull();
        assertThat(capability.value()).isEqualTo("USER.WRITE");
    }

    @Test
    void authServiceExposesDedicatedReconciliationMethodInsteadOfReusingLegacyAdminReset() {
        boolean present = Arrays.stream(com.sanad.platform.security.service.AuthService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("reconcileCredential"));

        assertThat(present)
                .as("AuthService must expose a dedicated reconciliation method for existing credentials")
                .isTrue();
    }
}
