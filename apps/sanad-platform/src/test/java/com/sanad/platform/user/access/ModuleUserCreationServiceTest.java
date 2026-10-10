package com.sanad.platform.user.access;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.dto.CreateUserRequest;
import com.sanad.platform.user.dto.UserResponse;
import com.sanad.platform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModuleUserCreationServiceTest {

    private static final UUID TENANT_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ACTOR_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CREATED_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void provisionIsTransactional() throws Exception {
        Transactional transactional = ModuleUserCreationService.class
                .getMethod("provision", UUID.class, UUID.class, ModuleUserProvisionRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void validatesModuleCapabilitiesBeforeCreatingUser() {
        UserService users = mock(UserService.class);
        ModuleUserProvisioningService moduleProvisioning = mock(ModuleUserProvisioningService.class);
        CapabilityEvaluationService evaluator = allowedEvaluator();
        ModuleUserCreationService service = new ModuleUserCreationService(users, moduleProvisioning, evaluator);

        when(moduleProvisioning.resolve(TENANT_ID, "crm")).thenReturn(context("CRM.ACCOUNT.READ"));

        ModuleUserProvisionRequest request = new ModuleUserProvisionRequest(
                createUserRequest(),
                "crm",
                List.of("HRM.EMPLOYEE.VIEW"));

        assertThatThrownBy(() -> service.provision(TENANT_ID, ACTOR_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cross-module");

        verify(users, never()).createUser(TENANT_ID, request.user());
    }

    @Test
    void createsUserAndGrantsCapabilitiesThroughOneTransactionalServiceBoundary() {
        UserService users = mock(UserService.class);
        ModuleUserProvisioningService moduleProvisioning = mock(ModuleUserProvisioningService.class);
        CapabilityEvaluationService evaluator = allowedEvaluator();
        ModuleUserCreationService service = new ModuleUserCreationService(users, moduleProvisioning, evaluator);

        when(moduleProvisioning.resolve(TENANT_ID, "crm")).thenReturn(
                context("CRM.ACCOUNT.READ", "CRM.ACCOUNT.WRITE"));

        UserResponse created = new UserResponse();
        created.setId(CREATED_ID);
        created.setTenantId(TENANT_ID);
        created.setEmail("new@example.com");
        created.setStatus(UserStatus.ACTIVE);

        ModuleUserProvisionRequest request = new ModuleUserProvisionRequest(
                createUserRequest(),
                "crm",
                List.of("crm.account.write", "CRM.ACCOUNT.READ"));

        when(users.createUser(TENANT_ID, request.user())).thenReturn(created);

        UserResponse result = service.provision(TENANT_ID, ACTOR_ID, request);

        assertThat(result).isSameAs(created);
        verify(users).createUser(TENANT_ID, request.user());
        verify(moduleProvisioning).grantCapabilities(
                TENANT_ID,
                ACTOR_ID,
                CREATED_ID,
                "crm",
                List.of("CRM.ACCOUNT.WRITE", "CRM.ACCOUNT.READ"));
    }

    @Test
    void refusesAtomicCreationWhenActorLacksUserCreateEvenIfGrantEndpointGatePassed() {
        UserService users = mock(UserService.class);
        ModuleUserProvisioningService moduleProvisioning = mock(ModuleUserProvisioningService.class);
        CapabilityEvaluationService evaluator = mock(CapabilityEvaluationService.class);
        when(evaluator.evaluate(TENANT_ID, ACTOR_ID, "USER.CREATE", null))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, ACTOR_ID, null, "USER.CREATE", false,
                        "NO_MATCHING_ACTIVE_ROLE", null, null));

        ModuleUserCreationService service =
                new ModuleUserCreationService(users, moduleProvisioning, evaluator);
        ModuleUserProvisionRequest request = new ModuleUserProvisionRequest(
                createUserRequest(), "crm", List.of("CRM.ACCOUNT.READ"));

        assertThatThrownBy(() -> service.provision(TENANT_ID, ACTOR_ID, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("USER.CREATE");

        verify(users, never()).createUser(TENANT_ID, request.user());
        verify(moduleProvisioning, never()).resolve(TENANT_ID, "crm");
    }

    private static CapabilityEvaluationService allowedEvaluator() {
        CapabilityEvaluationService evaluator = mock(CapabilityEvaluationService.class);
        when(evaluator.evaluate(TENANT_ID, ACTOR_ID, "USER.CREATE", null))
                .thenReturn(new AccessDecisionResponse(
                        TENANT_ID, ACTOR_ID, null, "USER.CREATE", true,
                        "ROLE_CAPABILITY_MATCH", null, null));
        return evaluator;
    }

    private static CreateUserRequest createUserRequest() {
        CreateUserRequest request =
                new CreateUserRequest("new@example.com", "new.user", "New User", UserStatus.ACTIVE);
        request.setInitialCredential("Temporary123!");
        return request;
    }

    private static ModuleProvisioningContext context(String... capabilities) {
        return new ModuleProvisioningContext(
                "CRM",
                "CRM",
                "إدارة علاقات العملاء",
                Set.of("CRM"),
                Set.of(capabilities),
                Set.of("TENANT"),
                List.of());
    }
}
