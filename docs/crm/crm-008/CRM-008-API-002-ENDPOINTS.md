# CRM-008-API-002: REST Endpoints

> **Agent:** Agent 4 — REST API & RBAC Implementation
> **Task:** 2 — REST Endpoints
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records all 40 REST endpoints implemented for CRM-008 Team Management.

---

## 2. Endpoint Inventory

### Teams (5 endpoints)

| Method | Path | Description | Capability |
|--------|------|-------------|------------|
| GET | /api/v1/crm/teams | List teams | CRM.TEAM.READ |
| GET | /api/v1/crm/teams/{teamId} | Get team details | CRM.TEAM.READ |
| POST | /api/v1/crm/teams | Create team | CRM.TEAM.WRITE |
| PATCH | /api/v1/crm/teams/{teamId} | Update team | CRM.TEAM.WRITE |
| PATCH | /api/v1/crm/teams/{teamId}/archive | Archive team | CRM.TEAM.WRITE |
| PATCH | /api/v1/crm/teams/{teamId}/activate | Activate team | CRM.TEAM.WRITE |

### Shift Templates (6 endpoints)

| Method | Path | Description | Capability |
|--------|------|-------------|------------|
| GET | /api/v1/crm/shift-templates | List templates | CRM.SHIFT.READ |
| GET | /api/v1/crm/shift-templates/{templateId} | Get template | CRM.SHIFT.READ |
| POST | /api/v1/crm/shift-templates | Create template | CRM.SHIFT.MANAGE |
| PATCH | /api/v1/crm/shift-templates/{templateId} | Update template | CRM.SHIFT.MANAGE |
| PATCH | /api/v1/crm/shift-templates/{templateId}/publish | Publish template | CRM.SHIFT.MANAGE |
| PATCH | /api/v1/crm/shift-templates/{templateId}/cancel | Cancel template | CRM.SHIFT.MANAGE |

### Shift Assignments (4 endpoints)

| Method | Path | Description | Capability |
|--------|------|-------------|------------|
| GET | /api/v1/crm/shift-assignments | List assignments | CRM.SHIFT.READ |
| POST | /api/v1/crm/shift-assignments | Assign shift | CRM.SHIFT.MANAGE |
| PATCH | /api/v1/crm/shift-assignments/{assignmentId} | Update assignment | CRM.SHIFT.MANAGE |
| PATCH | /api/v1/crm/shift-assignments/{assignmentId}/cancel | Cancel assignment | CRM.SHIFT.MANAGE |

### Availability (5 endpoints)

| Method | Path | Description | Capability |
|--------|------|-------------|------------|
| GET | /api/v1/crm/availability | Calendar query | CRM.AVAILABILITY.READ |
| POST | /api/v1/crm/availability | Submit availability | CRM.AVAILABILITY.MANAGE |
| PATCH | /api/v1/crm/availability/{availabilityId}/approve | Approve | CRM.AVAILABILITY.MANAGE |
| PATCH | /api/v1/crm/availability/{availabilityId}/reject | Reject | CRM.AVAILABILITY.MANAGE |
| DELETE | /api/v1/crm/availability/{availabilityId} | Delete | CRM.AVAILABILITY.MANAGE |

### Skills (4 endpoints)

| Method | Path | Description | Capability |
|--------|------|-------------|------------|
| GET | /api/v1/crm/skills | List skills | CRM.SKILLS.READ |
| POST | /api/v1/crm/skills | Register skill | CRM.SKILLS.MANAGE |
| PATCH | /api/v1/crm/skills/{skillId} | Update skill | CRM.SKILLS.MANAGE |
| DELETE | /api/v1/crm/skills/{skillId} | Delete skill | CRM.SKILLS.MANAGE |

### Capacity (5 endpoints)

| Method | Path | Description | Capability |
|--------|------|-------------|------------|
| GET | /api/v1/crm/capacity | List plans | CRM.CAPACITY.READ |
| GET | /api/v1/crm/capacity/{planId} | Get plan | CRM.CAPACITY.READ |
| POST | /api/v1/crm/capacity | Create plan | CRM.CAPACITY.MANAGE |
| PATCH | /api/v1/crm/capacity/{planId} | Adjust capacity | CRM.CAPACITY.MANAGE |
| GET | /api/v1/crm/capacity/forecast | Forecast capacity | CRM.CAPACITY.READ |

### Workload (5 endpoints)

| Method | Path | Description | Capability |
|--------|------|-------------|------------|
| GET | /api/v1/crm/workload | List workload | CRM.WORKLOAD.READ |
| GET | /api/v1/crm/workload/hours | Get hours | CRM.WORKLOAD.READ |
| POST | /api/v1/crm/workload | Assign work | CRM.WORKLOAD.MANAGE |
| PATCH | /api/v1/crm/workload/{workloadId}/reassign | Reassign | CRM.WORKLOAD.MANAGE |
| PATCH | /api/v1/crm/workload/{workloadId}/release | Release | CRM.WORKLOAD.MANAGE |

### Service Assignments (6 endpoints)

| Method | Path | Description | Capability |
|--------|------|-------------|------------|
| GET | /api/v1/crm/service-assignments | List assignments | CRM.ASSIGNMENT.READ |
| GET | /api/v1/crm/service-assignments/{assignmentId} | Get assignment | CRM.ASSIGNMENT.READ |
| POST | /api/v1/crm/service-assignments | Assign service | CRM.ASSIGNMENT.MANAGE |
| PATCH | /api/v1/crm/service-assignments/{assignmentId}/reassign | Reassign | CRM.ASSIGNMENT.MANAGE |
| PATCH | /api/v1/crm/service-assignments/{assignmentId}/complete | Complete | CRM.ASSIGNMENT.MANAGE |
| PATCH | /api/v1/crm/service-assignments/{assignmentId}/cancel | Cancel | CRM.ASSIGNMENT.MANAGE |

---

## 3. Total Endpoint Count

| Category | Count |
|----------|-------|
| GET (Read) | 15 |
| POST (Create) | 8 |
| PATCH (Update/Transition) | 16 |
| DELETE | 2 |
| **Total** | **41** |

---

**Certification Date:** 2026-07-28
**Agent 4 Task 2 Status:** COMPLETE
