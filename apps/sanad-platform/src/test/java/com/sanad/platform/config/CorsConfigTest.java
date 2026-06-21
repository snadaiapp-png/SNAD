package com.sanad.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for {@link CorsConfig}.
 *
 * <p>Verifies that CORS headers are present for allowed origins
 * and absent for disallowed origins on API routes.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CORS: allowed origin receives Access-Control-Allow-Origin header")
    void cors_allowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/organizations")
                        .header("Origin", "https://snad-app.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().string(
                        "Access-Control-Allow-Origin", "https://snad-app.vercel.app"));
    }

    @Test
    @DisplayName("CORS: disallowed origin does not receive Allow-Origin header")
    void cors_disallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/organizations")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS: allowed methods include GET, POST, PUT, PATCH, DELETE, OPTIONS")
    void cors_allowedMethods() throws Exception {
        mockMvc.perform(options("/api/v1/organizations")
                        .header("Origin", "https://snad-app.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("GET")))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PUT")))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PATCH")))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("DELETE")));
    }

    @Test
    @DisplayName("CORS: actuator routes are not CORS-enabled")
    void cors_actuatorNotEnabled() throws Exception {
        mockMvc.perform(options("/actuator/health")
                        .header("Origin", "https://snad-app.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
