package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.catalog.ApplicationCatalogService;
import com.sanad.platform.subscription.catalog.ProductCatalogService;
import com.sanad.platform.subscription.catalog.ProductEntity;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R0C-11 — Product catalog API contract (RBAC + audit + structural absence of
 * DELETE).
 *
 * <p>Closes the original missing SCP-G1 contract
 * {@code GET /api/v1/executive/products} and proves the additive
 * POST/PUT management surface: granular capabilities ({@code catalog.read} /
 * {@code catalog.manage}), control-plane guard, PRODUCT_CREATE / PRODUCT_UPDATE
 * success audits (resourceType=product, resourceId=<UUID>), no false success
 * audit on rejected mutations, and NO DELETE endpoint.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogController — R0C-11 product endpoints")
class CatalogControllerProductEndpointsTest {

    @Mock
    private ControlPlaneAccessGuard accessGuard;
    @Mock
    private ApplicationCatalogService applicationCatalogService;
    @Mock
    private ProductCatalogService productCatalogService;
    @Mock
    private PlatformAuditService auditService;
    @Mock
    private Authentication authentication;

    private CatalogController controller;
    private Validator validator;

    private static final UUID PRODUCT_ID = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final UUID APP_ID = UUID.fromString("b1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        controller = new CatalogController(accessGuard, applicationCatalogService,
                productCatalogService, auditService);
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private ProductEntity product(String status) {
        ProductEntity e = new ProductEntity();
        e.setId(PRODUCT_ID);
        e.setCode("HRM_ADDON");
        e.setName("HRM Add-On Pack");
        e.setDescription("Add-on");
        e.setApplicationId(APP_ID);
        e.setProductType("ADD_ON");
        e.setStatus(status);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    private ScpDtos.ProductRequest request(String code, String type, String status) {
        return new ScpDtos.ProductRequest(code, "HRM Add-On Pack", null,
                type, APP_ID, status);
    }

    // ---------------------------------------------------------------
    // GET — catalog.read
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GET /products: annotated @RequireCapability(\"catalog.read\")")
    void listProducts_hasCatalogReadAnnotation() throws NoSuchMethodException {
        Method method = CatalogController.class.getMethod("listProducts",
                boolean.class, Authentication.class);
        RequireCapability annotation = method.getAnnotation(RequireCapability.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("catalog.read");
    }

    @Test
    @DisplayName("GET /products/{id}: annotated @RequireCapability(\"catalog.read\")")
    void getProduct_hasCatalogReadAnnotation() throws NoSuchMethodException {
        Method method = CatalogController.class.getMethod("getProduct",
                UUID.class, Authentication.class);
        RequireCapability annotation = method.getAnnotation(RequireCapability.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("catalog.read");
    }

    @Test
    @DisplayName("GET /products: guard is enforced and availableOnly filters")
    void listProducts_enforcesGuardAndFilter() {
        when(productCatalogService.findAvailable()).thenReturn(List.of(product("ACTIVE")));

        ResponseEntity<List<ScpDtos.ProductResponse>> response =
                controller.listProducts(true, authentication);

        verify(accessGuard).require(authentication);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("GET /products/{id}: guard enforced; unknown id → domain error")
    void getProduct_unknownIdFailsClosed() {
        when(productCatalogService.findById(PRODUCT_ID))
                .thenThrow(new IllegalArgumentException("Unknown product: " + PRODUCT_ID));

        assertThatThrownBy(() -> controller.getProduct(PRODUCT_ID, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown product");
        verify(accessGuard).require(authentication);
    }

    // ---------------------------------------------------------------
    // POST — catalog.manage + PRODUCT_CREATE audit
    // ---------------------------------------------------------------

    @Test
    @DisplayName("POST /products: annotated @RequireCapability(\"catalog.manage\")")
    void createProduct_hasCatalogManageAnnotation() throws NoSuchMethodException {
        Method method = CatalogController.class.getMethod("createProduct",
                ScpDtos.ProductRequest.class, Authentication.class);
        RequireCapability annotation = method.getAnnotation(RequireCapability.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("catalog.manage");
    }

    @Test
    @DisplayName("POST /products: success emits PRODUCT_CREATE audit (product, UUID)")
    void createProduct_emitsAudit() {
        when(productCatalogService.create(any(ProductEntity.class)))
                .thenReturn(product("ACTIVE"));

        ResponseEntity<ScpDtos.ProductResponse> response =
                controller.createProduct(request("hrm_addon", "ADD_ON", null), authentication);

        verify(accessGuard).require(authentication);
        verify(auditService).success(eq(authentication), isNull(), eq("PRODUCT_CREATE"),
                eq("product"), eq(PRODUCT_ID.toString()), isNull(), isNull(), any());
        assertThat(response.getBody().code()).isEqualTo("HRM_ADDON");
    }

    @Test
    @DisplayName("POST /products: rejected mutation emits NO success audit")
    void createProduct_rejectedNoAudit() {
        when(productCatalogService.create(any(ProductEntity.class)))
                .thenThrow(new IllegalStateException("Product code already exists: HRM_ADDON"));

        assertThatThrownBy(() -> controller.createProduct(
                        request("HRM_ADDON", "ADD_ON", null), authentication))
                .isInstanceOf(IllegalStateException.class);
        verify(auditService, never()).success(any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    // ---------------------------------------------------------------
    // PUT — catalog.manage + PRODUCT_UPDATE audit
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PUT /products/{id}: annotated @RequireCapability(\"catalog.manage\")")
    void updateProduct_hasCatalogManageAnnotation() throws NoSuchMethodException {
        Method method = CatalogController.class.getMethod("updateProduct",
                UUID.class, ScpDtos.ProductRequest.class, Authentication.class);
        RequireCapability annotation = method.getAnnotation(RequireCapability.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("catalog.manage");
    }

    @Test
    @DisplayName("PUT /products/{id}: success emits PRODUCT_UPDATE audit with before/after")
    void updateProduct_emitsAudit() {
        ProductEntity before = product("ACTIVE");
        ProductEntity after = product("ARCHIVED");
        when(productCatalogService.findById(PRODUCT_ID)).thenReturn(before);
        when(productCatalogService.update(eq(PRODUCT_ID), any(ProductEntity.class)))
                .thenReturn(after);

        ResponseEntity<ScpDtos.ProductResponse> response =
                controller.updateProduct(PRODUCT_ID,
                        request("HRM_ADDON", "ADD_ON", "ARCHIVED"), authentication);

        verify(accessGuard).require(authentication);
        verify(auditService).success(eq(authentication), isNull(), eq("PRODUCT_UPDATE"),
                eq("product"), eq(PRODUCT_ID.toString()), isNull(), eq(before), eq(after));
        assertThat(response.getBody().status()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("PUT /products/{id}: rejected mutation emits NO success audit")
    void updateProduct_rejectedNoAudit() {
        when(productCatalogService.findById(PRODUCT_ID)).thenReturn(product("ACTIVE"));
        when(productCatalogService.update(eq(PRODUCT_ID), any(ProductEntity.class)))
                .thenThrow(new IllegalArgumentException("Invalid status: RETIRED"));

        assertThatThrownBy(() -> controller.updateProduct(PRODUCT_ID,
                        request("HRM_ADDON", "ADD_ON", "RETIRED"), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(auditService, never()).success(any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    // ---------------------------------------------------------------
    // guard denial
    // ---------------------------------------------------------------

    @Test
    @DisplayName("POST /products: guard denial blocks the mutation before the service")
    void createProduct_guardDenialBlocks() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Control-plane tenant required"))
                .when(accessGuard).require(authentication);

        assertThatThrownBy(() -> controller.createProduct(
                        request("HRM_ADDON", "ADD_ON", null), authentication))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(productCatalogService, never()).create(any());
        verify(auditService, never()).success(any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    // ---------------------------------------------------------------
    // structural safety
    // ---------------------------------------------------------------

    @Test
    @DisplayName("NO DELETE endpoint exists on the product surface")
    void noDeleteEndpoint() {
        for (Method method : CatalogController.class.getDeclaredMethods()) {
            if (method.getName().toLowerCase().contains("product")
                    && method.getName().toLowerCase().contains("delete")) {
                throw new AssertionError("Product DELETE endpoint must not exist: "
                        + method.getName());
            }
            org.springframework.web.bind.annotation.DeleteMapping delete =
                    method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class);
            assertThat(delete)
                    .as("CatalogController must not declare @DeleteMapping anywhere")
                    .isNull();
        }
    }

    // ---------------------------------------------------------------
    // DTO contract
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ProductRequest: blank code and blank name are rejected by validation")
    void productRequest_validation() {
        assertThat(validator.validate(request(null, "ADD_ON", "ACTIVE"))).isNotEmpty();
        assertThat(validator.validate(request("   ", "ADD_ON", "ACTIVE"))).isNotEmpty();
        assertThat(validator.validate(request("HRM_ADDON", "ADD_ON", "ACTIVE"))).isEmpty();
    }

    @Test
    @DisplayName("ProductResponse.from maps every catalog field")
    void productResponse_mapping() {
        ScpDtos.ProductResponse response = ScpDtos.ProductResponse.from(product("ACTIVE"));

        assertThat(response.id()).isEqualTo(PRODUCT_ID);
        assertThat(response.code()).isEqualTo("HRM_ADDON");
        assertThat(response.name()).isEqualTo("HRM Add-On Pack");
        assertThat(response.applicationId()).isEqualTo(APP_ID);
        assertThat(response.productType()).isEqualTo("ADD_ON");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }
}
