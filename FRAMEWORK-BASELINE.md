# FRAMEWORK BASELINE — SANAD Execution Framework v1.0.0

**Status:** FROZEN
**Version:** 1.0.0
**Date:** 2026-08-03
**Location:** `apps/web/lib/execution/`

---

## Overview

The SANAD Execution Framework is the **single execution engine** for all SANAD modules. This baseline document freezes all public interfaces, types, contracts, and APIs.

**Breaking changes are prohibited until v2.0.0.**

---

## Framework Structure

```
apps/web/lib/execution/
├── index.ts                    # Main barrel export
├── types/
│   ├── index.ts               # Type re-exports
│   └── execution-entities.ts  # Canonical entity definitions
├── calculators/
│   ├── index.ts               # Calculator re-exports
│   ├── group-progress.ts      # Group progress calculation
│   ├── program-progress.ts    # Program progress calculation
│   ├── certification.ts       # Certification eligibility
│   ├── dependencies.ts        # Dependency graph & cycle detection
│   └── evidence-coverage.ts   # Evidence coverage metrics
├── validators/
│   ├── index.ts               # Validator re-exports
│   ├── progress.ts            # Progress integrity validation
│   ├── certification.ts       # Certification integrity validation
│   ├── evidence.ts            # Evidence integrity validation
│   ├── dependencies.ts        # Dependency integrity validation
│   ├── tasks.ts               # Task integrity validation
│   ├── consistency.ts         # Cross-layer consistency validation
│   ├── group.ts               # Group-level validation orchestrator
│   └── program.ts             # Program-level validation orchestrator
├── providers/
│   ├── index.ts               # Provider re-exports
│   └── execution-provider.ts  # ExecutionProvider interface + InMemoryProvider
├── hooks/
│   ├── index.ts               # Hook re-exports
│   ├── use-execution-progress.ts    # Progress hooks
│   ├── use-execution-validation.ts  # Validation hooks
│   └── use-execution-provider.ts    # Provider hook
└── constants/
    └── index.ts               # Shared constants
```

---

## Frozen Public API

### Types (16)

| Type | Description |
|------|-------------|
| `ExecutionProgram` | Top-level container for all execution groups |
| `ExecutionGroup` | Logical grouping of milestones and tasks |
| `ExecutionMilestone` | Checkpoint within a group |
| `ExecutionTask` | Unit of work that can be completed |
| `ExecutionEvidence` | Proof that work was completed |
| `AcceptanceCriteria` | Conditions for certification |
| `Certification` | Formal approval of a group or milestone |
| `ExecutionProgress` | Calculated progress metrics |
| `ExecutionDependency` | Dependency between entities |
| `ExecutionArtifact` | File or artifact produced by execution |
| `GroupStatus` | Status enum for groups (7 values) |
| `TaskStatus` | Status enum for tasks (6 values) |
| `TaskType` | Type of work (13 values) |
| `TaskPriority` | Priority level (4 values) |
| `CertificationStatus` | Certification status (4 values) |
| `EvidenceType` | Type of evidence (9 values) |

### Calculators (11)

| Function | Signature | Description |
|----------|-----------|-------------|
| `calculateGroupProgress` | `(group: ExecutionGroup) => ExecutionProgress` | Calculate progress from tasks |
| `calculateProgramProgress` | `(program: ExecutionProgram) => ExecutionProgress` | Aggregate progress across groups |
| `calculateGroupProgressMap` | `(program: ExecutionProgram) => Map<string, ExecutionProgress>` | Progress map for all groups |
| `calculateCertificationStatus` | `(group: ExecutionGroup, cert: Certification) => CertificationStatus` | Determine certification status |
| `isEligibleForCertification` | `(group: ExecutionGroup, cert: Certification) => boolean` | Check certification eligibility |
| `buildDependencyGraph` | `(groups: ExecutionGroup[]) => ExecutionDependency[]` | Build dependency graph |
| `topologicalSort` | `(groups: ExecutionGroup[]) => string[]` | Sort groups by dependencies |
| `getDependents` | `(groups: ExecutionGroup[], code: string) => string[]` | Get dependent groups |
| `getAllDependencies` | `(groups: ExecutionGroup[], code: string) => Set<string>` | Get transitive dependencies |
| `getGroupEvidenceCoverage` | `(group: ExecutionGroup) => {...}` | Calculate evidence coverage |
| `hasSufficientEvidence` | `(group: ExecutionGroup) => boolean` | Check evidence sufficiency |

