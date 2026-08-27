package com.sanad.platform.erp.api;

import com.sanad.platform.erp.api.ErpDtos.MovementResponse;
import com.sanad.platform.erp.api.ErpDtos.ReservationResponse;
import com.sanad.platform.erp.application.ErpInventoryReservationService;
import com.sanad.platform.erp.application.ErpInventoryService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;

/** Read-only inventory surfaces required by the human ERP workspace. */
@RestController
@RequestMapping("/api/v1/erp")
public class ErpInventoryReadController {

    private final ErpInventoryService inventoryService;
    private final ErpInventoryReservationService reservationService;

    public ErpInventoryReadController(ErpInventoryService inventoryService,
                                      ErpInventoryReservationService reservationService) {
        this.inventoryService = inventoryService;
        this.reservationService = reservationService;
    }

    @GetMapping("/inventory/reservations")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<ReservationResponse>> listReservations(Authentication auth) {
        return ResponseEntity.ok(reservationService.list(tenantId(auth)));
    }

    @GetMapping("/inventory/movements")
    @RequireCapability("ERP.VIEW")
    public ResponseEntity<List<MovementResponse>> listMovements(Authentication auth) {
        return ResponseEntity.ok(inventoryService.listMovements(tenantId(auth)));
    }
}
