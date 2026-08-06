# CRM-009 Execution Plan

> **Module:** CRM-009 — Workflow Engine & AI Gateway Integration
> **Platform:** SANAD
> **Date:** 2026-07-29
> **Status:** INITIATED

---

## 1. Project Overview

CRM-009 integrates the CRM platform with the SANAD Workflow Engine and AI Gateway, enabling workflow-first, AI-native, event-driven CRM operations. The implementation extends the existing integration infrastructure established in CRM-008R with comprehensive workflow and AI capabilities.

### 1.1 Scope

| In Scope | Out of Scope |
|----------|--------------|
| Workflow Engine integration | Custom workflow runtime |
| AI Gateway integration | Direct model provider calls |
| Human approval workflows | Automated decision making |
| Event-driven architecture | Batch processing |
| Notification integration | Email/SMS delivery |
| Audit trail integration | Custom audit system |
| REST API endpoints | Frontend UI (separate track) |
| Production readiness | Performance optimization |

### 1.2 Primary Goals

| # | Goal | Description |
|---|------|-------------|
| 1 | Workflow-first CRM | Every business operation executable through Workflow Engine |
| 2 | AI-native CRM | AI capabilities exposed through SANAD AI Platform |
| 3 | Event-driven CRM | All business operations publish domain events |
| 4 | Human Approval | Support approvals, escalations, delegation, SLA timers |
| 5 | Automation | No-code/low-code workflow automation |
| 6 | AI Gateway | Secure AI operations through capability-based access |

---

## 2. Current State Assessment

### 2.1 Existing Infrastructure

| Component | Status | Evidence |
|-----------|--------|----------|
| WorkflowIntegrationPort | ✅ EXISTS | Port interface defined |
| HttpWorkflowIntegrationAdapter | ✅ EXISTS | HTTP transport implemented |
| CrmWorkflowUseCases | ✅ EXISTS | Orchestration layer complete |
| CrmWorkflowOutboxWorker | ✅ EXISTS | Transactional outbox operational |
| CrmWorkflowController | ✅ EXISTS | REST API at /api/v2/crm/integrations/workflows |
| CrmWorkflowCallbackController | ✅ EXISTS | Callback endpoint operational |
| AiGatewayPort | ✅ EXISTS | Port interface defined |
| HttpAiGatewayAdapter | ✅ EXISTS | HTTP transport implemented |
| CrmIntegrationUseCases | ✅ EXISTS | Orchestration layer complete |
| CrmIntegrationOutboxWorker | ✅ EXISTS | Transactional outbox operational |
| CrmIntegrationController | ✅ EXISTS | REST API at /api/v2/crm/integrations |
| CrmIntegrationStore | ✅ EXISTS | Central persistence operational |
| ServiceJwtProvider | ✅ EXISTS | Service-to-service auth operational |
| WorkflowCallbackSecurity | ✅ EXISTS | Callback verification operational |
| ProductionWorkflowStubGuard | ✅ EXISTS | Production guard operational |
| TeamManagementWorkflowTypes | ✅ EXISTS | 6 workflow types defined |
| TeamManagementAiCapabilities | ✅ EXISTS | 6 AI capabilities defined |

### 2.2 Database Schema

| Table | Status | Migration |
|-------|--------|-----------|
| crm_integration_requests | ✅ EXISTS | V20260723_1 |
| crm_integration_outbox | ✅ EXISTS | V20260723_1 |
| crm_integration_decisions | ✅ EXISTS | V20260723_1 |
| crm_integration_command_executions | ✅ EXISTS | V20260724_1 |
| crm_integration_command_artifacts | ✅ EXISTS | V20260724_2 |
| service_callback_replay | ✅ EXISTS | V20260724_2 |

### 2.3 RBAC Capabilities

| Capability | Status | Migration |
|------------|--------|-----------|
| CRM.WORKFLOW.EXECUTE | ✅ SEEDED | V20260723_1 |
| CRM.AI.READ | ✅ SEEDED | V20260723_1 |
| CRM.AI.CONFIRM | ✅ SEEDED | V20260723_1 |

### 2.4 Tests

| Category | Count | Status |
|----------|-------|--------|
| Integration Tests | 18+ | ✅ IMPLEMENTED |
| Unit Tests | 10+ | ✅ IMPLEMENTED |
| Acceptance Tests | 2 | ✅ IMPLEMENTED |
| **Total** | **30+** | **✅ IMPLEMENTED** |

---

## 3. Execution Model

### 3.1 Agent Breakdown

| Agent | Role | Scope | Dependencies |
|-------|------|-------|--------------|
| Agent 1 | Architecture & Workflow Foundation | Blueprint, contracts, migrations | None |
| Agent 2 | Workflow Domain Implementation | Domain models, ports, adapters | Agent 1 |
| Agent 3 | Workflow Runtime & Use Cases | Use cases, outbox workers | Agent 2 |
| Agent 4 | REST API & Gateway | Controllers, DTOs, RBAC | Agent 3 |
| Agent 5 | Platform Integration | Audit, timeline, notifications | Agent 4 |
| Agent 6 | QA Certification | Tests, validation, coverage | Agent 5 |
| Agent 7 | Production Readiness | Deployment, monitoring, rollback | Agent 6 |
| Agent 8 | Final Closure Package | Evidence, traceability, certificate | Agent 7 |
| Agent 9 | Official Governance Closure | Baseline, approval, certification | Agent 8 |

