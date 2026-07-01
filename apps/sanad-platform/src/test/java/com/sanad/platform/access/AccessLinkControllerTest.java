package com.sanad.platform.access;

import com.sanad.platform.access.api.*;
import com.sanad.platform.access.grant.*;
import com.sanad.platform.access.role.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({RoleAccessController.class, UserAccessController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(AccessApiExceptionHandler.class)
class AccessLinkControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RoleCapabilityService mappingService;
    @MockBean private UserRoleGrantService grantService;
    @MockBean private com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private final UUID capabilityId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void mockTenantResolver() {
        org.mockito.Mockito.when(tenantResolver.validateClientSelector(org.mockito.ArgumentMatchers.any())).thenReturn(tenantId);
        org.mockito.Mockito.when(tenantResolver.requireTenantId()).thenReturn(tenantId);
    }
    private final UUID organizationId = UUID.randomUUID();
    private final UUID grantId = UUID.randomUUID();

    @Test
    void attachCatalogItemReturns201() throws Exception {
        when(mappingService.attach(tenantId, roleId, capabilityId))
                .thenReturn(roleAccess());
        mockMvc.perform(post(
                        "/api/v1/access/roles/{roleId}/access-items/{capabilityId}",
                        roleId, capabilityId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capabilityCode").value("USER.READ"));
    }

    @Test
    void detachCatalogItemReturns204() throws Exception {
        mockMvc.perform(delete(
                        "/api/v1/access/roles/{roleId}/access-items/{capabilityId}",
                        roleId, capabilityId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNoContent());
        verify(mappingService).detach(tenantId, roleId, capabilityId);
    }

    @Test
    void listRoleCatalogItemsReturnsList() throws Exception {
        when(mappingService.list(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(roleId), org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(roleAccess())));
        mockMvc.perform(get("/api/v1/access/roles/{roleId}/access-items", roleId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void grantOrganizationRoleReturns201() throws Exception {
        when(grantService.grant(tenantId, userId, roleId, organizationId))
                .thenReturn(userAccess(UserGrantStatus.ACTIVE));
        mockMvc.perform(post(
                        "/api/v1/access/users/{userId}/role-links/{roleId}",
                        userId, roleId)
                        .param("tenantId", tenantId.toString())
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleCode").value("ADMIN"));
    }

    @Test
    void listUserRoleLinksReturnsList() throws Exception {
        when(grantService.list(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(userAccess(UserGrantStatus.ACTIVE))));
        mockMvc.perform(get("/api/v1/access/users/{userId}/role-links", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void revokeRoleLinkReturnsRevokedStatus() throws Exception {
        when(grantService.revoke(tenantId, grantId))
                .thenReturn(userAccess(UserGrantStatus.REVOKED));
        mockMvc.perform(patch(
                        "/api/v1/access/users/role-links/{grantId}/revoke", grantId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void unknownUserReturns404() throws Exception {
        when(grantService.list(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenThrow(new AccessResourceNotFoundException("User not found"));
        mockMvc.perform(get("/api/v1/access/users/{userId}/role-links", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound());
    }

    private RoleAccessResponse roleAccess() {
        return new RoleAccessResponse(UUID.randomUUID(), tenantId, roleId,
                capabilityId, "USER.READ", Instant.now());
    }

    private UserAccessResponse userAccess(UserGrantStatus status) {
        return new UserAccessResponse(grantId, tenantId, userId, roleId,
                "ADMIN", organizationId, status, Instant.now(), Instant.now());
    }
}
