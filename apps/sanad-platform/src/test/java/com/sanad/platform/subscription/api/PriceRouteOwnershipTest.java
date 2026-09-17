package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.subscription.plan.PlanVersionEntity;
import com.sanad.platform.subscription.plan.PlanVersionService;
import com.sanad.platform.subscription.pricing.CountryCurrencyRepository;
import com.sanad.platform.subscription.pricing.PriceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceRouteOwnershipTest {
    @Mock ControlPlaneAccessGuard accessGuard;
    @Mock PriceService priceService;
    @Mock CountryCurrencyRepository countryCurrencies;
    @Mock PlatformAuditService auditService;
    @Mock PlanVersionService planVersionService;

    @Test
    void planVersionPriceRoutesRequireTheVersionToBelongToThePathPlan() throws Exception {
        Constructor<?> governed = Arrays.stream(PriceController.class.getDeclaredConstructors())
                .filter(c -> Arrays.asList(c.getParameterTypes()).contains(PlanVersionService.class))
                .findFirst().orElse(null);
        assertThat(governed).as("PriceController must carry plan-version ownership authority").isNotNull();

        Object[] args = Arrays.stream(governed.getParameterTypes()).map(type -> {
            if (type == ControlPlaneAccessGuard.class) return accessGuard;
            if (type == PriceService.class) return priceService;
            if (type == CountryCurrencyRepository.class) return countryCurrencies;
            if (type == PlatformAuditService.class) return auditService;
            if (type == PlanVersionService.class) return planVersionService;
            throw new IllegalStateException("Unexpected constructor dependency: " + type);
        }).toArray();
        PriceController controller = (PriceController) governed.newInstance(args);

        UUID pathPlan = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID actualPlan = UUID.fromString("20000000-0000-4000-8000-000000000002");
        UUID versionId = UUID.fromString("30000000-0000-4000-8000-000000000003");
        PlanVersionEntity version = new PlanVersionEntity();
        version.setId(versionId);
        version.setPlanId(actualPlan);
        when(planVersionService.findVersion(versionId)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> controller.listPlanVersionPrices(pathPlan, versionId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan");
        verify(priceService, never()).listForPlanVersion(any());
    }
}
