# SANAD Execution Framework — Developer Guide

**Version:** 1.0.0  
**Audience:** Frontend & Backend Developers

---

## 1. Quick Start

### 1.1 Installation

The framework is already included in the SANAD monorepo:

```typescript
import { calculateGroupProgress, validateExecutionGroup } from "@/lib/execution";
```

### 1.2 Basic Usage

```typescript
import { 
  calculateGroupProgress, 
  validateExecutionGroup,
  isGroupValid 
} from "@/lib/execution";

// Calculate progress for a group
const progress = calculateGroupProgress(tasks);
console.log(`${progress.percentage}% complete`);

// Validate a group
const results = validateExecutionGroup(group);
const isValid = isGroupValid(group);
```

## 2. Implementing a Module

### 2.1 Create Your Provider

```typescript
// apps/web/app/your-module/your-module-provider.ts
import type { 
  ExecutionProvider, 
  ExecutionGroup, 
  ExecutionTask, 
  ExecutionProgress 
} from "@/lib/execution";
import { calculateGroupProgress } from "@/lib/execution";

export class YourModuleProvider implements ExecutionProvider {
  private groups: ExecutionGroup[];
  private tasks: ExecutionTask[];

  constructor(groups: ExecutionGroup[], tasks: ExecutionTask[]) {
    this.groups = groups;
    this.tasks = tasks;
  }

  getGroups(): ExecutionGroup[] {
    return this.groups;
  }

  getTasks(groupCode: string): ExecutionTask[] {
    return this.tasks.filter(t => t.groupCode === groupCode);
  }

  getCertification(groupCode: string): Certification | null {
    // Fetch from your data source
    return null;
  }

  getProgress(groupCode: string): ExecutionProgress {
    const tasks = this.getTasks(groupCode);
    return calculateGroupProgress(tasks);
  }
}
```

### 2.2 Create Execution Data

```typescript
// apps/web/app/your-module/your-module-execution-data.ts
import type { ExecutionGroup, ExecutionTask } from "@/lib/execution";

export const EXECUTION_GROUPS: ExecutionGroup[] = [
  {
    code: "G0",
    titleAr: "المرحلة الأولى",
    titleEn: "Phase 1",
    purposeAr: "...",
    purposeEn: "...",
    status: "IN_PROGRESS",
    dependencies: [],
    canParallelizeWith: [],
    tasks: [],
    stageReport: null,
  },
  // ... more groups
];

export const MODULE_TASKS: ExecutionTask[] = [
  {
    id: "G0-T01",
    number: "G0-01",
    nameAr: "المهمة الأولى",
    nameEn: "First Task",
    groupCode: "G0",
    descriptionAr: "...",
    descriptionEn: "...",
    type: "Frontend",
    priority: "Critical",
    status: "DONE",
    dependencies: [],
    acceptanceCriteriaAr: "...",
    implementationNotesAr: "...",
  },
  // ... more tasks
];
```

### 2.3 Create Execution Board

```typescript
// apps/web/app/your-module/your-module-execution-board.tsx
"use client";

import { useMemo } from "react";
import { 
  calculateGroupProgress, 
  validateExecutionGroup,
  GROUP_STATUS_LABELS_AR,
  STATUS_COLORS 
} from "@/lib/execution";
import { EXECUTION_GROUPS, MODULE_TASKS } from "./your-module-execution-data";

export function YourModuleExecutionBoard() {
  const groupData = useMemo(() => {
    return EXECUTION_GROUPS.map(group => {
      const tasks = MODULE_TASKS.filter(t => t.groupCode === group.code);
      const progress = calculateGroupProgress(tasks);
      const validation = validateExecutionGroup(group);
      const isValid = validation.every(r => r.passed);

      return { ...group, tasks, progress, validation, isValid };
    });
  }, []);

  return (
    <div>
      <h1>لوحة التنفيذ</h1>
      {groupData.map(group => (
        <div key={group.code} style={{ 
          borderLeft: `4px solid ${STATUS_COLORS[group.status]}` 
        }}>
          <h3>{group.titleAr}</h3>
          <p>{group.progress.percentage}%</p>
          <span>{GROUP_STATUS_LABELS_AR[group.status]}</span>
        </div>
      ))}
    </div>
  );
}
```

## 3. Using Hooks

### 3.1 Progress Hooks

```typescript
import { useGroupProgress, useProgramProgress } from "@/lib/execution";

function MyComponent() {
  const progress = useGroupProgress("G0");
  const programProgress = useProgramProgress();

  return (
    <div>
      <p>G0: {progress.percentage}%</p>
      <p>Total: {programProgress.percentage}%</p>
    </div>
  );
}
```

### 3.2 Validation Hooks

```typescript
import { useGroupValidation } from "@/lib/execution";

function ValidationPanel() {
  const results = useGroupValidation("G0");
  const failures = results.filter(r => !r.passed);

  return (
    <div>
      <h3>Validation Results</h3>
      {failures.map(f => (
        <div key={f.rule}>{f.message}</div>
      ))}
    </div>
  );
}
```

## 4. Best Practices

### 4.1 DO

- ✅ Use `calculateGroupProgress()` for all progress calculations
- ✅ Use `validateExecutionGroup()` before certification
- ✅ Store tasks in a single registry per module
- ✅ Use the ExecutionProvider interface for data access
- ✅ Run integrity validation in CI/CD

### 4.2 DON'T

- ❌ Hardcode progress percentages
- ❌ Implement custom progress calculation
- ❌ Store execution state outside the framework
- ❌ Skip validation before certification
- ❌ Create module-specific execution logic

## 5. Common Patterns

### 5.1 Task Status Updates

```typescript
import { MODULE_TASKS } from "./your-module-execution-data";

function updateTaskStatus(taskId: string, newStatus: TaskStatus) {
  const task = MODULE_TASKS.find(t => t.id === taskId);
  if (task) {
    task.status = newStatus;
    // Progress is automatically recalculated
  }
}
```

### 5.2 Certification Flow

```typescript
import { 
  isEligibleForCertification, 
  validateExecutionGroup 
} from "@/lib/execution";

function certifyGroup(group: ExecutionGroup, tasks: ExecutionTask[]) {
  // 1. Check eligibility
  if (!isEligibleForCertification(group)) {
    throw new Error("Group not eligible for certification");
  }

  // 2. Run validation
  const results = validateExecutionGroup(group);
  const failures = results.filter(r => !r.passed);
  
  if (failures.length > 0) {
    throw new Error(`Validation failed: ${failures.map(f => f.message).join(", ")}`);
  }

  // 3. Create certification
  return {
    groupCode: group.code,
    certifiedAt: new Date(),
    certifiedBy: getCurrentUser(),
    acceptanceCriteria: extractCriteria(group),
  };
}
```

## 6. Troubleshooting

### 6.1 Progress Shows 0%

**Cause:** No tasks found for the group.

**Fix:** Verify `groupCode` matches between groups and tasks.

### 6.2 Validation Fails

**Cause:** Inconsistent state between group status and task statuses.

**Fix:** Ensure all tasks are marked DONE before setting group to DONE.

### 6.3 Type Errors

**Cause:** Using wrong types from the framework.

**Fix:** Import types from `@/lib/execution`:
```typescript
import type { ExecutionGroup, ExecutionTask } from "@/lib/execution";
```

## 7. Support

- **Documentation:** See `EXECUTION-FRAMEWORK-API.md` for complete API reference
- **Examples:** Check `apps/web/app/crm/` for real-world usage
- **Issues:** Report to SANAD Architecture Team
