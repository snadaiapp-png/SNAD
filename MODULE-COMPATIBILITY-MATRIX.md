# SANAD Module Compatibility Matrix

**Version:** 1.0.0  
**Last Updated:** 2026-08-03

---

## 1. Overview

This matrix tracks the adoption status and compatibility of each SANAD module with the Execution Framework.

## 2. Compatibility Status

| Module | Status | Provider | Execution Board | Validation | Notes |
|--------|--------|----------|-----------------|------------|-------|
| CRM | ADOPTED ✅ | Custom | Implemented | Active | First adopter, reference implementation |
| ERP | READY | Pending | Pending | Pending | Ready for adoption |
| Finance | READY | Pending | Pending | Pending | Ready for adoption |
| Inventory | READY | Pending | Pending | Pending | Ready for adoption |
| POS | READY | Pending | Pending | Pending | Ready for adoption |
| HR | READY | Pending | Pending | Pending | Ready for adoption |
| Analytics | READY | Pending | Pending | Pending | Ready for adoption |
| Workflow | READY | Pending | Pending | Pending | Ready for adoption |
| AI Platform | READY | Pending | Pending | Pending | Ready for adoption |

## 3. Status Definitions

| Status | Definition |
|--------|------------|
| ADOPTED | Module fully integrated with framework |
| IN PROGRESS | Module currently being migrated |
| READY | Module prepared for adoption, not yet started |
| NOT READY | Module requires preparation before adoption |
| EXEMPT | Module has approved exception |

## 4. CRM Module — Reference Implementation

### 4.1 Adoption Details

- **Adoption Date:** 2026-08-03
- **Provider:** `CrmExecutionProvider` (planned)
- **Execution Data:** `apps/web/app/crm/crm-execution-data.ts`
- **Execution Board:** `apps/web/app/crm/crm-execution-board.tsx`

### 4.2 Groups

| Code | Title | Status | Tasks |
|------|-------|--------|-------|
| G0 | Execution Control & CRM Dashboard | APPROVED | 15 |
| G1 | Database & Multi-Tenant Foundation | NEEDS_REVIEW | 12 |
| G2 | i18n, RTL/LTR & UI Shell | NEEDS_REVIEW | 10 |
| G3 | Core CRM Entities | NOT_STARTED | 0 |
| G4 | Opportunities & Pipeline | NOT_STARTED | 0 |
| G5 | Tasks, Transfers & Employees | NOT_STARTED | 0 |
| G6 | Reports & Analytics | NOT_STARTED | 0 |
| G7 | Mobile Offline Foundation | NOT_STARTED | 0 |
| G8 | Caller Identification | NOT_STARTED | 0 |
| G9 | AI CRM Free & Paid Billing | NOT_STARTED | 0 |
| G10 | QA, Security & Acceptance | NOT_STARTED | 0 |

### 4.3 Framework Usage

```typescript
// Import from framework
import { 
  calculateGroupProgress, 
  validateExecutionGroup,
  GROUP_STATUS_LABELS_AR 
} from "@/lib/execution";

// Use in execution board
const progress = calculateGroupProgress(tasks);
const validation = validateExecutionGroup(group);
```

## 5. Module Adoption Guide

### 5.1 Steps to Adopt

1. **Create execution data file**
   - Define groups and tasks
   - Follow CRM example

2. **Implement provider (optional)**
   - For custom data access
   - Use InMemoryExecutionProvider for testing

3. **Create execution board**
   - Use framework hooks
   - Display progress and validation

4. **Add to CI/CD**
   - Run `npm run validate:integrity`
   - Add to pre-commit hooks

5. **Document adoption**
   - Update this matrix
   - Create module-specific docs

### 5.2 Example Adoption

```typescript
// apps/web/app/erp/erp-execution-data.ts
import type { ExecutionGroup, ExecutionTask } from "@/lib/execution";

export const ERP_GROUPS: ExecutionGroup[] = [
  {
    code: "G0",
    titleAr: "أساس ERP",
    titleEn: "ERP Foundation",
    // ... other fields
  },
];

export const ERP_TASKS: ExecutionTask[] = [
  // ... tasks
];
```

## 6. Exceptions

| Module | Exception | Reason | Expiry |
|--------|-----------|--------|--------|
| None | — | — | — |

## 7. Compliance Score

| Module | Compliance | Last Checked |
|--------|------------|--------------|
| CRM | 100% | 2026-08-03 |
| ERP | — | — |
| Finance | — | — |
| Inventory | — | — |
| POS | — | — |
| HR | — | — |
| Analytics | — | — |
| Workflow | — | — |
| AI Platform | — | — |

## 8. Migration Timeline

| Module | Target Date | Status |
|--------|-------------|--------|
| CRM | 2026-08-03 | COMPLETE |
| ERP | 2026-09-01 | PLANNED |
| Finance | 2026-09-15 | PLANNED |
| Inventory | 2026-10-01 | PLANNED |
| POS | 2026-10-15 | PLANNED |
| HR | 2026-11-01 | PLANNED |
| Analytics | 2026-11-15 | PLANNED |
| Workflow | 2026-12-01 | PLANNED |
| AI Platform | 2026-12-15 | PLANNED |

## 9. Support Contacts

| Module | Team Lead | Contact |
|--------|-----------|---------|
| CRM | TBD | TBD |
| ERP | TBD | TBD |
| Finance | TBD | TBD |
| Inventory | TBD | TBD |
| POS | TBD | TBD |
| HR | TBD | TBD |
| Analytics | TBD | TBD |
| Workflow | TBD | TBD |
| AI Platform | TBD | TBD |

## 10. Review Cycle

- **Monthly:** Update adoption status
- **Quarterly:** Compliance audit
- **Annually:** Full matrix review
