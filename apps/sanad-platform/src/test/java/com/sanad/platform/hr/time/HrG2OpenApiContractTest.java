package com.sanad.platform.hr.time;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * G2 OpenAPI contract test — verifies that all G2 V2 endpoints are
 * documented in the auto-generated OpenAPI (springdoc-openapi).
 *
 * <p>Controller ↔ OpenAPI drift = FAILURE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrG2OpenApiContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void g2ScheduleEndpointsAreDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();
        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode paths = api.path("paths");

        assertThat(paths.has("/api/v2/hr/time/schedules"))
                .as("GET /api/v2/hr/time/schedules must be documented in OpenAPI").isTrue();
    }

    @Test
    void g2TimesheetEndpointsAreDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();
        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode paths = api.path("paths");

        assertThat(paths.has("/api/v2/hr/time/timesheets"))
                .as("GET /api/v2/hr/time/timesheets must be documented").isTrue();
    }

    @Test
    void g2AttendanceEndpointsAreDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();
        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode paths = api.path("paths");

        assertThat(paths.has("/api/v2/hr/time/attendance"))
                .as("GET /api/v2/hr/time/attendance must be documented").isTrue();
        assertThat(paths.has("/api/v2/hr/time/attendance/clock-in"))
                .as("POST /api/v2/hr/time/attendance/clock-in must be documented").isTrue();
        assertThat(paths.has("/api/v2/hr/time/attendance/monthly-report"))
                .as("GET /api/v2/hr/time/attendance/monthly-report must be documented").isTrue();
    }

    @Test
    void g2LeaveEndpointsAreDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();
        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode paths = api.path("paths");

        assertThat(paths.has("/api/v2/hr/leave/types"))
                .as("GET /api/v2/hr/leave/types must be documented").isTrue();
        assertThat(paths.has("/api/v2/hr/leave/requests"))
                .as("GET/POST /api/v2/hr/leave/requests must be documented").isTrue();
        assertThat(paths.has("/api/v2/hr/leave/balances"))
                .as("GET /api/v2/hr/leave/balances must be documented").isTrue();
    }

    @Test
    void g2LeaveRequestActionEndpointsAreDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();
        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode paths = api.path("paths");

        // ALL 7 leave action endpoints must be documented (not >= 5)
        String[] expectedActionPaths = {
            "/api/v2/hr/leave/requests/{requestId}/submit",
            "/api/v2/hr/leave/requests/{requestId}/manager-approve",
            "/api/v2/hr/leave/requests/{requestId}/manager-reject",
            "/api/v2/hr/leave/requests/{requestId}/hr-approve",
            "/api/v2/hr/leave/requests/{requestId}/hr-reject",
            "/api/v2/hr/leave/requests/{requestId}/withdraw",
            "/api/v2/hr/leave/requests/{requestId}/cancel",
        };
        for (String path : expectedActionPaths) {
            assertThat(paths.has(path))
                    .as("Endpoint %s must be documented in OpenAPI", path)
                    .isTrue();
        }
    }
}
