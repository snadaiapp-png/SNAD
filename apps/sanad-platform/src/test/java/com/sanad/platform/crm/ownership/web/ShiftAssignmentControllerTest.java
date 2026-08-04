package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.ShiftManagementUseCases;
import com.sanad.platform.crm.ownership.application.ShiftManagementUseCases.CreateShiftAssignmentCommand;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignment;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentStatus;
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
import java.time.LocalDate;
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
 * MockMvc tests for {@link ShiftAssignmentController} using mocked use cases.
 *
 * <p>Uses {@code @WebMvcTest} because the shift-management use cases query
 * {@code crm_sales_teams} which is PostgreSQL-only and does not exist on H2.
 */
@WebMvcTest(ShiftAssignmentController.class)
@Import(com.sanad.platform.security.SecurityPermitAllTestConfig.class)
class ShiftAssignmentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ShiftManagementUseCases shiftManagement;

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

    private ShiftAssignment sampleAssignment() {
        return new ShiftAssignment(UUID.randomUUID(), TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                ShiftAssignmentStatus.SCHEDULED,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 1);
    }

    // ── GET /api/v1/crm/shift-assignments ─────────────────────────────────

    @Test
    void listAssignments_byTeam() throws Exception {
        UUID teamId = UUID.randomUUID();
        ShiftAssignment a = new ShiftAssignment(UUID.randomUUID(), TENANT_ID, teamId,
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                ShiftAssignmentStatus.SCHEDULED,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 1);
        when(shiftManagement.listShiftAssignmentsByTeam(eq(TENANT_ID), eq(teamId), eq(50), eq(0)))
                .thenReturn(List.of(a));

        mockMvc.perform(get("/api/v1/crm/shift-assignments")
                        .param("teamId", teamId.toString())
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].team_id").value(teamId.toString()));
    }

    @Test
    void listAssignments_emptyWhenNoParams() throws Exception {
        mockMvc.perform(get("/api/v1/crm/shift-assignments")
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/v1/crm/shift-assignments ────────────────────────────────

    @Test
    void assignShift_returns201() throws Exception {
        ShiftAssignment a = sampleAssignment();
        when(shiftManagement.assignShift(eq(TENANT_ID), eq(USER_ID), any(CreateShiftAssignmentCommand.class)))
                .thenReturn(a);

        mockMvc.perform(post("/api/v1/crm/shift-assignments")
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"teamId":"%s","staffId":"%s","shiftTemplateId":"%s","startDate":"2026-09-01","endDate":"2026-09-05"}
                                """.formatted(a.teamId(), a.staffId(), a.shiftTemplateId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void assignShift_returns400_whenMissingRequiredField() throws Exception {
        mockMvc.perform(post("/api/v1/crm/shift-assignments")
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/crm/shift-assignments/{assignmentId} ────────────────

    @Test
    void updateAssignment_appliesChanges() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment updated = sampleAssignment();
        when(shiftManagement.updateShiftAssignment(eq(TENANT_ID), eq(USER_ID), eq(assignmentId), any()))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/crm/shift-assignments/{assignmentId}", assignmentId)
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"startDate":"2026-08-11","endDate":"2026-08-15"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    // ── PATCH /api/v1/crm/shift-assignments/{assignmentId}/cancel ─────────

    @Test
    void cancelAssignment_setsCancelled() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        ShiftAssignment cancelled = new ShiftAssignment(assignmentId, TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                ShiftAssignmentStatus.CANCELLED,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 1);
        when(shiftManagement.cancelShiftAssignment(eq(TENANT_ID), eq(USER_ID), eq(assignmentId)))
                .thenReturn(cancelled);

        mockMvc.perform(patch("/api/v1/crm/shift-assignments/{assignmentId}/cancel", assignmentId)
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void listAssignments_isolatedByTenant() throws Exception {
        UUID teamId = UUID.randomUUID();
        when(shiftManagement.listShiftAssignmentsByTeam(eq(TENANT_ID), eq(teamId), eq(50), eq(0)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/crm/shift-assignments")
                        .param("teamId", teamId.toString())
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── AUTHENTICATION ─────────────────────────────────────────────────────

    @Test
    void listAssignments_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/crm/shift-assignments"))
                .andExpect(status().isUnauthorized());
    }
}
