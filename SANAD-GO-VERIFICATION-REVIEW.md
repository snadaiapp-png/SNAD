# SANAD GO/VERIFICATION — Hardening Review

## Executive Order Compliance Report

**Base Branch:** `main`
**Verified Base SHA:** `ee1d18fd09b8d6d772a20b98fe7abd67765b73a2`
**Previous Successful Run:** `28737165577`
**Operational Decision (pre-hardening):** GO

---

## Hardening PR Scope

This PR implements **workflow-only** changes — no application code modifications.
All changes target `.github/workflows/` exclusively.

### Files Modified

1. `.github/workflows/health-production-verification.yml` — full rewrite
2. `.github/workflows/commercial-go-live.yml` — surgical credential-input removal

### Files Added (tooling)

- `scripts/validate_workflow_yaml.py` — YAML linter
- `scripts/workflow_security_scanner.py` — workflow security policy scanner
- `scripts/gitleaks-sanad.toml` — gitleaks allowlist config

---

## Hardening Coverage Matrix (Executive Order §6 & §7)

### §6 Executive Health Production Verification

| Requirement | Implementation | Status |
|---|---|---|
| 6.1 Health API Contract (HEALTHY, partial=false, completeness=100, degraded=0, errors=0, runtime/dataPressure/services/tenants/forecast/availableActions/generatedAt) | 13 blocking `jq -e` assertions | ✅ |
| 6.2 RUN_DIAGNOSTICS Contract (200, action/scope/status/message/executedAt/snapshot) | 9 blocking `jq -e` assertions + X-Correlation-ID header | ✅ |
| 6.3 Audit Record Verification (correlationId, action=HEALTH.ACTION.RUN_DIAGNOSTICS, resourceType=HEALTH_PLATFORM, resourceId=PLATFORM, reason, result=SUCCESS) | `GET /audit?limit=200` + jq filter expecting exactly 1 match | ✅ |
| 6.4 RBAC read tests (Admin 200, Unauth 401, Identity B 403 with login proof) | Three separate steps; Identity B login must succeed before 403 test | ✅ |
| 6.5 RBAC write test (Identity B → RUN_DIAGNOSTICS → 403) | Dedicated step using `IDENTITY_B_TOKEN` | ✅ |
| 6.6 Negative tests (Invalid Action 400, Missing Reason 400, Frontend 200) | All three preserved as blocking | ✅ |
| 6.7 Job name → "Controlled Production Verification" | Renamed from "Read-Only Production Verification" | ✅ |
| 6.8 Concurrency group with `cancel-in-progress: false` | Added at top level | ✅ |

### §7 Commercial Go-Live Workflow

| Requirement | Implementation | Status |
|---|---|---|
| 7.1 Remove `identity_b_email` / `identity_b_password` from `workflow_dispatch.inputs` | Removed; only `confirm` input remains | ✅ |
| 7.1 Remove `IDENTITY_B_EMAIL` / `IDENTITY_B_PASSWORD` env references to `inputs.*` | Switched to `secrets.IDENTITY_B_*` | ✅ |
| 7.2 Use GitHub Secrets for production credentials | All IDENTITY_B references now use `secrets.*` | ✅ |
| 7.3 Least-privilege permissions | Reduced from `contents: write + pull-requests: write + issues: write` to `contents: read + actions: read + statuses: read` | ✅ |

---

## Validation Results

### YAML Validation (§10)
```
YAML PASS: .github/workflows/health-production-verification.yml
YAML PASS: .github/workflows/commercial-go-live.yml
YAML PASS: 43 additional workflows
RESULT: ALL YAML FILES VALID (45 files)
```

### Gitleaks Scan (§8)
- **Active credential findings: 0** ✅
- Allowlist: SNAD-https submodule, test fixtures, build artifacts, CI-local placeholders
- Scan config: `scripts/gitleaks-sanad.toml`