### Validators (9)

| Function | Signature | Description |
|----------|-----------|-------------|
| `validateProgressIntegrity` | `(group: ExecutionGroup) => ValidationResult[]` | Validate progress calculation |
| `validateCertificationIntegrity` | `(group: ExecutionGroup, cert: Certification) => ValidationResult[]` | Validate certification rules |
| `validateEvidenceIntegrity` | `(group: ExecutionGroup) => ValidationResult[]` | Validate evidence coverage |
| `validateDependencyIntegrity` | `(groups: ExecutionGroup[]) => ValidationResult[]` | Validate dependency graph |
| `validateTaskIntegrity` | `(group: ExecutionGroup) => ValidationResult[]` | Validate task structure |
| `validateCrossLayerConsistency` | `(group: ExecutionGroup) => ValidationResult[]` | Validate status consistency |
| `validateExecutionGroup` | `(group: ExecutionGroup, cert?: Certification) => ValidationResult[]` | Full group validation |
| `validateExecutionProgram` | `(program: ExecutionProgram, certs?: Map<string, Certification>) => ValidationResult[]` | Full program validation |
| `isGroupValid` | `(group: ExecutionGroup, cert?: Certification) => boolean` | Quick validity check |
| `isProgramValid` | `(program: ExecutionProgram, certs?: Map<string, Certification>) => boolean` | Quick validity check |
| `getValidationSummary` | `(program: ExecutionProgram, certs?: Map<string, Certification>) => ValidationSummary` | Validation summary |

### Providers (1 interface + 1 implementation)

| Export | Type | Description |
|--------|------|-------------|
| `ExecutionProvider` | `interface` | Contract for module-specific data |
| `InMemoryExecutionProvider` | `class` | In-memory implementation for testing |

**ExecutionProvider Interface:**

```typescript
interface ExecutionProvider {
  readonly moduleId: string;
  readonly moduleName: string;

  // Data Access
  getPrograms(): Promise<ExecutionProgram[]>;
  getProgram(programId: string): Promise<ExecutionProgram | null>;
  getGroups(programId: string): Promise<ExecutionGroup[]>;
  getGroup(programId: string, groupCode: string): Promise<ExecutionGroup | null>;
  getMilestones(programId: string, groupCode: string): Promise<ExecutionMilestone[]>;
  getTasks(programId: string, groupCode: string): Promise<ExecutionTask[]>;
  getEvidence(programId: string, groupCode: string, taskId: string): Promise<ExecutionEvidence[]>;
  getProgress(programId: string, groupCode: string): Promise<ExecutionProgress>;
  getProgramProgress(programId: string): Promise<ExecutionProgress>;
  getCertification(programId: string, groupCode: string): Promise<Certification | null>;

  // Optional Mutation
  updateTaskStatus?(programId: string, groupCode: string, taskId: string, status: string): Promise<void>;
  submitForCertification?(programId: string, groupCode: string): Promise<void>;
}
```

### Hooks (6)

| Hook | Signature | Description |
|------|-----------|-------------|
| `useGroupProgress` | `(group: ExecutionGroup) => ExecutionProgress` | Memoized group progress |
| `useProgramProgress` | `(program: ExecutionProgram) => ExecutionProgress` | Memoized program progress |
| `useGroupProgressMap` | `(groups: ExecutionGroup[]) => Map<string, ExecutionProgress>` | Memoized progress map |
| `useGroupValidation` | `(group: ExecutionGroup, cert?: Certification) => ValidationResult[]` | Memoized group validation |
| `useProgramValidation` | `(program: ExecutionProgram, certs?: Map<string, Certification>) => ValidationSummary` | Memoized program validation |
| `useExecutionProvider` | `(provider: ExecutionProvider) => UseExecutionProviderResult` | Provider access hook |

### Constants (11)

| Constant | Type | Description |
|----------|------|-------------|
| `GROUP_STATUS_LABELS_AR` | `Record<GroupStatus, string>` | Arabic labels for group statuses |
| `GROUP_STATUS_LABELS_EN` | `Record<GroupStatus, string>` | English labels for group statuses |
| `TASK_STATUS_LABELS_AR` | `Record<TaskStatus, string>` | Arabic labels for task statuses |
| `TASK_STATUS_LABELS_EN` | `Record<TaskStatus, string>` | English labels for task statuses |
| `TASK_TYPE_LABELS_AR` | `Record<TaskType, string>` | Arabic labels for task types |
| `TASK_TYPE_LABELS_EN` | `Record<TaskType, string>` | English labels for task types |
| `PRIORITY_LABELS_AR` | `Record<TaskPriority, string>` | Arabic labels for priorities |
| `PRIORITY_LABELS_EN` | `Record<TaskPriority, string>` | English labels for priorities |
| `STATUS_COLORS` | `Record<GroupStatus, string>` | Color codes for group statuses |
| `EXECUTION_RULES` | `object` | Execution rules constants |

