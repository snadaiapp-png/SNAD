package com.sanad.platform.shared.api;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 03A §9 — Verifies that the {@link GlobalExceptionHandler} does NOT
 * interfere with actuator endpoints that should remain outside the
 * ControllerAdvice scope.
 *
 * <p>Specifically: requests to non-existent actuator endpoints (e.g.
 * {@code /actuator/heapdump} when only {@code health,info} are exposed)
 * must return 404 from Spring Boot's actuator machinery, NOT a 500 from
 * the catch-all {@code @ExceptionHandler(Exception.class)}.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class GlobalExceptionHandlerActuatorExclusionTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/health returns 200 (not intercepted by ControllerAdvice)")
    void healthEndpoint_notIntercepted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/configprops (not exposed) returns 404, not 500")
    void configprops_notExposed_returns404() throws Exception {
        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /actuator/heapdump (not exposed) returns 404, not 500")
    void heapdump_notExposed_returns404() throws Exception {
        mockMvc.perform(get("/actuator/heapdump"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /actuator/threaddump (not exposed) returns 404, not 500")
    void threaddump_notExposed_returns404() throws Exception {
        mockMvc.perform(get("/actuator/threaddump"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /actuator/info returns 200 (not intercepted)")
    void infoEndpoint_notIntercepted() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
