package com.sanad.platform.workshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.idempotency.service.IdempotentCommandExecutor;
import com.sanad.platform.security.tenant.TenantResolver;
import com.sanad.platform.workshop.service.WorkshopItemService;
import com.sanad.platform.workshop.service.WorkshopLifecycleService;
import com.sanad.platform.workshop.service.WorkshopReadService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