---

## Dependency Flow

```
Types (execution-entities.ts)
    ↓
Calculators (group-progress, program-progress, certification, dependencies, evidence-coverage)
    ↓
Validators (progress, certification, evidence, dependencies, tasks, consistency, group, program)
    ↓
Providers (execution-provider.ts)
    ↓
Hooks (use-execution-progress, use-execution-validation, use-execution-provider)
    ↓
Main Export (index.ts)
```

**Rules:**
- Dependencies flow in one direction only
- No circular dependencies
- Types have no internal dependencies
- Calculators depend only on Types
- Validators depend on Types + Calculators
- Providers depend on Types + Calculators
- Hooks depend on Types + Calculators + Validators + Providers

---

## Integrity Rules (FROZEN)

| # | Rule | Enforcement |
|---|------|-------------|
| R1 | CERTIFIED requires Tasks | Automated |
| R2 | Progress = (done + approved) / total | Automated |
| R3 | 100% requires all tasks DONE/APPROVED | Automated |
| R4 | CERTIFICATION requires Acceptance Criteria | Automated |
| R5 | Dashboard must match API | Manual |
| R6 | API must match Database | Manual |
| R7 | No duplicate execution state | Automated |

---

## Semantic Versioning

| Version | Change Type | Description |
|---------|-------------|-------------|
| MAJOR | Breaking | API changes that break existing consumers |
| MINOR | Feature | New features, backwards compatible |
| PATCH | Fix | Bug fixes, backwards compatible |

**Current Version:** 1.0.0
**Next Major:** 2.0.0 (requires RFC)

---

## Breaking Change Prohibition

Until v2.0.0, the following changes are **PROHIBITED:**

1. Removing any exported type, function, or constant
2. Changing function signatures
3. Changing type definitions
4. Changing return types
5. Changing parameter types
6. Adding required parameters to existing functions
7. Changing error handling behavior

**Allowed changes (backwards compatible):**
1. Adding new exports
2. Adding optional parameters
3. Adding new types
4. Adding new functions
5. Adding new constants
6. Bug fixes that don't change API

---

## Files in Scope

Total files: **25**

| File | Lines | Status |
|------|-------|--------|
| `index.ts` | 93 | FROZEN |
| `types/index.ts` | 26 | FROZEN |
| `types/execution-entities.ts` | 294 | FROZEN |
| `calculators/index.ts` | 13 | FROZEN |
| `calculators/group-progress.ts` | 57 | FROZEN |
| `calculators/program-progress.ts` | 70 | FROZEN |
| `calculators/certification.ts` | 63 | FROZEN |
| `calculators/dependencies.ts` | 139 | FROZEN |
| `calculators/evidence-coverage.ts` | 89 | FROZEN |
| `validators/index.ts` | 21 | FROZEN |
| `validators/progress.ts` | 73 | FROZEN |
| `validators/certification.ts` | 82 | FROZEN |
| `validators/evidence.ts` | 48 | FROZEN |
| `validators/dependencies.ts` | 73 | FROZEN |
| `validators/tasks.ts` | 72 | FROZEN |
| `validators/consistency.ts` | 90 | FROZEN |
| `validators/group.ts` | 61 | FROZEN |
| `validators/program.ts` | 84 | FROZEN |
| `providers/index.ts` | 7 | FROZEN |
| `providers/execution-provider.ts` | 147 | FROZEN |
| `hooks/index.ts` | 9 | FROZEN |
| `hooks/use-execution-progress.ts` | 47 | FROZEN |
| `hooks/use-execution-validation.ts` | 51 | FROZEN |
| `hooks/use-execution-provider.ts` | 36 | FROZEN |
| `constants/index.ts` | 125 | FROZEN |

**Total Lines:** ~1,946

---

## Baseline Certification

✅ All 25 files audited
✅ All public interfaces documented
✅ All dependencies mapped
✅ No circular dependencies detected
✅ No CRM-specific imports in engine
✅ Semantic versioning assigned: v1.0.0
✅ Breaking changes prohibited

**BASELINE STATUS: LOCKED**
