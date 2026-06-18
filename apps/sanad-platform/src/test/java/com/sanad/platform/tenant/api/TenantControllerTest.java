package com.sanad.platform.tenant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.api.OrganizationApiExceptionHandler;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.dto.CreateTenantRequest;
import com.sanad.platform.tenant.dto.TenantResponse;
import com.sanad.platform.tenant.dto.UpdateTenantRequest;
import com.sanad.platform.tenant.exception.TenantAlreadyExistsException;
import com.sanad.platform.tenant.service.TenantService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link TenantController}.
 */
@WebMvcTest(TenantController.class)
@Import({TenantApiExceptionHandler.class, OrganizationApiExceptionHandler.class})
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantService tenantService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final Instant now = Instant.now();

    @Test
    @DisplayName("CASE 1: POST valid -> 201 Created")
    void createTenant_valid_returns201() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme");
        TenantResponse response = new TenantResponse(tenantId, "Acme Corp", "acme",
                TenantStatus.ACTIVE, now, now);
        when(tenantService.createTenant(any(CreateTenantRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(tenantId.toString()))
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.subdomain").value("acme"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("CASE 2: POST duplicate subdomain -> 409 Conflict")
    void createTenant_duplicate_returns409() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme");
        when(tenantService.createTenant(any(CreateTenantRequest.class)))
                .thenThrow(new TenantAlreadyExistsException("acme"));

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Tenant already exists with subdomain: acme")))
                .andExpect(jsonPath("$.path").value("/api/v1/tenants"));
    }

    @Test
    @DisplayName("CASE 3: POST invalid body (blank name) -> 400")
    void createTenant_invalidBody_returns400() throws Exception {
        String invalidJson = "{\"name\":\"\",\"subdomain\":\"acme\"}";

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("CASE 4: GET list -> 200 OK")
    void listTenants_returns200() throws Exception {
        TenantResponse r1 = new TenantResponse(tenantId, "Acme", "acme",
                TenantStatus.ACTIVE, now, now);
        when(tenantService.listTenants()).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/tenants").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Acme"));
    }

    @Test
    @DisplayName("CASE 5: GET by id -> 200 OK")
    void getTenant_returns200() throws Exception {
        TenantResponse response = new TenantResponse(tenantId, "Acme", "acme",
                TenantStatus.ACTIVE, now, now);
        when(tenantService.getTenant(eq(tenantId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/tenants/{id}", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantId.toString()))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    @DisplayName("CASE 6: GET by id not found -> 404")
    void getTenant_notFound_returns404() throws Exception {
        when(tenantService.getTenant(eq(tenantId)))
                .thenThrow(new EntityNotFoundException("Tenant not found with id: " + tenantId));

        mockMvc.perform(get("/api/v1/tenants/{id}", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Tenant not found with id")));
    }

    @Test
    @DisplayName("CASE 7: PUT update -> 200 OK")
    void updateTenant_returns200() throws Exception {
        UpdateTenantRequest request = new UpdateTenantRequest("Acme Renamed");
        TenantResponse response = new TenantResponse(tenantId, "Acme Renamed", "acme",
                TenantStatus.ACTIVE, now, now);
        when(tenantService.updateTenant(eq(tenantId), any(UpdateTenantRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/tenants/{id}", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Renamed"))
                .andExpect(jsonPath("$.subdomain").value("acme")); // unchanged
    }

    @Test
    @DisplayName("CASE 8: PATCH activate -> 200 OK")
    void activateTenant_returns200() throws Exception {
        TenantResponse response = new TenantResponse(tenantId, "Acme", "acme",
                TenantStatus.ACTIVE, now, now);
        when(tenantService.activateTenant(eq(tenantId))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/tenants/{id}/activate", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("CASE 9: PATCH deactivate -> 200 OK")
    void deactivateTenant_returns200() throws Exception {
        TenantResponse response = new TenantResponse(tenantId, "Acme", "acme",
                TenantStatus.SUSPENDED, now, now);
        when(tenantService.deactivateTenant(eq(tenantId))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/tenants/{id}/deactivate", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("CASE 10: PATCH archive -> 200 OK")
    void archiveTenant_returns200() throws Exception {
        TenantResponse response = new TenantResponse(tenantId, "Acme", "acme",
                TenantStatus.ARCHIVED, now, now);
        when(tenantService.archiveTenant(eq(tenantId))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/tenants/{id}/archive", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("CASE 11: PATCH activate not found -> 404")
    void activateTenant_notFound_returns404() throws Exception {
        when(tenantService.activateTenant(eq(tenantId)))
                .thenThrow(new EntityNotFoundException("Tenant not found with id: " + tenantId));

        mockMvc.perform(patch("/api/v1/tenants/{id}/activate", tenantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
