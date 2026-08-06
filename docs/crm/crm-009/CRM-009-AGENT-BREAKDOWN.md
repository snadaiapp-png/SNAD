# CRM-009 Agent Breakdown

> **Module:** CRM-009 — Workflow Engine & AI Gateway Integration
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Agent Overview

| Agent | Role | Scope | Duration |
|-------|------|-------|----------|
| Agent 1 | Architecture & Workflow Foundation | Blueprint, contracts, migrations | 1 day |
| Agent 2 | Workflow Domain Implementation | Domain models, ports, adapters | 1 day |
| Agent 3 | Workflow Runtime & Use Cases | Use cases, outbox workers | 1 day |
| Agent 4 | REST API & Gateway | Controllers, DTOs, RBAC | 1 day |
| Agent 5 | Platform Integration | Audit, timeline, notifications | 1 day |
| Agent 6 | QA Certification | Tests, validation, coverage | 1 day |
| Agent 7 | Production Readiness | Deployment, monitoring, rollback | 1 day |
| Agent 8 | Final Closure Package | Evidence, traceability, certificate | 1 day |
| Agent 9 | Official Governance Closure | Baseline, approval, certification | 1 day |

---

## 2. Agent 1 — Architecture & Workflow Foundation

### 2.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Define architecture blueprint | CRM-009-ARCHITECTURE-BLUEPRINT.md |
| 2 | Define workflow contracts | Workflow type definitions |
| 3 | Define AI capability contracts | AI capability definitions |
| 4 | Create database migrations | V20260723_1, V20260724_1, V20260724_2 |
| 5 | Define RBAC capabilities | CRM.WORKFLOW.EXECUTE, CRM.AI.READ, CRM.AI.CONFIRM |

### 2.2 Output Files

| File | Description |
|------|-------------|
| CRM-009-EXECUTION-PLAN.md | Execution plan |
| CRM-009-ARCHITECTURE-BLUEPRINT.md | Architecture blueprint |
| CRM-009-IMPLEMENTATION-BACKLOG.md | Implementation backlog |
| CRM-009-AGENT-BREAKDOWN.md | Agent breakdown |
| CRM-009-RISK-REGISTER.md | Risk register |
| CRM-009-EXECUTION-SCHEDULE.md | Execution schedule |

---

## 3. Agent 2 — Workflow Domain Implementation

### 3.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Implement WorkflowIntegrationPort | Port interface |
| 2 | Implement HttpWorkflowIntegrationAdapter | HTTP adapter |
| 3 | Implement AiGatewayPort | Port interface |
| 4 | Implement HttpAiGatewayAdapter | HTTP adapter |
| 5 | Implement ServiceJwtProvider | JWT provider |
| 6 | Implement WorkflowCallbackSecurity | Callback security |
| 7 | Implement CallbackReplayStore | Replay protection |
| 8 | Implement CrmEntitySnapshotPort | Entity snapshots |

### 3.2 Output Files

| File | Description |
|------|-------------|
| WorkflowIntegrationPort.java | Workflow port interface |
| HttpWorkflowIntegrationAdapter.java | Workflow HTTP adapter |
| AiGatewayPort.java | AI port interface |
| HttpAiGatewayAdapter.java | AI HTTP adapter |
| ServiceJwtProvider.java | JWT provider |
| WorkflowCallbackSecurity.java | Callback security |
| CallbackReplayStore.java | Replay protection |
| CrmEntitySnapshotPort.java | Entity snapshot port |

---

## 4. Agent 3 — Workflow Runtime & Use Cases

### 4.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Implement CrmWorkflowUseCases | Workflow orchestration |
| 2 | Implement CrmWorkflowOutboxWorker | Workflow outbox worker |
| 3 | Implement CrmIntegrationUseCases | AI orchestration |
| 4 | Implement CrmIntegrationOutboxWorker | AI outbox worker |
| 5 | Implement CrmIntegrationStore | Central persistence |
| 6 | Implement ConfirmedRecommendationExecutor | Command execution |

### 4.2 Output Files

