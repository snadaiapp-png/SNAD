package com.sanad.platform.organization.membership.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.dto.InviteOrganizationMemberRequest;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipAlreadyExistsException;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipNotFoundException;
import com.sanad.platform.organization.membership.service.OrganizationMembershipService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link OrganizationMembershipController}.
 */
@WebMvcTest(OrganizationMembershipController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrganizationMembershipApiExceptionHandler.class)
class OrganizationMembershipControllerTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.sanad.platform.audit.service.TenantSecurityDenialAuditService tenantSecurityDenialAuditService;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.sanad.platform.security.denial.SecurityDenialCoordinator securityDenialCoordinator;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.sanad.platform.security.tenant.TenantContextProvider tenantContextProvider;
    @org.junit.jupiter.api.BeforeEach
    void mockTenantResolver() {
        org.mockito.Mockito.when(tenantResolver.requireTenantId()).thenReturn(tenantId);
        org.mockito.Mockito.when(tenantResolver.validateClientSelector(org.mockito.ArgumentMatchers.any())).thenReturn(tenantId);
    }


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean     private OrganizationMembershipService membershipService;

    @MockBean     private com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID organizationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID membershipId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final Instant now = Instant.now();

    private OrganizationMembershipResponse sampleResponse(MembershipStatus status) {
        return new OrganizationMembershipResponse(membershipId, tenantId, organizationId,
                "alice@example.com", "Alice", status, now, now);
    }

    // ============================================================
    // CASE 1: POST invite valid -> 201
    // ============================================================
    @Test
    @DisplayName("CASE 1: POST invite valid -> 201 Created")
    void inviteMember_valid_returns201() throws Exception {
        InviteOrganizationMemberRequest request = new InviteOrganizationMemberRequest(organizationId, "alice@example.com", "Alice");
        when(membershipService.inviteMember(eq(tenantId), any(InviteOrganizationMemberRequest.class)))
                .thenReturn(sampleResponse(MembershipStatus.INVITED));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(membershipId.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.status").value("INVITED"));
    }

    // ============================================================
    // CASE 2: POST duplicate email -> 409
    // ============================================================
    @Test
    @DisplayName("CASE 2: POST duplicate email -> 409 Conflict")
    void inviteMember_duplicate_returns409() throws Exception {
        InviteOrganizationMemberRequest request = new InviteOrganizationMemberRequest(organizationId, "alice@example.com", "Alice");
        when(membershipService.inviteMember(eq(tenantId), any(InviteOrganizationMemberRequest.class)))
                .thenThrow(new OrganizationMembershipAlreadyExistsException(tenantId, organizationId, "alice@example.com"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").exists());
    }

    // ============================================================
    // CASE 3: POST invalid request body -> 400
    // ============================================================
    @Test
    @DisplayName("CASE 3: POST invalid body -> 400 Bad Request")
    void inviteMember_invalidBody_returns400() throws Exception {
        // Missing email
        String invalidJson = objectMapper.writeValueAsString(Map.of(
                "tenantId", tenantId.toString(),
                "organizationId", organizationId.toString()
        ));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ============================================================
    // CASE 4: GET list valid -> 200
    // ============================================================
    @Test
    @DisplayName("CASE 4: GET list valid -> 200 OK")
    void listMemberships_valid_returns200() throws Exception {
        when(membershipService.listMemberships(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(sampleResponse(MembershipStatus.INVITED))));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships", organizationId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
    }

    // ============================================================
    // CASE 5: GET by id valid -> 200
    // ============================================================
    @Test
    @DisplayName("CASE 5: GET by id valid -> 200 OK")
    void getMembership_valid_returns200() throws Exception {
        when(membershipService.getMembership(eq(tenantId), eq(organizationId), eq(membershipId)))
                .thenReturn(sampleResponse(MembershipStatus.INVITED));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(membershipId.toString()))
                .andExpect(jsonPath("$.status").value("INVITED"));
    }

    // ============================================================
    // CASE 6: GET by id not found -> 404
    // ============================================================
    @Test
    @DisplayName("CASE 6: GET by id not found -> 404")
    void getMembership_notFound_returns404() throws Exception {
        when(membershipService.getMembership(eq(tenantId), eq(organizationId), eq(membershipId)))
                .thenThrow(new OrganizationMembershipNotFoundException(tenantId, organizationId, membershipId));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").exists());
    }

    // ============================================================
    // CASE 7: PATCH activate valid -> 200
    // ============================================================
    @Test
    @DisplayName("CASE 7: PATCH activate valid -> 200 OK")
    void activateMembership_valid_returns200() throws Exception {
        when(membershipService.activateMembership(eq(tenantId), eq(organizationId), eq(membershipId)))
                .thenReturn(sampleResponse(MembershipStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}/activate",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ============================================================
    // CASE 8: PATCH deactivate valid -> 200
    // ============================================================
    @Test
    @DisplayName("CASE 8: PATCH deactivate valid -> 200 OK")
    void deactivateMembership_valid_returns200() throws Exception {
        when(membershipService.deactivateMembership(eq(tenantId), eq(organizationId), eq(membershipId)))
                .thenReturn(sampleResponse(MembershipStatus.INACTIVE));

        mockMvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}/deactivate",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ============================================================
    // CASE 9: PATCH remove valid -> 200
    // ============================================================
    @Test
    @DisplayName("CASE 9: PATCH remove valid -> 200 OK")
    void removeMembership_valid_returns200() throws Exception {
        when(membershipService.removeMembership(eq(tenantId), eq(organizationId), eq(membershipId)))
                .thenReturn(sampleResponse(MembershipStatus.REMOVED));

        mockMvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}/remove",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));
    }

    // ============================================================
    // CASE 10: PATCH remove not found -> 404
    // ============================================================
    @Test
    @DisplayName("CASE 10: PATCH remove not found -> 404")
    void removeMembership_notFound_returns404() throws Exception {
        when(membershipService.removeMembership(eq(tenantId), eq(organizationId), eq(membershipId)))
                .thenThrow(new OrganizationMembershipNotFoundException(tenantId, organizationId, membershipId));

        mockMvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}/remove",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ============================================================
    // CASE 11: Missing tenantId -> 400
    // ============================================================
    @Test
    @DisplayName("CASE 11: GET list missing tenantId -> 400 Bad Request")
    void listMemberships_missingTenantId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships", organizationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists());
    }
}
