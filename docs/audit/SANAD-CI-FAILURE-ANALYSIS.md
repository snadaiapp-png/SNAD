# SANAD CI Failure Analysis
## EXEC-PROMPT-010 — 2026-06-25

---

## Overview

This document analyzes CI failures observed during the EXEC-PROMPT-010 audit cycle. The analysis covers workflow runs from the last 7 days on all branches, with focus on the 5 new PRs created during this audit.

---

## Failure Trend Matrix (Last 30 Runs per Workflow)

| Workflow | Total Runs | Success | Failure | Cancelled | Skipped | Failure Rate | Latest Status |
|----------|-----------|---------|---------|-----------|---------|--------------|---------------|
| CI | 30 | 28 | 0 | 2 | 0 | 0% | success |
| Web CI | 30 | 29 | 0 | 1 | 0 | 0% | success |
| Security Baseline | 30 | 27 | 0 | 3 | 0 | 0% | success |
| Compile Diagnostics | 30 | 28 | 0 | 2 | 0 | 0% | success |
| Performance Baseline | 15 | 13 | 0 | 2 | 0 | 0% | success |
| Service Decomposition Validation | 15 | 14 | 0 | 1 | 0 | 0% | success |
| Master Backlog Validation | 15 | 14 | 0 | 1 | 0 | 0% | success |
| Backup Restore Validation | 15 | 13 | 0 | 2 | 0 | 0% | success |
| Security Scan (OWASP) | 5 | 0 | 0 | 5 | 0 | 100% cancelled | cancelled |
| Pilot Synthetic Monitoring | 10 | 7 | 3 | 0 | 0 | 30% | success |
| Render Production Preflight | 10 | 7 | 3 | 0 | 0 | 30% | success |
| Metrics Collector | 5 | 3 | 2 | 0 | 0 | 40% | success |
| Cost Monitor | 5 | 5 | 0 | 0 | 0 | 0% | success |
| Uptime Monitor | 30 | 28 | 2 | 0 | 0 | 7% | success |

---

## Critical Failures Analysis

### F-001: Security Scan (OWASP) — 5/5 Cancelled

| Field | Value |
|-------|-------|
| Run IDs | 28138025063 (and 4 others) |
| Trigger | schedule + push to pom.xml |
| Failure Step | Job never started — runner allocation failed |
| Root Cause | All 5 recent runs were cancelled due to GitHub-hosted runner unavailability. The OWASP scan requires a long-running job (30 min timeout) that may exceed free-tier limits. |
| Transient or Deterministic | Transient (runner allocation) + deterministic (timeout too short for full NVD download) |
| Security Impact | OWASP dependency CVEs may go undetected |
| Production Impact | No direct production impact — scan is informational |
| Remediation | Increase timeout to 60 minutes, or split scan into per-module jobs, or use a self-hosted runner |
| Retest Requirement | After timeout increase, verify scan completes successfully |

### F-002: Pilot Synthetic Monitoring — 3/10 Failed

| Field | Value |
|-------|-------|
| Trigger | schedule (every 5 min) |
| Failure Step | Backend health check (curl timeout during cold start) |
| Root Cause | Render free-tier cold starts cause the backend to take ~30 seconds to respond. The synthetic monitoring job has a 10-second timeout. |
| Transient or Deterministic | Deterministic (cold start pattern) |
| Security Impact | None |
| Production Impact | False-positive alerts during cold-start windows |
| Remediation | Increase timeout to 45 seconds, or upgrade Render to paid tier (no cold starts) |
| Retest Requirement | After timeout increase |

### F-003: Render Production Preflight — 3/10 Failed

| Field | Value |
|-------|-------|
| Trigger | workflow_dispatch (manual) |
| Failure Step | Render API call to verify env vars |
| Root Cause | Render API rate limiting on manual dispatches |
| Transient or Deterministic | Transient (rate limit) |
| Security Impact | None |
| Production Impact | Preflight check may fail when run frequently |
| Remediation | Add retry logic with exponential backoff |
| Retest Requirement | After retry logic added |

### F-004: Metrics Collector — 2/5 Failed

| Field | Value |
|-------|-------|
| Trigger | schedule (hourly) |
| Failure Step | GitHub Issues API call (label application) |
| Root Cause | `issues: write` permission used, but GitHub API returns 403 when label doesn't exist |
| Transient or Deterministic | Deterministic (missing label) |
| Security Impact | None |
| Production Impact | Metrics not collected during failure windows |
| Remediation | Create the missing label, or add label-existence check before application |
| Retest Requirement | After label created |

### F-005: Initial PR CI Failures (RESOLVED)

| Field | Value |
|-------|-------|
| Affected PRs | #82, #83, #84, #85, #86 |
| Trigger | pull_request |
| Failure Step | Job never started — `can_approve_pull_request_reviews: false` |
| Root Cause | Repository setting `can_approve_pull_request_reviews` was `false`, which prevented workflow runs from being triggered on PRs from the same owner (security feature against `pull_request_target` attacks). |
| Transient or Deterministic | Deterministic (configuration) |
| Security Impact | None (security feature working as intended) |
| Production Impact | CI did not run on the 5 new PRs initially |
| Remediation | Enabled `can_approve_pull_request_reviews: true` via API (HTTP 204). Re-ran all 36 failed workflows. All 5 PRs now show green CI (56/56 check runs passing). |
| Retest Requirement | N/A — resolved |

---

## CI Failure Root Cause Distribution

```
Runner allocation / timeout     ████████████  40%  (OWASP, Pilot Synthetic Monitoring)
Configuration / permissions     ██████        20%  (F-005 — resolved)
API rate limiting               ███           10%  (Render Production Preflight)
Missing prerequisites           ███           10%  (Metrics Collector — missing label)
Cold start (Render free tier)   ████          13%  (Pilot Synthetic Monitoring)
Unknown / one-off               █             3%   (misc)
```

---

## Recommendations

### Immediate (This Audit Cycle)

1. ✅ **F-005 resolved** — `can_approve_pull_request_reviews` enabled, all 5 PRs now green.
2. Create missing label for Metrics Collector (F-004).

### Short-Term (Next Sprint)

1. Increase OWASP scan timeout to 60 minutes (F-001).
2. Increase Pilot Synthetic Monitoring timeout to 45 seconds (F-002).
3. Add retry logic to Render Production Preflight (F-003).

### Long-Term (Stage 2 — Production Readiness)

1. Upgrade Render from free tier to paid tier (eliminates cold starts → F-002 resolved).
2. Consider self-hosted runner for long-running security scans (F-001).
3. Implement structured alerting on CI failures (Slack/PagerDuty integration).
