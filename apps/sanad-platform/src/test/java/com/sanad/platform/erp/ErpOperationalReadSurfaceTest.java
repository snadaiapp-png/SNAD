package com.sanad.platform.erp;

import com.sanad.platform.erp.api.ErpController;
import com.sanad.platform.erp.application.ErpInventoryService;
import com.sanad.platform.erp.application.ErpInventoryReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ErpOperationalReadSurfaceTest {

    @Test
    void inventoryServiceExposesTenantScopedMovementLedgerRead() {
        assertThatCode(() -> ErpInventoryService.class.getMethod("listMovements", UUID.class))
                .doesNotThrowAnyException();
    }

    @Test
    void reservationServiceAlreadyExposesTenantScopedList() {
        assertThatCode(() -> ErpInventoryReservationService.class.getMethod("list", UUID.class))
                .doesNotThrowAnyException();
    }

    @Test
    void controllerExposesReservationAndMovementGetEndpoints() {
        assertThat(hasGetMapping("/inventory/reservations")).isTrue();
        assertThat(hasGetMapping("/inventory/movements")).isTrue();
    }

    private boolean hasGetMapping(String path) {
        return Arrays.stream(ErpController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .anyMatch(path::equals);
    }
}
