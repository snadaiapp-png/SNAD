package com.sanad.platform.organization.membership.api;

import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipUserLinkConflictException;
import com.sanad.platform.organization.membership.service.OrganizationMembershipUserLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationMembershipAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MembershipAssignmentApiExceptionHandler.class)
class OrganizationMembershipAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private OrganizationMembershipUserLinkService assignmentService;
    @MockBean
    private com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID organizationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID membershipId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @org.junit.jupiter.api.BeforeEach
    void mockTenantResolver() {
        org.mockito.Mockito.when(tenantResolver.validateClientSelector(org.mockito.ArgumentMatchers.any())).thenReturn(tenantId);
        org.mockito.Mockito.when(tenantResolver.requireTenantId()).thenReturn(tenantId);
    }

    @Test
    void assignReturnsLinkedMembership() throws Exception {
        when(assignmentService.linkUser(tenantId, organizationId, membershipId, userId))
                .thenReturn(response(userId));

        mockMvc.perform(patch(
                        "/api/v1/organizations/{organizationId}/memberships/{membershipId}/assign/{userId}",
                        organizationId, membershipId, userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void assignConflictReturns409() throws Exception {
        when(assignmentService.linkUser(tenantId, organizationId, membershipId, userId))
                .thenThrow(new OrganizationMembershipUserLinkConflictException(
                        membershipId, userId, "Membership email must match the user email"));

        mockMvc.perform(patch(
                        "/api/v1/organizations/{organizationId}/memberships/{membershipId}/assign/{userId}",
                        organizationId, membershipId, userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unassignReturnsNullUserId() throws Exception {
        when(assignmentService.unlinkUser(tenantId, organizationId, membershipId))
                .thenReturn(response(null));

        mockMvc.perform(patch(
                        "/api/v1/organizations/{organizationId}/memberships/{membershipId}/unassign",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isEmpty());
    }

    @Test
    void missingTenantReturns400() throws Exception {
        mockMvc.perform(patch(
                        "/api/v1/organizations/{organizationId}/memberships/{membershipId}/assign/{userId}",
                        organizationId, membershipId, userId))
                .andExpect(status().isBadRequest());
    }

    private OrganizationMembershipResponse response(UUID linkedUserId) {
        return new OrganizationMembershipResponse(
                membershipId, tenantId, organizationId, linkedUserId,
                "alice@example.com", "Alice", MembershipStatus.ACTIVE,
                Instant.now(), Instant.now());
    }
}
