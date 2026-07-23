package com.sanad.platform.crm.web;

import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Static contract gate for the CRM-008 public ownership surface. */
class CrmOwnershipApiInventoryTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            CrmOwnershipResourceController.class,
            CrmOwnershipAssignmentController.class,
            CrmOwnershipTransferController.class);

    @Test
    void exposesExactlyThirtyEightDistinctGovernedOperations() {
        List<Method> operations = operations();
        assertThat(operations).hasSize(38);
        assertThat(operations.stream().map(this::route).distinct()).hasSize(38);
        assertThat(operations).allSatisfy(method ->
                assertThat(method.getAnnotation(RequireCapability.class))
                        .as(method.toGenericString())
                        .isNotNull());
    }

    @Test
    void transferExecuteCapabilityIsInternalOnly() {
        Set<String> publicCapabilities = new LinkedHashSet<>();
        operations().forEach(method -> publicCapabilities.add(
                method.getAnnotation(RequireCapability.class).value()));

        assertThat(publicCapabilities)
                .contains(
                        "CRM.TEAM.READ", "CRM.TEAM.ADMIN",
                        "CRM.QUEUE.READ", "CRM.QUEUE.ADMIN", "CRM.QUEUE.CLAIM",
                        "CRM.TERRITORY.READ", "CRM.TERRITORY.ADMIN",
                        "CRM.ASSIGNMENT_RULE.READ", "CRM.ASSIGNMENT_RULE.ADMIN",
                        "CRM.ASSIGNMENT.READ", "CRM.ASSIGNMENT.WRITE", "CRM.ASSIGNMENT.ADMIN",
                        "CRM.OWNERSHIP_HISTORY.READ",
                        "CRM.TRANSFER.READ", "CRM.TRANSFER.REQUEST", "CRM.TRANSFER.APPROVE")
                .doesNotContain("CRM.TRANSFER.EXECUTE");
    }

    @Test
    void externalRequestRecordsNeverAcceptTenantIdentity() {
        List<Class<?>> requestRecords = List.of(
                CrmOwnershipResourceController.CreateTeamRequest.class,
                CrmOwnershipResourceController.UpdateTeamRequest.class,
                CrmOwnershipResourceController.AddTeamMembershipRequest.class,
                CrmOwnershipResourceController.UpdateTeamMembershipRequest.class,
                CrmOwnershipResourceController.CreateQueueRequest.class,
                CrmOwnershipResourceController.UpdateQueueRequest.class,
                CrmOwnershipResourceController.ClaimQueueItemRequest.class,
                CrmOwnershipResourceController.ReleaseQueueItemRequest.class,
                CrmOwnershipResourceController.CreateTerritoryRequest.class,
                CrmOwnershipResourceController.UpdateTerritoryRequest.class,
                CrmOwnershipResourceController.AssignTerritoryRequest.class,
                CrmOwnershipAssignmentController.CreateRuleRequest.class,
                CrmOwnershipAssignmentController.VersionDefinitionRequest.class,
                CrmOwnershipAssignmentController.SimulateRuleRequest.class,
                CrmOwnershipAssignmentController.ReassignRequest.class,
                CrmOwnershipAssignmentController.BulkReassignRequest.class,
                CrmOwnershipTransferController.CreateTransferRequest.class,
                CrmOwnershipTransferController.SubmitTransferRequest.class,
                CrmOwnershipTransferController.DecideTransferRequest.class,
                CrmOwnershipTransferController.CancelTransferRequest.class);

        assertThat(requestRecords).allSatisfy(type -> {
            assertThat(type.isRecord()).isTrue();
            assertThat(Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName))
                    .noneMatch(name -> name.equalsIgnoreCase("tenantId")
                            || name.equalsIgnoreCase("tenant_id"));
        });
    }

    private List<Method> operations() {
        return CONTROLLERS.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(this::isMapped)
                .toList();
    }

    private boolean isMapped(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    private String route(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) {
            return "GET " + first(method.getAnnotation(GetMapping.class).value());
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            return "POST " + first(method.getAnnotation(PostMapping.class).value());
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            return "PATCH " + first(method.getAnnotation(PatchMapping.class).value());
        }
        return "DELETE " + first(method.getAnnotation(DeleteMapping.class).value());
    }

    private String first(String[] values) {
        return values.length == 0 ? "" : values[0];
    }
}
