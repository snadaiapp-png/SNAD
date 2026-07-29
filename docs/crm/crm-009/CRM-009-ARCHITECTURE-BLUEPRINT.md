# CRM-009 Architecture Blueprint

> **Module:** CRM-009 — Workflow Engine & AI Gateway Integration
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Architecture Overview

CRM-009 follows the existing hexagonal architecture pattern established in CRM-008R, with clear separation between domain, application, infrastructure, and web layers.

### 1.1 Layer Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Web Layer                            │
│  CrmWorkflowController, CrmIntegrationController        │
│  CrmWorkflowCallbackController                         │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│                Application Layer                        │
│  CrmWorkflowUseCases, CrmIntegrationUseCases            │
│  CrmWorkflowOutboxWorker, CrmIntegrationOutboxWorker    │
│  ConfirmedRecommendationExecutor                        │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│                  Domain Layer                           │
│  IntegrationEnvelope, WorkflowIntegrationPort           │
│  AiGatewayPort, CrmIntegrationStore                     │
│  AuditPort, TimelineEventPort                           │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│              Infrastructure Layer                       │
│  HttpWorkflowIntegrationAdapter                         │
│  HttpAiGatewayAdapter                                   │
│  JdbcAuditAdapter, JdbcTimelineEventAdapter             │
│  ServiceJwtProvider, WorkflowCallbackSecurity           │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Domain Models

### 2.1 IntegrationEnvelope

| Field | Type | Description |
|-------|------|-------------|
| contractName | String | Workflow/AI contract identifier |
| contractVersion | String | Contract version |
| tenantId | UUID | Tenant identifier |
| actorId | UUID | User performing action |
| correlationId | String | Request correlation ID |
| causationId | String | Causal chain identifier |
| idempotencyKey | String | Idempotency key |
| sourceEntityType | String | CRM entity type |
| sourceEntityId | UUID | CRM entity ID |
| sourceEntityVersion | long | Entity version for optimistic lock |
| requestedAt | Instant | Request timestamp |
| expiresAt | Instant | Expiration timestamp |
| locale | Locale | User locale |
| requiredCapability | String | RBAC capability required |
| dataClassification | String | Data classification level |

### 2.2 WorkflowDispatch

| Field | Type | Description |
|-------|------|-------------|
| workflowRunId | UUID | Workflow run identifier |
| status | Status | ACCEPTED, COMPLETED, REJECTED, UNAVAILABLE, TIMED_OUT |
| externalReference | String | External workflow reference |

### 2.3 AiResult

| Field | Type | Description |
|-------|------|-------------|
| generatedText | String | AI-generated text |
| actionCode | String | Recommended action code |
| explanation | String | Explanation of recommendation |
| confidence | double | Confidence score |
| generatedAt | Instant | Generation timestamp |
| expiresAt | Instant | Expiration timestamp |
| humanConfirmationRequired | boolean | Whether human approval needed |
| sourceReferences | List | Source references |
| policyVersion | String | Policy version used |
| modelVersion | String | Model version used |

---

## 3. Port Interfaces

### 3.1 WorkflowIntegrationPort

```java
public interface WorkflowIntegrationPort {
    WorkflowDispatch dispatch(IntegrationEnvelope envelope, String workflowType, JsonNode payload);
    void cancel(UUID tenantId, UUID workflowRunId, String correlationId, String idempotencyKey, String reason);
}
```

### 3.2 AiGatewayPort

```java
public interface AiGatewayPort {
    AiResult request(IntegrationEnvelope envelope, Capability capability, JsonNode payload);
}
```

### 3.3 AuditPort

```java
public interface AuditPort {
    void record(UUID tenantId, String actorId, String action, AuditChange change);
}
```

### 3.4 TimelineEventPort

```java
public interface TimelineEventPort {
    void record(UUID tenantId, String subjectType, UUID subjectId, String eventType, String summary, String sourceType, UUID sourceId, UUID actorId, Instant occurredAt);
}
```

---

## 4. Adapter Implementations

### 4.1 HttpWorkflowIntegrationAdapter

| Property | Value |
|----------|-------|
| Transport | HTTP/HTTPS |
| Authentication | Service JWT |
| Timeout | 5000ms (configurable) |
| Fail Mode | Fail-closed |
| Endpoint | {baseUrl}/v1/workflows/runs |

