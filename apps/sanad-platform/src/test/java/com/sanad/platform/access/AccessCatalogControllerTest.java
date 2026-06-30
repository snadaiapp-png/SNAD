package com.sanad.platform.access;

import com.sanad.platform.access.api.*;
import com.sanad.platform.access.capability.*;
import com.sanad.platform.access.role.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({RoleController.class, CapabilityController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(AccessApiExceptionHandler.class)
class AccessCatalogControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RoleService roleService;
    @MockBean private AccessCapabilityService capabilityService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private final UUID capabilityId = UUID.randomUUID();

    @Test
    void createRoleReturns201() throws Exception {
        when(roleService.create(any(), any())).thenReturn(roleResponse(RoleStatus.ACTIVE));
        mockMvc.perform(post("/api/v1/access/roles")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ADMIN\",\"name\":\"Admin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ADMIN"));
    }

    @Test
    void duplicateRoleReturns409() throws Exception {
        when(roleService.create(any(), any()))
                .thenThrow(new AccessConflictException("duplicate"));
        mockMvc.perform(post("/api/v1/access/roles")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ADMIN\",\"name\":\"Admin\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void listRolesReturnsTenantScopedList() throws Exception {
        when(roleService.list(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(roleResponse(RoleStatus.ACTIVE))));
        mockMvc.perform(get("/api/v1/access/roles")
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void missingTenantReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/access/roles"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleLifecycleReturnsUpdatedStatus() throws Exception {
        when(roleService.changeStatus(tenantId, roleId, RoleStatus.ARCHIVED))
                .thenReturn(roleResponse(RoleStatus.ARCHIVED));
        mockMvc.perform(patch("/api/v1/access/roles/{roleId}/archive", roleId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void createCapabilityReturns201() throws Exception {
        when(capabilityService.create("USER.READ", "Read users", null))
                .thenReturn(capabilityResponse(CapabilityStatus.ACTIVE));
        mockMvc.perform(post("/api/v1/access/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"USER.READ\",\"name\":\"Read users\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("USER.READ"));
    }

    @Test
    void invalidCapabilityReturns400() throws Exception {
        when(capabilityService.create(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid capability code"));
        mockMvc.perform(post("/api/v1/access/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad code\",\"name\":\"Read users\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capabilityLifecycleReturnsUpdatedStatus() throws Exception {
        when(capabilityService.changeStatus(capabilityId, CapabilityStatus.INACTIVE))
                .thenReturn(capabilityResponse(CapabilityStatus.INACTIVE));
        mockMvc.perform(patch(
                        "/api/v1/access/capabilities/{capabilityId}/deactivate", capabilityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    private RoleResponse roleResponse(RoleStatus status) {
        return new RoleResponse(roleId, tenantId, "ADMIN", "Admin", null,
                status, Instant.now(), Instant.now());
    }

    private CapabilityResponse capabilityResponse(CapabilityStatus status) {
        return new CapabilityResponse(capabilityId, "USER.READ", "Read users", null,
                status, Instant.now(), Instant.now());
    }
}
