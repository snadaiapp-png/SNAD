# Workflow Y2 Strangler Cutover Runbook

> **Status:** operational runbook for the LEGACY → Y2 cutover of the
> Workflow Orchestration Platform. This runbook is exercised only through
> code paths shipped on `design/workflow-orchestration-spec` (PR #923).
> **No production cutover is authorized by this document alone** — operator
> approval and change-management gates apply.

---

## A. Scope and invariants

- **Z3** — backward-compatible evolution: legacy endpoints, legacy lifecycle
  (`DRAFT → ACTIVE → INACTIVE → ARCHIVED`), and wall-clock `slaHours`
  behavior remain until explicit retirement.
- **AA3** — strict strangler cutover: engine selection happens exactly once,
  at instance creation. `workflow_instances.engine_generation` is the
  immutable routing authority for every later command.
- **No dual execution**: one instance is advanced by exactly one engine for
  its whole life. The legacy advance command refuses Y2 instances; the Y2
  graph command refuses LEGACY instances.
- **No in-flight migration**: nothing converts a running LEGACY instance to
  Y2 (no SQL, no runtime promotion, no step-instance rewriting).

## B. Preconditions

1. All Flyway migrations deployed through the standard application bootstrap
   (PostgreSQL Direct); `flyway_schema_history` shows no failed rows.
2. `V20260830_1..V20260830_7` (this workstream) applied exactly once each.
3. A Y2 definition version is **PUBLISHED** for the target family and the
   publish validator returned `valid = true`
   (`POST /api/v1/workflows/definitions/{id}/validate`).
4. Operator-approved rollback thresholds recorded (see section K).
5. Break-glass operators hold `WORKFLOW.BREAK_GLASS`; cutover operators hold
   `WORKFLOW.MONITOR`.

## C. Current version inventory

Flyway terminal version and repeatable inventory:

```sql
SELECT version, description, script, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 20;
```

Verify exactly one row per `20260830.x` version of this workstream and zero
duplicate version numbers across all migration scripts (a duplicate signals
an unresolved divergence with another workstream — resolve before cutover).

## D. Preflight SQL (READ ONLY)

Invalid or missing engine generation (must return zero rows):

```sql
SELECT id, tenant_id, status
FROM workflow_instances
WHERE engine_generation IS NULL
   OR engine_generation NOT IN ('LEGACY', 'Y2');
```

Instances referencing missing definitions (must return zero rows):

```sql
SELECT i.id, i.tenant_id
FROM workflow_instances i
LEFT JOIN workflow_definitions d ON d.id = i.workflow_definition_id
WHERE d.id IS NULL;
```

Y2 instances not pinned to a Y2 definition (must return zero rows):

```sql
SELECT i.id, i.tenant_id, i.workflow_definition_id, d.engine_generation AS def_gen
FROM workflow_instances i
JOIN workflow_definitions d ON d.id = i.workflow_definition_id
WHERE i.engine_generation = 'Y2' AND d.engine_generation <> 'Y2';
```

Legacy instances executing a Y2 definition (invalid under the model —
must return zero rows):

```sql
SELECT i.id, i.tenant_id, i.workflow_definition_id
FROM workflow_instances i
JOIN workflow_definitions d ON d.id = i.workflow_definition_id
WHERE i.engine_generation = 'LEGACY' AND d.engine_generation = 'Y2';
```

Orphan step instances (must return zero rows):

```sql
SELECT si.id, si.tenant_id
FROM workflow_step_instances si
LEFT JOIN workflow_instances i ON i.id = si.workflow_instance_id
WHERE i.id IS NULL;
```

## E. LEGACY instance counts by state

```sql
SELECT status, count(*)
FROM workflow_instances
WHERE engine_generation = 'LEGACY'
GROUP BY status;
```

Retirement requires every non-terminal count (`RUNNING`, `PAUSED`) to reach
zero (section N).

## F. Y2 instance counts by state

```sql
SELECT status, count(*)
FROM workflow_instances
WHERE engine_generation = 'Y2'
GROUP BY status;
```

## G. Published definition/version inventory

```sql
SELECT definition_family_id, version, engine_generation, publication_state,
       status, published_at, definition_checksum
FROM workflow_definitions
WHERE publication_state = 'PUBLISHED'
ORDER BY definition_family_id, version DESC;
```

Current start target per family = highest `version` with
`publication_state = 'PUBLISHED'`.

## H. Open incidents by engine generation

```sql
SELECT i.engine_generation, w.status AS incident_status, count(*)
FROM workflow_incidents w
JOIN workflow_instances i ON i.id = w.workflow_instance_id
WHERE w.status IN ('OPEN', 'ACKNOWLEDGED')
GROUP BY i.engine_generation, w.status;
```

## I. Cutover sequence

1. **PRECHECK** — run sections C–H; every "must return zero rows" query
   returns zero.
2. Verify the target Y2 version is `PUBLISHED` and validator PASS (section B.3).
3. Record LEGACY running/paused counts (section E snapshot).
4. Record Y2 counts (section F snapshot).
5. **Enable/repoint NEW START target**: publish (or keep published) the Y2
   concrete version for the family. Publication state alone selects the
   start target — there is no separate global cutover flag.
6. Start one controlled canary instance through the normal authorized start
   path (`POST /api/v1/workflows/instances`, `WORKFLOW.START` capability).
7. Verify the canary:
   - `engine_generation = 'Y2'`, `workflow_version` = published version,
     `definition_version_id` = that concrete version;
   - first graph execution advanced the START step;
   - WorkItem/approval rows created for human steps;
   - OVERRIDE/audit rows only from explicitly authorized commands;
   - monitoring gauges (`workflow_queue_depth`, `workflow_open_incidents`)
     move without errors.
8. Observe for the operator-approved window, then continue rollout.

## J. Health verification

- `workflow_scheduler_ticks_total` increasing;
  `workflow_scheduler_tick_failures_total` flat.
- `workflow_inbox_lag_seconds` / `workflow_outbox_lag_seconds` bounded.
- `workflow_stuck_joins` = 0 growth.
- `workflow_action_failures` not trending up; incidents, when raised, get
  acknowledged/resolved through the incidents API.
- No `workflow_sla_breach_total` spike attributable to Y2 steps.

## K. Rollback triggers

Objective triggers (each alone justifies rollback of future starts):

1. New Y2 start failures (HTTP 5xx on start) repeating across tenants.
2. Duplicate instance creation for one start request (idempotency breach).
3. Any observed wrong-engine routing (Y2 instance advancing through the
   legacy command or vice versa).
4. Any cross-tenant violation demonstrated at runtime.
5. Persistent graph-resolution incidents (recurring
   `Graph resolution incident` on the same step).
6. Stuck-join growth without branch completion.
7. WorkItem creation failure for an activated human step.
8. Approval progression corruption (double decisions, lost decisions).
9. Audit discontinuity (missing START/ADVANCE rows for executed instances).
10. Database integrity violations from workflow writes.
11. SLA scheduler severe regression (tick failures across tenants).

Numeric paging thresholds: **operator-approved thresholds required before
production cutover** (no SLO for these signals exists yet in this repo).

## L. Rollback procedure

Rollback changes **only future start resolution**:

1. Repoint the family's start target: set the offending Y2 version to
   `RETIRED` (and, if a prior safe published version exists, it becomes the
   resolution automatically; otherwise publish a known-good version):

```sql
-- change-control approved only; not part of application runtime
UPDATE workflow_definitions
SET publication_state = 'RETIRED', updated_at = NOW()
WHERE id = :offending_version_id;
```

2. Stop-new-starts alternative: retire ALL Y2 versions of the family
   (LEGACY versions with `status='ACTIVE'` remain start targets).

**Rollback MUST NOT**: rewrite `engine_generation` on any instance, delete
Y2 instances, rewrite graph state, delete/rewrite audit rows, or change any
`definition_version_id` pin.

## M. Post-rollback verification

```sql
-- No instance changed generation (compare against the section E/F snapshot).
SELECT engine_generation, status, count(*)
FROM workflow_instances
GROUP BY engine_generation, status;

-- New starts now resolve to the rollback target.
SELECT id, version, publication_state
FROM workflow_definitions
WHERE definition_family_id = :family_id
ORDER BY version DESC;
```

- The offending version shows `RETIRED`; prior published version resolves.
- Running instances (LEGACY and Y2) keep their original pins and continue
  on their own engines.
- `workflow_scheduler_tick_failures_total` flat after the change.

## N. Legacy retirement conditions

Legacy runtime retirement is a **separate future project** and requires ALL
of:

```text
ACTIVE_LEGACY_INSTANCES      = 0
PAUSED_LEGACY_INSTANCES      = 0
WAITING_LEGACY_INSTANCES     = 0
UNRESOLVED_LEGACY_INCIDENTS  = 0
AUDIT_VERIFICATION           = PASS
DATA_INTEGRITY               = PASS
ROLLBACK_REQUIREMENT         = CLOSED
```

(Evidence queries: sections E and H plus a full audit-continuity check.)
Until then the legacy runtime stays deployed and routes every persisted
LEGACY instance.

## O. Audit/evidence capture

- Capture sections C–H outputs (with timestamps) before and after cutover.
- Export the canary instance's `workflow_transition_audit` rows.
- Archive the CI run evidence for the cutover build
  (`Maven Test Suite`, `PostgreSQL Acceptance Tests`, `CRM Integration
  Tests` all SUCCESS on the exact deployed commit).
- Store everything with the change-management record for the cutover.
