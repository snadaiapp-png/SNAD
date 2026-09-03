package com.sanad.platform.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the Y2 immutable-publication contract.
 * A concrete published definition version must reject all graph mutation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowPublishedDefinitionImmutabilityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE workflow_transition_audit, workflow_approval_requests, "
                + "workflow_step_instances, workflow_instances, workflow_transitions, workflow_steps, "
                + "workflow_definitions RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Workflow Immutability', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-immut-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Workflow Immutability User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wf-immut-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    private Authentication auth() {
        var token = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return token;
    }

    @Test
    void publishedDefinitionRejectsStepAndTransitionMutation() throws Exception {
        String code = "IMMUT-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode definition = json(postJson("/api/v1/workflows/definitions", """
                {"code":"%s","name":"Immutable graph","module":"GENERAL","triggerType":"MANUAL"}
                """.formatted(code)));
        UUID definitionId = UUID.fromString(definition.get("id").asText());
        long versionLock = definition.get("versionLock").asLong();

        UUID startId = UUID.fromString(json(postJson("/api/v1/workflows/definitions/" + definitionId + "/steps", """
                {"stepKey":"start","name":"Start","stepType":"START","sequenceOrder":1,"configuration":"{}"}
                """)).get("id").asText());
        UUID taskId = UUID.fromString(json(postJson("/api/v1/workflows/definitions/" + definitionId + "/steps", """
                {"stepKey":"task","name":"Task","stepType":"ACTION","sequenceOrder":2,"configuration":"{}"}
                """)).get("id").asText());
        UUID endId = UUID.fromString(json(postJson("/api/v1/workflows/definitions/" + definitionId + "/steps", """
                {"stepKey":"end","name":"End","stepType":"END","sequenceOrder":3,"configuration":"{}"}
                """)).get("id").asText());

        postJson("/api/v1/workflows/definitions/" + definitionId + "/transitions", """
                {"fromStepId":"%s","toStepId":"%s","transitionKey":"begin","outcome":"SUCCESS","priority":10}
                """.formatted(startId, taskId));
        postJson("/api/v1/workflows/definitions/" + definitionId + "/transitions", """
                {"fromStepId":"%s","toStepId":"%s","transitionKey":"done","outcome":"SUCCESS","priority":10}
                """.formatted(taskId, endId));

        mockMvc.perform(post("/api/v1/workflows/definitions/" + definitionId + "/publish")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("{\"expectedVersion\":" + versionLock + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/definitions/" + definitionId + "/steps")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {"stepKey":"forbidden","name":"Forbidden","stepType":"ACTION","sequenceOrder":99,"configuration":"{}"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/workflows/definitions/" + definitionId + "/transitions")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {"fromStepId":"%s","toStepId":"%s","transitionKey":"forbidden","outcome":"SUCCESS","priority":0}
                                """.formatted(startId, endId)))
                .andExpect(status().isConflict());

        Integer forbiddenCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_steps WHERE workflow_definition_id = ? AND step_key = 'forbidden'",
                Integer.class, definitionId);
        assertThat(forbiddenCount).isZero();
    }

    private String postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
