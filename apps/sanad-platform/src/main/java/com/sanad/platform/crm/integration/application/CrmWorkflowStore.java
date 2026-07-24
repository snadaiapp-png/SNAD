package com.sanad.platform.crm.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Workflow-specific persistence operations that preserve AI result immutability. */
@Component
public class CrmWorkflowStore {

    private final JdbcTemplate jdbc;
    private final CrmIntegrationStore integrationStore;

    public CrmWorkflowStore(JdbcTemplate jdbc, CrmIntegrationStore integrationStore) {
        this.jdbc = jdbc;
        this.integrationStore = integrationStore;
    }

    public CrmIntegrationStore.StoredRequest attachAcceptedRun(
            UUID tenantId,
            UUID requestId,
            long expectedVersion,
            UUID workflowRunId) {
        int updated = jdbc.update(
                "UPDATE crm_integration_requests SET status='ACCEPTED', external_reference=?, " +
                        "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                        "WHERE tenant_id=? AND id=? AND version=? AND integration_type='WORKFLOW' " +
                        "AND status='DISPATCHED' AND external_reference IS NULL",
                workflowRunId, tenantId, requestId, expectedVersion);
        if (updated != 1) {
            throw new IntegrationException(
                    IntegrationErrorCode.STATE_TRANSITION_FAILED,
                    "Workflow dispatch acceptance transition conflict");
        }
        return integrationStore.find(tenantId, requestId).orElseThrow();
    }

    public CrmIntegrationStore.StoredRequest finalizeImmediateDispatch(
            UUID tenantId,
            UUID requestId,
            long expectedVersion,
            UUID workflowRunId,
            String targetStatus,
            JsonNode result,
            String errorCode) {
        CrmIntegrationStore.TransitionResult transition = integrationStore.transitionWithResult(
                tenantId,
                requestId,
                expectedVersion,
                Set.of("DISPATCHED"),
                targetStatus,
                workflowRunId,
                result,
                errorCode);
        if (!transition.success()) {
            throw new IntegrationException(
                    IntegrationErrorCode.STATE_TRANSITION_FAILED,
                    "Workflow immediate finalization conflict");
        }
        return transition.request();
    }

    public Optional<CrmIntegrationStore.StoredRequest> findByExternalReference(
            UUID tenantId,
            UUID workflowRunId) {
        return jdbc.query(
                        "SELECT id FROM crm_integration_requests " +
                                "WHERE tenant_id=? AND integration_type='WORKFLOW' AND external_reference=?",
                        (rs, row) -> (UUID) rs.getObject("id"),
                        tenantId,
                        workflowRunId)
                .stream()
                .findFirst()
                .flatMap(requestId -> integrationStore.find(tenantId, requestId));
    }
}
