# Workflow Y2 Production Release & Strangler Cutover Runbook

> **Status:** authoritative operational runbook after Workflow Y2 V1 G4 closure.
> The implementation was independently reviewed and merged through PR #997.
> G4 merged baseline: `cf27d0e260691ce16b4ef884e408f8943977870c`.
> The exact production candidate is always the **current protected `main` head at dispatch time**.

This runbook separates two distinct operations:

1. **Platform production release** — deploy the exact immutable `main` image through `.github/workflows/production-release.yml`.
2. **Workflow Y2 functional cutover** — after platform verification, enable/repoint future starts to an approved PUBLISHED Y2 definition version and run a controlled canary.

A successful platform deployment does **not** automatically migrate or repoint existing Workflow families.

---

## A. Non-negotiable invariants

- PostgreSQL is the authoritative persisted Workflow state.
- Production release uses the repository's canonical `SANAD Production Release` workflow.
- Release input `commit_sha` must equal the current remote `main` head; the workflow rejects drift.
- The backend deploy is image-backed and immutable: `ghcr.io/<owner>/snad-backend:<exact-main-sha>`.
- Existing LEGACY instances remain LEGACY for their whole lifetime.
- Existing Y2 instances remain Y2 for their whole lifetime.
- No instance may execute through both engines.
- No automatic in-flight LEGACY → Y2 migration is permitted.
- Running instances stay pinned to their concrete definition version.
- Rollback of a Y2 cutover changes **future start resolution only**; it never rewrites running instances.
- Cross-tenant references and authorization failures remain fail-closed.

---

## B. Entry authority

Before any production action, all of the following must be true:

```text
G4_FINAL_CLOSURE             = COMPLETE
CURRENT_MAIN_SHA             = RESOLVED_FROM_GITHUB
MAIN_PROTECTION              = ACTIVE
PRODUCTION_RELEASE_WORKFLOW  = PRESENT
EXACT_SHA_IMAGE              = REQUIRED
ROLLBACK_ON_FAILURE          = TRUE (default)
PRODUCTION_ENVIRONMENT       = AUTHORIZED
```

The final G4 implementation evidence is:

- `docs/reports/workflow-y2-release-evidence.md`
- PR #997
- final tested head `928ed95c4eeb55baa3ac20e6dbc838e393b74073`
- G4 merge `cf27d0e260691ce16b4ef884e408f8943977870c`

Any post-G4 cleanup merged before production becomes part of the release candidate; therefore the production SHA is resolved only after all cleanup is merged.

---

## C. Phase 1 — Platform production release

Canonical workflow: `.github/workflows/production-release.yml` (`SANAD Production Release`).

Required dispatch inputs:

```text
commit_sha            = exact current main SHA
pull_request_number   = PR/change record used for production evidence
rollback_on_failure   = true
```

The workflow is responsible for proving, before/after deployment:

1. requested SHA is exactly current `main`;
2. exact SHA-tagged GHCR image exists;
3. required Render environment variables are present;
4. `BOOTSTRAP_ENABLED=false`;
5. Control Plane tenant exists in the production database;
6. previous live image is captured for rollback;
7. exact immutable image is deployed to Render;
8. Render reports the same requested image as live;
9. production readiness is `UP`;
10. runtime Flyway invariants are safe (`FLYWAY_ENABLED=true`, out-of-order disabled/unset);
11. production Flyway compatibility passes;
12. production auth/security contract passes;
13. SCP contract smoke passes;
14. Vercel Control Plane and BFF checks pass;
15. sanitized evidence artifact is uploaded;
16. on failure, the previous live image is redeployed when rollback is enabled.

### Platform release stop conditions

Stop and classify; do not blind-rerun if any of these occur:

- requested SHA is not current `main`;
- exact image is missing;
- Render environment proof is incomplete;
- production DB/Control Plane tenant proof fails;
- Flyway compatibility fails;
- deployed image does not equal requested immutable image;
- readiness does not reach `UP`;
- security/SCP/BFF verification fails;
- rollback fails or previous-image identity cannot be proven.

