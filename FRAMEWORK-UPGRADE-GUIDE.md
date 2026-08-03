# FRAMEWORK UPGRADE GUIDE — SANAD Execution Framework v1.0.0

**Purpose:** Guide for modules upgrading from local execution logic to the shared framework.

---

## Overview

This guide walks through adopting the SANAD Execution Framework in your module. The framework replaces local types, calculators, and validators with a shared, validated execution engine.

---

## Before You Start

### Prerequisites

1. ✅ Node.js 18+ installed
2. ✅ Module has existing execution data (groups, tasks)
3. ✅ Git working tree clean
4. ✅ Tests passing

### Estimate

- **Small module:** 2-4 hours
- **Medium module:** 4-8 hours
- **Large module:** 1-2 days

---

## Step 1: Identify Local Artifacts

Scan your module for these patterns:

```bash
# Find local type definitions
grep -r "type GroupStatus\|interface ExecutionGroup\|interface.*Task" apps/your-module/

# Find local calculators
grep -r "getGroupProgress\|getOverallProgress\|calculate.*Progress" apps/your-module/

# Find local constants
grep -r "GROUP_STATUS_LABELS\|TASK_STATUS_LABELS\|STATUS_COLORS" apps/your-module/
```

List what you find:

| Artifact | Location | Action |
|----------|----------|--------|
| Types | `types.ts` | Remove → Import from framework |
| Calculators | `progress.ts` | Remove → Use `calculateGroupProgress` |
| Constants | `constants.ts` | Remove → Import from framework |

---

## Step 2: Create Data File

Create a data file for your module's execution data:

```typescript
// apps/your-module/your-module-execution-data.ts

import type { ExecutionGroup, ExecutionTask } from "../../lib/execution";

/**
 * Your Module execution groups
 */
export const YOUR_MODULE_GROUP_DATA: ExecutionGroup[] = [
  {
    code: "M0",
    titleAr: "المجموعة الأولى",
    titleEn: "Group Zero",
    purposeAr: "الهدف",
    purposeEn: "Purpose",
    dependencies: [],
    canParallelizeWith: [],
    status: "NOT_STARTED",
    stageReport: null,
    milestones: [],
  },
  // ... more groups
];

/**
 * Your Module tasks
 */
export const YOUR_MODULE_TASKS: ExecutionTask[] = [
  {
    id: "M0-T1",
    groupId: "M0",
    number: 1,
    nameAr: "المهمة الأولى",
    nameEn: "Task One",
    descriptionAr: "الوصف",
    descriptionEn: "Description",
    status: "NOT_STARTED",
    type: "IMPLEMENTATION",
    priority: "P1",
    acceptanceCriteriaAr: "المعايير",
    acceptanceCriteriaEn: "Criteria",
    implementationNotesAr: null,
    implementationNotesEn: null,
  },
  // ... more tasks
];
```

---

## Step 3: Create Provider

Create a provider implementing the `ExecutionProvider` interface:

```typescript
// apps/your-module/your-module-execution-provider.ts

import type {
  ExecutionProvider,
  ExecutionProgram,
  ExecutionGroup,
  ExecutionTask,
  ExecutionProgress,
  Certification,
} from "../../lib/execution";
import { calculateGroupProgress, calculateProgramProgress } from "../../lib/execution";
import { YOUR_MODULE_GROUP_DATA, YOUR_MODULE_TASKS } from "./your-module-execution-data";

/**
 * Your Module execution provider
 */
export class YourModuleExecutionProvider implements ExecutionProvider {
  readonly moduleId = "YOUR_MODULE";
  readonly moduleName = "Your Module Name";
  readonly totalGroups = YOUR_MODULE_GROUP_DATA.length;
  readonly totalTasks = YOUR_MODULE_TASKS.length;

  async getExecutionProgram(): Promise<ExecutionProgram> {
    return {
      moduleId: this.moduleId,
      moduleName: this.moduleName,
      groups: YOUR_MODULE_GROUP_DATA,
      tasks: YOUR_MODULE_TASKS,
      progress: calculateProgramProgress(YOUR_MODULE_GROUP_DATA, YOUR_MODULE_TASKS),
      certification: null,
    };
  }

  async getExecutionGroup(groupCode: string): Promise<ExecutionGroup | null> {
    return YOUR_MODULE_GROUP_DATA.find((g) => g.code === groupCode) ?? null;
  }

  async getExecutionGroups(): Promise<ExecutionGroup[]> {
    return YOUR_MODULE_GROUP_DATA;
  }

  async getExecutionTasks(groupCode?: string): Promise<ExecutionTask[]> {
    if (groupCode) {
      return YOUR_MODULE_TASKS.filter((t) => t.groupId === groupCode);
    }
    return YOUR_MODULE_TASKS;
  }

  async getGroupProgress(groupCode: string): Promise<ExecutionProgress | null> {
    const group = YOUR_MODULE_GROUP_DATA.find((g) => g.code === groupCode);
    const tasks = YOUR_MODULE_TASKS.filter((t) => t.groupId === groupCode);
    if (!group) return null;
    return calculateGroupProgress(group, tasks);
  }

  async getProgramProgress(): Promise<ExecutionProgress> {
    return calculateProgramProgress(YOUR_MODULE_GROUP_DATA, YOUR_MODULE_TASKS);
  }

  async getCertification(): Promise<Certification | null> {
    // TODO: Implement when certification is available
    return null;
  }
}
```

