package com.sanad.platform.crm.mobile.sync.web;

import com.sanad.platform.crm.integration.domain.TenantContextPort;
import com.sanad.platform.crm.mobile.sync.model.DeviceRegistrationRequest;
import com.sanad.platform.crm.mobile.sync.model.DeviceRegistrationResponse;
import com.sanad.platform.crm.mobile.sync.service.MobileDeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/mobile/devices")
public class MobileDeviceController {

    private final MobileDeviceService mobileDeviceService;
    private final TenantContextPort tenantContext;

    public MobileDeviceController(MobileDeviceService mobileDeviceService,
                                  TenantContextPort tenantContext) {
        this.mobileDeviceService = mobileDeviceService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/register")
    public ResponseEntity<DeviceRegistrationResponse> register(
            @Valid @RequestBody DeviceRegistrationRequest request) {
        DeviceRegistrationResponse response = mobileDeviceService.register(
            tenantContext.getTenantId(), tenantContext.getPrincipalId(), request
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deviceId}/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(@PathVariable UUID deviceId) {
        mobileDeviceService.heartbeat(
            tenantContext.getTenantId(), tenantContext.getPrincipalId(), deviceId
        );
        return ResponseEntity.ok(Map.of(
            "deviceId", deviceId.toString(),
            "status", "ACTIVE"
        ));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID deviceId) {
        mobileDeviceService.deactivate(
            tenantContext.getTenantId(), tenantContext.getPrincipalId(), deviceId
        );
        return ResponseEntity.noContent().build();
    }
}
