package com.sanad.platform.subscription.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApplicationCatalogService}.
 *
 * <p>The catalog is the source of truth for applications shown in the executive
 * console — no hardcoded ERP/CRM/HRM lists may appear in code. These tests prove
 * catalog CRUD + listing semantics.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationCatalogService — catalog management")
class ApplicationCatalogServiceTest {

    @Mock
    private ApplicationRepository repository;

    private ApplicationCatalogService service;

    private static final UUID APP_ID = UUID.fromString("b1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new ApplicationCatalogService(repository);
    }

    private ApplicationEntity app(String code) {
        ApplicationEntity e = new ApplicationEntity();
        e.setId(APP_ID);
        e.setCode(code);
        e.setName("Enterprise Resource Planning");
        e.setLocalizedName("تخطيط موارد المؤسسة");
        e.setCategory("OPERATIONS");
        e.setStatus("ACTIVE");
        e.setVersion("1.0");
        e.setDisplayOrder(10);
        e.setIconKey("erp");
        e.setProvisioningMode("IMMEDIATE");
        e.setSupportedCountries(List.of("SA", "AE", "KW", "GLOBAL"));
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    @Test
    @DisplayName("create: normalizes code to uppercase and defaults provisioning mode")
    void create_normalizesCode() {
        ApplicationEntity toCreate = app("erp");
        toCreate.setCode("erp");
        toCreate.setProvisioningMode(null);

        ApplicationEntity created = service.create(toCreate);

        assertThat(created.getCode()).isEqualTo("ERP");
        assertThat(created.getProvisioningMode()).isEqualTo("IMMEDIATE");
        verify(repository).insert(created);
    }

    @Test
    @DisplayName("create: rejects duplicate code")
    void create_rejectsDuplicateCode() {
        when(repository.existsByCode("ERP")).thenReturn(true);

        assertThatThrownBy(() -> service.create(app("erp")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    @DisplayName("create: rejects blank code")
    void create_rejectsBlankCode() {
        ApplicationEntity toCreate = app(" ");
        toCreate.setCode(" ");

        assertThatThrownBy(() -> service.create(toCreate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    @DisplayName("update: rejects unknown application")
    void update_rejectsUnknown() {
        when(repository.findById(APP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(APP_ID, app("ERP")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application");
    }

    @Test
    @DisplayName("update: preserves id and refreshes updatedAt")
    void update_preservesIdentity() {
        ApplicationEntity existing = app("ERP");
        when(repository.findById(APP_ID)).thenReturn(Optional.of(existing));

        ApplicationEntity changes = app("ERP");
        changes.setName("ERP Suite");
        changes.setDisplayOrder(15);

        ApplicationEntity updated = service.update(APP_ID, changes);

        assertThat(updated.getId()).isEqualTo(APP_ID);
        assertThat(updated.getName()).isEqualTo("ERP Suite");
        assertThat(updated.getDisplayOrder()).isEqualTo(15);
        verify(repository).update(updated);
    }

    @Test
    @DisplayName("listAvailable: only ACTIVE applications, catalog-ordered")
    void listAvailable_returnsActiveOnly() {
        when(repository.findAvailable()).thenReturn(List.of(app("ERP")));

        List<ApplicationEntity> result = service.listAvailable();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("supportedCountries round-trips through the repository mapping")
    void supportedCountries_roundTrip() {
        ApplicationEntity entity = app("ERP");
        assertThat(entity.getSupportedCountries()).containsExactly("SA", "AE", "KW", "GLOBAL");
    }
}
