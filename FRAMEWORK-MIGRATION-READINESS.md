# FRAMEWORK MIGRATION READINESS — SANAD Execution Framework

**Status:** READY
**Version:** 1.0.0
**Date:** 2026-08-03

---

## Overview

This document certifies that the SANAD Execution Framework is ready to be migrated from `apps/web/lib/execution/` to `packages/execution/` without breaking changes.

---

## Migration Requirements

### Requirement 1: Public APIs MUST NOT Change

**Status:** ✅ VERIFIED

All public APIs are defined in `apps/web/lib/execution/index.ts`:

```typescript
// Types (16)
export type {
  ExecutionProgram,
  ExecutionGroup,
  ExecutionMilestone,
  ExecutionTask,
  ExecutionEvidence,
  AcceptanceCriteria,
  Certification,
  ExecutionProgress,
  ExecutionDependency,
  ExecutionArtifact,
  GroupStatus,
  TaskStatus,
  TaskType,
  TaskPriority,
  CertificationStatus,
  EvidenceType,
} from "./types";

// Calculators (11)
export {
  calculateGroupProgress,
  calculateProgramProgress,
  calculateGroupProgressMap,
  calculateCertificationStatus,
  isEligibleForCertification,
  buildDependencyGraph,
  topologicalSort,
  getDependents,
  getAllDependencies,
  getGroupEvidenceCoverage,
  hasSufficientEvidence,
} from "./calculators";

// Validators (11)
export {
  validateProgressIntegrity,
  validateCertificationIntegrity,
  validateEvidenceIntegrity,
  validateDependencyIntegrity,
  validateTaskIntegrity,
  validateCrossLayerConsistency,
  validateExecutionGroup,
  validateExecutionProgram,
  isGroupValid,
  isProgramValid,
  getValidationSummary,
} from "./validators";

// Providers (2)
export type { ExecutionProvider } from "./providers";
export { InMemoryExecutionProvider } from "./providers/execution-provider";

// Hooks (6)
export {
  useGroupProgress,
  useProgramProgress,
  useGroupProgressMap,
  useGroupValidation,
  useProgramValidation,
  useExecutionProvider,
} from "./hooks";

// Constants (11)
export {
  GROUP_STATUS_LABELS_AR,
  GROUP_STATUS_LABELS_EN,
  TASK_STATUS_LABELS_AR,
  TASK_STATUS_LABELS_EN,
  TASK_TYPE_LABELS_AR,
  TASK_TYPE_LABELS_EN,
  PRIORITY_LABELS_AR,
  PRIORITY_LABELS_EN,
  STATUS_COLORS,
  EXECUTION_RULES,
} from "./constants";
```

**Migration Impact:** None. All exports remain the same.

---

### Requirement 2: Provider Contracts MUST NOT Change

**Status:** ✅ VERIFIED

The `ExecutionProvider` interface is defined in `apps/web/lib/execution/providers/execution-provider.ts`:

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

**Migration Impact:** None. Interface remains the same.

---

### Requirement 3: Validator Interfaces MUST NOT Change

**Status:** ✅ VERIFIED

All validator functions are defined in `apps/web/lib/execution/validators/`:

```typescript
// Progress Validator
function validateProgressIntegrity(group: ExecutionGroup): ValidationResult[];

// Certification Validator
function validateCertificationIntegrity(group: ExecutionGroup, certification: Certification): ValidationResult[];

// Evidence Validator
function validateEvidenceIntegrity(group: ExecutionGroup): ValidationResult[];

// Dependencies Validator
function validateDependencyIntegrity(groups: ExecutionGroup[]): ValidationResult[];

// Tasks Validator
function validateTaskIntegrity(group: ExecutionGroup): ValidationResult[];

// Consistency Validator
function validateCrossLayerConsistency(group: ExecutionGroup): ValidationResult[];
function validateProgramConsistency(program: ExecutionProgram): ValidationResult[];

// Group Validator
function validateExecutionGroup(group: ExecutionGroup, certification?: Certification): ValidationResult[];
function isGroupValid(group: ExecutionGroup, certification?: Certification): boolean;

// Program Validator
function validateExecutionProgram(program: ExecutionProgram, certifications?: Map<string, Certification>): ValidationResult[];
function isProgramValid(program: ExecutionProgram, certifications?: Map<string, Certification>): boolean;
function getValidationSummary(program: ExecutionProgram, certifications?: Map<string, Certification>): ValidationSummary;
```

