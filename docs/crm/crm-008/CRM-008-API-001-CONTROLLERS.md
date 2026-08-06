# CRM-008-API-001: REST Controllers

> **Agent:** Agent 4 — REST API & RBAC Implementation
> **Task:** 1 — REST Controllers
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation of 8 REST controllers for CRM-008 Team Management.

---

## 2. Controller Inventory

| # | Controller | Path | UseCase | Methods |
|---|------------|------|---------|---------|
| 1 | TeamController | /api/v1/crm/teams | SalesTeamUseCases, TeamManagementUseCases | 5 |
| 2 | ShiftTemplateController | /api/v1/crm/shift-templates | ShiftManagementUseCases | 6 |
| 3 | ShiftAssignmentController | /api/v1/crm/shift-assignments | ShiftManagementUseCases | 4 |
| 4 | AvailabilityController | /api/v1/crm/availability | AvailabilityManagementUseCases | 5 |
| 5 | SkillController | /api/v1/crm/skills | SkillManagementUseCases | 4 |
| 6 | CapacityController | /api/v1/crm/capacity | CapacityManagementUseCases | 5 |
| 7 | WorkloadController | /api/v1/crm/workload | WorkloadManagementUseCases | 5 |
| 8 | ServiceAssignmentController | /api/v1/crm/service-assignments | ServiceAssignmentUseCases | 6 |

---

## 3. Controller Patterns

- **Annotation**: `@RestController` + `@RequestMapping`
- **Authorization**: `@RequireCapability` on every method
- **Tenant Isolation**: Extracted from `Authentication.getDetails()`
- **Validation**: `@Valid @RequestBody` with Bean Validation
- **Response Format**: `Map<String, Object>` with snake_case keys
- **HTTP Status**: 201 Created for POST, 204 No Content for DELETE, 200 OK for GET/PATCH

---

## 4. File Manifest

| File | Location |
|------|----------|
| TeamController.java | ownership/web/ |
| ShiftTemplateController.java | ownership/web/ |
| ShiftAssignmentController.java | ownership/web/ |
| AvailabilityController.java | ownership/web/ |
| SkillController.java | ownership/web/ |
| CapacityController.java | ownership/web/ |
| WorkloadController.java | ownership/web/ |
| ServiceAssignmentController.java | ownership/web/ |
| TeamModels.java | ownership/web/ |

---

**Certification Date:** 2026-07-28
**Agent 4 Task 1 Status:** COMPLETE
