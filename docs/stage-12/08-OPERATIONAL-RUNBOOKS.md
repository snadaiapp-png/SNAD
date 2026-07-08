# Stage 12 — Operational Runbooks

**Date**: 2026-07-08

---

## 1. Production Runbook

### Purpose
Day-to-day production operations and health verification.

### Daily Operations

```
1. Health Check (Daily)
   - curl -sS -o /dev/null -w "%{http_code}" https://snad-app.vercel.app/
   - Expected: 200
   - If non-200: Open Issue (Critical severity)

2. Brand Identity Check (Daily)
   - curl -sS https://snad-app.vercel.app/ | grep -c "SNAD"
   - Expected: > 0
   - If 0: Open Issue (Critical severity)

3. Route Smoke Test (Weekly)
   - Test all 6 routes: /, /auth/forgot-password, /reset-password, /workspace, /control-plane, /crm
   - All should return HTTP 200
   - If any fails: Open Issue (severity based on route)

4. CI Health Check (Daily)
   - Review GitHub Actions for recent failures
   - If required check fails: Investigate immediately
```

### Production Deployment Process

```
1. PR created with clear scope
2. CI required checks pass (Build Next.js Web + provenance)
3. Vercel Preview deployment reaches READY
4. Preview smoke test passes
5. PR merged to main
6. Vercel auto-deploys to production
7. Verify production deployment READY
8. Run production smoke test (all 6 routes)
9. Document SHA and deployment ID
```

---

## 2. Incident Runbook

### Purpose
Response procedures for production incidents.

### Severity Classification

```
Critical: Production down, login failure, core route failure
High: User experience, security, or data impact
Medium: Limited issue, fixable without disruption
Low: Cosmetic or improvement
```

### Response Process

```
1. DETECT: Monitoring, user report, or CI failure
2. CLASSIFY: Assign severity (Critical/High/Medium/Low)
3. OPEN ISSUE: Create GitHub Issue with severity label
4. INVESTIGATE: Check Vercel, CI, recent commits
5. DECIDE: Fix (Hotfix PR) or Rollback
6. EXECUTE: Implement fix or rollback
7. VERIFY: Confirm production recovery
8. REVIEW: Post-incident review (Critical/High only)
9. CLOSE: Close Issue with resolution
```

### Critical Incident Timeline

```
T+0: Detection
T+1: Classification (Critical)
T+5: Issue opened
T+15: Investigation complete
T+30: Fix or rollback decision
T+45: Execution complete
T+50: Verification complete
T+60: Post-incident review started (within 24h)
```

---

## 3. Rollback Runbook

### Purpose
Procedure for reverting production to a previous stable state.

### Rollback Triggers

```
1. Critical issue cannot be fixed within 2 hours
2. Production data integrity at risk
3. Security breach detected
4. Owner approves rollback decision
```

### Rollback Procedure

```
1. IDENTIFY: Determine the merge commit to revert
   git log --oneline origin/main

2. DECIDE: Owner must approve rollback (comment on Issue)

3. REVERT: Create revert commit
   git revert -m 1 <merge-sha>
   git push origin main

4. WAIT: Vercel auto-deploys the revert (2-5 minutes)

5. VERIFY:
   - Production URL returns HTTP 200
   - Brand identity present (SNAD + سند)
   - HTML attributes correct (lang=ar, dir=rtl, data-theme=light)
   - All 6 routes return HTTP 200
   - Vercel deployment state = success

6. DOCUMENT:
   - Reverted SHA
   - Rollback commit SHA
   - New production deployment ID
   - Verification results
   - Timeline
```

### Rollback Authorization

```
snadaiapp-png (Owner): YES — full authority
abdulrhmansenan1985-creator (Collaborator): NO — recommend only
```

---

## 4. Secret Exposure Runbook

### Purpose
Response procedure for exposed credentials.

### Detection

```
1. Secret scan fails in CI
2. Token found in repository (current tree or history)
3. Token reported as exposed externally
```

### Response Process

```
1. DO NOT REPUBLISH the token value in any artifact
2. Open Issue with "security" label
3. Classify the exposure:
   - True positive: rotate/revoke/remove
   - False positive: document match/reason/allowlist
   - Historical non-active: document revocation/residual risk
4. Remove token from current tree (if present)
5. Request revocation by token owner
6. Verify secret scan passes after removal
7. Document residual risk and owner acceptance
8. Close Issue with classification and resolution
```

### Permanent Rules

```
- Any exposed token is permanently compromised
- Token value must NOT be republished
- Repository removal is necessary but NOT sufficient
- Token MUST be revoked by the owning account
- Git history rewrite requires separate governance approval
```

---

## 5. Deployment Verification Runbook

### Purpose
Verify that a production deployment is correct and stable.

### Verification Steps

```
1. CHECK SHA: Verify production deployment SHA matches merge commit
   curl -sS -H "Authorization: token $TOKEN" \
     "https://api.github.com/repos/snadaiapp-png/SNAD/deployments?sha=<merge-sha>"

2. CHECK STATE: Verify deployment state = success
   curl -sS -H "Authorization: token $TOKEN" \
     "https://api.github.com/repos/snadaiapp-png/SNAD/deployments/<id>/statuses"

3. CHECK HTTP: Verify production URL returns HTTP 200
   curl -sS -o /dev/null -w "%{http_code}" https://snad-app.vercel.app/

4. CHECK BRAND: Verify SNAD and سند are present
   curl -sS https://snad-app.vercel.app/ | grep -c "SNAD"
   curl -sS https://snad-app.vercel.app/ | grep -c "سند"

5. CHECK HTML: Verify lang, dir, data-theme attributes
   curl -sS https://snad-app.vercel.app/ | grep -oE '<html[^>]*>'

6. CHECK ROUTES: Verify all 6 routes return HTTP 200
   for route in / /auth/forgot-password /reset-password /workspace /control-plane /crm; do
     curl -sS -o /dev/null -w "$route: %{http_code}\n" "https://snad-app.vercel.app$route"
   done

7. CHECK FORBIDDEN: Verify SANAD (forbidden) is not present
   curl -sS https://snad-app.vercel.app/ | grep -c "SANAD"
   Expected: 0
```

### Verification Result

```
All checks pass: Deployment is VERIFIED
Any check fails: Open Issue and investigate
```
