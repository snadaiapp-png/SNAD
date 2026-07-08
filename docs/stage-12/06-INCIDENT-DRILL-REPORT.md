# Stage 12 — Incident Drill Report

**Date**: 2026-07-08
**Drill Type**: Tabletop exercise (simulated)

---

## Drill Scenario

**Scenario**: Production login page returns HTTP 500 error.

**Severity**: Critical (per Incident Triage Policy)

**Simulation**: The production URL `https://snad-app.vercel.app/` hypothetically
returns HTTP 500 instead of HTTP 200.

## Drill Execution

### Step 1: Detection

```
Detection method: Daily health check (curl production URL)
Detected by: Operator (or automated uptime monitor)
Detection time: T+0 (immediate upon health check)
```

### Step 2: Classification

```
Severity: Critical
Reason: Production login page failure (core route down)
Classification time: T+1 minute
```

### Step 3: Issue Creation

```
Action: Open GitHub Issue with "critical" label
Issue title: "CRITICAL: Production login page returns HTTP 500"
Issue content: Describe detection, current state, impact
Time: T+5 minutes
```

### Step 4: Investigation

```
Actions:
  1. Check Vercel deployment status (is latest deployment READY?)
  2. Check GitHub Actions (did Post-Merge Verification pass?)
  3. Check Vercel runtime logs (if accessible)
  4. Reproduce locally (does local build work?)
  5. Check recent commits (what changed?)
Time: T+15 minutes
```

### Step 5: Fix or Rollback Decision

```
Decision criteria (per Incident Triage Policy):
  - If fix is ready within 1 hour: Create Hotfix PR
  - If fix takes > 2 hours: Execute rollback
  - If production data at risk: Execute rollback immediately

For this drill:
  - Assumed root cause: Bad deployment (broken build)
  - Decision: ROLLBACK to previous stable SHA
  - Time: T+30 minutes
```

### Step 6: Rollback Execution

```
Actions:
  1. Identify previous stable SHA (e.g., 9dfdeba)
  2. git revert -m 1 <bad-merge-sha>
  3. git push origin main
  4. Wait for Vercel auto-deploy
  5. Verify production returns HTTP 200
  6. Verify brand identity (SNAD + سند)
  7. Run production smoke test (all 6 routes)
Time: T+45 minutes
```

### Step 7: Verification

```
Checks:
  ✅ Production URL returns HTTP 200
  ✅ Brand identity present (SNAD + سند)
  ✅ HTML attributes correct (lang=ar, dir=rtl, data-theme=light)
  ✅ All 6 routes return HTTP 200
  ✅ Vercel deployment state = success
Time: T+50 minutes
```

### Step 8: Post-Incident Review

```
Actions:
  1. Document root cause in Issue
  2. Document rollback decision and timeline
  3. Document preventive measures
  4. Close Issue with resolution
Time: T+60 minutes (within 24 hours per policy)
```

## Drill Results

```
Total response time: ~50 minutes (simulated)
Critical threshold: 2 hours (per policy)
Result: WITHIN threshold ✅

Process:
  - Detection: ✅ Clear
  - Classification: ✅ Correct (Critical)
  - Issue creation: ✅ Documented
  - Investigation: ✅ Systematic
  - Decision: ✅ Rollback chosen for speed
  - Execution: ✅ Procedure clear
  - Verification: ✅ All checks defined
  - Post-incident: ✅ Review documented
```

## Lessons Learned

1. **Rollback is faster than hotfix for deployment issues**: If the issue is
   a bad deployment, reverting is faster than debugging and fixing.

2. **Daily health checks are critical**: Without monitoring, detection could
   take hours. Automated uptime monitoring is recommended.

3. **Communication is key**: The Issue should be the single source of truth
   for incident status, decisions, and timeline.

4. **Rollback target must be pre-identified**: Knowing the last stable SHA
   saves time during an incident.

## Drill Conclusion

```
Incident drill: COMPLETED
Process: VALIDATED
Response time: WITHIN threshold
Improvements needed: Automated uptime monitoring (Stage 12 recommendation)
```
