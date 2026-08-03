# SANAD Execution Framework — API Reference

**Version:** 1.0.0  
**Status:** FROZEN 🔒

---

## 1. Overview

This document defines the **frozen public API** of the SANAD Execution Framework. All exports are stable and versioned according to semantic versioning.

## 2. Import Path

```typescript
import { ... } from "@/lib/execution";
```

## 3. Types

### 3.1 Core Entities

```typescript
// Program — top-level execution container
type ExecutionProgram = {
  code: string;
  titleAr: string;
  titleEn: string;
  groups: ExecutionGroup[];
  milestones: ExecutionMilestone[];
};

// Group — execution group (G0, G1, G2, ...)
type ExecutionGroup = {
  code: string;
  titleAr: string;
  titleEn: string;
  purposeAr: string;
  purposeEn: string;
  status: GroupStatus;
  dependencies: string[];
  canParallelizeWith: string[];
  tasks: ExecutionTask[];
  stageReport: string | null;
};

// Task — atomic unit of work
type ExecutionTask = {
  id: string;
  number: string;
  nameAr: string;
  nameEn: string;
  groupCode: string;
  descriptionAr: string;
  descriptionEn: string;
  type: TaskType;
  priority: TaskPriority;
  status: TaskStatus;
  dependencies: string[];
  acceptanceCriteriaAr: string;
  implementationNotesAr: string;
};

// Evidence — proof of completion
type ExecutionEvidence = {
  taskId: string;
  type: EvidenceType;
  description: string;
  verified: boolean;
};

// Certification — group completion record
type Certification = {
  groupCode: string;
  certifiedAt: Date;
  certifiedBy: string;
  acceptanceCriteria: AcceptanceCriteria[];
};

// Acceptance Criteria
type AcceptanceCriteria = {
  criterion: string;
  met: boolean;
  evidence?: string;
};
```

### 3.2 Enums / Unions

```typescript
type GroupStatus = 
  | "NOT_STARTED" 
  | "IN_PROGRESS" 
  | "BLOCKED" 
  | "DONE" 
  | "NEEDS_REVIEW" 
  | "APPROVED" 
  | "REJECTED";

type TaskStatus = 
  | "NOT_STARTED" 
  | "IN_PROGRESS" 
  | "BLOCKED" 
  | "DONE" 
  | "NEEDS_REVIEW" 
  | "APPROVED";

type TaskType = 
  | "Backend" | "Frontend" | "Database" | "API" | "Security" 
  | "Test" | "Report" | "Mobile" | "AI" | "Billing";

type TaskPriority = "Critical" | "High" | "Medium" | "Low";

type CertificationStatus = "PENDING" | "CERTIFIED" | "EXPIRED";

type EvidenceType = 
  | "TEST_RESULT" 
  | "CODE_REVIEW" 
  | "DEPLOYMENT" 
  | "SIGN_OFF" 
  | "METRIC";
```

### 3.3 Progress

```typescript
type ExecutionProgress = {
  totalTasks: number;
  completedTasks: number;
  percentage: number;
  breakdown: Record<TaskStatus, number>;
};
```

## 4. Calculators

### 4.1 Progress Calculation

```typescript
// Calculate progress for a single group
function calculateGroupProgress(tasks: ExecutionTask[]): ExecutionProgress;

// Calculate progress for entire program
function calculateProgramProgress(groups: ExecutionGroup[]): ExecutionProgress;

// Get progress map for all groups
function calculateGroupProgressMap(groups: ExecutionGroup[]): Map<string, ExecutionProgress>;
```

### 4.2 Certification

```typescript
// Check if group is eligible for certification
function isEligibleForCertification(group: ExecutionGroup): boolean;

// Calculate certification status
function calculateCertificationStatus(
  group: ExecutionGroup, 
  certification?: Certification
): CertificationStatus;
```

### 4.3 Dependencies

```typescript
// Build dependency graph from groups
function buildDependencyGraph(groups: ExecutionGroup[]): Map<string, string[]>;

// Check if adding dependency would create cycle
function wouldCreateCycle(
  graph: Map<string, string[]>, 
  from: string, 
  to: string
): boolean;

// Topological sort of groups
function topologicalSort(groups: ExecutionGroup[]): string[];

// Get all dependents of a group
function getDependents(groups: ExecutionGroup[], code: string): string[];

// Get all dependencies of a group
function getAllDependencies(groups: ExecutionGroup[], code: string): string[];
```

