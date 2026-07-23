package com.sanad.platform.crm.integration.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationContractsTest {

    @Test
    void envelopeRejectsExpiredOrInvalidWindow() {
        Instant now = Instant.parse("2026-07-23T18:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> envelope(now, now));

        IntegrationEnvelope valid = envelope(now, now.plusSeconds(30));
        assertFalse(valid.isExpired(now.plusSeconds(29)));
        assertTrue(valid.isExpired(now.plusSeconds(30)));
    }

    @Test
    void workflowOnlyApprovedOrCompletedResultsPermitMutation() {
        Instant now = Instant.parse("2026-07-23T18:00:00Z");
        var pending = new WorkflowIntegrationPort.WorkflowReference(
                UUID.randomUUID(), WorkflowIntegrationPort.Status.PENDING, 3, now, null);
        var approved = new WorkflowIntegrationPort.WorkflowReference(
                UUID.randomUUID(), WorkflowIntegrationPort.Status.APPROVED, 3, now, null);

        assertFalse(pending.permitsMutation());
        assertTrue(approved.permitsMutation());
    }

    @Test
    void unavailableAiResultCannotLeakAdvisoryPayload() {
        Instant now = Instant.parse("2026-07-23T18:00:00Z");
        var result = new AiGatewayPort.AiResult(
                AiGatewayPort.Status.TIMEOUT,
                "must be removed",
                "REASSIGN",
                "must be removed",
                0.9,
                now,
                now.plusSeconds(60),
                List.of("contact:1"),
                "ALLOW",
                false,
                "AI_TIMEOUT");

        assertNull(result.advisoryText());
        assertNull(result.actionCode());
        assertNull(result.explanation());
        assertNull(result.confidence());
        assertTrue(result.sourceReferences().isEmpty());
        assertTrue(result.isAdvisoryOnly());
    }

    @Test
    void actionableAiRecommendationRequiresHumanConfirmation() {
        Instant now = Instant.parse("2026-07-23T18:00:00Z");
        var result = new AiGatewayPort.AiResult(
                AiGatewayPort.Status.AVAILABLE,
                "Consider reassignment",
                "REASSIGN",
                "Queue SLA risk",
                0.75,
                now,
                now.plusSeconds(60),
                List.of("queue:item:1"),
                "ALLOW_WITH_CONFIRMATION",
                false,
                null);

        assertTrue(result.requiresHumanConfirmation());
    }

    private static IntegrationEnvelope envelope(Instant requestedAt, Instant expiresAt) {
        return new IntegrationEnvelope(
                "crm.workflow.assignment",
                "1.0",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "corr-1",
                "cause-1",
                "idem-1",
                "CONTACT",
                UUID.randomUUID(),
                1,
                requestedAt,
                expiresAt,
                Locale.ENGLISH,
                "crm.assignments.manage",
                "CONFIDENTIAL");
    }
}
