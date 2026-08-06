# CRM-008-INT-001: Workflow Integration

> **Agent:** Agent 5 — Workflow Engine & Platform Integration
> **Task:** 1 — Workflow Integration
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the workflow integration definitions for CRM-008 Team Management.

---

## 2. Workflow Types

| # | Workflow Type | Description | Source Entity |
|---|---------------|-------------|---------------|
| 1 | TEAM_LIFECYCLE | Team creation, activation, archival | CRM_SALES_TEAM |
| 2 | SHIFT_SCHEDULING | Template creation, assignment, publishing | CRM_SHIFT_TEMPLATE |
| 3 | AVAILABILITY_APPROVAL | Submission, approval, rejection | CRM_STAFF_AVAILABILITY |
| 4 | CAPACITY_PLANNING | Plan creation, adjustment, forecasting | CRM_CAPACITY_PLAN |
| 5 | WORKLOAD_ASSIGNMENT | Assignment, reassignment, balancing | CRM_WORKLOAD_ASSIGNMENT |
| 6 | SERVICE_ASSIGNMENT | Assignment, reassignment, completion | CRM_SERVICE_ASSIGNMENT |

---

## 3. Workflow Dispatch Pattern

CRM-008 follows the existing outbox pattern:

1. **UseCase** creates `IntegrationEnvelope` with workflow type
2. **CrmIntegrationStore** persists request + outbox event atomically
3. **CrmWorkflowOutboxWorker** dispatches to external engine
4. **CrmWorkflowCallbackController** handles callbacks

---

## 4. Contract Names

| Workflow Type | Contract Name |
|---------------|---------------|
| TEAM_LIFECYCLE | crm.team_management.team_lifecycle |
| SHIFT_SCHEDULING | crm.team_management.shift_scheduling |
| AVAILABILITY_APPROVAL | crm.team_management.availability_approval |
| CAPACITY_PLANNING | crm.team_management.capacity_planning |
| WORKLOAD_ASSIGNMENT | crm.team_management.workload_assignment |
| SERVICE_ASSIGNMENT | crm.team_management.service_assignment |

---

## 5. Terminal States

| State | Description |
|-------|-------------|
| COMPLETED | Workflow finished successfully |
| REJECTED | Workflow rejected by approver |
| CANCELLED | Workflow cancelled by user |
| TIMED_OUT | Workflow exceeded timeout |
| UNAVAILABLE | Workflow engine unavailable |

---

## 6. Integration Files

| File | Location |
|------|----------|
| TeamManagementWorkflowTypes.java | ownership/integration/ |

---

**Certification Date:** 2026-07-28
**Agent 5 Task 1 Status:** COMPLETE
