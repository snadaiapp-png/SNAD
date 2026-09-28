package com.sanad.platform.organization.legalentity;

import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LegalEntityEligibilityApiContractTest {

    @Test
    void exposesTenantScopedReadOnlyEligibilityEndpointUnderOrganizations() throws Exception {
        Class<?> controller = Class.forName(
                "com.sanad.platform.organization.legalentity.LegalEntityEligibilityController");

        RequestMapping root = controller.getAnnotation(RequestMapping.class);
        assertThat(root).isNotNull();
        assertThat(Arrays.asList(root.value()))
                .contains("/api/v1/organizations/{organizationId}/legal-entities");

        Method endpoint = Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.getName().equals("listEligible"))
                .findFirst()
                .orElseThrow();

        GetMapping get = endpoint.getAnnotation(GetMapping.class);
        assertThat(get).isNotNull();
        assertThat(Arrays.asList(get.value())).contains("/eligible");

        RequireCapability capability = endpoint.getAnnotation(RequireCapability.class);
        assertThat(capability).isNotNull();
        assertThat(capability.value()).isEqualTo("ORGANIZATION.READ");

        assertThat(Arrays.stream(endpoint.getParameterTypes()).toList())
                .contains(UUID.class, LocalDate.class);
    }

    @Test
    void repositoryContractCanListActiveEligibleLegalEntitiesForOrganizationAndDate() throws Exception {
        Method method = LegalEntityRepository.class.getMethod(
                "findActiveEligibleForOrganization",
                UUID.class,
                UUID.class,
                LocalDate.class);

        assertThat(method.getReturnType()).isEqualTo(java.util.List.class);
    }
}
