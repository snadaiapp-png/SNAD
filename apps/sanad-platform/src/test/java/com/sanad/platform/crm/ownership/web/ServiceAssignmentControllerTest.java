package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.ServiceAssignmentUseCases;
import com.sanad.platform.crm.ownership.application.ServiceAssignmentUseCases.AssignServiceCommand;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignment;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link ServiceAssignmentController} using mocked use cases.
 *
 * <p>Uses {@code @WebMvcTest} because the service-assignment use cases query
 * {@code crm_sales_teams} which is PostgreSQL-only and does not exist on H2.
 */
@WebMvcTest(ServiceAssignmentController.class)
@Import(com.sanad.platform.security.SecurityPermitAllTestConfig.class)
class ServiceAssignmentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ServiceAssignmentUseCases serviceAssignments;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private Authentication createAuth() {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", TENANT_ID.toString());
        details.put("user_id", USER_ID.toString());
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(USER_ID.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(details);
        return token;
    }

    private ServiceAssignment sampleAssignment() {
        return new ServiceAssignment(UUID.randomUUID(), TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), ServiceAssignmentStatus.ACTIVE,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 1);
    }

    // ── GET /api/v1/crm/service-assignments ────────────────────────────────

    @Test
    void listAssignments_byTeam() throws Exception {
        UUID teamId = UUID.randomUUID();
        ServiceAssignment a = new ServiceAssignment(UUID.randomUUID(), TENANT_ID, teamId,
                UUID.randomUUID(), ServiceAssignmentStatus.ACTIVE,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 1);
        when(serviceAssignments.listByTeam(eq(TENANT_ID), eq(teamId)))
                .thenReturn(List.of(a));

        mockMvc.perform(get("/api/v1/crm/service-assignments")
                        .param("teamId", teamId.toString())
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].team_id").value(teamId.toString()));
    }

    @Test
    void listAssignments_byService() throws Exception {
        UUID serviceId = UUID.randomUUID();
        ServiceAssignment a = sampleAssignment();
        when(serviceAssignments.listByService(eq(TENANT_ID), eq(serviceId)))
                .thenReturn(List.of(a));

        mockMvc.perform(get("/api/v1/crm/service-assignments")
                        .param("serviceId", serviceId.toString())
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listAssignments_emptyWhenNoParams() throws Exception {
        mockMvc.perform(get("/api/v1/crm/service-assignments")
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/v1/crm/service-assignments/{id} ──────────────────────────

    @Test
    void getAssignment_returnsAssignment() throws Exception {
        ServiceAssignment a = sampleAssignment();
        when(serviceAssignments.getServiceAssignment(eq(TENANT_ID), eq(a.id())))
                .thenReturn(a);

        mockMvc.perform(get("/api/v1/crm/service-assignments/{id}", a.id())
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a.id().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ── POST /api/v1/crm/service-assignments ──────────────────────────────

    @Test
    void assignService_returns201() throws Exception {
        ServiceAssignment a = sampleAssignment();
        when(serviceAssignments.assignService(eq(TENANT_ID), eq(USER_ID), any(AssignServiceCommand.class)))
                .thenReturn(a);

        mockMvc.perform(post("/api/v1/crm/service-assignments")
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"teamId":"%s","serviceId":"%s"}
                                """.formatted(a.teamId(), a.serviceId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void assignService_returns400_whenMissingServiceId() throws Exception {
        mockMvc.perform(post("/api/v1/crm/service-assignments")
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/crm/service-assignments/{id}/reassign ───────────────

    @Test
    void reassignService_updatesTeamId() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID newTeamId = UUID.randomUUID();
        ServiceAssignment reassigned = new ServiceAssignment(assignmentId, TENANT_ID, newTeamId,
                UUID.randomUUID(), ServiceAssignmentStatus.ACTIVE,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 2);
        when(serviceAssignments.reassignService(eq(TENANT_ID), eq(USER_ID), eq(assignmentId), eq(newTeamId)))
                .thenReturn(reassigned);

        mockMvc.perform(patch("/api/v1/crm/service-assignments/{id}/reassign", assignmentId)
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"new_team_id":"%s"}
                                """.formatted(newTeamId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team_id").value(newTeamId.toString()));
    }

    // ── PATCH /api/v1/crm/service-assignments/{id}/complete ───────────────

    @Test
    void completeService_setsCompleted() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        ServiceAssignment completed = new ServiceAssignment(assignmentId, TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), ServiceAssignmentStatus.INACTIVE,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 2);
        when(serviceAssignments.completeService(eq(TENANT_ID), eq(USER_ID), eq(assignmentId)))
                .thenReturn(completed);

        mockMvc.perform(patch("/api/v1/crm/service-assignments/{id}/complete", assignmentId)
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ── PATCH /api/v1/crm/service-assignments/{id}/cancel ─────────────────

    @Test
    void cancelService_setsCancelled() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        ServiceAssignment cancelled = new ServiceAssignment(assignmentId, TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), ServiceAssignmentStatus.INACTIVE,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 2);
        when(serviceAssignments.cancelService(eq(TENANT_ID), eq(USER_ID), eq(assignmentId)))
                .thenReturn(cancelled);

        mockMvc.perform(patch("/api/v1/crm/service-assignments/{id}/cancel", assignmentId)
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ── AUTHENTICATION ─────────────────────────────────────────────────────

    @Test
    void listAssignments_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/crm/service-assignments"))
                .andExpect(status().isUnauthorized());
    }
}
