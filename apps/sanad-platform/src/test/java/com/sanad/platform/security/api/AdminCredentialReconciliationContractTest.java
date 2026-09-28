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
    void exposesGovernedAdminCredentialReconciliationEndpoint() {
        Method method = Arrays.stream(AuthController.class.getDeclaredMethods())
                .filter(candidate -> {
                    PostMapping mapping = candidate.getAnnotation(PostMapping.class);
                    return mapping != null
                            && Arrays.stream(mapping.value())
                            .anyMatch("/admin-reconcile-credential/{userId}"::equals);
                })
                .findFirst()
                .orElse(null);

        assertThat(method)
                .as("AuthController must expose a governed credential reconciliation endpoint")
                .isNotNull();

        RequireCapability capability = Objects.requireNonNull(method)
                .getAnnotation(RequireCapability.class);
        assertThat(capability)
                .as("reconciliation endpoint must be capability-gated")
                .isNotNull();
        assertThat(capability.value()).isEqualTo("USER.WRITE");
    }

    @Test
    void authServiceExposesReconciliationOperation() {
        boolean present = Arrays.stream(com.sanad.platform.security.service.AuthService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("reconcileCredential")
                        && method.getParameterCount() == 4);

        assertThat(present)
                .as("AuthService must expose tenant-scoped governed credential reconciliation")
                .isTrue();
    }
}