| File | Description |
|------|-------------|
| CrmWorkflowUseCases.java | Workflow orchestration |
| CrmWorkflowOutboxWorker.java | Workflow outbox worker |
| CrmIntegrationUseCases.java | AI orchestration |
| CrmIntegrationOutboxWorker.java | AI outbox worker |
| CrmIntegrationStore.java | Central persistence |
| ConfirmedRecommendationExecutor.java | Command execution |

---

## 5. Agent 4 — REST API & Gateway

### 5.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Implement CrmWorkflowController | Workflow REST API |
| 2 | Implement CrmWorkflowCallbackController | Callback endpoint |
| 3 | Implement CrmIntegrationController | AI REST API |
| 4 | Define request/response DTOs | DTO classes |
| 5 | Enforce RBAC capabilities | @RequireCapability |

### 5.2 Output Files

| File | Description |
|------|-------------|
| CrmWorkflowController.java | Workflow REST API |
| CrmWorkflowCallbackController.java | Callback endpoint |
| CrmIntegrationController.java | AI REST API |
| AiRequest.java | AI request DTO |
| WorkflowDispatchRequest.java | Workflow dispatch DTO |

---

## 6. Agent 5 — Platform Integration

### 6.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Implement AuditPort integration | Audit trail |
| 2 | Implement TimelineEventPort integration | Timeline events |
| 3 | Implement ProductionWorkflowStubGuard | Production guard |
| 4 | Verify notification integration | Notifications |

### 6.2 Output Files

| File | Description |
|------|-------------|
| JdbcAuditAdapter.java | Audit adapter |
| JdbcTimelineEventAdapter.java | Timeline adapter |
| ProductionWorkflowStubGuard.java | Production guard |

---

## 7. Agent 6 — QA Certification

### 7.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Create workflow integration tests | Test classes |
| 2 | Create AI integration tests | Test classes |
| 3 | Create security tests | Test classes |
| 4 | Create outbox worker tests | Test classes |
| 5 | Validate test coverage | Coverage report |

### 7.2 Output Files

| File | Description |
|------|-------------|
| CrmWorkflowIntegrationPostgresTest.java | Workflow tests |
| WorkflowCallbackSecurityPostgresTest.java | Security tests |
| CrmIntegrationOutboxPostgresTest.java | Outbox tests |
| CRM-009-QA-*.md | QA documentation |

---

## 8. Agent 7 — Production Readiness

### 8.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Validate deployment readiness | Deployment config |
| 2 | Validate monitoring | Monitoring config |
| 3 | Validate rollback procedures | Rollback docs |
| 4 | Create operational runbooks | Runbooks |

### 8.2 Output Files

| File | Description |
|------|-------------|
| CRM-009-PROD-*.md | Production documentation |
| CRM-009-PRODUCTION-READINESS-CERTIFICATE.md | Production certificate |

---

## 9. Agent 8 — Final Closure Package

### 9.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Collect evidence | Evidence collection |
| 2 | Create traceability matrix | Traceability |
| 3 | Create implementation inventory | Inventory |
| 4 | Create quality summary | Quality report |
| 5 | Create risk review | Risk review |
| 6 | Create executive summary | Executive summary |
| 7 | Create final closure certificate | Closure certificate |

### 9.2 Output Files

| File | Description |
|------|-------------|
| CRM-009-CLOSURE-*.md | Closure documentation |
| CRM-009-FINAL-CLOSURE-CERTIFICATE.md | Final certificate |
| CRM-009-FINAL-CLOSURE-PACKAGE/ | Closure package |

---

## 10. Agent 9 — Official Governance Closure

### 10.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Generate final certification | Final certification |
| 2 | Generate official closure record | Closure record |
| 3 | Generate approval matrix | Approval matrix |
| 4 | Update master registers | Register updates |
| 5 | Validate governance | Governance validation |

### 10.2 Output Files

| File | Description |
|------|-------------|
| CRM-009-FINAL-CERTIFICATION.md | Final certification |
| CRM-009-OFFICIAL-CLOSURE-RECORD.md | Closure record |
| CRM-009-CLOSURE-APPROVAL-MATRIX.md | Approval matrix |
| CRM-009-GOVERNANCE-VALIDATION.md | Governance validation |

---

**Agent Breakdown Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ DEFINED
