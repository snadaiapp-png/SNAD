# CRM-008-APP-001: Team Management Use Cases

> **Agent:** Agent 3 — Application Layer & Use Case Implementation
> **Task:** 1 — Team Management Use Cases
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation of Team Management use cases for CRM-008.

---

## 2. Use Case Inventory

| Use Case | Method | Transactional | Description |
|----------|--------|---------------|-------------|
| ActivateTeam | `activateTeam()` | ✅ | Activate an archived team |
| GetTeamDetails | `getTeamDetails()` | ❌ | Get team details |
| SearchTeams | `searchTeams()` | ❌ | Search teams by status and name |

---

## 3. Business Rules

### ActivateTeam
- **Preconditions**: Team must exist and be ARCHIVED
- **Postconditions**: Team status changes to ACTIVE
- **Invariants**: Team cannot be activated if already ACTIVE
- **Tenant Isolation**: Team must belong to the same tenant

### SearchTeams
- **Preconditions**: None
- **Postconditions**: Returns filtered list of teams
- **Filters**: Status (required), Name (optional, case-insensitive)
- **Tenant Isolation**: Only returns teams in the same tenant

---

## 4. State Transitions

```
ARCHIVED → ACTIVE (via activateTeam)
```

---

## 5. Integration

- Delegates to `SalesTeamRepository` for persistence
- Records audit events via `AuditPort`
- Records timeline events via `TimelineEventPort`

---

**Certification Date:** 2026-07-28
**Agent 3 Task 1 Status:** COMPLETE
