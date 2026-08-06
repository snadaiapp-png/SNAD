# FRAMEWORK API FREEZE — SANAD Execution Framework v1.0.0

**Status:** FROZEN
**Version:** 1.0.0
**Date:** 2026-08-03

---

## API Stability Classification

All exports from `@/lib/execution` are classified as:

| Classification | Description | Breaking Changes |
|----------------|-------------|------------------|
| **Stable** | Public API, guaranteed stable | Major version required |
| **Experimental** | May change, use with caution | Minor version required |
| **Internal** | Internal use only, may change | No version requirement |

---

## Stable Exports

These exports are **frozen** and will not change without a major version bump.

### Types (16)

| Export | Classification | Description |
|--------|----------------|-------------|
| `ExecutionProgram` | Stable | Top-level container for all execution groups |
| `ExecutionGroup` | Stable | Logical grouping of milestones and tasks |
| `ExecutionMilestone` | Stable | Checkpoint within a group |
| `ExecutionTask` | Stable | Unit of work that can be completed |
| `ExecutionEvidence` | Stable | Proof that work was completed |
| `AcceptanceCriteria` | Stable | Conditions for certification |
| `Certification` | Stable | Formal approval of a group or milestone |
| `ExecutionProgress` | Stable | Calculated progress metrics |
| `ExecutionDependency` | Stable | Dependency between entities |
| `ExecutionArtifact` | Stable | File or artifact produced by execution |
| `GroupStatus` | Stable | Status enum for groups |
| `TaskStatus` | Stable | Status enum for tasks |
| `TaskType` | Stable | Type of work |
| `TaskPriority` | Stable | Priority level |
| `CertificationStatus` | Stable | Certification status |
| `EvidenceType` | Stable | Type of evidence |

### Calculators (11)

| Export | Classification | Description |
|--------|----------------|-------------|
| `calculateGroupProgress` | Stable | Calculate progress from tasks |
| `calculateProgramProgress` | Stable | Aggregate progress across groups |
| `calculateGroupProgressMap` | Stable | Progress map for all groups |
| `calculateCertificationStatus` | Stable | Determine certification status |
| `isEligibleForCertification` | Stable | Check certification eligibility |
| `buildDependencyGraph` | Stable | Build dependency graph |
| `topologicalSort` | Stable | Sort groups by dependencies |
| `getDependents` | Stable | Get dependent groups |
| `getAllDependencies` | Stable | Get transitive dependencies |
| `getGroupEvidenceCoverage` | Stable | Calculate evidence coverage |
| `hasSufficientEvidence` | Stable | Check evidence sufficiency |

### Validators (11)

| Export | Classification | Description |
|--------|----------------|-------------|
| `validateProgressIntegrity` | Stable | Validate progress calculation |
| `validateCertificationIntegrity` | Stable | Validate certification rules |
| `validateEvidenceIntegrity` | Stable | Validate evidence coverage |
| `validateDependencyIntegrity` | Stable | Validate dependency graph |
| `validateTaskIntegrity` | Stable | Validate task structure |
| `validateCrossLayerConsistency` | Stable | Validate status consistency |
| `validateExecutionGroup` | Stable | Full group validation |
| `validateExecutionProgram` | Stable | Full program validation |
| `isGroupValid` | Stable | Quick validity check |
| `isProgramValid` | Stable | Quick validity check |
| `getValidationSummary` | Stable | Validation summary |

### Providers (2)

| Export | Classification | Description |
|--------|----------------|-------------|
| `ExecutionProvider` | Stable | Interface for module-specific data |
| `InMemoryExecutionProvider` | Stable | In-memory implementation for testing |

### Hooks (6)

| Export | Classification | Description |
|--------|----------------|-------------|
| `useGroupProgress` | Stable | Memoized group progress |
| `useProgramProgress` | Stable | Memoized program progress |
| `useGroupProgressMap` | Stable | Memoized progress map |
| `useGroupValidation` | Stable | Memoized group validation |
| `useProgramValidation` | Stable | Memoized program validation |
| `useExecutionProvider` | Stable | Provider access hook |

### Constants (11)

| Export | Classification | Description |
|--------|----------------|-------------|
| `GROUP_STATUS_LABELS_AR` | Stable | Arabic labels for group statuses |
| `GROUP_STATUS_LABELS_EN` | Stable | English labels for group statuses |
| `TASK_STATUS_LABELS_AR` | Stable | Arabic labels for task statuses |
| `TASK_STATUS_LABELS_EN` | Stable | English labels for task statuses |
| `TASK_TYPE_LABELS_AR` | Stable | Arabic labels for task types |
| `TASK_TYPE_LABELS_EN` | Stable | English labels for task types |
| `PRIORITY_LABELS_AR` | Stable | Arabic labels for priorities |
| `PRIORITY_LABELS_EN` | Stable | English labels for priorities |
| `STATUS_COLORS` | Stable | Color codes for group statuses |
| `EXECUTION_RULES` | Stable | Execution rules constants |

---

## Experimental Exports

These exports may change in future minor versions.

*None at this time.*

---

## Internal Exports

These exports are for internal use only and may change without notice.

| Export | Classification | Description |
|--------|----------------|-------------|
| `validateProgramConsistency` | Internal | Program consistency validation |

---

## Breaking Change Policy

### Major Version (x.0.0)

Required for:
- Removing any Stable export
- Changing function signatures
- Changing type definitions
- Changing return types
- Changing parameter types
- Adding required parameters to existing functions
- Changing error handling behavior

### Minor Version (0.x.0)

Allowed for:
- Adding new Stable exports
- Adding optional parameters
- Adding new types
- Adding new functions
- Adding new constants
- Bug fixes that don't change API

### Patch Version (0.0.x)

Allowed for:
- Bug fixes
- Documentation updates
- Internal refactoring
- Test improvements

---

## API Surface Summary

| Category | Count | Classification |
|----------|-------|----------------|
| Types | 16 | Stable |
| Calculators | 11 | Stable |
| Validators | 11 | Stable |
| Providers | 2 | Stable |
| Hooks | 6 | Stable |
| Constants | 11 | Stable |
| **Total Public** | **57** | **Stable** |
| Internal | 1 | Internal |

---

## Freeze Verification

✅ All 57 public exports documented
✅ All exports classified as Stable
✅ Breaking change policy defined
✅ Versioning policy adopted
✅ API surface frozen

**API FREEZE STATUS: ACTIVE**
