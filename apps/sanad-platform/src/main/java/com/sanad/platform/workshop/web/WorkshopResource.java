package com.sanad.platform.workshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.idempotency.IdempotencyCommandInterceptor;
import com.sanad.platform.idempotency.service.IdempotentCommandExecutor;
import com.sanad.platform.idempotency.service.IdempotentHttpResult;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.security.tenant.TenantResolver;
import com.sanad.platform.shared.api.PageResponse;
import com.sanad.platform.shared.api.PageResponseBuilder;
import com.sanad.platform.workshop.dto.WorkshopDtos;
import com.sanad.platform.workshop.service.WorkshopItemService;
import com.sanad.platform.workshop.service.WorkshopLifecycleService;
import com.sanad.platform.workshop.service.WorkshopReadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/workshops")
public class WorkshopResource {
    private final WorkshopLifecycleService lifecycle;
    private final WorkshopItemService workItems;
    private final WorkshopReadService reads;
    private final TenantResolver tenants;
    private final IdempotentCommandExecutor commands;
    private final ObjectMapper json;

    public WorkshopResource(WorkshopLifecycleService lifecycle,
                            WorkshopItemService workItems,
                            WorkshopReadService reads,
                            TenantResolver tenants,
                            IdempotentCommandExecutor commands,
                            ObjectMapper json) {
        this.lifecycle = lifecycle;
        this.workItems = workItems;
        this.reads = reads;
        this.tenants = tenants;
        this.commands = commands;
        this.json = json;
    }

    @GetMapping
    @RequireCapability("WORKSHOP.READ")
    public PageResponse<WorkshopDtos.WorkshopResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = tenants.requireTenantId();
        Page<WorkshopDtos.WorkshopResponse> result = reads.list(tenantId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return PageResponseBuilder.from(result, result.getContent());
    }

    @GetMapping("/{workshopId}")
    @RequireCapability("WORKSHOP.READ")
    public WorkshopDtos.WorkshopResponse get(@PathVariable UUID workshopId) {
        return reads.get(tenants.requireTenantId(), workshopId);
    }

    @GetMapping("/{workshopId}/board")
    @RequireCapability("WORKSHOP.READ")
    public WorkshopDtos.BoardResponse board(@PathVariable UUID workshopId) {
        return reads.board(tenants.requireTenantId(), workshopId);
    }

    private <T> ResponseEntity<?> run(HttpServletRequest request, String operation,
                                      String resourceType, Object payload, Supplier<T> action) {
        String key = (String) request.getAttribute(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_ATTR);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for workshop commands.");
        }
        String requestBody;
        try {
            requestBody = json.writeValueAsString(payload);
        } catch (Exception e) {
            requestBody = String.valueOf(payload);
        }
        IdempotentHttpResult<T> result = commands.execute(
                new IdempotentCommandExecutor.OperationMetadata(operation, request.getRequestURI(), resourceType),
                key, requestBody, request.getMethod(), request.getQueryString(), action);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (result.replayed()) {
            headers.add(IdempotencyCommandInterceptor.IDEMPOTENCY_REPLAYED_HEADER, "true");
            return ResponseEntity.status(result.httpStatus()).headers(headers).body(result.body());
        }
        return new ResponseEntity<>(result.businessResult(), headers,
                HttpStatus.valueOf(result.httpStatus()));
    }
}
