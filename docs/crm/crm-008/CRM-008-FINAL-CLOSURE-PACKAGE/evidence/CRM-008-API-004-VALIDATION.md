# CRM-008-API-004: Bean Validation

> **Agent:** Agent 4 — REST API & RBAC Implementation
> **Task:** 4 — Bean Validation
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the validation rules implemented across all CRM-008 controllers.

---

## 2. Validation Summary

### Controller-Level Validation

| Controller | Method | Validation | Error |
|------------|--------|------------|-------|
| TeamController | createTeam | @Valid @RequestBody | 400 Bad Request |
| TeamController | updateTeam | @Valid @RequestBody | 400 Bad Request |
| ShiftTemplateController | createTemplate | @Valid @RequestBody | 400 Bad Request |
| ShiftTemplateController | updateTemplate | @Valid @RequestBody | 400 Bad Request |
| ShiftAssignmentController | assignShift | @Valid @RequestBody | 400 Bad Request |
| ShiftAssignmentController | updateAssignment | @Valid @RequestBody | 400 Bad Request |
| AvailabilityController | submitAvailability | @Valid @RequestBody | 400 Bad Request |
| SkillController | registerSkill | @Valid @RequestBody | 400 Bad Request |
| SkillController | updateSkill | @Valid @RequestBody | 400 Bad Request |
| CapacityController | createPlan | @Valid @RequestBody | 400 Bad Request |
| CapacityController | adjustCapacity | @Valid @RequestBody | 400 Bad Request |
| WorkloadController | assignWork | @Valid @RequestBody | 400 Bad Request |

### Validation Rules

| Field | Rule | DTO |
|-------|------|-----|
| code | @NotBlank @Size(max=50) | CreateTeamRequest |
| displayName | @NotBlank @Size(max=200) | CreateTeamRequest |
| description | @Size(max=2000) | CreateTeamRequest, UpdateTeamRequest |
| name | @NotBlank @Size(max=200) | CreateShiftTemplateRequest |
| skillName | @NotBlank @Size(max=200) | RegisterSkillRequest |
| reason | @Size(max=500) | SubmitAvailabilityRequest |
| proficiency | @Min(1) @Max(100) | RegisterSkillRequest, UpdateSkillRequest |
| maxCapacity | @Min(1) | CreateCapacityPlanRequest, AdjustCapacityRequest |
| allocatedCapacity | @Min(0) | AdjustCapacityRequest |
| estimatedHours | @Min(1) | AssignWorkRequest |

---

## 3. Tenant Isolation

All controllers extract `tenantId` from `Authentication.getDetails()`. Tenant is never accepted from request body or URL path.

---

## 4. Authentication Validation

All controllers use the `context()` helper which:
1. Checks Authentication is not null
2. Checks Authentication is authenticated
3. Checks details is a Map
4. Checks key exists in map
5. Parses UUID from string

Returns HTTP 401 on any failure.

---

**Certification Date:** 2026-07-28
**Agent 4 Task 4 Status:** COMPLETE
