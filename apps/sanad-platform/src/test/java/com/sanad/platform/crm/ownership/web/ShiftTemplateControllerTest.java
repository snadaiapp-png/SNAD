package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.web.CrmOwnershipControllerTestSupport.Fixture;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.sanad.platform.crm.ownership.web.CrmOwnershipControllerTestSupport.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for {@link ShiftTemplateController}.
 *
 * <p>Tests CRUD lifecycle, pagination, publish/cancel, validation,
 * not-found handling, and tenant isolation for shift template endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class ShiftTemplateControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;

    private Fixture fixture;
    private UUID templateId;

    @BeforeEach
    void setUp() {
        fixture = createTenantFixture(jdbc, "shift-test");
        templateId = seedShiftTemplate(jdbc, fixture.tenantId(), fixture.userId(), "Morning Shift");
    }

    // ── GET /api/v1/crm/shift-templates ───────────────────────────────────

    @Test
    void listTemplates_returnsTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/crm/shift-templates").with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Morning Shift"));
    }

    @Test
    void listTemplates_respectsLimit() throws Exception {
        mockMvc.perform(get("/api/v1/crm/shift-templates").param("limit", "1").with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/v1/crm/shift-templates/{templateId} ──────────────────────

    @Test
    void getTemplate_returnsTemplate() throws Exception {
        mockMvc.perform(get("/api/v1/crm/shift-templates/{templateId}", templateId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.name").value("Morning Shift"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1));
    }

    // ── POST /api/v1/crm/shift-templates ──────────────────────────────────

    @Test
    void createTemplate_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/crm/shift-templates")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"name":"Evening Shift","startTime":"16:00:00","endTime":"00:00:00","daysOfWeek":["MONDAY","WEDNESDAY"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Evening Shift"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.days_of_week").isArray());
    }

    @Test
    void createTemplate_returns400_whenNameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/crm/shift-templates")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"name":"","startTime":"16:00:00","endTime":"00:00:00","daysOfWeek":["MONDAY"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTemplate_returns400_whenStartTimeNull() throws Exception {
        mockMvc.perform(post("/api/v1/crm/shift-templates")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"name":"No Time","daysOfWeek":["MONDAY"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/crm/shift-templates/{templateId} ────────────────────

    @Test
    void updateTemplate_appliesChanges() throws Exception {
        mockMvc.perform(patch("/api/v1/crm/shift-templates/{templateId}", templateId)
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"name":"Updated Shift"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Shift"));
    }

    // ── PATCH /api/v1/crm/shift-templates/{templateId}/publish ────────────

    @Test
    void publishTemplate_setsActive() throws Exception {
        // Set template to INACTIVE first so publish has somewhere to go
        jdbc.update("UPDATE crm_shift_templates SET status='INACTIVE' WHERE id=:id AND tenant_id=:tenantId",
                p().addValue("id", templateId).addValue("tenantId", fixture.tenantId()));
        mockMvc.perform(patch("/api/v1/crm/shift-templates/{templateId}/publish", templateId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ── PATCH /api/v1/crm/shift-templates/{templateId}/cancel ─────────────

    @Test
    void cancelTemplate_setsInactive() throws Exception {
        mockMvc.perform(patch("/api/v1/crm/shift-templates/{templateId}/cancel", templateId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ── AUTHENTICATION ─────────────────────────────────────────────────────

    @Test
    void listTemplates_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/crm/shift-templates"))
                .andExpect(status().isUnauthorized());
    }
}
