package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.SalesTeamUseCases;
import com.sanad.platform.crm.ownership.application.TeamManagementUseCases;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
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
 * MockMvc tests for {@link TeamController} using mocked use cases.
 *
 * <p>Uses {@code @WebMvcTest} because {@code crm_sales_teams} is PostgreSQL-only
 * and does not exist on H2. Tests HTTP routing, serialization, validation,
 * and not-found handling against mocked service layer.
 */
@WebMvcTest(TeamController.class)
@Import(com.sanad.platform.security.SecurityPermitAllTestConfig.class)
class TeamControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SalesTeamUseCases salesTeams;
    @MockBean TeamManagementUseCases teamManagement;

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

    private SalesTeam sampleTeam() {
        return new SalesTeam(UUID.randomUUID(), TENANT_ID, "TEAM-ALPHA", "Team Alpha",
                "Description", TeamStatus.ACTIVE, null, null, null,
                Instant.now(), Instant.now(), USER_ID, USER_ID);
    }

    // ── GET /api/v1/crm/teams ─────────────────────────────────────────────

    @Test
    void listTeams_returnsTeamList() throws Exception {
        SalesTeam team = sampleTeam();
        when(teamManagement.searchTeams(eq(TENANT_ID), eq(TeamStatus.ACTIVE), eq(null)))
                .thenReturn(List.of(team));

        mockMvc.perform(get("/api/v1/crm/teams")
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TEAM-ALPHA"));
    }

    @Test
    void listTeams_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/crm/teams"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/crm/teams ────────────────────────────────────────────

    @Test
    void createTeam_returns201() throws Exception {
        SalesTeam team = sampleTeam();
        when(salesTeams.createTeam(eq(TENANT_ID), eq(USER_ID), any())).thenReturn(team);

        mockMvc.perform(post("/api/v1/crm/teams")
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"code":"NEW-TEAM","displayName":"New Team","description":"A new team"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TEAM-ALPHA"));
    }

    @Test
    void createTeam_returns400_whenCodeBlank() throws Exception {
        mockMvc.perform(post("/api/v1/crm/teams")
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"code":"","displayName":"New Team"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/crm/teams/{teamId} ──────────────────────────────────

    @Test
    void updateTeam_appliesChanges() throws Exception {
        UUID teamId = UUID.randomUUID();
        SalesTeam updated = new SalesTeam(teamId, TENANT_ID, "TEAM-ALPHA", "Updated Team",
                "Updated", TeamStatus.ACTIVE, null, null, null,
                Instant.now(), Instant.now(), USER_ID, USER_ID);
        when(salesTeams.getTeam(eq(TENANT_ID), eq(teamId))).thenReturn(sampleTeam());
        when(salesTeams.updateTeam(eq(TENANT_ID), eq(USER_ID), eq(teamId), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/crm/teams/{teamId}", teamId)
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Updated Team","description":"Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("Updated Team"));
    }

    // ── PATCH /api/v1/crm/teams/{teamId}/archive ──────────────────────────

    @Test
    void archiveTeam_setsStatus() throws Exception {
        UUID teamId = UUID.randomUUID();
        SalesTeam archived = new SalesTeam(teamId, TENANT_ID, "TEAM-ALPHA", "Team Alpha",
                "Description", TeamStatus.ARCHIVED, null, null, null,
                Instant.now(), Instant.now(), USER_ID, USER_ID);
        when(salesTeams.archiveTeam(eq(TENANT_ID), eq(USER_ID), eq(teamId))).thenReturn(archived);

        mockMvc.perform(patch("/api/v1/crm/teams/{teamId}/archive", teamId)
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    // ── PATCH /api/v1/crm/teams/{teamId}/activate ─────────────────────────

    @Test
    void activateTeam_setsActive() throws Exception {
        UUID teamId = UUID.randomUUID();
        SalesTeam activated = new SalesTeam(teamId, TENANT_ID, "TEAM-ALPHA", "Team Alpha",
                "Description", TeamStatus.ACTIVE, null, null, null,
                Instant.now(), Instant.now(), USER_ID, USER_ID);
        when(teamManagement.activateTeam(eq(TENANT_ID), eq(USER_ID), eq(teamId))).thenReturn(activated);

        mockMvc.perform(patch("/api/v1/crm/teams/{teamId}/activate", teamId)
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
