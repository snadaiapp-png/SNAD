package com.sanad.platform.security.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class AdminInitializeCredentialContractTest {

    @Test
    void exposesInitializationOnlyAdminCredentialEndpoint() {
        boolean present = Arrays.stream(AuthController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch("/admin-initialize-credential/{userId}"::equals);

        assertThat(present)
                .as("AuthController must expose an initialization-only admin credential endpoint")
                .isTrue();
    }
}
