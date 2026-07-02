package com.sanad.platform.workshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.idempotency.service.IdempotentCommandExecutor;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.security.tenant.TenantResolver;
import com.sanad.platform.shared.api.PageResponse;
import com.sanad.platform.shared.api.PageResponseBuilder;
import com.sanad.platform.workshop.dto.WorkshopDtos;
import com.sanad.platform.workshop.service.WorkshopItemService;
import com.sanad.platform.workshop.service.WorkshopLifecycleService;
import com.sanad.platform.workshop.service.WorkshopReadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
}
