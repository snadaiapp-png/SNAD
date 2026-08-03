# PLATFORM ADOPTION MATRIX — SANAD Execution Framework

**Date:** 2026-08-03
**Framework Version:** v1.0.0
**Status:** ACTIVE

---

## Overview

This document tracks the adoption status of the SANAD Execution Framework across all platform modules.

**Adoption Levels:**
- ✅ **Already Adopted** — Module fully uses shared framework
- 🔄 **Ready for Adoption** — Module exists, no execution logic to migrate
- ⚠️ **Requires Refactoring** — Module has local execution logic that must be replaced
- 🔧 **Requires Adapter** — Module needs custom adapter to interface with framework
- 🚫 **Blocked** — Module cannot adopt due to dependencies
- 📋 **Planned** — Module planned but not yet implemented

---

## Module Adoption Status

### Current Platform Modules

| Module | Location | Status | Action Required |
|--------|----------|--------|-----------------|
| **CRM** | `app/crm/` | ⚠️ Requires Refactoring | Replace local types, calculators, constants with framework |
| **Control Plane** | `app/control-plane/` | 🔄 Ready for Adoption | No execution logic, adopts by reference |
| **Workspace** | `app/workspace/` | 🔄 Ready for Adoption | No execution logic, adopts by reference |
| **API** | `app/api/` | 🔄 Ready for Adoption | Backend routes, no execution logic |
| **Auth** | `app/auth/` | 🔄 Ready for Adoption | Authentication, no execution logic |

### Planned Platform Modules (from Roadmap)

| Module | Status | Action Required |
|--------|--------|-----------------|
| **ERP** | 📋 Planned | Will adopt framework from inception |
| **Finance** | 📋 Planned | Will adopt framework from inception |
| **Inventory** | 📋 Planned | Will adopt framework from inception |
| **POS** | 📋 Planned | Will adopt framework from inception |
| **HR** | 📋 Planned | Will adopt framework from inception |
| **Analytics** | 📋 Planned | Will adopt framework from inception |
| **Workflow** | 📋 Planned | Will adopt framework from inception |
| **Identity** | 📋 Planned | Will adopt framework from inception |
| **Subscriptions** | 📋 Planned | Will adopt framework from inception |
| **Licensing** | 📋 Planned | Will adopt framework from inception |
| **Notifications** | 📋 Planned | Will adopt framework from inception |
| **AI Platform** | 📋 Planned | Will adopt framework from inception |

---

## CRM Module — Detailed Analysis

### Current State (Violations Found)

**Location:** `apps/web/app/crm/crm-execution-data.ts`

#### Violation 1: Local Type Definitions
```typescript
// ❌ LOCAL TYPES (VIOLATION)
export type GroupStatus = "NOT_STARTED" | "IN_PROGRESS" | ...;
export type TaskType = "Backend" | "Frontend" | ...;
export type TaskPriority = "Critical" | "High" | ...;
export type TaskStatus = "NOT_STARTED" | "IN_PROGRESS" | ...;
```

**Should be:**
```typescript
// ✅ FRAMEWORK TYPES
import type { GroupStatus, TaskType, TaskPriority, TaskStatus } from "@/lib/execution";
```

#### Violation 2: Local Interface Definitions
```typescript
// ❌ LOCAL INTERFACES (VIOLATION)
export interface ExecutionGroup {
  code: string; titleAr: string; ...
}
export interface CrmTask {
  id: string; number: string; ...
}
```

**Should be:**
```typescript
// ✅ FRAMEWORK INTERFACES
import type { ExecutionGroup, ExecutionTask } from "@/lib/execution";
```

#### Violation 3: Local Progress Calculations
```typescript
// ❌ LOCAL CALCULATORS (VIOLATION)
export function getGroupProgress(groupCode: string) {
  const tasks = getGroupTasks(groupCode);
  const total = tasks.length;
  const done = tasks.filter((t) => t.status === "DONE").length;
  // ... duplicate logic
}
```

**Should be:**
```typescript
// ✅ FRAMEWORK CALCULATORS
import { calculateGroupProgress } from "@/lib/execution";
```

#### Violation 4: Local Constants
```typescript
// ❌ LOCAL CONSTANTS (VIOLATION)
export const GROUP_STATUS_LABELS_AR: Record<GroupStatus, string> = { ... };
export const GROUP_STATUS_LABELS_EN: Record<GroupStatus, string> = { ... };
export const TASK_STATUS_LABELS_AR: Record<TaskStatus, string> = { ... };
export const TASK_TYPE_LABELS_AR: Record<TaskType, string> = { ... };
export const PRIORITY_LABELS_AR: Record<TaskPriority, string> = { ... };
```

