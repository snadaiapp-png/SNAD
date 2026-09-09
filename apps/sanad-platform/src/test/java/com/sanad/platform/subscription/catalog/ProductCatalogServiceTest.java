package com.sanad.platform.subscription.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R0C-11 — Product catalog domain runtime (unit contract).
 *
 * <p>Proves the frozen R0C-11 semantics: code normalization + stability,
 * required name, validated product type/status, fail-closed unique code,
 * nullable/validated application FK, ACTIVE-only availability listing, and
 * the structural absence of any physical delete.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCatalogService — R0C-11 product catalog runtime")
class ProductCatalogServiceTest {

    @Mock
    private ProductRepository repository;

    private ProductCatalogService service;

    private static final UUID APP_ID = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("f1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new ProductCatalogService(repository);
        // default fixture: the application FK resolves (explicit stubs override)
        lenient().when(repository.applicationExists(APP_ID)).thenReturn(true);
    }

    private ProductEntity product(String code) {
        ProductEntity e = new ProductEntity();
        e.setId(PRODUCT_ID);
        e.setCode(code);
        e.setName("HRM Add-On Pack");
        e.setDescription("Add-on pack");
        e.setApplicationId(APP_ID);
        e.setProductType("ADD_ON");
        e.setStatus("ACTIVE");
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    // ---------------------------------------------------------------
    // create
    // ---------------------------------------------------------------

    @Test
    @DisplayName("create: normalizes code (trim + uppercase) and defaults status ACTIVE")
    void create_normalizesCodeAndDefaultsStatus() {
        ProductEntity toCreate = product(" hrm_addon ");
        toCreate.setCode(" hrm_addon ");
        toCreate.setStatus(null);

        ProductEntity created = service.create(toCreate);

        assertThat(created.getCode()).isEqualTo("HRM_ADDON");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        verify(repository).insert(created);
    }

    @Test
    @DisplayName("create: rejects blank name")
    void create_rejectsBlankName() {
        ProductEntity toCreate = product("HRM_ADDON");
        toCreate.setName("   ");

        assertThatThrownBy(() -> service.create(toCreate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("create: rejects invalid product type (fail-closed, mirrors DB CHECK)")
    void create_rejectsInvalidType() {
        ProductEntity toCreate = product("HRM_ADDON");
        toCreate.setProductType("BUNDLE");

        assertThatThrownBy(() -> service.create(toCreate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("create: accepts all four existing product types")
    void create_acceptsAllExistingTypes() {
        for (String type : List.of("APPLICATION", "ADD_ON", "METERED", "OTHER")) {
            ProductEntity toCreate = product("CODE_" + type);
            toCreate.setProductType(type);
            ProductEntity created = service.create(toCreate);
            assertThat(created.getProductType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("create: rejects invalid status (fail-closed, mirrors DB CHECK)")
    void create_rejectsInvalidStatus() {
        ProductEntity toCreate = product("HRM_ADDON");
        toCreate.setStatus("RETIRED");

        assertThatThrownBy(() -> service.create(toCreate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("create: rejects duplicate code fail-closed (deterministic domain error)")
    void create_rejectsDuplicateCode() {
        when(repository.existsByCode("HRM_ADDON")).thenReturn(true);

        assertThatThrownBy(() -> service.create(product("hrm_addon")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("create: rejects code with invalid characters")
    void create_rejectsInvalidCodeCharacters() {
        ProductEntity toCreate = product("HRM ADDON!");

        assertThatThrownBy(() -> service.create(toCreate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("create: nullable application is valid")
    void create_allowsNullApplication() {
        ProductEntity toCreate = product("STANDALONE");
        toCreate.setApplicationId(null);

        ProductEntity created = service.create(toCreate);

        assertThat(created.getApplicationId()).isNull();
        verify(repository).insert(created);
    }

    @Test
    @DisplayName("create: unknown application FK becomes deterministic domain error")
    void create_rejectsUnknownApplication() {
        when(repository.applicationExists(APP_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(product("HRM_ADDON")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown application");
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("create: valid application FK is accepted")
    void create_acceptsValidApplication() {
        when(repository.applicationExists(APP_ID)).thenReturn(true);

        ProductEntity created = service.create(product("HRM_ADDON"));

        assertThat(created.getApplicationId()).isEqualTo(APP_ID);
        verify(repository).insert(created);
    }

    // ---------------------------------------------------------------
    // update
    // ---------------------------------------------------------------

    @Test
    @DisplayName("update: code is stable identity — changes to code are ignored")
    void update_codeIsStable() {
        ProductEntity existing = product("HRM_ADDON");
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        ProductEntity changes = product("HRM_ADDON");
        changes.setCode("RENAME_ATTEMPT");
        changes.setName("Renamed Pack");

        ProductEntity updated = service.update(PRODUCT_ID, changes);

        assertThat(updated.getCode()).isEqualTo("HRM_ADDON");
        assertThat(updated.getName()).isEqualTo("Renamed Pack");
        verify(repository).update(updated);
    }

    @Test
    @DisplayName("update: rejects invalid status")
    void update_rejectsInvalidStatus() {
        ProductEntity existing = product("HRM_ADDON");
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        ProductEntity changes = product("HRM_ADDON");
        changes.setStatus("RETIRED");

        assertThatThrownBy(() -> service.update(PRODUCT_ID, changes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
        verify(repository, never()).update(any());
    }

    @Test
    @DisplayName("update: rejects invalid product type")
    void update_rejectsInvalidType() {
        ProductEntity existing = product("HRM_ADDON");
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        ProductEntity changes = product("HRM_ADDON");
        changes.setProductType("BUNDLE");

        assertThatThrownBy(() -> service.update(PRODUCT_ID, changes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
        verify(repository, never()).update(any());
    }

    @Test
    @DisplayName("update: rejects unknown application FK")
    void update_rejectsUnknownApplication() {
        ProductEntity existing = product("HRM_ADDON");
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(repository.applicationExists(APP_ID)).thenReturn(false);

        ProductEntity changes = product("HRM_ADDON");
        changes.setApplicationId(APP_ID);

        assertThatThrownBy(() -> service.update(PRODUCT_ID, changes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown application");
        verify(repository, never()).update(any());
    }

    @Test
    @DisplayName("update: ARCHIVED status is a valid transition (archive without delete)")
    void update_archivesWithoutDelete() {
        ProductEntity existing = product("HRM_ADDON");
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        ProductEntity changes = product("HRM_ADDON");
        changes.setStatus("ARCHIVED");

        ProductEntity updated = service.update(PRODUCT_ID, changes);

        assertThat(updated.getStatus()).isEqualTo("ARCHIVED");
        verify(repository).update(updated);
    }

    @Test
    @DisplayName("update: rejects unknown product")
    void update_rejectsUnknownProduct() {
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(PRODUCT_ID, product("HRM_ADDON")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product");
    }

    // ---------------------------------------------------------------
    // reads
    // ---------------------------------------------------------------

    @Test
    @DisplayName("findById returns the product when present")
    void findById_found() {
        ProductEntity existing = product("HRM_ADDON");
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        assertThat(service.findById(PRODUCT_ID)).isEqualTo(existing);
    }

    @Test
    @DisplayName("findById throws deterministic domain error when absent")
    void findById_absent() {
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(PRODUCT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product");
    }

    @Test
    @DisplayName("findByCode normalizes and delegates")
    void findByCode_normalizes() {
        when(repository.findByCode("HRM_ADDON")).thenReturn(Optional.of(product("HRM_ADDON")));

        assertThat(service.findByCode("hrm_addon")).isPresent();
        verify(repository).findByCode("HRM_ADDON");
    }

    @Test
    @DisplayName("findAvailable lists ACTIVE products only")
    void findAvailable_activeOnly() {
        ProductEntity active = product("HRM_ADDON");
        when(repository.findAvailable()).thenReturn(List.of(active));

        List<ProductEntity> result = service.findAvailable();

        assertThat(result).containsExactly(active);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("findAll lists every product regardless of status")
    void findAll_allStatuses() {
        when(repository.findAll()).thenReturn(List.of(product("HRM_ADDON")));

        assertThat(service.findAll()).hasSize(1);
    }

    // ---------------------------------------------------------------
    // structural safety
    // ---------------------------------------------------------------

    @Test
    @DisplayName("no physical delete: service exposes no delete method")
    void service_hasNoDeleteMethod() {
        for (Method method : ProductCatalogService.class.getDeclaredMethods()) {
            assertThat(method.getName().toLowerCase())
                    .as("ProductCatalogService must not expose a delete method")
                    .doesNotContain("delete").doesNotContain("remove");
        }
    }
}
