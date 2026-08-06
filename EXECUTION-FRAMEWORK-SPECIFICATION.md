# EXECUTION FRAMEWORK SPECIFICATION

**Date:** 2026-08-03
**Repository:** snadaiapp-png/SNAD
**Version:** 1.0.0

---

## 1. Executive Summary

The SANAD Execution Framework is a reusable execution engine for all SANAD modules. It provides a single source of truth for execution state, progress calculation, certification, and validation.

**No module may implement its own execution logic.**

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    SANAD Execution Framework                 │
├─────────────────────────────────────────────────────────────┤
│  Types │ Calculators │ Validators │ Providers │ Hooks       │
├─────────────────────────────────────────────────────────────┤
│                    Module Data Providers                     │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐        │
│  │ CRM │ │ ERP │ │ POS │ │ Fin │ │ HR  │ │ ... │        │
│  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘        │
├─────────────────────────────────────────────────────────────┤
│                    Shared Validation Engine                  │
├─────────────────────────────────────────────────────────────┤
│                    Shared Calculation Engine                 │
├─────────────────────────────────────────────────────────────┤
│                    Shared UI Components                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Location

```
apps/web/lib/execution/
├── types/              # Core entity types
├── calculators/        # Progress and completion calculators
├── validators/         # Integrity validators
├── providers/          # Provider interface
├── hooks/              # React hooks
├── constants/          # Shared constants
└── index.ts            # Barrel export
```

---

## 4. Design Principles

| Principle | Description |
|-----------|-------------|
| **Single Source of Truth** | Execution state has exactly one authoritative source |
| **No Duplication** | No module may duplicate execution logic |
| **Automatic Calculation** | Progress is always calculated from tasks, never hardcoded |
| **Evidence-Based Certification** | Certification requires evidence and passing acceptance criteria |
| **Module Isolation** | Each module provides its own data via a provider interface |
| **Reusable Validation** | All modules inherit the same validation rules |

---

## 5. Core Components

### 5.1 Types (`types/`)

Canonical entity types for the execution model:

- `ExecutionProgram` — Top-level container
- `ExecutionGroup` — Logical grouping of tasks
- `ExecutionMilestone` — Checkpoint within a group
- `ExecutionTask` — Unit of work
- `ExecutionEvidence` — Proof of completion
- `AcceptanceCriteria` — Conditions for certification
- `Certification` — Formal approval
- `ExecutionProgress` — Calculated progress metrics

### 5.2 Calculators (`calculators/`)

Reusable calculation engines:

- `calculateGroupProgress()` — Progress from tasks
- `calculateProgramProgress()` — Aggregate progress
- `calculateCertificationStatus()` — Certification eligibility
- `buildDependencyGraph()` — Dependency relationships
- `topologicalSort()` — Execution order
- `getGroupEvidenceCoverage()` — Evidence metrics

### 5.3 Validators (`validators/`)

Integrity validation rules:

- `validateProgressIntegrity()` — Progress calculation correctness
- `validateCertificationIntegrity()` — Certification requirements
- `validateEvidenceIntegrity()` — Evidence coverage
- `validateDependencyIntegrity()` — Dependency graph validity
- `validateTaskIntegrity()` — Task data completeness
- `validateCrossLayerConsistency()` — Cross-layer alignment
- `validateExecutionGroup()` — Comprehensive group validation
- `validateExecutionProgram()` — Comprehensive program validation

### 5.4 Providers (`providers/`)

Module-specific data interface:

```typescript
interface ExecutionProvider {
  readonly moduleId: string;
  readonly moduleName: string;
  getPrograms(): Promise<ExecutionProgram[]>;
  getGroups(programId: string): Promise<ExecutionGroup[]>;
  getTasks(programId: string, groupCode: string): Promise<ExecutionTask[]>;
  getProgress(programId: string, groupCode: string): Promise<ExecutionProgress>;
  getCertification(programId: string, groupCode: string): Promise<Certification | null>;
}
```

### 5.5 Hooks (`hooks/`)

React hooks for consuming execution data:

- `useGroupProgress()` — Calculate group progress
- `useProgramProgress()` — Calculate program progress
- `useGroupValidation()` — Validate a group
- `useProgramValidation()` — Validate a program
- `useExecutionProvider()` — Access a provider

---

## 6. Module Integration

### 6.1 Creating a Module Provider

```typescript
import { InMemoryExecutionProvider } from "@/lib/execution";

const crmProvider = new InMemoryExecutionProvider("CRM", "CRM Platform");
crmProvider.loadPrograms(crmPrograms);
```

### 6.2 Using Calculators

```typescript
import { calculateGroupProgress } from "@/lib/execution";

const progress = calculateGroupProgress(group);
console.log(progress.percentage); // 100
```

### 6.3 Using Validators

```typescript
import { validateExecutionGroup } from "@/lib/execution";

const results = validateExecutionGroup(group, certification);
const allPassed = results.every(r => r.passed);
```

### 6.4 Using Hooks

```typescript
import { useGroupProgress, useGroupValidation } from "@/lib/execution";

function GroupCard({ group }) {
  const progress = useGroupProgress(group);
  const validation = useGroupValidation(group);
  
  return (
    <div>
      <span>{progress.percentage}%</span>
      {!validation.allPassed && <WarningBadge />}
    </div>
  );
}
```

---

## 7. Validation Rules

| Rule | Description | Enforcement |
|------|-------------|-------------|
| Rule 1 | CERTIFIED group must contain at least one Task | Build-time |
| Rule 2 | Progress must equal Completed Tasks / Total Tasks | Build-time |
| Rule 3 | Progress = 100% requires every Task = DONE | Build-time |
| Rule 4 | CERTIFIED requires Acceptance Criteria PASS | Build-time |
| Rule 5 | Dashboard must exactly match API | Runtime |
| Rule 6 | API must exactly match Database | Runtime |
| Rule 7 | No duplicated execution state | Architecture |

---

## 8. Future Migration

The framework is designed for portability:

- **Current location:** `apps/web/lib/execution/`
- **Future location:** `packages/execution/`
- **Migration requirement:** No public API changes

When the monorepo adopts a package manager (pnpm workspaces, turborepo), the framework can be extracted to `packages/execution/` without changing any consumer code.

---

## 9. Acceptance Criteria

- ✅ One reusable execution engine
- ✅ Module-specific data providers
- ✅ Shared validation engine
- ✅ Shared calculation engine
- ✅ Strongly typed domain model
- ✅ Zero duplicated execution logic
- ✅ Repository structure unchanged
- ✅ Ready for future extraction into packages/execution
