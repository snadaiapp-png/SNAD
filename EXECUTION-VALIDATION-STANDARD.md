# EXECUTION VALIDATION STANDARD

**Date:** 2026-08-03
**Repository:** snadaiapp-png/SNAD
**Version:** 1.0.0

---

## 1. Overview

The Execution Validation Standard defines the integrity rules that every SANAD module MUST satisfy. Violations MUST fail the build.

---

## 2. Mandatory Rules

### Rule 1: CERTIFIED Group Has Tasks

**Description:** A CERTIFIED group must contain at least one task.

**Rationale:** Certification without tasks is meaningless.

**Validation:**
```typescript
if (certification.status === "CERTIFIED") {
  assert(group.tasks.length > 0);
}
```

**Enforcement:** Build-time

---

### Rule 2: Progress From Tasks

**Description:** Progress must equal (done + approved) / total × 100.

**Rationale:** Progress is always calculated from tasks, never hardcoded.

**Validation:**
```typescript
const expected = Math.round(((done + approved) / total) * 100);
assert(progress.percentage === expected);
```

**Enforcement:** Build-time

---

### Rule 3: 100% Requires Completion

**Description:** Progress = 100% requires every task to be DONE or APPROVED.

**Rationale:** You cannot claim 100% completion with incomplete tasks.

**Validation:**
```typescript
if (progress.percentage === 100) {
  assert(group.tasks.every(t => t.status === "DONE" || t.status === "APPROVED"));
}
```

**Enforcement:** Build-time

---

### Rule 4: Certification Requires Criteria

**Description:** CERTIFIED requires all acceptance criteria to pass.

**Rationale:** Certification is evidence-based, not主观.

**Validation:**
```typescript
if (certification.status === "CERTIFIED") {
  assert(certification.acceptanceCriteria.every(c => c.passed));
}
```

**Enforcement:** Build-time

---

### Rule 5: Dashboard Matches API

**Description:** Dashboard values must exactly match API responses.

**Rationale:** No stale or inconsistent data in the UI.

**Validation:**
```typescript
const apiProgress = await api.getProgress(programId, groupCode);
const dashboardProgress = renderDashboard(group);
assert(apiProgress.percentage === dashboardProgress.percentage);
```

**Enforcement:** Runtime (E2E tests)

---

### Rule 6: API Matches Database

**Description:** API responses must exactly match database values.

**Rationale:** No caching or transformation errors.

**Validation:**
```typescript
const dbProgress = await db.query("SELECT ...");
const apiProgress = await api.getProgress(programId, groupCode);
assert(dbProgress === apiProgress);
```

**Enforcement:** Runtime (integration tests)

---

### Rule 7: No Duplicate State

**Description:** No duplicated execution state across layers.

**Rationale:** Single source of truth enforced.

**Validation:**
```typescript
// Progress is calculated, not stored
// Badge is derived from status, not independently set
// Color is derived from status, not independently set
assert(noDuplicateProgressState);
```

**Enforcement:** Architecture (code review)

---

## 3. Additional Rules

### Rule 8: No Self-Dependency

**Description:** A group cannot depend on itself.

**Validation:**
```typescript
assert(!group.dependencies.includes(group.code));
```

---

### Rule 9: No Circular Dependencies

**Description:** The dependency graph must be acyclic.

**Validation:**
```typescript
const sorted = topologicalSort(groups);
assert(sorted.length === groups.length);
```

---

### Rule 10: Unique Task IDs

**Description:** All task IDs within a group must be unique.

**Validation:**
```typescript
const ids = group.tasks.map(t => t.id);
assert(new Set(ids).size === ids.length);
```

---

### Rule 11: Acceptance Criteria Present

**Description:** Every task must have acceptance criteria.

**Validation:**
```typescript
assert(group.tasks.every(t => t.acceptanceCriteriaAr.length > 0));
```

---

### Rule 12: Evidence for Completed Tasks

**Description:** Every DONE/APPROVED task must have at least one evidence item.

**Validation:**
```typescript
const completed = group.tasks.filter(t => t.status === "DONE" || t.status === "APPROVED");
assert(completed.every(t => t.evidence.length > 0));
```

---

## 4. Validation Engine

The validation engine runs all rules and returns structured results:

```typescript
interface ValidationResult {
  rule: string;
  passed: boolean;
  message: string;
}

function validateExecutionGroup(group, certification?): ValidationResult[];
function validateExecutionProgram(program, certifications?): ValidationResult[];
```

---

## 5. Enforcement Points

| Point | Rules | Action |
|-------|-------|--------|
| `npm run build` | Rules 1-4, 8-12 | Fail build |
| `npm run validate:integrity` | All rules | Fail build |
| E2E tests | Rules 5-6 | Fail test |
| Code review | Rule 7 | Block merge |

---

## 6. CI Integration

```yaml
# .github/workflows/execution-integrity.yml
- name: Validate Execution Integrity
  run: |
    cd apps/web
    npm run validate:integrity
```

---

## 7. Reporting

Validation results are reported as:

```
=== EXECUTION INTEGRITY VALIDATION ===

✅ Rule 1: G1 CERTIFIED has tasks: G1 has 12 tasks
✅ Rule 2: G1 progress calculation: Expected 100%, got 100%
✅ Rule 3: G1 100% requires all DONE: All 12 tasks are DONE/APPROVED
❌ Rule 4: G2 CERTIFIED has criteria: Some tasks missing acceptance criteria

=== SUMMARY ===
Total rules: 23
Passed: 22
Failed: 1
```