A failed release is an incident/root-cause exercise, not permission to deploy a different SHA.

---

## D. Phase 2 — Workflow database/read-only preflight

Run after Phase 1 is verified and before changing a family start target.

### D1. Flyway inventory

```sql
SELECT version, description, script, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 30;
```

Requirements:

- no failed Flyway row;
- expected Y2 migrations appear exactly once;
- no unresolved duplicate migration version;
- production schema is at or beyond the repository-required Workflow version.

### D2. Invalid/missing engine generation — must return zero

```sql
SELECT id, tenant_id, status
FROM workflow_instances
WHERE engine_generation IS NULL
   OR engine_generation NOT IN ('LEGACY', 'Y2');
```

### D3. Missing definition references — must return zero

```sql
SELECT i.id, i.tenant_id
FROM workflow_instances i
LEFT JOIN workflow_definitions d ON d.id = i.workflow_definition_id
WHERE d.id IS NULL;
```

### D4. Wrong engine/definition pairing — both must return zero

```sql
SELECT i.id, i.tenant_id, i.workflow_definition_id
FROM workflow_instances i
JOIN workflow_definitions d ON d.id = i.workflow_definition_id
WHERE i.engine_generation = 'Y2'
  AND d.engine_generation <> 'Y2';
```

```sql
SELECT i.id, i.tenant_id, i.workflow_definition_id
FROM workflow_instances i
JOIN workflow_definitions d ON d.id = i.workflow_definition_id
WHERE i.engine_generation = 'LEGACY'
  AND d.engine_generation = 'Y2';
```

### D5. Orphan step instances — must return zero

```sql
SELECT si.id, si.tenant_id
FROM workflow_step_instances si
LEFT JOIN workflow_instances i ON i.id = si.workflow_instance_id
WHERE i.id IS NULL;
```

### D6. LEGACY/Y2 state snapshots

```sql
SELECT engine_generation, status, count(*)
FROM workflow_instances
GROUP BY engine_generation, status
ORDER BY engine_generation, status;
```

### D7. Published definitions

```sql
SELECT definition_family_id,
       version,
       engine_generation,
       publication_state,
       status,
       published_at,
       definition_checksum
FROM workflow_definitions
WHERE publication_state = 'PUBLISHED'
ORDER BY definition_family_id, version DESC;
```

### D8. Open incidents

```sql
SELECT i.engine_generation,
       w.status AS incident_status,
       count(*)
FROM workflow_incidents w
JOIN workflow_instances i ON i.id = w.workflow_instance_id
WHERE w.status IN ('OPEN', 'ACKNOWLEDGED')
GROUP BY i.engine_generation, w.status;
```

Archive these outputs with timestamp before cutover.

---

## E. Phase 3 — Family-level Y2 cutover authorization

A platform release does not identify which business Workflow family should switch to Y2. For each target family, record before action:

```text
TENANT / SCOPE
DEFINITION_FAMILY_ID
CURRENT_START_TARGET
TARGET_Y2_VERSION_ID
TARGET_Y2_VERSION
VALIDATOR_RESULT = PASS
ROLLBACK_TARGET
CANARY_OWNER
OBSERVATION_WINDOW
ROLLBACK_THRESHOLDS
```

The target Y2 definition must be `PUBLISHED` and server validation must return valid.

No generic SQL should publish/activate a definition as a shortcut around the server-authoritative command path.

---

## F. Phase 4 — Controlled Y2 canary

1. Keep all existing running instances unchanged.
2. Repoint/retain the target family so new starts resolve to the approved PUBLISHED Y2 version.
3. Start **one controlled canary** through the normal authorized API path (`WORKFLOW.START`).
4. Verify persisted identity:
   - `engine_generation = 'Y2'`;
   - concrete `definition_version_id` equals the approved target;
   - version pin is immutable.
