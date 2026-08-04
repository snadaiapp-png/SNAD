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
 * MockMvc integration tests for {@link AvailabilityController}.
 *
 * <p>Tests CRUD lifecycle, calendar query, approve/reject, delete, validation,
 * not-found handling, and tenant isolation for availability endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class AvailabilityControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;

    private Fixture fixture;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        fixture = createTenantFixture(jdbc, "avail-test");
        staffId = UUID.randomUUID();
    }

    private UUID seedAvailability(String type, String startDate, String endDate) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_staff_availability (id,tenant_id,staff_id,type,start_date,end_date,
                    created_by,updated_by,created_at,updated_at,version)
                VALUES (:id,:tenantId,:staffId,:type,:startDate,:endDate,
                    :actor,:actor,:now,:now,1)
                """, p()
                .addValue("id", id)
                .addValue("tenantId", fixture.tenantId())
                .addValue("staffId", staffId)
                .addValue("type", type)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("actor", fixture.userId())
                .addValue("now", java.time.Instant.now()));
        return id;
    }

    // ── GET /api/v1/crm/availability ───────────────────────────────────────

    @Test
    void calendarQuery_returnsAvailability() throws Exception {
        seedAvailability("AVAILABLE", "2026-08-10", "2026-08-14");

        mockMvc.perform(get("/api/v1/crm/availability")
                        .param("staffId", staffId.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].type").value("AVAILABLE"));
    }

    // ── POST /api/v1/crm/availability ─────────────────────────────────────

    @Test
    void submitAvailability_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/crm/availability")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"staffId":"%s","type":"ON_LEAVE","startDate":"2026-08-10","endDate":"2026-08-20","reason":"Vacation"}
                                """.formatted(staffId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ON_LEAVE"))
                .andExpect(jsonPath("$.reason").value("Vacation"));
    }

    @Test
    void submitAvailability_withOptionalTimeFields() throws Exception {
        mockMvc.perform(post("/api/v1/crm/availability")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"staffId":"%s","type":"AVAILABLE","startDate":"2026-08-10","endDate":"2026-08-10",
                                 "startTime":"09:00:00","endTime":"12:00:00"}
                                """.formatted(staffId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.start_time").value("09:00"))
                .andExpect(jsonPath("$.end_time").value("12:00"));
    }

    @Test
    void submitAvailability_returns400_whenMissingType() throws Exception {
        mockMvc.perform(post("/api/v1/crm/availability")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"staffId":"%s","startDate":"2026-08-10","endDate":"2026-08-20"}
                                """.formatted(staffId)))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/crm/availability/{id}/approve ───────────────────────

    @Test
    void approveAvailability_returnsOk() throws Exception {
        UUID availId = seedAvailability("ON_LEAVE", "2026-08-10", "2026-08-14");
        mockMvc.perform(patch("/api/v1/crm/availability/{id}/approve", availId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk());
    }

    // ── PATCH /api/v1/crm/availability/{id}/reject ────────────────────────

    @Test
    void rejectAvailability_returnsOk() throws Exception {
        UUID availId = seedAvailability("ON_LEAVE", "2026-08-10", "2026-08-14");
        mockMvc.perform(patch("/api/v1/crm/availability/{id}/reject", availId)
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"reason":"Insufficient notice"}
                                """))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/v1/crm/availability/{id} ──────────────────────────────

    @Test
    void deleteAvailability_returns204() throws Exception {
        UUID availId = seedAvailability("AVAILABLE", "2026-08-10", "2026-08-14");
        mockMvc.perform(delete("/api/v1/crm/availability/{id}", availId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isNoContent());
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void calendarQuery_isolatedByTenant() throws Exception {
        seedAvailability("AVAILABLE", "2026-08-10", "2026-08-14");
        Fixture other = createTenantFixture(jdbc, "avail-outsider");

        mockMvc.perform(get("/api/v1/crm/availability")
                        .param("staffId", staffId.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .with(authentication(auth(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── AUTHENTICATION ─────────────────────────────────────────────────────

    @Test
    void calendarQuery_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/crm/availability")
                        .param("staffId", UUID.randomUUID().toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isUnauthorized());
    }
}
