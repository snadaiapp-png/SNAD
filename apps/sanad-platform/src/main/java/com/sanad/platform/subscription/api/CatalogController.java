package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.catalog.ApplicationCatalogService;
import com.sanad.platform.subscription.catalog.ApplicationEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Executive API for the application catalog.
 *
 * <p>The catalog is data, not code: the console renders whatever this API
 * returns. Endpoints are additive to the existing {@code /api/v1/executive}
 * namespace and follow its conventions (control-plane guard + capability).
 */
@RestController
@RequestMapping("/api/v1/executive")
public class CatalogController {

    private static final Logger log = LoggerFactory.getLogger(CatalogController.class);

    private final ControlPlaneAccessGuard accessGuard;
    private final ApplicationCatalogService catalogService;
    private final PlatformAuditService auditService;

    public CatalogController(ControlPlaneAccessGuard accessGuard,
                             ApplicationCatalogService catalogService,
                             PlatformAuditService auditService) {
        this.accessGuard = accessGuard;
        this.catalogService = catalogService;
        this.auditService = auditService;
    }

    @GetMapping("/applications")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ScpDtos.ApplicationResponse>> listApplications(
            @RequestParam(name = "availableOnly", required = false, defaultValue = "false")
            boolean availableOnly,
            Authentication authentication) {
        accessGuard.require(authentication);
        List<ScpDtos.ApplicationResponse> applications = (availableOnly
                ? catalogService.listAvailable()
                : catalogService.listAll())
                .stream().map(ScpDtos.ApplicationResponse::from).toList();
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/applications/{id}")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<ScpDtos.ApplicationResponse> getApplication(
            @PathVariable UUID id,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(ScpDtos.ApplicationResponse.from(catalogService.get(id)));
    }

    @PostMapping("/applications")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ScpDtos.ApplicationResponse> createApplication(
            @Valid @RequestBody ScpDtos.ApplicationRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        ApplicationEntity created = catalogService.create(fromRequest(request));
        auditService.success(authentication, null, "APPLICATION_CREATE",
                "application", created.getId().toString(), null, null, created);
        log.info("Application created: code={} id={}", created.getCode(), created.getId());
        return ResponseEntity.ok(ScpDtos.ApplicationResponse.from(created));
    }

    @PutMapping("/applications/{id}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ScpDtos.ApplicationResponse> updateApplication(
            @PathVariable UUID id,
            @Valid @RequestBody ScpDtos.ApplicationRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        ApplicationEntity before = catalogService.get(id);
        ApplicationEntity updated = catalogService.update(id, fromRequest(request));
        auditService.success(authentication, null, "APPLICATION_UPDATE",
                "application", id.toString(), null, before, updated);
        return ResponseEntity.ok(ScpDtos.ApplicationResponse.from(updated));
    }

    private static ApplicationEntity fromRequest(ScpDtos.ApplicationRequest request) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setLocalizedName(request.localizedName());
        entity.setDescription(request.description());
        entity.setCategory(request.category());
        entity.setIconKey(request.iconKey());
        entity.setProvisioningMode(request.provisioningMode());
        entity.setSupportedCountries(request.supportedCountries());
        entity.setDependencies(request.dependencies());
        entity.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        return entity;
    }
}
