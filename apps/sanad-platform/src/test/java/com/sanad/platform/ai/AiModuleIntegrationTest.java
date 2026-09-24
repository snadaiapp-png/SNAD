package com.sanad.platform.ai;

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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive integration test for the AI Module.
 *
 * Covers:
 * - Agent lifecycle (create → activate → deactivate → archive)
 * - Inference execution (deterministic provider)
 * - Advisory-only enforcement
 * - Cross-tenant isolation
 * - Quota counting
 * - Business entity linking
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class AiModuleIntegrationTest {

    @Autowired private AiAgentService agentService;
    @Autowired private AiExecutionService executionService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE ai_inference_log, ai_agents RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "ai-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "ai-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    private AiAgent buildActiveAgent(String code) {
        var agent = AiAgent.create(
                tenantId, code, "Test Agent " + code, "Test description",
                AiAgent.Provider.DETERMINISTIC, null, "You are a test agent.",
                null, 1000, 0.7, userId);
        var saved = agentService.create(agent);
        return agentService.activate(tenantId, saved.id(), userId);
    }

    // ===== AGENT LIFECYCLE =====

    @Test
    void agentLifecycle_createActivateDeactivateArchive() {
        var agent = AiAgent.create(
                tenantId, "AGENT-1", "Test Agent", "Test",
                AiAgent.Provider.DETERMINISTIC, null, "prompt",
                null, 100, 0.5, userId);
        var created = agentService.create(agent);
        assertThat(created.status()).isEqualTo(AiAgent.Status.DRAFT);

        var activated = agentService.activate(tenantId, created.id(), userId);
        assertThat(activated.status()).isEqualTo(AiAgent.Status.ACTIVE);

        var deactivated = agentService.deactivate(tenantId, created.id(), userId);
        assertThat(deactivated.status()).isEqualTo(AiAgent.Status.INACTIVE);

        var archived = agentService.archive(tenantId, created.id(), userId);
        assertThat(archived.status()).isEqualTo(AiAgent.Status.ARCHIVED);
    }

    @Test
    void agentLifecycle_cannotActivateFromArchived() {
        var agent = buildActiveAgent("AGENT-2");
        agentService.deactivate(tenantId, agent.id(), userId);
        agentService.archive(tenantId, agent.id(), userId);

        assertThatThrownBy(() -> agentService.activate(tenantId, agent.id(), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot activate from ARCHIVED");
    }

    @Test
    void agentLifecycle_duplicateCodeRejected() {
        var agent1 = AiAgent.create(
                tenantId, "DUP-CODE", "First", "Test",
                AiAgent.Provider.DETERMINISTIC, null, null, null, null, null, userId);
        agentService.create(agent1);

        var agent2 = AiAgent.create(
                tenantId, "DUP-CODE", "Second", "Test",
                AiAgent.Provider.DETERMINISTIC, null, null, null, null, null, userId);
        assertThatThrownBy(() -> agentService.create(agent2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ===== INFERENCE EXECUTION =====

    @Test
    void inferenceExecution_deterministicProvider() {
        var agent = buildActiveAgent("INF-1");
        var inference = executionService.execute(
                tenantId, agent.id(), userId,
                "Test input for inference", null, null, null);

        assertThat(inference.status()).isEqualTo(AiInference.Status.COMPLETED);
        assertThat(inference.advisory()).isTrue();
        assertThat(inference.outputSummary()).contains("[ADVISORY]");
        assertThat(inference.tokensInput()).isPositive();
        assertThat(inference.tokensOutput()).isPositive();
        assertThat(inference.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(inference.costCents()).isZero();  // deterministic is free
    }

    @Test
    void inferenceExecution_cannotInvokeInactiveAgent() {
        var agent = buildActiveAgent("INF-2");
        agentService.deactivate(tenantId, agent.id(), userId);

        assertThatThrownBy(() -> executionService.execute(
                tenantId, agent.id(), userId, "input", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not ACTIVE");
    }

    @Test
    void inferenceExecution_cannotInvokeNonExistentAgent() {
        assertThatThrownBy(() -> executionService.execute(
                tenantId, UUID.randomUUID(), userId, "input", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AiAgent not found");
    }

    // ===== ADVISORY-ONLY ENFORCEMENT =====

    @Test
    void advisoryOnly_allInferencesAreAdvisory() {
        var agent = buildActiveAgent("ADV-1");
        var inference = executionService.execute(
                tenantId, agent.id(), userId, "input", null, null, null);

        // The advisory field is ALWAYS true — it cannot be set to false
        assertThat(inference.advisory()).isTrue();
        assertThat(AiInference.ADVISORY_ONLY).isTrue();
    }

    // ===== CROSS-TENANT ISOLATION =====

    @Test
    void crossTenant_agentReadReturnsEmpty() {
        var agent = buildActiveAgent("XT-1");
        var otherTenant = UUID.randomUUID();

        // Other tenant should not see this agent
        var found = agentService.findById(otherTenant, agent.id());
        assertThat(found).isEmpty();
    }

    @Test
    void crossTenant_inferenceReadReturnsEmpty() {
        var agent = buildActiveAgent("XT-2");
        var inference = executionService.execute(
                tenantId, agent.id(), userId, "input", null, null, null);

        var otherTenant = UUID.randomUUID();
        var found = executionService.findInferenceById(otherTenant, inference.id());
        assertThat(found).isEmpty();
    }

    @Test
    void crossTenant_listAgentsDoesNotLeak() {
        buildActiveAgent("XT-3");
        var otherTenant = UUID.randomUUID();

        var otherTenantAgents = agentService.findByTenant(otherTenant, 100);
        assertThat(otherTenantAgents).isEmpty();
    }

    // ===== QUOTA COUNTING =====

    @Test
    void quotaCount_incrementsAfterExecution() {
        var agent = buildActiveAgent("QUOTA-1");
        var before = executionService.countThisMonth(tenantId);
        assertThat(before).isZero();

        executionService.execute(tenantId, agent.id(), userId, "input1", null, null, null);
        assertThat(executionService.countThisMonth(tenantId)).isEqualTo(1);

        executionService.execute(tenantId, agent.id(), userId, "input2", null, null, null);
        assertThat(executionService.countThisMonth(tenantId)).isEqualTo(2);
    }

    // ===== BUSINESS ENTITY LINKING =====

    @Test
    void businessEntityLinking_inferenceLinkedToEntity() {
        var agent = buildActiveAgent("ENTITY-1");
        var entityId = UUID.randomUUID();
        var inference = executionService.execute(
                tenantId, agent.id(), userId, "input", null,
                "DECISION", entityId);

        var byEntity = executionService.findInferencesByBusinessEntity(tenantId, "DECISION", entityId);
        assertThat(byEntity).hasSize(1);
        assertThat(byEntity.get(0).id()).isEqualTo(inference.id());
    }

    // ===== INFERENCE LISTING =====

    @Test
    void inferenceListing_byAgent() {
        var agent = buildActiveAgent("LIST-1");
        executionService.execute(tenantId, agent.id(), userId, "input1", null, null, null);
        executionService.execute(tenantId, agent.id(), userId, "input2", null, null, null);

        var byAgent = executionService.findInferencesByAgent(tenantId, agent.id(), 100);
        assertThat(byAgent).hasSize(2);
    }
}