**Should be:**
```typescript
// ✅ FRAMEWORK CONSTANTS
import {
  GROUP_STATUS_LABELS_AR,
  GROUP_STATUS_LABELS_EN,
  TASK_STATUS_LABELS_AR,
  TASK_TYPE_LABELS_AR,
  PRIORITY_LABELS_AR,
} from "@/lib/execution";
```

### Required Refactoring

| Component | Current | Target | Effort |
|-----------|---------|--------|--------|
| Types | 4 local types | Import from framework | Low |
| Interfaces | 2 local interfaces | Import from framework | Low |
| Calculators | 2 local functions | Use framework calculators | Medium |
| Constants | 5 local constants | Import from framework | Low |
| Data Structure | `CrmTask[]` | `ExecutionTask[]` | Medium |
| Evidence | Not implemented | Add evidence to tasks | High |

### Adoption Checklist

- [ ] Remove local type definitions
- [ ] Remove local interface definitions
- [ ] Remove local progress calculations
- [ ] Remove local constants
- [ ] Import all types from `@/lib/execution`
- [ ] Import all calculators from `@/lib/execution`
- [ ] Import all constants from `@/lib/execution`
- [ ] Implement `ExecutionProvider` for CRM
- [ ] Add evidence to all completed tasks
- [ ] Add acceptance criteria to milestones
- [ ] Run validation tests
- [ ] Update CRM execution board to use framework

---

## Other Module Analysis

### Control Plane Module
**Location:** `apps/web/app/control-plane/`
**Execution Logic:** None
**Adoption Action:** No refactoring needed. Adopts framework by reference if execution features are added.

### Workspace Module
**Location:** `apps/web/app/workspace/`
**Execution Logic:** None
**Adoption Action:** No refactoring needed. Navigation hub only.

### API Module
**Location:** `apps/web/app/api/`
**Execution Logic:** None (backend routes only)
**Adoption Action:** No refactoring needed. Backend APIs can expose execution data via framework providers.

### Auth Module
**Location:** `apps/web/app/auth/`
**Execution Logic:** None
**Adoption Action:** No refactoring needed. Authentication only.

---

## Future Module Requirements

All new modules **MUST** adopt the framework from inception:

1. **Import types** from `@/lib/execution`
2. **Implement `ExecutionProvider`** interface
3. **Use shared calculators** for progress calculation
4. **Use shared validators** for integrity checks
5. **Use shared constants** for labels and colors
6. **Never define local execution types**
7. **Never implement local progress calculations**
8. **Never implement local validation rules**

---

## Adoption Progress

| Metric | Count |
|--------|-------|
| Total Modules | 5 |
| Already Adopted | 0 |
| Ready for Adoption | 4 |
| Requires Refactoring | 1 |
| Requires Adapter | 0 |
| Blocked | 0 |
| Planned | 12 |

**Overall Adoption:** 0% (0/5 existing modules)
**Framework Compliance:** 0% (CRM violates single source of truth)

---

## Next Steps

1. **Phase 3:** Implement `CrmExecutionProvider`
2. **Phase 4:** Refactor CRM to use shared framework
3. **Phase 5:** Create contract tests for CRM provider
4. **Phase 6:** Update dashboard to use framework
5. **Phase 7:** Enforce governance rules
6. **Phase 8:** Add CI enforcement
7. **Phase 9:** Prepare for package extraction
8. **Phase 10:** Certify platform adoption

---

## Certification Status

| Criteria | Status |
|----------|--------|
| Framework API frozen | ✅ Complete |
| All providers implement contract | ❌ Pending (CRM) |
| No duplicated execution logic | ❌ Violated (CRM) |
| No duplicated validation logic | ✅ Complete (engine only) |
| No duplicated progress calculation | ❌ Violated (CRM) |
| CRM fully adopts framework | ❌ Pending |
| Remaining modules have adoption plans | ✅ Complete |
| Contract tests pass | ⏳ Pending |
| Integrity validation passes | ✅ Complete |
| CI enforcement is active | ⏳ Pending |
| Platform dashboard consumes framework | ⏳ Pending |

**Platform Certification:** NOT YET CERTIFIED