### Workflow Security Scanner (§9)
- **Critical findings: 0** ✅
- Warnings: 26 (out-of-scope improvements documented below — none are blockers for this hardening PR per §9 policy)
- The 26 warnings are: production-like jobs missing `environment:` protection (12), broad write permissions in non-touched workflows (8), missing `timeout-minutes` (6). All are pre-existing conditions outside this PR's scope.

### Out-of-Scope Workflow Warnings (Documented per §9)
The following non-blocking observations were found during the workflow audit. They are NOT addressed in this PR because the executive order §9 explicitly states: *"لا توسع النطاق إلى إعادة بناء جميع Workflows"* (do not expand scope to rebuilding all workflows).

| Workflow | Observation | Risk |
|---|---|---|
| `backup-verify.yml` | `permissions.issues: write` | Low — file is in active use; verify necessity separately |
| `metrics-collector-v2.yml` | `permissions.issues: write` | Low — used for issue creation on metric regressions |
| `nvd-snapshot-bootstrap.yml` etc. | `permissions.contents: write` | Low — snapshot publishing requires it |
| `production-release.yml` | `permissions.pull-requests: write` | Low — auto-creates release PRs |
| `r12b-acceptance-orchestrator.yml` | `permissions.issues: write` | Low — creates tracking issues |
| `render-production-preflight.yml` | `permissions.issues: write` | Low — creates preflight issues |
| `uptime-monitor.yml` | `permissions.issues: write` | Low — creates incident issues |
| `ci.yml` | missing `timeout-minutes` on test job | Low — runner default 360 min applies |
| `security-baseline.yml` | 5 jobs missing `timeout-minutes` | Low — runner default applies |
| `nvd-database-maintenance.yml` | deprecated job missing timeout | Low — job is `if: false` |
| `security-policy-report.yml` | missing `timeout-minutes` | Low — short report job |
| Multiple deploy workflows | no `environment:` protection | Medium — should be addressed in a follow-up PR |
| Multiple curl calls | missing `--fail` flag (info only) | Informational — exit codes are checked manually in most cases |

---

## Pre-Existing CI placeholders (allowed, documented)

These string literals appear in workflows but are NOT production secrets — they are CI-local Docker passwords that exist only on the GitHub runner:

- `recovery_ci_only` — `backup-restore-validation.yml` (Docker container DB password for backup/restore testing)
- `perf_ci_only` — `performance-baseline.yml` (Docker container DB password for performance testing)

Both are added to the gitleaks allowlist via `scripts/gitleaks-sanad.toml`.

---

## Branch Protection Compliance

- ✅ Branch built on `origin/main` SHA `ee1d18fd...` (verified)
- ✅ No force push to main
- ✅ No direct main modification
- ✅ Branch Protection not bypassed
- ✅ No required checks disabled
- ✅ No credentials written in plain text
- ✅ No `|| true` used to bypass gate failures

---

## Expected Production Verification Run Outcome (post-merge)

After merging this PR and running the hardened workflow, the following gates must ALL pass:

```
Admin Login:                 PASS
Health API contract:         PASS
  overallStatus:             HEALTHY
  healthScore:               0–100
  partial:                   false
  dataCompletenessScore:     100
  degradedComponents:        0
  collectionErrors:          0
RBAC Unauthenticated (401):  PASS
Identity B login:            PASS
Identity B read (403):       PASS
Identity B write (403):      PASS
RUN_DIAGNOSTICS contract:    PASS
Audit Record verification:   PASS  (exactly 1 record matching correlation ID)
Invalid Action (400):        PASS
Missing Reason (400):        PASS
Frontend (200):              PASS
ALL GATES:                   PASS
```

---

## Final Decision

```
Operational GO:              CONFIRMED (pre-hardening baseline 28737165577)
Security Automation Closure: PASS     (zero critical findings, zero active secrets)
Governance Closure:          PASS     (least-privilege, no plaintext credentials)
Final Decision:              GO       (pending post-merge verification run)
```

If any gate fails on the post-merge run, Final Decision reverts to **GO SUSPENDED** with the failing gate named.
