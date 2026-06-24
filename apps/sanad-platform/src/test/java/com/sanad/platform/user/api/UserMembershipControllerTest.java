package com.sanad.platform.user.api;

import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.service.OrganizationMembershipUserLinkService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link UserMembershipController}.
 *
 * <p>DEFECT-021 remediation: verifies that the {@code listMemberships}
 * endpoint declares the {@code MEMBERSHIP.READ} capability and returns
 * the expected payload.</p>
 */
@WebMvcTest(UserMembershipController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserApiExceptionHandler.class)
class UserMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrganizationMembershipUserLinkService assignmentService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID organizationId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID membershipId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final Instant now = Instant.parse("2026-06-24T00:00:00Z");

    /**
     * DEFECT-021: the {@code listMemberships} method MUST be annotated
     * with {@code @RequireCapability("MEMBERSHIP.READ")}. This is a
     * static guarantee that survives even when the Spring AOP proxy is
     * not exercised in a slice test.
     */
    @Test
    @DisplayName("DEFECT-021: listMemberships is annotated with @RequireCapability(\"MEMBERSHIP.READ\")")
    void listMemberships_hasRequireCapabilityAnnotation() throws NoSuchMethodException {
        Method method = UserMembershipController.class.getMethod(
                "listMemberships", UUID.class, UUID.class);

        RequireCapability annotation = method.getAnnotation(RequireCapability.class);

        assertThat(annotation)
                .as("listMemberships must declare @RequireCapability to satisfy RBAC convention")
                .isNotNull();
        assertThat(annotation.value())
                .isEqualTo("MEMBERSHIP.READ");
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/memberships returns 200 with list")
    void listMemberships_returns200() throws Exception {
        OrganizationMembershipResponse response = new OrganizationMembershipResponse(
                membershipId, tenantId, organizationId,
                "alice@example.com", "Alice",
                com.sanad.platform.organization.membership.domain.MembershipStatus.ACTIVE,
                now, now);

        when(assignmentService.listMembershipsByUser(eq(tenantId), eq(userId)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/users/{userId}/memberships", userId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(membershipId.toString()))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/memberships with no memberships returns 200 empty list")
    void listMemberships_empty_returns200Empty() throws Exception {
        when(assignmentService.listMembershipsByUser(eq(tenantId), eq(userId)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/{userId}/memberships", userId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
