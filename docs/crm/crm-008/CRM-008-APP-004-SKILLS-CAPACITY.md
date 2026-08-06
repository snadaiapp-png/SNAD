# CRM-008-APP-004: Skills & Capacity Use Cases

> **Agent:** Agent 3 — Application Layer & Use Case Implementation
> **Task:** 4 — Skills & Capacity Use Cases
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation of Skills Management and Capacity Planning use cases for CRM-008.

---

## 2. Skills Use Cases

| Use Case | Method | Transactional | Description |
|----------|--------|---------------|-------------|
| RegisterSkill | `registerSkill()` | ✅ | Register a new skill |
| UpdateSkill | `updateSkill()` | ✅ | Update skill level/proficiency |
| DeleteSkill | `deleteSkill()` | ✅ | Delete a skill record |
| ListSkillsByStaff | `listSkillsByStaff()` | ❌ | List skills for a staff member |
| ListBySkillName | `listBySkillName()` | ❌ | Find staff by skill name |

### Business Rules

#### RegisterSkill
- **Preconditions**: Staff must exist in tenant
- **Postconditions**: Skill record created
- **Validations**: skillName required, level required, proficiency 1-100
- **Uniqueness**: One skill per staff member per skill name

#### UpdateSkill
- **Preconditions**: Skill must exist
- **Postconditions**: Level/proficiency updated
- **Validations**: proficiency 1-100

---

## 3. Capacity Use Cases

| Use Case | Method | Transactional | Description |
|----------|--------|---------------|-------------|
| CreateCapacityPlan | `createCapacityPlan()` | ✅ | Create a capacity plan |
| AdjustCapacity | `adjustCapacity()` | ✅ | Adjust capacity allocation |
| ForecastCapacity | `forecastCapacity()` | ❌ | Forecast future capacity |
| ListCapacityPlans | `listCapacityPlans()` | ❌ | List plans for a team |
| GetCapacityPlan | `getCapacityPlan()` | ❌ | Get a specific plan |

### Business Rules

#### CreateCapacityPlan
- **Preconditions**: Team must exist
- **Postconditions**: Plan created with DRAFT status
- **Validations**: periodEnd >= periodStart, maxCapacity > 0
- **Overlap Prevention**: No active plan for same team and overlapping period

#### AdjustCapacity
- **Preconditions**: Plan must exist and not be COMPLETED
- **Postconditions**: Capacity adjusted
- **Validations**: allocatedCapacity <= maxCapacity

#### ForecastCapacity
- **Preconditions**: Team must exist
- **Postconditions**: Returns forecast based on historical averages
- **Calculation**: Average maxCapacity and allocatedCapacity from history

---

## 4. State Transitions

### CapacityPlan
```
DRAFT → ACTIVE → COMPLETED
```

---

## 5. Integration

- Delegates to `SkillRepository` and `CapacityRepository`
- Validates team existence via `SalesTeamRepository`
- Records audit and timeline events

---

**Certification Date:** 2026-07-28
**Agent 3 Task 4 Status:** COMPLETE
