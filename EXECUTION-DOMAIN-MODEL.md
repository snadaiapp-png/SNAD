# EXECUTION DOMAIN MODEL

**Date:** 2026-08-03
**Repository:** snadaiapp-png/SNAD
**Version:** 1.0.0

---

## 1. Overview

The SANAD Execution Domain Model defines the canonical entities for tracking execution progress across all modules.

---

## 2. Entity Relationship Diagram

```
┌─────────────────────┐
│  ExecutionProgram   │
│  ─────────────────  │
│  id: string         │
│  code: string       │
│  titleAr: string    │
│  titleEn: string    │
│  status: GroupStatus│
└─────────┬───────────┘
          │ 1:N
          ▼
┌─────────────────────┐
│  ExecutionGroup     │
│  ─────────────────  │
│  id: string         │
│  code: string       │
│  titleAr: string    │
│  titleEn: string    │
│  status: GroupStatus│
│  dependencies: []   │
└─────────┬───────────┘
          │ 1:N
          ▼
┌─────────────────────┐
│  ExecutionMilestone │
│  ─────────────────  │
│  id: string         │
│  code: string       │
│  titleAr: string    │
│  status: TaskStatus │
└─────────┬───────────┘
          │ 1:N
          ▼
┌─────────────────────┐
│  ExecutionTask      │
│  ─────────────────  │
│  id: string         │
│  number: string     │
│  nameAr: string     │
│  type: TaskType     │
│  priority: Priority │
│  status: TaskStatus │
│  evidence: []       │
└─────────┬───────────┘
          │ 1:N
          ▼
┌─────────────────────┐
│  ExecutionEvidence  │
│  ─────────────────  │
│  id: string         │
│  type: EvidenceType │
│  title: string      │
│  hash?: string      │
└─────────────────────┘
```

---

## 3. Core Entities

### 3.1 ExecutionProgram

Top-level container for all execution groups.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` | Unique identifier |
| `code` | `string` | Program code (e.g., "CRM", "ERP") |
| `titleAr` | `string` | Arabic title |
| `titleEn` | `string` | English title |
| `descriptionAr` | `string` | Arabic description |
| `descriptionEn` | `string` | English description |
| `status` | `GroupStatus` | Current status |
| `groups` | `ExecutionGroup[]` | All groups in this program |

### 3.2 ExecutionGroup

Logical grouping of milestones and tasks.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` | Unique identifier |
| `code` | `string` | Group code (e.g., "G0", "G1") |
| `titleAr` | `string` | Arabic title |
| `titleEn` | `string` | English title |
| `purposeAr` | `string` | Arabic purpose |
| `purposeEn` | `string` | English purpose |
| `status` | `GroupStatus` | Current status |
| `dependencies` | `string[]` | IDs of dependent groups |
| `canParallelizeWith` | `string[]` | IDs of parallelizable groups |
| `stageReport` | `string \| null` | Stage report content |
| `milestones` | `ExecutionMilestone[]` | Milestones in this group |
| `tasks` | `ExecutionTask[]` | Tasks in this group |

### 3.3 ExecutionMilestone

Checkpoint within a group.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` | Unique identifier |
| `code` | `string` | Milestone code (e.g., "M1") |
| `titleAr` | `string` | Arabic title |
| `titleEn` | `string` | English title |
| `description` | `string` | Description |
| `status` | `TaskStatus` | Current status |
| `taskDependencies` | `string[]` | Task IDs required |
| `acceptanceCriteria` | `AcceptanceCriteria[]` | Criteria to pass |

### 3.4 ExecutionTask

Unit of work that can be completed.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` | Unique identifier |
| `number` | `string` | Task number (e.g., "G0-01") |
| `nameAr` | `string` | Arabic name |
| `nameEn` | `string` | English name |
| `groupCode` | `string` | Parent group code |
| `descriptionAr` | `string` | Arabic description |
| `descriptionEn` | `string` | English description |
| `type` | `TaskType` | Work type |
| `priority` | `TaskPriority` | Priority level |
| `status` | `TaskStatus` | Current status |
| `dependencies` | `string[]` | Task IDs this depends on |
| `acceptanceCriteriaAr` | `string` | Arabic acceptance criteria |
| `implementationNotesAr` | `string` | Implementation notes |
| `evidence` | `ExecutionEvidence[]` | Supporting evidence |