### 3.2 Execution Sequence

```
Agent 1 (Foundation)
    ↓
Agent 2 (Domain)
    ↓
Agent 3 (Runtime)
    ↓
Agent 4 (API)
    ↓
Agent 5 (Integration)
    ↓
Agent 6 (QA)
    ↓
Agent 7 (Production)
    ↓
Agent 8 (Closure)
    ↓
Agent 9 (Governance)
```

---

## 4. Implementation Scope

### 4.1 Workflow Engine Integration

| Component | Description | Status |
|-----------|-------------|--------|
| Workflow Types | Define workflow contracts for each CRM operation | ✅ DEFINED |
| Workflow Executors | Execute workflow definitions through engine | ✅ IMPLEMENTED |
| Workflow State Machine | Manage workflow lifecycle states | ✅ IMPLEMENTED |
| Approval Policies | Configure approval workflows | ✅ IMPLEMENTED |
| Escalation Rules | Define escalation behaviors | ✅ IMPLEMENTED |
| SLA Timers | Track SLA compliance | ✅ IMPLEMENTED |

### 4.2 AI Gateway Integration

| Component | Description | Status |
|-----------|-------------|--------|
| AI Capability Registry | Register AI capabilities | ✅ DEFINED |
| Prompt Templates | Define prompt structures | ✅ DEFINED |
| AI Action Definitions | Define AI actions | ✅ DEFINED |
| Human Confirmation | Require human approval for actions | ✅ IMPLEMENTED |
| Result Immutability | Prevent concurrent result overwrites | ✅ IMPLEMENTED |

### 4.3 Event Integration

| Component | Description | Status |
|-----------|-------------|--------|
| Domain Events | Publish business events | ✅ IMPLEMENTED |
| Event Bus | Transactional outbox pattern | ✅ IMPLEMENTED |
| Event Workers | Process outbox events | ✅ IMPLEMENTED |
| Event Correlation | Track event lineage | ✅ IMPLEMENTED |

### 4.4 Notification Integration

| Component | Description | Status |
|-----------|-------------|--------|
| Notification Types | Define notification categories | ✅ DEFINED |
| Notification Port | Provider-neutral notification interface | ✅ DEFINED |
| Notification Adapter | Webhook-based notification delivery | ✅ IMPLEMENTED |

### 4.5 Audit Integration

| Component | Description | Status |
|-----------|-------------|--------|
| Audit Trail | Record all mutations | ✅ IMPLEMENTED |
| Timeline Events | Record timeline events | ✅ IMPLEMENTED |
| Audit Port | Centralized audit interface | ✅ IMPLEMENTED |

---

## 5. Quality Principles

| Principle | Description | Implementation |
|-----------|-------------|----------------|
| Workflow First | Every operation through workflow engine | CrmWorkflowUseCases |
| AI Native | AI through SANAD AI Platform | CrmIntegrationUseCases |
| Event Driven | All operations publish events | Transactional outbox |
| Tenant Safe | Tenant isolation enforced | tenant_id in all queries |
| API First | REST API design first | /api/v2/crm/integrations |
| Secure by Design | Capability-based access | @RequireCapability |
| Audit by Default | All mutations audited | AuditPort integration |

---

## 6. Success Criteria

| # | Criterion | Target |
|---|-----------|--------|
| 1 | All workflow types executable | 6/6 |
| 2 | All AI capabilities operational | 6/6 |
| 3 | All REST endpoints functional | 7/7 |
| 4 | All tests passing | 100% |
| 5 | Zero critical defects | 0 |
| 6 | Production ready | YES |
| 7 | Documentation complete | 100% |

---

## 7. Risk Assessment

| # | Risk | Probability | Impact | Mitigation |
|---|------|-------------|--------|------------|
| 1 | Workflow engine unavailable | LOW | HIGH | Fail-closed design, retry logic |
| 2 | AI gateway unavailable | LOW | HIGH | Fail-closed design, graceful degradation |
| 3 | Callback security breach | LOW | CRITICAL | HMAC + JWT + replay protection |
| 4 | Outbox event loss | LOW | HIGH | Durable outbox with recovery |
| 5 | Concurrent modification | MEDIUM | MEDIUM | Optimistic locking, If-Match |

---

## 8. Timeline

| Phase | Duration | Start | End |
|-------|----------|-------|-----|
| Agent 1-2: Foundation & Domain | 2 days | 2026-07-29 | 2026-07-30 |
| Agent 3-4: Runtime & API | 2 days | 2026-07-31 | 2026-08-01 |
| Agent 5: Integration | 1 day | 2026-08-02 | 2026-08-02 |
| Agent 6: QA | 1 day | 2026-08-03 | 2026-08-03 |
| Agent 7: Production | 1 day | 2026-08-04 | 2026-08-04 |
| Agent 8-9: Closure | 1 day | 2026-08-05 | 2026-08-05 |
| **Total** | **8 days** | **2026-07-29** | **2026-08-05** |

---

**Execution Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ INITIATED
