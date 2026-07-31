# BRANCH-PROTECTION-AUDIT — RECOVERY-CRM-022 R3

| Field | Value |
|-------|-------|
| Workstream | R3 — Branch Protection Hardening (RECOVERY-CRM-022) |
| Date | 2026-07-31 |
| Repo | `snadaiapp-png/SNAD` |
| Branch | `main` |
| Method | GitHub REST API (`gh api .../branches/main/protection`) — token has `admin:true` |

---

## 1. Why this was needed

The CRM-022 closure gate (BLOCKED) found that the four workflows which were
RED on `main` (`Maven Test Suite`, `CRM Deployment Readiness`, `Post-Merge
Verification`, `CRM G1 Schema Isolation`) were **not** in the required-checks
set. Branch protection required only three contexts, so the red merges of
#825–#830 were permitted despite failing validators. This is governance gap
TD-CRM022-3 in the closure certificate.

## 2. Before (at the failed gate, `main` @ `61cf9a5b`)

```json
{
  "strict": true,
  "contexts": ["Build Next.js Web", "provenance", "CRM Integration Tests"]
}
```

| Setting | Value |
|---------|-------|
| Required status checks | 3 (above) |
| Strict (require branches up-to-date) | true |
| Required approving reviews | 0 |
| Enforce admins | false |
| Dismiss stale reviews | true |

Gap: the four RED workflows were not required, so they did not block merges.

## 3. After (applied 2026-07-31 via `PUT /branches/main/protection`)

```json
{
  "strict": true,
  "contexts": [
    "Build Next.js Web",
    "provenance",
    "CRM Integration Tests",
    "Maven Test Suite",
    "CRM Deployment Readiness",
    "Post-Merge Verification",
    "Verify 8 tables, 26 indexes, and tenant isolation"
  ]
}
```

| Setting | Value |
|---------|-------|
| Required status checks | **7** (3 original + 4 added) |
| Strict | true |
| Required approving reviews | 0 |
| Enforce admins | false |
| Dismiss stale reviews | true |

The four added contexts are the exact check-run (job) names as they appear
in GitHub, confirmed from each workflow's job names:
- `Maven Test Suite` (job of `CI` / `.github/workflows/ci.yml`)
- `CRM Deployment Readiness` (job of `crm-deployment-readiness.yml`)
- `Post-Merge Verification` (job of `post-merge-verification.yml`)
- `Verify 8 tables, 26 indexes, and tenant isolation` (job of the G1 Schema
  Isolation workflow)

## 4. Verification (evidence)

Post-change API read:
```
required_status_checks.contexts = [
  "Build Next.js Web","provenance","CRM Integration Tests",
  "Maven Test Suite","CRM Deployment Readiness",
  "Post-Merge Verification",
  "Verify 8 tables, 26 indexes, and tenant isolation"
]
count = 7, strict = true
```

Merge-block verification — the two open recovery PRs immediately after the
change:
- PR #831 (now closed): `mergeStateStatus: BLOCKED`, `mergeable: MERGEABLE`
- PR #832: `mergeStateStatus: BLOCKED`, `mergeable: MERGEABLE`

`BLOCKED` confirms that **no merge is possible while any of the 7 required
checks is RED or pending** — the hardening objective is met.

## 5. Residual considerations (not blockers for R3)

- `required_approving_review_count` remains 0 and `enforce_admins` is false.
  These were left at their existing values (the CRM-022 scope did not flag
  them, and changing review policy is a broader process decision). Flagging
  as optional future hardening, not part of R3's acceptance.
- The required-context names are tied to the workflow **job** names. If a
  workflow's job is renamed, the corresponding required context must be
  updated or it will never be satisfied. This is a maintenance caveat, not a
  defect.

## 6. Acceptance

- [x] Branch protection requires all 7 contexts (verified via API).
- [x] A RED required check blocks merge (verified via `mergeStateStatus: BLOCKED`
      on open PRs after the change).

R3 acceptance: **MET.**
