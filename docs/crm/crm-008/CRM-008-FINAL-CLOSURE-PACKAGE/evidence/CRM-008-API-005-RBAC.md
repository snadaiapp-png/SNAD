# CRM-008-API-005: RBAC Implementation

> **Agent:** Agent 4 — REST API & RBAC Implementation
> **Task:** 5 — RBAC Implementation
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the RBAC implementation for CRM-008 Team Management.

---

## 2. Capability Inventory

| # | Capability Code | Name | Description |
|---|-----------------|------|-------------|
| 1 | CRM.TEAM.READ | CRM Team Read | View teams (existing) |
| 2 | CRM.TEAM.WRITE | CRM Team Write | Create, update teams |
| 3 | CRM.TEAM.MANAGE | CRM Team Manage | Full team management |
| 4 | CRM.SHIFT.READ | CRM Shift Read | View shift templates/assignments |
| 5 | CRM.SHIFT.MANAGE | CRM Shift Manage | Manage shifts |
| 6 | CRM.AVAILABILITY.READ | CRM Availability Read | View availability |
| 7 | CRM.AVAILABILITY.MANAGE | CRM Availability Manage | Manage availability |
| 8 | CRM.SKILLS.READ | CRM Skills Read | View skills |
| 9 | CRM.SKILLS.MANAGE | CRM Skills Manage | Manage skills |
| 10 | CRM.CAPACITY.READ | CRM Capacity Read | View capacity plans |
| 11 | CRM.CAPACITY.MANAGE | CRM Capacity Manage | Manage capacity plans |
| 12 | CRM.WORKLOAD.READ | CRM Workload Read | View workload |
| 13 | CRM.WORKLOAD.MANAGE | CRM Workload Manage | Manage workload |
| 14 | CRM.ASSIGNMENT.READ | CRM Assignment Read | View service assignments (existing) |
| 15 | CRM.ASSIGNMENT.MANAGE | CRM Assignment Manage | Manage service assignments |

---

## 3. Permission-to-Endpoint Mapping

### Team Management

| Endpoint | Capability | Read/Write |
|----------|------------|------------|
| GET /teams | CRM.TEAM.READ | Read |
| GET /teams/{id} | CRM.TEAM.READ | Read |
| POST /teams | CRM.TEAM.WRITE | Write |
| PATCH /teams/{id} | CRM.TEAM.WRITE | Write |
| PATCH /teams/{id}/archive | CRM.TEAM.WRITE | Write |
| PATCH /teams/{id}/activate | CRM.TEAM.WRITE | Write |

### Shift Management

| Endpoint | Capability | Read/Write |
|----------|------------|------------|
| GET /shift-templates | CRM.SHIFT.READ | Read |
| GET /shift-templates/{id} | CRM.SHIFT.READ | Read |
| POST /shift-templates | CRM.SHIFT.MANAGE | Write |
| PATCH /shift-templates/{id} | CRM.SHIFT.MANAGE | Write |
| PATCH /shift-templates/{id}/publish | CRM.SHIFT.MANAGE | Write |
| PATCH /shift-templates/{id}/cancel | CRM.SHIFT.MANAGE | Write |
| GET /shift-assignments | CRM.SHIFT.READ | Read |
| POST /shift-assignments | CRM.SHIFT.MANAGE | Write |
| PATCH /shift-assignments/{id} | CRM.SHIFT.MANAGE | Write |
| PATCH /shift-assignments/{id}/cancel | CRM.SHIFT.MANAGE | Write |

### Availability Management

| Endpoint | Capability | Read/Write |
|----------|------------|------------|
| GET /availability | CRM.AVAILABILITY.READ | Read |
| POST /availability | CRM.AVAILABILITY.MANAGE | Write |
| PATCH /availability/{id}/approve | CRM.AVAILABILITY.MANAGE | Write |
| PATCH /availability/{id}/reject | CRM.AVAILABILITY.MANAGE | Write |
| DELETE /availability/{id} | CRM.AVAILABILITY.MANAGE | Write |

### Skills Management

| Endpoint | Capability | Read/Write |
|----------|------------|------------|
| GET /skills | CRM.SKILLS.READ | Read |
| POST /skills | CRM.SKILLS.MANAGE | Write |
| PATCH /skills/{id} | CRM.SKILLS.MANAGE | Write |
| DELETE /skills/{id} | CRM.SKILLS.MANAGE | Write |

### Capacity Management

| Endpoint | Capability | Read/Write |
|----------|------------|------------|
| GET /capacity | CRM.CAPACITY.READ | Read |
| GET /capacity/{id} | CRM.CAPACITY.READ | Read |
| POST /capacity | CRM.CAPACITY.MANAGE | Write |
| PATCH /capacity/{id} | CRM.CAPACITY.MANAGE | Write |
| GET /capacity/forecast | CRM.CAPACITY.READ | Read |

### Workload Management

| Endpoint | Capability | Read/Write |
|----------|------------|------------|
| GET /workload | CRM.WORKLOAD.READ | Read |
| GET /workload/hours | CRM.WORKLOAD.READ | Read |
| POST /workload | CRM.WORKLOAD.MANAGE | Write |
| PATCH /workload/{id}/reassign | CRM.WORKLOAD.MANAGE | Write |
| PATCH /workload/{id}/release | CRM.WORKLOAD.MANAGE | Write |

### Service Assignment Management

| Endpoint | Capability | Read/Write |
|----------|------------|------------|
| GET /service-assignments | CRM.ASSIGNMENT.READ | Read |
| GET /service-assignments/{id} | CRM.ASSIGNMENT.READ | Read |
| POST /service-assignments | CRM.ASSIGNMENT.MANAGE | Write |
| PATCH /service-assignments/{id}/reassign | CRM.ASSIGNMENT.MANAGE | Write |
| PATCH /service-assignments/{id}/complete | CRM.ASSIGNMENT.MANAGE | Write |
| PATCH /service-assignments/{id}/cancel | CRM.ASSIGNMENT.MANAGE | Write |

---

## 4. Migration File

| File | Description |
|------|-------------|
| V20260728_1__seed_crm_008_team_management_capabilities.sql | Seeds 13 new capabilities |

---

## 5. Enforcement

- **Mechanism**: `@RequireCapability` annotation on controller methods
- **Aspect**: `CapabilityAuthorizationAspect` intercepts annotated methods
- **Evaluation**: `CapabilityEvaluationService` checks user's role grants
- **Default**: Deny-by-default (no match = HTTP 403)

---

**Certification Date:** 2026-07-28
**Agent 4 Task 5 Status:** COMPLETE
