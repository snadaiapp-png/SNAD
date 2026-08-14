package com.sanad.platform.crm.integration.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpIntegrationAdaptersTest {

    @Test
    void aiGatewayFailsSafelyWhenUnconfigured() {
        var adapter = new HttpAiGatewayAdapter(new ObjectMapper(), "", 1000);
        var result = adapter.request(envelope(), AiGatewayPort.Capability.CUSTOMER_SUMMARY,
                new ObjectMapper().createObjectNode());
        assertEquals(AiGatewayPort.Status.UNAVAILABLE, result.status());
        assertNull(result.generatedText());
        assertFalse(result.actionable());
    }

    @Test
    void workflowGatewayFailsClosedWhenUnconfigured() {
        var adapter = new HttpWorkflowIntegrationAdapter(new ObjectMapper(), "", 1000);
        var result = adapter.dispatch(envelope(), "CRM_TRANSFER_APPROVAL",
                new ObjectMapper().createObjectNode());
        assertEquals(WorkflowIntegrationPort.Status.UNAVAILABLE, result.status());
        assertFalse(result.permitsMutation());
        assertNull(result.workflowRunId());
    }

    private static IntegrationEnvelope envelope() {
        Instant now = Instant.now();
        return new IntegrationEnvelope(
                "crm.integration.test", "1.0", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "idem-test",
                "ACCOUNT", UUID.randomUUID(), 1, now, now.plusSeconds(30), Locale.ENGLISH,
                "CRM.AI.READ", "INTERNAL");
    }
}
