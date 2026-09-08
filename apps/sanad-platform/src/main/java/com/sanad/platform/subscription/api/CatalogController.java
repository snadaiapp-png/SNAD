package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.catalog.ApplicationCatalogService;
import com.sanad.platform.subscription.catalog.ApplicationEntity;
import com.sanad.platform.subscription.catalog.ProductCatalogService;
import com.sanad.platform.subscription.catalog.ProductEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Executive API for the application catalog and the product catalog.
 *
 * <p>The catalog is data, not code: the console renders whatever this API
 * returns. Endpoints are additive to the existing {@code /api/v1/executive}
 * namespace and follow its conventions (control-plane guard + capability).
 * R0C-11 adds the product surface with granular capabilities:
 * reads require {@code catalog.read}, writes require {@code catalog.manage}.
 * There is deliberately NO DELETE endpoint — products are retired through
 * INACTIVE/ARCHIVED statuses, never physically removed.</p>
 */
@RestController
@RequestMapping("/api/v1/executive")
public class CatalogController {

    private static final Logger log = LoggerFactory.getLogger(CatalogController.class);

    private final ControlPlaneAccessGuard accessGuard;
    private final ApplicationCatalogService catalogService;
    private final ProductCatalogService productCatalogService;
    private final PlatformAuditService auditService;

    public CatalogController(ControlPlaneAccessGuard accessGuard,
                             ApplicationCatalogService catalogService,
                             ProductCatalogService productCatalogService,
                             PlatformAuditService auditService) {
        this.accessGuard = accessGuard;
        this.catalogService = catalogService;
        this.productCatalogService = productCatalogService;
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

    // ============================================================
    // Products (R0C-11) — reads: catalog.read; writes: catalog.manage
    // ============================================================

    @GetMapping("/products")
    @RequireCapability("catalog.read")
    public ResponseEntity<List<ScpDtos.ProductResponse>> listProducts(
            @RequestParam(name = "availableOnly", required = false, defaultValue = "false")
            boolean availableOnly,
            Authentication authentication) {
        accessGuard.require(authentication);
        List<ScpDtos.ProductResponse> products = (availableOnly
                ? productCatalogService.findAvailable()
                : productCatalogService.findAll())
                .stream().map(ScpDtos.ProductResponse::from).toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/{id}")
    @RequireCapability("catalog.read")
    public ResponseEntity<ScpDtos.ProductResponse> getProduct(
            @PathVariable UUID id,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(ScpDtos.ProductResponse.from(productCatalogService.findById(id)));
    }

    @PostMapping("/products")
    @RequireCapability("catalog.manage")
    public ResponseEntity<ScpDtos.ProductResponse> createProduct(
            @Valid @RequestBody ScpDtos.ProductRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        ProductEntity created = productCatalogService.create(fromProductRequest(request));
        auditService.success(authentication, null, "PRODUCT_CREATE",
                "product", created.getId().toString(), null, null, created);
        log.info("Product created: code={} id={}", created.getCode(), created.getId());
        return ResponseEntity.ok(ScpDtos.ProductResponse.from(created));
    }

    @PutMapping("/products/{id}")
    @RequireCapability("catalog.manage")
    public ResponseEntity<ScpDtos.ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ScpDtos.ProductRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        ProductEntity before = productCatalogService.findById(id);
        ProductEntity updated = productCatalogService.update(id, fromProductRequest(request));
        auditService.success(authentication, null, "PRODUCT_UPDATE",
                "product", id.toString(), null, before, updated);
        return ResponseEntity.ok(ScpDtos.ProductResponse.from(updated));
    }

    private static ProductEntity fromProductRequest(ScpDtos.ProductRequest request) {
        ProductEntity entity = new ProductEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setProductType(request.productType());
        entity.setApplicationId(request.applicationId());
        entity.setStatus(request.status());
        return entity;
    }
}