5. Verify initial graph execution and expected WorkItem/approval creation.
6. Verify tenant, actor, audit, correlation/causation and idempotency evidence.
7. Verify no LEGACY instance changed generation or definition pin.
8. Observe for the approved window before expanding new starts.

---

## G. Runtime health during canary/rollout

Monitor at minimum:

- scheduler ticks continue;
- scheduler failures remain flat;
- inbox/outbox lag stays bounded;
- stuck joins do not grow;
- action retry/failure rate does not regress;
- open incidents are explainable and handled;
- SLA breach rate does not spike due to Y2;
- no duplicate start or duplicate human decision appears;
- no cross-tenant leakage or unauthorized action appears;
- no wrong-engine routing appears.

---

## H. Immediate rollback triggers

Any one of these is sufficient to stop/rollback future Y2 starts for the affected family:

1. repeated HTTP 5xx on new Y2 starts;
2. duplicate instance creation for one idempotency key;
3. wrong-engine routing;
4. any confirmed cross-tenant violation;
5. recurring graph-resolution incidents on the same path;
6. stuck-join growth;
7. failed WorkItem creation for activated human steps;
8. approval corruption (duplicate/lost decisions);
9. missing audit continuity for executed transitions;
10. Workflow DB integrity violations;
11. severe scheduler regression;
12. inability to prove the deployed image or schema state.

Numeric paging thresholds must be explicitly recorded for the production change before broad rollout.

---

## I. Functional cutover rollback

Rollback changes only **future starts**.

Preferred method: retire the offending Y2 published version through the authorized definition lifecycle and restore a known-good published target.

If an operator-approved SQL recovery is required, it must be separately change-controlled. Example semantics only:

```sql
UPDATE workflow_definitions
SET publication_state = 'RETIRED', updated_at = NOW()
WHERE id = :offending_version_id;
```

Never during rollback:

- rewrite `workflow_instances.engine_generation`;
- rewrite running `definition_version_id`;
- delete running Y2 instances;
- delete/rewrite graph state or audit history;
- migrate a running instance to another engine.

---

## J. Post-cutover verification

```sql
SELECT engine_generation, status, count(*)
FROM workflow_instances
GROUP BY engine_generation, status;
```

Verify:

- the canary/new instances use Y2 only;
- old running LEGACY instances remain LEGACY;
- no unexpected count discontinuity;
- target definition/version is correct;
- incidents/audit/metrics are healthy;
- platform production release evidence and Workflow cutover evidence point to the same deployed main SHA.

---

## K. Legacy retirement remains future scope

Legacy runtime removal is **not** part of Y2 V1 production cutover. Retirement requires all of:

```text
ACTIVE_LEGACY_INSTANCES      = 0
PAUSED_LEGACY_INSTANCES      = 0
WAITING_LEGACY_INSTANCES     = 0
UNRESOLVED_LEGACY_INCIDENTS  = 0
AUDIT_VERIFICATION           = PASS
DATA_INTEGRITY               = PASS
ROLLBACK_REQUIREMENT         = CLOSED
```

Until then, the LEGACY runtime remains deployed and continues routing persisted LEGACY instances.

---

## L. Evidence package required for production closure

Archive together:

1. exact protected `main` SHA deployed;
2. immutable GHCR image reference;
3. Render deployment ID and previous image reference;
4. production-release workflow run ID/result;
5. readiness/Flyway/security/SCP/BFF results;
6. preflight SQL outputs D1–D8;
7. target definition family/version and validator result;
8. canary instance ID and engine/version proof;
9. transition/audit evidence for the canary;
10. monitoring snapshot during observation window;
11. rollback thresholds and final decision;
12. post-cutover SQL snapshot;
13. final production verdict: `PASS`, `ROLLED_BACK`, or `BLOCKED`.

No production `PASS` is valid unless the exact deployed SHA, database state, and canary/cutover evidence are all tied together.
