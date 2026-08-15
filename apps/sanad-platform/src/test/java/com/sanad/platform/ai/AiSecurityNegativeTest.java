package com.sanad.platform.ai;

import com.sanad.platform.ai.api.AiController;
import com.sanad.platform.ai.application.AiAgentService;
import com.sanad.platform.ai.application.AiExecutionService;
import com.sanad.platform.ai.domain.*;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security negative tests for the AI Module.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class AiSecurityNegativeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AiAgentService agentService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE ai_inference_log, ai_agents RESTART IDENTITY CASCADE");

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        for (var tid : List.of(tenantA, tenantB)) {
            jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                    + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tid, "Tenant " + tid.toString().substring(0, 8),
                    "ais-" + tid.toString().substring(0, 8), now, now);
        }
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User A', 'ACTIVE', 'dummy', ?, ?)",
                userA, tenantA, "ais-" + userA.toString().substring(0, 8) + "@test", now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User B', 'ACTIVE', 'dummy', ?, ?)",
                userB, tenantB, "ais-" + userB.toString().substring(0, 8) + "@test", now, now);

        for (var tid : List.of(tenantA, tenantB)) {
            var uid = tid == tenantA ? userA : userB;
            var roleId = UUID.randomUUID();
            jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                    + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                    roleId, tid, now, now);
            var caps = jdbc.queryForList("SELECT id FROM access_capabilities WHERE code LIKE 'AI.%'");
            for (var cap : caps) {
                jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                        + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tid, roleId, cap.get("id"), now);
            }
        }
    }

    private Authentication auth(UUID tid, UUID uid) {
        var token = new UsernamePasswordAuthenticationToken(
                uid.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tid.toString(), "user_id", uid.toString()));
        return token;
    }

    // ===== @RequireCapability COVERAGE =====

    @Test
    void everyEndpointHasRequireCapability() {
        var methods = AiController.class.getDeclaredMethods();
        var endpointCount = 0;
        for (Method m : methods) {
            if (m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                    || m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)) {
                endpointCount++;
                var cap = m.getAnnotation(com.sanad.platform.security.authorization.RequireCapability.class);
                assertThat(cap).as("method " + m.getName() + " must have @RequireCapability").isNotNull();
                assertThat(cap.value()).as("method " + m.getName() + " capability must start with AI.").startsWith("AI.");
            }
        }
        assertThat(endpointCount).as("must find at least 8 endpoints").isGreaterThanOrEqualTo(8);
    }

    // ===== CROSS-TENANT ISOLATION =====

    @Test
    void crossTenant_agentReadReturnsNotFound() throws Exception {
        // Create agent in tenantA
        var agent = AiAgent.create(
                tenantA, "SEC-XT-1", "Test", "Test",
                AiAgent.Provider.DETERMINISTIC, null, null, null, null, null, userA);
        var saved = agentService.create(agent);

        // TenantB tries to read tenantA's agent → 404
        mockMvc.perform(get("/api/v1/ai/agents/" + saved.id())
                        .with(authentication(auth(tenantB, userB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenant_agentListDoesNotLeak() throws Exception {
        agentService.create(AiAgent.create(
                tenantA, "SEC-LEAK-1", "A", "Test",
                AiAgent.Provider.DETERMINISTIC, null, null, null, null, null, userA));

        // TenantB should see 0 agents
        var result = mockMvc.perform(get("/api/v1/ai/agents")
                        .with(authentication(auth(tenantB, userB))))
                .andExpect(status().isOk())
                .andReturn();
        // The response should be an empty array
        assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void crossTenant_inferenceListDoesNotLeak() throws Exception {
        // TenantB should see 0 inferences
        var result = mockMvc.perform(get("/api/v1/ai/inferences")
                        .with(authentication(auth(tenantB, userB))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
    }

    // ===== TENANT_ID NOT IN REQUEST BODY =====

    @Test
    void tenantIdNotInRequestBody() {
        for (var c : AiController.CreateAgentRequest.class.getRecordComponents()) {
            assertThat(c.getName()).isNotEqualTo("tenantId");
        }
        for (var c : AiController.ExecuteRequest.class.getRecordComponents()) {
            assertThat(c.getName()).isNotEqualTo("tenantId");
        }
    }

    // ===== NO JDBC IN CONTROLLER =====

    @Test
    void noJdbcInController() {
        for (var f : AiController.class.getDeclaredFields()) {
            var typeName = f.getType().getName();
            assertThat(typeName).doesNotContain("JdbcTemplate");
            assertThat(typeName).doesNotContain("DataSource");
        }
    }

    @Test
    void controllerFieldsAreApplicationServices() {
        for (var f : AiController.class.getDeclaredFields()) {
            assertThat(f.getType().getName())
                    .as("controller field " + f.getName() + " must be in ai.application package")
                    .contains(".application.");
        }
    }
}
