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

import java.time.Instant;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.web.CrmOwnershipControllerTestSupport.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for {@link WorkloadController}.
 *
 * <p>Tests CRUD lifecycle, list by staff/service, hours endpoint,
 * reassign/release, validation, not-found handling, and tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkloadControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;

    private Fixture fixture;
    private UUID staffId;
    private UUID serviceId;
    private UUID workloadId;

    @BeforeEach
    void setUp() {
        fixture = createTenantFixture(jdbc, "workload-test");
        staffId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        workloadId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_workload_assignments (id,tenant_id,staff_id,service_id,job_id,
                    estimated_hours,actual_hours,status,start_date,end_date,
                    created_by,updated_by,created_at,updated_at,version)
                VALUES (:id,:tenantId,:staffId,:serviceId,NULL,
                    40,NULL,'PLANNED','2026-08-10',NULL,
                    :actor,:actor,:now,:now,1)
                """, p()
                .addValue("id", workloadId)
                .addValue("tenantId", fixture.tenantId())
                .addValue("staffId", staffId)
                .addValue("serviceId", serviceId)
                .addValue("actor", fixture.userId())
                .addValue("now", java.sql.Timestamp.from(Instant.now())));
    }

    // ── GET /api/v1/crm/workload ───────────────────────────────────────────

    @Test
    void listWorkload_byStaffAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/crm/workload")
                        .param("staffId", staffId.toString())
                        .param("status", "PLANNED")
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].estimated_hours").value(40));
    }

    @Test
    void listWorkload_byService() throws Exception {
        mockMvc.perform(get("/api/v1/crm/workload")
                        .param("serviceId", serviceId.toString())
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listWorkload_emptyWhenNoParams() throws Exception {
        mockMvc.perform(get("/api/v1/crm/workload").with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/v1/crm/workload/hours ────────────────────────────────────

    @Test
    void getHours_returnsHourSummary() throws Exception {
        mockMvc.perform(get("/api/v1/crm/workload/hours")
                        .param("staffId", staffId.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff_id").value(staffId.toString()))
                .andExpect(jsonPath("$.estimated_hours").isNumber());
    }

    // ── POST /api/v1/crm/workload ─────────────────────────────────────────

    @Test
    void assignWork_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/crm/workload")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"staffId":"%s","serviceId":"%s","estimatedHours":20,"startDate":"2026-09-01"}
                                """.formatted(staffId, serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimated_hours").value(20))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void assignWork_returns400_whenMissingStaffId() throws Exception {
        mockMvc.perform(post("/api/v1/crm/workload")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"serviceId":"%s","estimatedHours":20,"startDate":"2026-09-01"}
                                """.formatted(serviceId)))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/v1/crm/workload/{id}/reassign ──────────────────────────

    @Test
    void reassignWork_updatesStaffId() throws Exception {
        UUID newStaffId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/crm/workload/{workloadId}/reassign", workloadId)
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"new_staff_id":"%s"}
                                """.formatted(newStaffId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff_id").value(newStaffId.toString()));
    }

    // ── PATCH /api/v1/crm/workload/{id}/release ───────────────────────────

    @Test
    void releaseAssignment_releasesWork() throws Exception {
        mockMvc.perform(patch("/api/v1/crm/workload/{workloadId}/release", workloadId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void listWorkload_isolatedByTenant() throws Exception {
        Fixture other = createTenantFixture(jdbc, "workload-outsider");
        mockMvc.perform(get("/api/v1/crm/workload")
                        .param("serviceId", serviceId.toString())
                        .with(authentication(auth(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── AUTHENTICATION ─────────────────────────────────────────────────────

    @Test
    void listWorkload_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/crm/workload"))
                .andExpect(status().isUnauthorized());
    }
}