### 3.5 ExecutionEvidence

Proof that work was completed.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` | Unique identifier |
| `type` | `EvidenceType` | Evidence type |
| `title` | `string` | Evidence title |
| `description` | `string` | Evidence description |
| `path` | `string?` | File path |
| `hash` | `string?` | SHA-256 hash |
| `createdAt` | `Date` | Creation timestamp |
| `createdBy` | `string` | Author |

### 3.6 AcceptanceCriteria

Conditions that must be met for certification.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` | Unique identifier |
| `descriptionAr` | `string` | Arabic description |
| `descriptionEn` | `string` | English description |
| `passed` | `boolean` | Whether criteria is met |
| `evidenceId` | `string?` | Supporting evidence |

### 3.7 Certification

Formal approval of a group or milestone.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` | Unique identifier |
| `entityId` | `string` | Entity being certified |
| `entityType` | `"PROGRAM" \| "GROUP" \| "MILESTONE"` | Entity type |
| `status` | `CertificationStatus` | Current status |
| `acceptanceCriteria` | `AcceptanceCriteria[]` | Criteria to pass |
| `certifiedAt` | `Date?` | Certification timestamp |
| `certifiedBy` | `string?` | Certifier |
| `notes` | `string?` | Notes |

### 3.8 ExecutionProgress

Calculated progress metrics.

| Field | Type | Description |
|-------|------|-------------|
| `total` | `number` | Total items |
| `done` | `number` | Completed items |
| `approved` | `number` | Approved items |
| `inProgress` | `number` | In-progress items |
| `blocked` | `number` | Blocked items |
| `notStarted` | `number` | Not-started items |
| `needsReview` | `number` | Needs-review items |
| `percentage` | `number` | Progress percentage (0-100) |

---

## 4. Status Enums

### 4.1 GroupStatus

```
NOT_STARTED → IN_PROGRESS → DONE → NEEDS_REVIEW → APPROVED
                    ↓
                 BLOCKED
                    ↓
                 REJECTED
```

### 4.2 TaskStatus

```
NOT_STARTED → IN_PROGRESS → DONE → NEEDS_REVIEW → APPROVED
                    ↓
                 BLOCKED
```

### 4.3 TaskType

`Backend | Frontend | Database | API | Security | Test | Report | Mobile | AI | Billing | Design | DevOps | Documentation`

### 4.4 TaskPriority

`Critical | High | Medium | Low`

### 4.5 CertificationStatus

```
NOT_CERTIFIED → PENDING_REVIEW → CERTIFIED
                      ↓
                   REJECTED
```

### 4.6 EvidenceType

```
SOURCE_CODE | DATABASE_MIGRATION | API_IMPLEMENTATION |
FRONTEND_IMPLEMENTATION | TEST | DOCUMENTATION |
CI_EVIDENCE | PRODUCTION_DEPLOYMENT | MANUAL_VERIFICATION
```

---

## 5. Calculated Fields

| Field | Calculation | Source |
|-------|-------------|--------|
| `ExecutionProgress.percentage` | `(done + approved) / total * 100` | Tasks |
| `ExecutionProgress.done` | `count(tasks where status = "DONE")` | Tasks |
| `ExecutionProgress.approved` | `count(tasks where status = "APPROVED")` | Tasks |
| `GroupStatus` | Derived from task statuses | Tasks |

---

## 6. Invariants

| Invariant | Description |
|-----------|-------------|
| I1 | Progress is always calculated from tasks |
| I2 | Progress = 100% implies all tasks are DONE or APPROVED |
| I3 | CERTIFIED implies progress = 100% |
| I4 | CERTIFIED implies all acceptance criteria pass |
| I5 | CERTIFIED implies at least one task exists |
| I6 | No duplicate task IDs within a group |
| I7 | All dependency references must exist |
| I8 | No circular dependencies |
