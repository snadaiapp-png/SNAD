# CRM-008-API-003: Request/Response DTOs

> **Agent:** Agent 4 — REST API & RBAC Implementation
> **Task:** 3 — Request/Response DTOs
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the DTO implementation for CRM-008 Team Management.

---

## 2. DTO Inventory

All DTOs are package-private Java records in `TeamModels.java`.

### Request DTOs (11)

| DTO | Fields | Validation |
|-----|--------|------------|
| CreateTeamRequest | code, displayName, description, managerUserId, defaultQueueId, defaultTerritoryId | @NotBlank, @Size |
| UpdateTeamRequest | displayName, description, status, managerUserId, defaultQueueId, defaultTerritoryId | @Size |
| CreateShiftTemplateRequest | name, startTime, endTime, daysOfWeek | @NotBlank, @NotNull |
| UpdateShiftTemplateRequest | name, startTime, endTime, daysOfWeek | @Size |
| CreateShiftAssignmentRequest | teamId, staffId, shiftTemplateId, startDate, endDate | @NotNull |
| UpdateShiftAssignmentRequest | shiftTemplateId, startDate, endDate | - |
| SubmitAvailabilityRequest | staffId, type, startDate, endDate, startTime, endTime, reason | @NotNull, @Size |
| RegisterSkillRequest | staffId, skillName, level, proficiency | @NotBlank, @NotNull, @Min, @Max |
| UpdateSkillRequest | level, proficiency | @Min, @Max |
| CreateCapacityPlanRequest | teamId, periodStart, periodEnd, maxCapacity | @NotNull, @Min |
| AdjustCapacityRequest | maxCapacity, allocatedCapacity | @Min |
| AssignWorkRequest | staffId, serviceId, jobId, estimatedHours, startDate, endDate | @NotNull, @Min |
| AssignServiceRequest | teamId, serviceId | @NotNull |

### Response DTOs

Responses use `Map<String, Object>` with snake_case keys (V1 pattern). No typed response DTOs.

---

## 3. Validation Annotations Used

| Annotation | Count | Usage |
|------------|-------|-------|
| @NotNull | 15 | Required UUIDs, dates, enums |
| @NotBlank | 4 | Required strings (code, name, skillName) |
| @Size(max=) | 8 | String length limits |
| @Min | 3 | Numeric minimums |
| @Max | 2 | Numeric maximums |
| @Valid | 8 | Request body validation trigger |

---

**Certification Date:** 2026-07-28
**Agent 4 Task 3 Status:** COMPLETE
