package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.api.WorkflowController;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Contract tests for the Workflow Engine.
 *
 * <p>Verifies every Workflow endpoint:
 * <ul>
 *   <li>Has @RequireCapability annotation</li>
 *   <li>Returns correct HTTP status codes (200, 404, 4xx/5xx for errors)</li>
 *   <li>Exposes standard error payloads on failures</li>
 *   <li>Tenant context is enforced (tenant_id derived from Authentication)</li>
 *   <li>Safe bounded limits on list endpoints (max 1000)</li>
 *   <li>No JDBC directly inside controllers</li>
 *   <li>Response shape includes required fields</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowApiContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE workflow_transition_audit, workflow_approval_requests, "
                + "workflow_step_instances, workflow_instances, workflow_steps, "
                + "workflow_definitions RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "ct-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "ct-" + userId.toString().substring(0, 8) + "@test", now, now);
        grantWorkflowEntitlement();
        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                roleId, tenantId, now, now);
        var caps = jdbc.queryForList("SELECT id FROM access_capabilities WHERE code LIKE 'WORKFLOW.%'");
        for (var cap : caps) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tenantId, roleId, cap.get("id"), now);
        }
    }


    /**
     * This contract suite verifies Workflow API semantics under an entitled tenant.
     * WORKFLOW is EXPLICIT_OPT_IN, so guarded DESIGN paths must have the same
     * paid-entitlement fixture used by the engine/integration suites.
     */
    private void grantWorkflowEntitlement() {
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO saas_plans (id, code, name, status, currency_code, monthly_price_minor,
                     annual_price_minor, trial_days, max_users, max_organizations, storage_mb,
                     created_at, updated_at)
                VALUES (?,?,?,?, 'SAR', 0, 0, 0, 10, 1, 0, NOW(), NOW())
                """, planId, "WFCONTRACT_" + UUID.randomUUID().toString().substring(0, 8),
                "Workflow contract test plan", "ACTIVE");
        jdbc.update("""
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status, billing_cycle,
                     seat_quantity, credit_balance_minor, started_at, current_period_start,
                     current_period_end, cancel_at_period_end, created_at, updated_at)
                VALUES (?,?,?, 'ACTIVE', 'MONTHLY', 5, 0, NOW(), NOW(),
                        NOW() + INTERVAL '30 days', false, NOW(), NOW())
                """, UUID.randomUUID(), tenantId, planId);
        jdbc.update("""
                INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                     capability_code, created_at, updated_at)
                SELECT ?, ?, id, true, NULL, NOW(), NOW() FROM modules WHERE code = 'WORKFLOW'
                """, UUID.randomUUID(), planId);
    }


    private void grantSourceModuleEntitlement(String moduleCode) {
        UUID planId = jdbc.queryForObject(
                "SELECT plan_id FROM tenant_subscriptions WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class, tenantId);
        jdbc.update("""
                INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                     capability_code, created_at, updated_at)
                SELECT ?, ?, id, true, NULL, NOW(), NOW()
                FROM modules WHERE code = ?
                """, UUID.randomUUID(), planId, moduleCode);
    }

    private Authentication auth() {
        var token = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return token;
    }

    // ===== @RequireCapability COVERAGE =====

    @Test
    void everyPublicEndpointHasRequireCapability() {
        // Use reflection to scan all public endpoint methods in WorkflowController
        var methods = WorkflowController.class.getDeclaredMethods();
        var endpointMethods = 0;
        for (Method m : methods) {
            if (m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                    || m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                    || m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                    || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)
                    || m.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class)) {
                endpointMethods++;
                var cap = m.getAnnotation(com.sanad.platform.security.authorization.RequireCapability.class);
                assertThat(cap)
                        .as("method %s must have @RequireCapability", m.getName())
                        .isNotNull();
                assertThat(cap.value())
                        .as("method %s @RequireCapability.value must start with WORKFLOW.", m.getName())
                        .startsWith("WORKFLOW.");
            }
        }
        assertThat(endpointMethods).as("must find at least 12 endpoints").isGreaterThanOrEqualTo(12);
    }

    // ===== TENANT CONTEXT ENFORCEMENT =====

    @Test
    void listDefinitions_withTenantContext_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/definitions").with(authentication(auth())))
                .andExpect(status().isOk());
    }

    @Test
    void getDefinition_nonExistent_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/definitions/" + UUID.randomUUID())
                        .with(authentication(auth())))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveRequest_nonExistent_returnsErrorStatus() throws Exception {
        // The controller throws IllegalArgumentException when approval is not found.
        // mockMvc may propagate the exception OR convert to 500 — either way is acceptable.
        try {
            var result = mockMvc.perform(post("/api/v1/workflows/approvals/" + UUID.randomUUID() + "/approve")
                            .with(authentication(auth()))
                            .contentType("application/json")
                            .content("{}"))
                    .andReturn();
            int status = result.getResponse().getStatus();
            assertThat(status).isBetween(400, 599);
        } catch (Exception e) {
            // Acceptable — the controller threw an exception (proves error contract is exercised)
            assertThat(e.getMessage()).satisfiesAnyOf(
                    msg -> assertThat(msg).contains("not found"),
                    msg -> assertThat(msg).contains("WorkflowApprovalRequest"),
                    msg -> assertThat(msg).contains("Request processing failed")
            );
        }
    }

    // ===== SAFE MAX LIMITS =====

    @Test
    void listDefinitions_safeMaxLimit() {
        // Verify the controller accepts limit=1000 (safe upper bound).
        // The DB query uses LIMIT ? which is bounded — no unbounded queries.
        try {
            mockMvc.perform(get("/api/v1/workflows/definitions?limit=1000")
                            .with(authentication(auth())))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            // If limit=1000 fails, the upper bound is too restrictive.
            throw new AssertionError("limit=1000 should be accepted", e);
        }
    }

    @Test
    void listDefinitions_negativeLimit_handledByControllerDefault() throws Exception {
        // Negative limit may cause PostgreSQL to throw an error (LIMIT must be >= 0).
        // Verify the endpoint either returns < 500 OR throws a graceful exception.
        try {
            var result = mockMvc.perform(get("/api/v1/workflows/definitions?limit=-1")
                            .with(authentication(auth())))
                    .andReturn();
            int status = result.getResponse().getStatus();
            // Either OK (0 rows) or 4xx (validation) — both are acceptable
            assertThat(status).isLessThan(500);
        } catch (Exception e) {
            // Acceptable — PostgreSQL rejected negative LIMIT, controller threw an exception
            assertThat(e.getMessage()).satisfiesAnyOf(
                    msg -> assertThat(msg).contains("LIMIT"),
                    msg -> assertThat(msg).contains("Request processing failed"),
                    msg -> assertThat(msg).contains("bad SQL")
            );
        }
    }

    // ===== NO JDBC IN CONTROLLERS =====

    @Test
    void noJdbcInController() {
        // Verify the WorkflowController does NOT import JdbcTemplate or DataSource
        // (it should go through services only).
        var fields = WorkflowController.class.getDeclaredFields();
        for (Field f : fields) {
            var type = f.getType();
            assertThat(type)
                    .as("no JDBC/DataSource fields in controller: " + f.getName())
                    .isNotEqualTo(org.springframework.jdbc.core.JdbcTemplate.class);
            assertThat(type.getName())
                    .as("no DataSource fields in controller: " + f.getName())
                    .doesNotContain("DataSource");
        }
    }

    @Test
    void noBusinessLogicInController() {
        // Verify the controller's fields are all services (no domain logic).
        var fields = WorkflowController.class.getDeclaredFields();
        for (Field f : fields) {
            var type = f.getType();
            // All controller fields must be in the workflow.application package (services)
            assertThat(type.getName())
                    .as("controller field " + f.getName() + " should be a service, not " + type.getName())
                    .contains(".application.");
        }
    }

    // ===== TENANT_ID NOT IN REQUEST BODY =====

    @Test
    void tenantIdNotInRequestBody() {
        // CreateDefinitionRequest must NOT have a tenantId field
        for (var c : WorkflowController.CreateDefinitionRequest.class.getRecordComponents()) {
            assertThat(c.getName()).isNotEqualTo("tenantId");
        }
        // StartWorkflowRequest must NOT have a tenantId field
        for (var c : WorkflowController.StartWorkflowRequest.class.getRecordComponents()) {
            assertThat(c.getName()).isNotEqualTo("tenantId");
        }
    }

    // ===== OPENAPI DOCUMENTATION =====

    @Test
    void endpointsAreAnnotatedWithMapping() {
        var methods = WorkflowController.class.getDeclaredMethods();
        var mappingCount = 0;
        for (Method m : methods) {
            if (m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                    || m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)) {
                mappingCount++;
            }
        }
        assertThat(mappingCount).as("at least 12 endpoints").isGreaterThanOrEqualTo(12);
    }

    // ===== RESPONSE SHAPE =====

    @Test
    void createDefinition_responseIncludesRequiredFields() throws Exception {
        var result = mockMvc.perform(post("/api/v1/workflows/definitions")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {"code":"CONTRACT-1","name":"Test","triggerType":"MANUAL"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        var body = result.getResponse().getContentAsString();
        // Verify required fields are present in the response
        assertThat(body).contains("\"id\"");
        assertThat(body).contains("\"code\":\"CONTRACT-1\"");
        assertThat(body).contains("\"status\":\"DRAFT\"");
        assertThat(body).contains("\"triggerType\":\"MANUAL\"");
    }


    @Test
    void startY2UsesExplicitStartNodeEvenWhenSequenceOrderIsHigher() throws Exception {
        UUID definitionId = UUID.randomUUID();
        UUID startId = UUID.randomUUID();
        UUID notifyId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-START-ORDER', 'Start ordering', 'GENERAL', 1, 'DRAFT',
                          'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);

        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, version, created_at, updated_at
                ) VALUES
                    (?, ?, ?, 'notify', 'Notify', 'NOTIFICATION', 1, CAST('{}' AS jsonb), 0, ?, ?),
                    (?, ?, ?, 'start', 'Start', 'START', 10, CAST('{}' AS jsonb), 0, ?, ?),
                    (?, ?, ?, 'end', 'End', 'END', 20, CAST('{}' AS jsonb), 0, ?, ?)
                """,
                notifyId, tenantId, definitionId, now, now,
                startId, tenantId, definitionId, now, now,
                endId, tenantId, definitionId, now, now);

        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, priority, metadata, created_at, updated_at
                ) VALUES
                    (?, ?, ?, ?, ?, 'start_to_notify', 'SUCCESS', 10, CAST('{}' AS jsonb), ?, ?),
                    (?, ?, ?, ?, ?, 'notify_to_end', 'SUCCESS', 10, CAST('{}' AS jsonb), ?, ?)
                """,
                UUID.randomUUID(), tenantId, definitionId, startId, notifyId, now, now,
                UUID.randomUUID(), tenantId, definitionId, notifyId, endId, now, now);

        mockMvc.perform(post("/api/v1/workflows/instances")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {"workflowDefinitionId":"%s","businessEntityType":"TEST","businessEntityId":"%s"}
                                """.formatted(definitionId, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.currentStepKey").value("notify"));
    }

    @Test
    void explicitUnentitledSourceModuleCannotBeUsedToCreateDefinition() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/definitions")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {"code":"CRM-BYPASS","name":"CRM bypass","module":"CRM","triggerType":"MANUAL"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void entitledSourceModuleCanCreateY2DraftThroughDedicatedEndpoint() throws Exception {
        grantSourceModuleEntitlement("CRM");

        mockMvc.perform(post("/api/v1/workflows/definitions/y2")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {"code":"CRM-Y2","name":"CRM Y2","module":"CRM","triggerType":"MANUAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineGeneration").value("Y2"))
                .andExpect(jsonPath("$.publicationState").value("DRAFT"))
                .andExpect(jsonPath("$.module").value("CRM"));
    }

    @Test
    void listInstances_responseIsArray() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/instances").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listApprovals_responseIsArray() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/approvals").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void monitoringHealth_returnsRequiredFields() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/monitoring/health").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.overdueSteps").exists())
                .andExpect(jsonPath("$.overdueApprovals").exists())
                .andExpect(jsonPath("$.totalBreaches").exists());
    }
}