### 4.2 HttpAiGatewayAdapter

| Property | Value |
|----------|-------|
| Transport | HTTP/HTTPS |
| Authentication | Service JWT |
| Timeout | 5000ms (configurable) |
| Fail Mode | Fail-closed |
| Endpoint | {baseUrl}/v1/ai/execute |

---

## 5. Transactional Outbox Pattern

### 5.1 Event Flow

```
1. UseCase creates request + outbox event (same transaction)
2. OutboxWorker claims event (short transaction)
3. OutboxWorker transitions request PENDING → DISPATCHED
4. OutboxWorker makes external HTTP call (outside transaction)
5. OutboxWorker persists result + completes outbox event
```

### 5.2 Event Types

| Event Type | Worker | Description |
|------------|--------|-------------|
| WORKFLOW_DISPATCH | CrmWorkflowOutboxWorker | Workflow engine dispatch |
| AI_REQUEST_DISPATCH | CrmIntegrationOutboxWorker | AI gateway dispatch |
| CONFIRMED_COMMAND_EXECUTION | ConfirmedRecommendationExecutor | AI recommendation execution |

---

## 6. Security Architecture

### 6.1 Service-to-Service Authentication

| Component | Description |
|-----------|-------------|
| ServiceJwtProvider | Mints short-lived JWTs |
| JWT Claims | aud, service_name, tenant_id, correlation_id, contract_version |
| HMAC Secret | Minimum 32 bytes |

### 6.2 Callback Security

| Component | Description |
|-----------|-------------|
| Signed JWT | Service JWT validation |
| HMAC Body Signature | SHA-256(body) → HMAC-SHA256(timestamp.nonce.bodyDigest) |
| Replay Protection | JTI + nonce via durable callback_replay table |

---

## 7. REST API Design

### 7.1 Workflow Endpoints

| Method | Path | Capability | Description |
|--------|------|------------|-------------|
| POST | /api/v2/crm/integrations/workflows | CRM.WORKFLOW.EXECUTE | Dispatch workflow |
| GET | /api/v2/crm/integrations/workflows/{requestId} | CRM.WORKFLOW.EXECUTE | Get workflow status |
| POST | /api/v2/crm/integrations/workflows/{requestId}/cancel | CRM.WORKFLOW.EXECUTE | Cancel workflow |

### 7.2 AI Endpoints

| Method | Path | Capability | Description |
|--------|------|------------|-------------|
| POST | /api/v2/crm/integrations/ai | CRM.AI.READ | Request AI insight |
| GET | /api/v2/crm/integrations/{requestId} | CRM.AI.READ | Get integration status |
| POST | /api/v2/crm/integrations/{requestId}/confirm | CRM.AI.CONFIRM | Confirm recommendation |
| POST | /api/v2/crm/integrations/{requestId}/reject | CRM.AI.CONFIRM | Reject recommendation |

### 7.3 Callback Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /internal/crm/integrations/workflows/callback | Signed JWT + HMAC | Workflow callback |

---

## 8. Database Schema

### 8.1 Tables

| Table | Purpose | Indexes |
|-------|---------|---------|
| crm_integration_requests | Request/result store | 9 |
| crm_integration_outbox | Transactional outbox | 4 |
| crm_integration_decisions | Human confirmation | 3 |
| crm_integration_command_executions | Crash recovery ledger | 2 |
| crm_integration_command_artifacts | Artifact idempotency | 2 |
| service_callback_replay | Replay protection | 2 |

---

## 9. Monitoring & Observability

| Component | Metric | Description |
|-----------|--------|-------------|
| Outbox Worker | outbox_claim_total | Events claimed |
| Outbox Worker | outbox_complete_total | Events completed |
| Outbox Worker | outbox_fail_total | Events failed |
| Workflow | workflow_dispatch_total | Workflows dispatched |
| AI | ai_request_total | AI requests made |
| Security | callback_replay_total | Replay attempts blocked |

---

**Architecture Authority:** Agent 1 — Architecture & Workflow Foundation
**Date:** 2026-07-29
**Status:** ✅ DEFINED