**Migration Impact:** None. All interfaces remain the same.

---

### Requirement 4: Calculator Interfaces MUST NOT Change

**Status:** ✅ VERIFIED

All calculator functions are defined in `apps/web/lib/execution/calculators/`:

```typescript
// Group Progress
function calculateGroupProgress(group: ExecutionGroup): ExecutionProgress;

// Program Progress
function calculateProgramProgress(program: ExecutionProgram): ExecutionProgress;
function calculateGroupProgressMap(program: ExecutionProgram): Map<string, ExecutionProgress>;

// Certification
function isEligibleForCertification(group: ExecutionGroup, certification: Certification): boolean;
function calculateCertificationStatus(group: ExecutionGroup, certification: Certification): CertificationStatus;

// Dependencies
function buildDependencyGraph(groups: ExecutionGroup[]): ExecutionDependency[];
function wouldCreateCycle(groups: ExecutionGroup[], fromCode: string, toCode: string): boolean;
function topologicalSort(groups: ExecutionGroup[]): string[];
function getDependents(groups: ExecutionGroup[], code: string): string[];
function getAllDependencies(groups: ExecutionGroup[], code: string): Set<string>;

// Evidence Coverage
function getTaskEvidenceCount(task: ExecutionTask): number;
function getGroupEvidenceCoverage(group: ExecutionGroup): EvidenceCoverage;
function hasSufficientEvidence(group: ExecutionGroup): boolean;
```

**Migration Impact:** None. All interfaces remain the same.

---

## File Structure for Migration

### Current Structure
```
apps/web/lib/execution/
├── index.ts
├── types/
├── calculators/
├── validators/
├── providers/
├── hooks/
└── constants/
```

### Target Structure
```
packages/execution/
├── src/
│   ├── index.ts
│   ├── types/
│   ├── calculators/
│   ├── validators/
│   ├── providers/
│   ├── hooks/
│   └── constants/
├── package.json
├── tsconfig.json
└── README.md
```

---

## Migration Steps

### Step 1: Create Package Structure

```bash
mkdir -p packages/execution/src
cd packages/execution
npm init -y
```

### Step 2: Copy Source Files

```bash
cp -r apps/web/lib/execution/* packages/execution/src/
```

### Step 3: Update Import Paths

```typescript
// Before
import type { ExecutionGroup } from "./types";

// After
import type { ExecutionGroup } from "../types";
```

### Step 4: Update Package.json

```json
{
  "name": "@sanad/execution",
  "version": "1.0.0",
  "main": "src/index.ts",
  "types": "src/index.ts",
  "peerDependencies": {
    "react": ">=18.0.0"
  }
}
```

### Step 5: Update Consumer Imports

```typescript
// Before
import { calculateGroupProgress } from "@/lib/execution";

// After
import { calculateGroupProgress } from "@sanad/execution";
```

### Step 6: Update TypeScript Config

```json
{
  "compilerOptions": {
    "paths": {
      "@sanad/execution": ["./packages/execution/src"]
    }
  }
}
```

---

## Migration Checklist

- [ ] Package structure created
- [ ] Source files copied
- [ ] Import paths updated
- [ ] Package.json configured
- [ ] Consumer imports updated
- [ ] TypeScript config updated
- [ ] Tests passing
- [ ] Build successful
- [ ] Documentation updated

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Import path changes | Low | Use path aliases |
| React version compatibility | Low | Test with React 18+ |
| Build configuration | Medium | Use established patterns |
| Consumer adoption | Medium | Gradual migration |

---

## Rollback Plan

If migration fails:

1. Revert package.json changes
2. Revert import path changes
3. Revert TypeScript config changes
4. Notify team of failure
5. Investigate root cause
6. Fix and retry

---

## Timeline

| Phase | Duration | Status |
|-------|----------|--------|
| Package setup | 1 day | Ready |
| File migration | 1 day | Ready |
| Import updates | 2 days | Ready |
| Testing | 2 days | Ready |
| Documentation | 1 day | Ready |
| **Total** | **7 days** | **Ready** |

---

## Certification

✅ Public APIs verified
✅ Provider contracts verified
✅ Validator interfaces verified
✅ Calculator interfaces verified
✅ File structure documented
✅ Migration steps defined
✅ Checklist created
✅ Risk assessment complete
✅ Rollback plan defined
✅ Timeline established

**MIGRATION READINESS STATUS: READY**
