package com.sanad.platform.workflow;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate G3 — direct API proof that the legacy activation endpoint cannot mutate
 * a Y2 definition. UI hiding is not authority; the server/domain must reject it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowG3ServerAuthorityApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE workflow_transition_audit, workflow_approval_requests, "
                + "workflow_step_instances, workflow_instances, workflow_steps, "
                + "workflow_definitions RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'G3 Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "g3-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'G3 User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "g3-" + userId.toString().substring(0, 8) + "@test", now, now);

        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                        + "VALUES (?, ?, 'G3_ADMIN', 'G3 Admin', 'Test', 'ACTIVE', ?, ?)",
                roleId, tenantId, now, now);
        for (var cap : jdbc.queryForList("SELECT id FROM access_capabilities WHERE code LIKE 'WORKFLOW.%'")) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, roleId, cap.get("id"), now);
        }
    }

    private Authentication auth() {
        var token = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return token;
    }

    @Test
    void directLegacyActivateRequestCannotMutateY2Definition() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/definitions")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {"code":"G3-Y2-DIRECT","name":"G3 Y2 Direct","triggerType":"MANUAL"}
                                """))
                .andExpect(status().isOk());

        var definitionId = jdbc.queryForObject(
                "SELECT id FROM workflow_definitions WHERE tenant_id = ? AND code = ?",
                UUID.class, tenantId, "G3-Y2-DIRECT");
        jdbc.update("UPDATE workflow_definitions SET engine_generation = 'Y2', publication_state = 'DRAFT' "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId, definitionId);

        mockMvc.perform(post("/api/v1/workflows/definitions/" + definitionId + "/activate")
                        .with(authentication(auth())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Y2")));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM workflow_definitions WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, definitionId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                "SELECT engine_generation FROM workflow_definitions WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, definitionId)).isEqualTo("Y2");
    }
}
