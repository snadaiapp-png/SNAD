package com.sanad.platform.api;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 03A — Flyway validation against PostgreSQL.
 *
 * <p>Unlike {@link HealthEndpointTest}, this test does NOT declare
 * {@code @ActiveProfiles("local")}. The active profile is controlled by
 * the {@code -Dspring.profiles.active} system property, which the CI
 * flyway-validation job sets to {@code prod} along with the PostgreSQL
 * datasource properties.</p>
 *
 * <p>Loading the Spring context triggers Flyway to apply all migrations
 * against the configured datasource. The CI job then queries
 * {@code flyway_schema_history} directly via psql to verify migrations
 * were applied successfully.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
class FlywayValidationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Context loads with prod profile — Flyway migrations applied")
    void contextLoadsWithProdProfile() throws Exception {
        // Just loading the context triggers Flyway. Verify health endpoint
        // to confirm the app started successfully.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