---

## Step 4: Update Components

Replace local logic with framework imports:

### Before (Local Logic)

```typescript
// Old: Local types and calculators
import { getGroupProgress, getOverallProgress } from "./progress";
import { GROUP_STATUS_LABELS } from "./constants";
```

### After (Framework)

```typescript
// New: Framework imports
import { calculateGroupProgress, calculateProgramProgress } from "../../lib/execution";
import { GROUP_STATUS_LABELS_AR, GROUP_STATUS_LABELS_EN } from "../../lib/execution";
import { YOUR_MODULE_GROUP_DATA, YOUR_MODULE_TASKS } from "./your-module-execution-data";
```

### Update Progress Calculation

```typescript
// Before
const progress = getGroupProgress(group.code);

// After
const progress = calculateGroupProgress(group, YOUR_MODULE_TASKS.filter(t => t.groupId === group.code));
```

### Update Progress Display

```typescript
// Before
const overall = getOverallProgress();

// After
const overall = calculateProgramProgress(YOUR_MODULE_GROUP_DATA, YOUR_MODULE_TASKS);
```

---

## Step 5: Register Provider

Register your provider in the execution dashboard:

```typescript
// apps/web/app/control-plane/execution/page.tsx

import { YourModuleExecutionProvider } from "../../your-module/your-module-execution-provider";

// Register providers
const providers: ExecutionProvider[] = [
  new CrmExecutionProvider(),
  new YourModuleExecutionProvider(),  // ← Add this
];
```

---

## Step 6: Run Validation

```bash
# Run integrity validation
npx tsx scripts/validate-execution-integrity.ts

# Run contract tests
npx vitest run lib/execution/contract-tests.test.ts

# Run module-specific tests
npx vitest run app/your-module/ --reporter=verbose
```

**Expected results:**
- Integrity validation: All rules pass
- Contract tests: 20/20 pass
- Module tests: All pass

---

## Step 7: Clean Up

Remove local artifacts:

```bash
# Remove local types (if no longer used)
rm apps/your-module/types.ts  # or remove execution-related exports

# Remove local calculators (if no longer used)
rm apps/your-module/progress.ts  # or remove progress functions

# Remove local constants (if no longer used)
rm apps/your-module/constants.ts  # or remove execution-related constants
```

**⚠️ Important:** Only remove files/exports that are no longer used by other parts of your module.

---

## Step 8: Update Documentation

Update your module's documentation:

```markdown
## Execution Model

This module uses the SANAD Execution Framework.

- **Provider:** `YourModuleExecutionProvider`
- **Groups:** M0-M10
- **Tasks:** M0-T1 through M10-T5
- **Progress:** Calculated from tasks

See `apps/web/lib/execution/README.md` for framework documentation.
```

---

## Verification Checklist

- [ ] Local types removed or marked deprecated
- [ ] Local calculators removed or marked deprecated
- [ ] Local constants removed or marked deprecated
- [ ] Data file created with correct types
- [ ] Provider implements `ExecutionProvider` interface
- [ ] Provider registered in dashboard
- [ ] Components updated to use framework
- [ ] All tests passing
- [ ] Integrity validation passing
- [ ] Documentation updated

---

## Common Issues

### Issue: Type mismatch

**Error:** `Type 'X' is not assignable to type 'Y'`

**Solution:** Ensure your data matches the framework types exactly:
- Use `GroupStatus` from `lib/execution` instead of local `GroupStatus`
- Use `TaskStatus` from `lib/execution` instead of local `TaskStatus`

### Issue: Missing import

**Error:** `Cannot find module '../../lib/execution'`

**Solution:** Use relative imports, not path aliases:
- ✅ `../../lib/execution`
- ❌ `@/lib/execution`

### Issue: Progress calculation wrong

**Error:** Progress shows 0% when tasks are done

**Solution:** Ensure task `groupId` matches group `code`:
```typescript
// Group code must match task groupId
const group = { code: "M0", ... };
const task = { groupId: "M0", ... };
```

---

## Rollback Plan

If upgrade fails:

1. Revert changes: `git checkout -- apps/your-module/`
2. Restore local artifacts from git history
3. Keep framework imports for future retry

---

## Support

- **Documentation:** See `FRAMEWORK-DEVELOPER-GUIDE.md`
- **Examples:** See `apps/web/app/crm/crm-execution-provider.ts`
- **Issues:** Report via GitHub Issues

---

**Last Updated:** 2026-08-03
**Framework Version:** 1.0.0
