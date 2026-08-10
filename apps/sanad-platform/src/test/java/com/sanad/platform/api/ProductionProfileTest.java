package com.sanad.platform.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

/**
 * Production-profile integration tests using PostgreSQL Direct.
 *
 * <p>Verifies that the {@code prod} profile loads correctly against a
 * real PostgreSQL database, health/liveness/readiness endpoints work,
 * sensitive actuator endpoints are not exposed, and Swagger is disabled.</p>
 *
 * <p>Automatically disabled when PostgreSQL Direct is not available (e.g. local
 * dev environment without PostgreSQL). Runs in CI where PostgreSQL is present.</p>
 */
@SpringBootTest(
    properties = {
        "sanad.cors.allowed-origins=https://snad-app.vercel.app",
        "sanad.production-guard.enabled=false",
        "management.health.mail.enabled=false",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sanad",
        "spring.datasource.username=sanad",
        "spring.datasource.password="
    }
)
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisabledIf("postgresqlNotAvailable")
class ProductionProfileTest {

    static boolean postgresqlNotAvailable() {
        try {
            return !Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("ProductionProfileTest");
        } catch (Exception e) {
            return true;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"));
        registry.add("spring.datasource.username", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"));
        registry.add("spring.datasource.password", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Prod profile: health endpoint returns UP")
    void health_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Prod profile: liveness endpoint returns UP")
    void liveness_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Prod profile: readiness endpoint returns UP")
    void readiness_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Prod profile: env endpoint NOT exposed")
    void env_notExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Prod profile: beans endpoint NOT exposed")
    void beans_notExposed() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Prod profile: configprops endpoint NOT exposed")
    void configprops_notExposed() throws Exception {
        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Prod profile: heapdump endpoint NOT exposed")
    void heapdump_notExposed() throws Exception {
        mockMvc.perform(get("/actuator/heapdump"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Prod profile: threaddump endpoint NOT exposed")
    void threaddump_notExposed() throws Exception {
        mockMvc.perform(get("/actuator/threaddump"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Prod profile: Swagger API docs disabled")
    void swaggerApiDocs_disabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Prod profile: Swagger UI disabled")
    void swaggerUi_disabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }
}
