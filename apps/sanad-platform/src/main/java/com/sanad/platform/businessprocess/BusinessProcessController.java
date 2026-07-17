package com.sanad.platform.businessprocess;

import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/business-process-e2e")
public class BusinessProcessController {

    private final BusinessProcessService service;

    public BusinessProcessController(BusinessProcessService service) {
        this.service = service;
    }

    @PostMapping("/{processCode}/execute")
    @RequireCapability("BUSINESS_PROCESS.EXECUTE")
    public ResponseEntity<BusinessProcessService.ExecutionResult> execute(
            Authentication authentication,
            @PathVariable String processCode,
            @Valid @RequestBody ExecuteRequest request
    ) {
        Principal principal = principal(authentication);
        BusinessProcessService.ExecutionResult result = service.execute(
                principal.tenantId(), principal.userId(), processCode,
                new BusinessProcessService.ExecuteCommand(
                        request.externalReference(), request.grossAmount(), request.taxAmount(),
                        request.quantity(), request.currencyCode(), request.sku(), request.failAtStep()));
        return ResponseEntity.status(result.idempotent() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @GetMapping("/runs/{runId}")
    @RequireCapability("BUSINESS_PROCESS.READ")
    public BusinessProcessService.ExecutionResult get(
            Authentication authentication,
            @PathVariable UUID runId
    ) {
        Principal principal = principal(authentication);
        return service.get(principal.tenantId(), runId);
    }

    private Principal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            throw new AccessDeniedException("Missing authenticated tenant context");
        }
        Object tenant = details.get("tenant_id");
        Object user = details.get("user_id");
        if (tenant == null || user == null) {
            throw new AccessDeniedException("Missing authenticated tenant or user identifier");
        }
        try {
            return new Principal(UUID.fromString(tenant.toString()), UUID.fromString(user.toString()));
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid authenticated tenant context", exception);
        }
    }

    public record ExecuteRequest(
            @NotBlank @Size(max = 120) String externalReference,
            @DecimalMin(value = "0.000001") BigDecimal grossAmount,
            @DecimalMin(value = "0.0") BigDecimal taxAmount,
            @DecimalMin(value = "0.000001") BigDecimal quantity,
            @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
            @Size(max = 120) String sku,
            @Size(max = 80) String failAtStep
    ) {}

    private record Principal(UUID tenantId, UUID userId) {}
}
