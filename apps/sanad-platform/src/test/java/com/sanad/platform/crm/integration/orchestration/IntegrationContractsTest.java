package com.sanad.platform.crm.integration.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationContractsTest {

    @Test
    void envelopeRejectsInvalidExpiryAndDetectsExpiration() {
        Instant now = Instant.parse("2026-07-23T18:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> envelope(now, now));
        IntegrationEnvelope valid = envelope(now, now.plusSeconds(30));
        assertFalse(valid.isExpired(now.plusSeconds(29)));
        assertTrue(valid.isExpired(now.plusSeconds(30)));
    }

    @Test
    void workflowOnlyCompletedResultPermitsMutation() {
        UUID run = UUID.randomUUID();
        var accepted = new WorkflowIntegrationPort.WorkflowDispatch(
                run, WorkflowIntegrationPort.Status.ACCEPTED, Instant.now(), null);
        var completed = new WorkflowIntegrationPort.WorkflowDispatch(
                run, WorkflowIntegrationPort.Status.COMPLETED, Instant.now(), null);
        assertFalse(accepted.permitsMutation());
        assertTrue(completed.permitsMutation());
    }

    @Test
    void unavailableAiOutputIsSuppressed() {
        var result = new AiGatewayPort.AiResult(
                AiGatewayPort.Status.UNAVAILABLE, "must not leak", "CALL", "reason", .9,
                Instant.now(), Instant.now().plusSeconds(60), true,
                List.of("secret-source"), "policy-v1", "model-v1");
        assertNull(result.generatedText());
        assertNull(result.actionCode());
        assertTrue(result.sourceReferences().isEmpty());
        assertFalse(result.actionable());
    }

    @Test
    void actionableAiOutputRequiresExplicitHumanConfirmation() {
        assertThrows(IllegalArgumentException.class, () -> new AiGatewayPort.AiResult(
                AiGatewayPort.Status.AVAILABLE, "Call customer", "CALL_CUSTOMER", "Renewal due", .8,
                Instant.now(), Instant.now().plusSeconds(60), false,
                List.of("account:1"), "policy-v1", "model-v1"));
    }

    private static IntegrationEnvelope envelope(Instant requestedAt, Instant expiresAt) {
        return new IntegrationEnvelope(
                "crm.customer-summary", "1.0", UUID.randomUUID(), UUID.randomUUID(),
                "corr-1", "cause-1", "idem-1", "ACCOUNT", UUID.randomUUID(), 1,
                requestedAt, expiresAt, Locale.ENGLISH, "CRM.AI.READ", "CONFIDENTIAL");
    }
}
