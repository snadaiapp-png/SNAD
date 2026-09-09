package com.sanad.platform.subscription.usage;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Executive API for usage metering — ingestion and read model.
 */
@RestController
@RequestMapping("/api/v1/executive/usage")
public class UsageController {

    public record IngestUsageRequest(
            @NotBlank String metricCode,
            @NotNull Long quantity,
            String source,
            @NotBlank String idempotencyKey,
            Instant occurredAt) {
    }

    private final ControlPlaneAccessGuard accessGuard;
    private final UsageMeteringService usageMeteringService;

    public UsageController(ControlPlaneAccessGuard accessGuard,
                           UsageMeteringService usageMeteringService) {
        this.accessGuard = accessGuard;
        this.usageMeteringService = usageMeteringService;
    }

    @PostMapping("/events")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<UsageMeteringService.IngestResult> ingest(
            @Valid @RequestBody IngestUsageRequest request,
            @RequestParam("tenantId") UUID tenantId,
            Authentication authentication) {
        accessGuard.require(authentication);
        UsageMeteringService.IngestResult result = usageMeteringService.ingest(
                tenantId, request.metricCode(), request.quantity(),
                request.source(), request.idempotencyKey(),
                request.occurredAt() == null ? Instant.now() : request.occurredAt());
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @RequireCapability("usage.read")
    public ResponseEntity<List<UsageMeteringService.UsageSnapshot>> usage(
            @RequestParam("tenantId") UUID tenantId,
            Authentication authentication) {
        accessGuard.require(authentication);
        // batched read model — fixed statement budget regardless of metric count
        return ResponseEntity.ok(usageMeteringService.usageSnapshots(tenantId));
    }

}
