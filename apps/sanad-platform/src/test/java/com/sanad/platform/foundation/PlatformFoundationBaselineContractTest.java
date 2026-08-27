package com.sanad.platform.foundation;

import com.sanad.platform.access.api.RoleController;
import com.sanad.platform.access.api.UserAccessController;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.hr.api.HrController;
import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.organization.membership.api.OrganizationMembershipController;
import com.sanad.platform.user.api.UserController;
import com.sanad.platform.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformFoundationBaselineContractTest {

    @Test
    void keepsAuthoritativeIdentityAccessAndWorkforceBuildingBlocks() {
        assertThat(User.class).isNotNull();
        assertThat(Role.class).isNotNull();
        assertThat(UserRoleGrant.class).isNotNull();
        assertThat(CapabilityEvaluationService.class).isNotNull();
        assertThat(EntitlementResolver.class).isNotNull();
        assertThat(HrEmployee.class).isNotNull();
    }

    @Test
    void keepsExistingTenantAdministrationControllerRoots() {
        assertControllerRoot(UserController.class, "/api/v1/users");
        assertControllerRoot(RoleController.class, "/api/v1/access/roles");
        assertControllerRoot(UserAccessController.class, "/api/v1/access/users");
        assertControllerRoot(
                OrganizationMembershipController.class,
                "/api/v1/organizations/{organizationId}/memberships");
        assertControllerRoot(HrController.class, "/api/v1/hr");
    }

    private static void assertControllerRoot(Class<?> controllerType, String expectedRoot) {
        RequestMapping mapping = AnnotationUtils.findAnnotation(controllerType, RequestMapping.class);
        assertThat(mapping)
                .as("%s must retain a class-level @RequestMapping", controllerType.getSimpleName())
                .isNotNull();
        assertThat(mapping.value())
                .as("%s controller root", controllerType.getSimpleName())
                .containsExactly(expectedRoot);
    }
}
