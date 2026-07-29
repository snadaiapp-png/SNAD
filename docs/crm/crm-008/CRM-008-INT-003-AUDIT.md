# CRM-008-INT-003: Audit & Timeline Integration

> **Agent:** Agent 5 — Workflow Engine & Platform Integration
> **Task:** 3 — Audit & Timeline Integration
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the audit and timeline integration for CRM-008 Team Management.

---

## 2. Audit Integration

### AuditPort Usage

All 7 UseCase classes record audit events via `AuditPort.record()`:

| UseCase | Audit Actions |
|---------|---------------|
| TeamManagementUseCases | ACTIVATE |
| ShiftManagementUseCases | CREATE, UPDATE, ACTIVATE, DEACTIVATE |
| AvailabilityManagementUseCases | CREATE, UPDATE, DELETE |
| SkillManagementUseCases | CREATE, UPDATE, DELETE |
| CapacityManagementUseCases | CREATE, UPDATE |
| WorkloadManagementUseCases | CREATE, REASSIGN, RELEASE |
| ServiceAssignmentUseCases | CREATE, REASSIGN, COMPLETE, CANCEL |

### Audit Record Format

```java
AuditPort.record(
    tenantId,           // UUID - tenant scope
    actorId,            // UUID - who performed action
    "CREATE",           // String - action type
    "SHIFT_TEMPLATE",   // String - entity type
    entityId,           // UUID - entity ID
    new AuditChange(beforeJson, afterJson),  // JsonNode snapshots
    Instant.now()       // Instant - timestamp
);
```

### Before/After Snapshots

- **Create**: before=null, after=serialized entity
- **Update**: before=previous state, after=new state
- **Delete**: before=previous state, after=null
- **Archive/Activate**: before=previous state, after=new state

---

## 3. Timeline Integration

### TimelineEventPort Usage

All 7 UseCase classes record timeline events via `TimelineEventPort.record()`:

| UseCase | Timeline Events |
|---------|-----------------|
| TeamManagementUseCases | crm.team.activated |
| ShiftManagementUseCases | crm.shift_template.*, crm.shift.* |
| AvailabilityManagementUseCases | crm.availability.* |
| SkillManagementUseCases | crm.skill.* |
| CapacityManagementUseCases | crm.capacity.* |
| WorkloadManagementUseCases | crm.workload.* |
| ServiceAssignmentUseCases | crm.service.* |

### Timeline Record Format

```java
TimelineEventPort.record(
    tenantId,           // UUID - tenant scope
    "SHIFT_TEMPLATE",   // String - subject type
    templateId,         // UUID - subject ID
    "crm.shift_template.created",  // String - event type
    "Shift template created",      // String - summary
    "CRM_SHIFT_TEMPLATE",          // String - source type
    templateId,         // UUID - source ID
    actorId,            // UUID - actor
    Instant.now()       // Instant - timestamp
);
```

---

## 4. Event Persistence

| Requirement | Status |
|-------------|--------|
| Events persisted in same transaction | ✅ |
| Actor tracking via actorId | ✅ |
| Tenant scope via tenantId | ✅ |
| Correlation IDs via CorrelationContextPort | ✅ |
| JSON snapshots for audit | ✅ |

---

## 5. Integration Files

| File | Location |
|------|----------|
| AuditPort.java | integration/domain/ |
| TimelineEventPort.java | integration/domain/ |
| CorrelationContextPort.java | integration/domain/ |

---

**Certification Date:** 2026-07-28
**Agent 5 Task 3 Status:** COMPLETE
