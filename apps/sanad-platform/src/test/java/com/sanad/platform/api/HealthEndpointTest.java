package com.sanad.platform.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for Spring Boot Actuator health endpoints.
 *
 * <p>Verifies that the health, liveness, and readiness endpoints
 * are available and return the expected status.</p>
 *
 * <p>Sensitive endpoint restrictions are enforced by the production
 * profile ({@code application-prod.yml}) which only exposes
 * {@code health}. The local profile intentionally exposes more
 * endpoints for developer convenience.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/health returns 200 with status UP")
    void health_returns200_statusUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/health/liveness returns 200 with status UP")
    void liveness_returns200_statusUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/health/readiness returns 200 with status UP")
    void readiness_returns200_statusUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/configprops returns 404 (sensitive endpoint not exposed)")
    void configprops_notExposed() throws Exception {
        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /actuator/heapdump returns 404 (sensitive endpoint not exposed)")
    void heapdump_notExposed() throws Exception {
        mockMvc.perform(get("/actuator/heapdump"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /actuator/threaddump returns 404 (sensitive endpoint not exposed)")
    void threaddump_notExposed() throws Exception {
        mockMvc.perform(get("/actuator/threaddump"))
                .andExpect(status().isNotFound());
    }
}
