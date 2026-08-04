package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.CapacityManagementUseCases;
import com.sanad.platform.crm.ownership.application.CapacityManagementUseCases.CapacityForecast;
import com.sanad.platform.crm.ownership.application.CapacityManagementUseCases.CreateCapacityPlanCommand;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityPlan;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityStatus;
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
 * MockMvc tests for {@link CapacityController} using mocked use cases.
 *
 * <p>Uses {@code @WebMvcTest} because the capacity-management use cases query
 * {@code crm_sales_teams} which is PostgreSQL-only and does not exist on H2.
 */
@WebMvcTest(CapacityController.class)
@Import(com.sanad.platform.security.SecurityPermitAllTestConfig.class)
class CapacityControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CapacityManagementUseCases capacityManagement;

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

    private CapacityPlan samplePlan() {
        return new CapacityPlan(UUID.randomUUID(), TENANT_ID, UUID.randomUUID(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100, 0, CapacityStatus.DRAFT,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 1);
    }

    // ── GET /api/v1/crm/capacity ───────────────────────────────────────────

    @Test
    void listPlans_returnsPlans() throws Exception {
        UUID teamId = UUID.randomUUID();
        CapacityPlan plan = samplePlan();
        when(capacityManagement.listCapacityPlans(eq(TENANT_ID), eq(teamId)))
                .thenReturn(List.of(plan));

        mockMvc.perform(get("/api/v1/crm/capacity")
                        .param("teamId", teamId.toString())
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].max_capacity").value(100));
    }

    // ── GET /api/v1/crm/capacity/{planId} ──────────────────────────────────

    @Test
    void getPlan_returnsPlan() throws Exception {
        CapacityPlan plan = samplePlan();
        when(capacityManagement.getCapacityPlan(eq(TENANT_ID), eq(plan.id())))
                .thenReturn(plan);

        mockMvc.perform(get("/api/v1/crm/capacity/{planId}", plan.id())
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plan.id().toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.remaining_capacity").value(100));
    }

    // ── POST /api/v1/crm/capacity ─────────────────────────────────────────

    @Test
    void createPlan_returns201() throws Exception {
        CapacityPlan plan = samplePlan();
        when(capacityManagement.createCapacityPlan(eq(TENANT_ID), eq(USER_ID), any(CreateCapacityPlanCommand.class)))
                .thenReturn(plan);

        mockMvc.perform(post("/api/v1/crm/capacity")
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"teamId":"%s","periodStart":"2026-09-01","periodEnd":"2026-09-30","maxCapacity":200}
                                """.formatted(plan.teamId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.max_capacity").value(100))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createPlan_returns400_whenMaxCapacityZero() throws Exception {
        mockMvc.perform(post("/api/v1/crm/capacity")
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"teamId":"%s","periodStart":"2026-09-01","periodEnd":"2026-09-30","maxCapacity":0}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/crm/capacity/{planId} ───────────────────────────────

    @Test
    void adjustCapacity_appliesChanges() throws Exception {
        CapacityPlan plan = samplePlan();
        CapacityPlan adjusted = new CapacityPlan(plan.id(), TENANT_ID, plan.teamId(),
                plan.periodStart(), plan.periodEnd(),
                150, 50, CapacityStatus.DRAFT,
                USER_ID, USER_ID, Instant.now(), Instant.now(), 2);
        when(capacityManagement.adjustCapacity(eq(TENANT_ID), eq(USER_ID), eq(plan.id()), any()))
                .thenReturn(adjusted);

        mockMvc.perform(patch("/api/v1/crm/capacity/{planId}", plan.id())
                        .with(authentication(createAuth()))
                        .contentType("application/json")
                        .content("""
                                {"maxCapacity":150,"allocatedCapacity":50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.max_capacity").value(150))
                .andExpect(jsonPath("$.allocated_capacity").value(50))
                .andExpect(jsonPath("$.remaining_capacity").value(100));
    }

    // ── GET /api/v1/crm/capacity/forecast ──────────────────────────────────

    @Test
    void forecastCapacity_returnsForecast() throws Exception {
        UUID teamId = UUID.randomUUID();
        CapacityForecast forecast = new CapacityForecast(teamId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100, 0, 0.0);
        when(capacityManagement.forecastCapacity(eq(TENANT_ID), eq(teamId),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
                .thenReturn(forecast);

        mockMvc.perform(get("/api/v1/crm/capacity/forecast")
                        .param("teamId", teamId.toString())
                        .param("periodStart", "2026-08-01")
                        .param("periodEnd", "2026-08-31")
                        .with(authentication(createAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team_id").value(teamId.toString()))
                .andExpect(jsonPath("$.forecasted_max_capacity").value(100));
    }

    // ── AUTHENTICATION ─────────────────────────────────────────────────────

    @Test
    void listPlans_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/crm/capacity")
                        .param("teamId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
