# CRM-008-INT-002: Domain Events

> **Agent:** Agent 5 — Workflow Engine & Platform Integration
> **Task:** 2 — Domain Events
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the domain event definitions for CRM-008 Team Management.

---

## 2. Event Inventory

### Team Events (4)

| Event | Description | Published By |
|-------|-------------|--------------|
| crm.team.created | Team created | SalesTeamUseCases |
| crm.team.updated | Team updated | SalesTeamUseCases |
| crm.team.archived | Team archived | SalesTeamUseCases |
| crm.team.activated | Team activated | TeamManagementUseCases |

### Shift Events (7)

| Event | Description | Published By |
|-------|-------------|--------------|
| crm.shift_template.created | Template created | ShiftManagementUseCases |
| crm.shift_template.updated | Template updated | ShiftManagementUseCases |
| crm.shift_template.published | Template activated | ShiftManagementUseCases |
| crm.shift_template.cancelled | Template deactivated | ShiftManagementUseCases |
| crm.shift.assigned | Shift assigned | ShiftManagementUseCases |
| crm.shift_assignment.updated | Assignment updated | ShiftManagementUseCases |
| crm.shift_assignment.cancelled | Assignment cancelled | ShiftManagementUseCases |

### Availability Events (4)

| Event | Description | Published By |
|-------|-------------|--------------|
| crm.availability.submitted | Availability submitted | AvailabilityManagementUseCases |
| crm.availability.approved | Availability approved | AvailabilityManagementUseCases |
| crm.availability.rejected | Availability rejected | AvailabilityManagementUseCases |
| crm.availability.deleted | Availability deleted | AvailabilityManagementUseCases |

### Skill Events (3)

| Event | Description | Published By |
|-------|-------------|--------------|
| crm.skill.registered | Skill registered | SkillManagementUseCases |
| crm.skill.updated | Skill updated | SkillManagementUseCases |
| crm.skill.deleted | Skill deleted | SkillManagementUseCases |

### Capacity Events (3)

| Event | Description | Published By |
|-------|-------------|--------------|
| crm.capacity.created | Plan created | CapacityManagementUseCases |
| crm.capacity.adjusted | Plan adjusted | CapacityManagementUseCases |
| crm.capacity.changed | Capacity changed | CapacityManagementUseCases |

### Workload Events (4)

| Event | Description | Published By |
|-------|-------------|--------------|
| crm.workload.assigned | Work assigned | WorkloadManagementUseCases |
| crm.workload.reassigned | Work reassigned | WorkloadManagementUseCases |
| crm.workload.released | Work released | WorkloadManagementUseCases |
| crm.workload.balanced | Workload balanced | WorkloadManagementUseCases |

### Service Assignment Events (4)

| Event | Description | Published By |
|-------|-------------|--------------|
| crm.service.assigned | Service assigned | ServiceAssignmentUseCases |
| crm.service.reassigned | Service reassigned | ServiceAssignmentUseCases |
| crm.service.completed | Service completed | ServiceAssignmentUseCases |
| crm.service.cancelled | Service cancelled | ServiceAssignmentUseCases |

---

## 3. Total Event Count

| Category | Count |
|----------|-------|
| Team | 4 |
| Shift | 7 |
| Availability | 4 |
| Skill | 3 |
| Capacity | 3 |
| Workload | 4 |
| Service | 4 |
| **Total** | **29** |

---

## 4. Event Publishing Mechanism

Events are published via two ports:
- **AuditPort**: Records audit trail with before/after JSON snapshots
- **TimelineEventPort**: Records timeline events for entity history

Both must be called within the same transaction as the mutation.

---

## 5. Integration Files

| File | Location |
|------|----------|
| TeamManagementEventTypes.java | ownership/integration/ |

---

**Certification Date:** 2026-07-28
**Agent 5 Task 2 Status:** COMPLETE