### 4.4 Evidence Coverage

```typescript
// Get evidence count for a task
function getTaskEvidenceCount(task: ExecutionTask): number;

// Get evidence coverage for a group
function getGroupEvidenceCoverage(group: ExecutionGroup): number;

// Check if group has sufficient evidence
function hasSufficientEvidence(group: ExecutionGroup): boolean;
```

## 5. Validators

### 5.1 Integrity Validators

```typescript
// Validate progress integrity
function validateProgressIntegrity(group: ExecutionGroup): ValidationResult[];

// Validate certification integrity
function validateCertificationIntegrity(
  group: ExecutionGroup, 
  certification: Certification
): ValidationResult[];

// Validate evidence integrity
function validateEvidenceIntegrity(group: ExecutionGroup): ValidationResult[];

// Validate dependency integrity
function validateDependencyIntegrity(groups: ExecutionGroup[]): ValidationResult[];

// Validate task integrity
function validateTaskIntegrity(group: ExecutionGroup): ValidationResult[];

// Validate cross-layer consistency
function validateCrossLayerConsistency(group: ExecutionGroup): ValidationResult[];
```

### 5.2 Composite Validators

```typescript
// Run all validations for a group
function validateExecutionGroup(
  group: ExecutionGroup, 
  certification?: Certification
): ValidationResult[];

// Run all validations for a program
function validateExecutionProgram(
  program: ExecutionProgram, 
  certifications?: Map<string, Certification>
): ValidationResult[];

// Check if group is valid
function isGroupValid(
  group: ExecutionGroup, 
  certification?: Certification
): boolean;

// Check if program is valid
function isProgramValid(
  program: ExecutionProgram, 
  certifications?: Map<string, Certification>
): boolean;

// Get validation summary
function getValidationSummary(
  program: ExecutionProgram, 
  certifications?: Map<string, Certification>
): ValidationSummary;
```

## 6. Providers

```typescript
// Provider interface — implement this for your module
interface ExecutionProvider {
  getGroups(): ExecutionGroup[];
  getTasks(groupCode: string): ExecutionTask[];
  getCertification(groupCode: string): Certification | null;
  getProgress(groupCode: string): ExecutionProgress;
}

// In-memory implementation for testing
class InMemoryExecutionProvider implements ExecutionProvider {
  constructor(groups: ExecutionGroup[], tasks: ExecutionTask[]);
  // ... implements all methods
}
```

## 7. Hooks

```typescript
// React hooks for consuming execution data
function useGroupProgress(groupCode: string): ExecutionProgress;
function useProgramProgress(): ExecutionProgress;
function useGroupProgressMap(): Map<string, ExecutionProgress>;
function useGroupValidation(groupCode: string): ValidationResult[];
function useProgramValidation(): ValidationResult[];
function useExecutionProvider(): ExecutionProvider;
```

## 8. Constants

```typescript
// Status labels (Arabic/English)
const GROUP_STATUS_LABELS_AR: Record<GroupStatus, string>;
const GROUP_STATUS_LABELS_EN: Record<GroupStatus, string>;
const TASK_STATUS_LABELS_AR: Record<TaskStatus, string>;
const TASK_STATUS_LABELS_EN: Record<TaskStatus, string>;

// Type labels
const TASK_TYPE_LABELS_AR: Record<TaskType, string>;
const TASK_TYPE_LABELS_EN: Record<TaskType, string>;

// Priority labels
const PRIORITY_LABELS_AR: Record<TaskPriority, string>;
const PRIORITY_LABELS_EN: Record<TaskPriority, string>;

// Colors
const STATUS_COLORS: Record<GroupStatus, string>;

// Rules
const EXECUTION_RULES: {
  MAX_GROUP_DEPENDENCIES: number;
  REQUIRED_EVIDENCE_PER_TASK: number;
  CERTIFICATION_THRESHOLD: number;
};
```

## 9. API Stability Policy

| Change Type | Version Bump | Example |
|-------------|--------------|---------|
| New export | MINOR | Adding `useNewHook()` |
| Bug fix | PATCH | Fixing calculation error |
| Breaking change | MAJOR | Removing `oldFunction()` |

**Frozen since:** 2026-08-03  
**Next review:** 2026-09-03
