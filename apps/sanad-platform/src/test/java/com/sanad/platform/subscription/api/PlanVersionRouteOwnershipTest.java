package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.subscription.plan.PlanVersionEntity;
import com.sanad.platform.subscription.plan.PlanVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanVersionRouteOwnershipTest {
    @Mock ControlPlaneAccessGuard accessGuard;
    @Mock PlanVersionService planVersionService;
    @Mock PlatformAuditService auditService;

    @Test
    void activationRejectsVersionThatDoesNotBelongToPathPlan() {
        UUID pathPlan = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID actualPlan = UUID.fromString("20000000-0000-4000-8000-000000000002");
        UUID versionId = UUID.fromString("30000000-0000-4000-8000-000000000003");

        PlanVersionEntity version = new PlanVersionEntity();
        version.setId(versionId);
        version.setPlanId(actualPlan);
        version.setStatus("DRAFT");

        when(planVersionService.findVersion(versionId)).thenReturn(Optional.of(version));
        when(planVersionService.activate(versionId)).thenReturn(version);
        PlanVersionController controller =
                new PlanVersionController(accessGuard, planVersionService, auditService);

        assertThatThrownBy(() -> controller.activateVersion(pathPlan, versionId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan");
        verify(planVersionService, never()).activate(versionId);
    }
}
