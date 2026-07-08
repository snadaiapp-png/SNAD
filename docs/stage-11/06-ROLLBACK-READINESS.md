# Stage 11 — Rollback Readiness Record

**Date**: 2026-07-08
**Issue**: #370

---

## Last Stable SHA

```
SHA: ee4659436416dd9376ec774183325915636db5ce
Merge: PR #365 (governance(owner): establish sole owner approval authority)
Merged at: 2026-07-08T04:07:24Z
Status: Production LIVE, all smoke tests PASS
```

## Last Stable Deployment

```
Deployment ID: 5355266573
Environment: Production
State: success
Created: 2026-07-08T04:07:58Z
Creator: vercel[bot]
Production URL: https://snad-app.vercel.app/
```

## Previous Stable SHAs (Rollback Targets)

| SHA | Merge | Date | Notes |
|-----|-------|------|-------|
| ee46594 | PR #365 | 2026-07-08T04:07:24Z | Current stable (owner governance) |
| 2852e85 | PR #369 | 2026-07-08T03:42:57Z | TD-07-007 amendment waiver |
| 50590b0 | PR #368 | 2026-07-08T03:20:46Z | Approval register (superseded) |
| b29f952 | PR #366 | 2026-07-08T02:59:11Z | Approval register (1/5) |
| db4676e | PR #364 | 2026-07-08T02:49:56Z | Production smoke fix |
| 79cc60c | PR #359 | 2026-07-08T02:05:37Z | Gate 8F final closure |
| 3cb5304 | PR #358 | 2026-07-07T23:54:54Z | Bilingual UI + dynamic theme |

**Recommended rollback target**: `3cb5304` (last stable before governance docs)
or `db4676e` (last stable with production smoke fix).

---

## Rollback Triggers

Rollback is executed when:

1. **Critical issue cannot be fixed within 2 hours**
2. **Production data integrity at risk**
3. **Security breach detected**
4. **Owner approves rollback decision**

---

## Rollback Procedure

### Step 1: Decision

```
Decision maker: snadaiapp-png (Owner)
Decision recorded in: GitHub Issue (Critical/High severity)
Approval: Owner must comment "Rollback approved" on the Issue
```

### Step 2: Execute Revert

```bash
# Identify the merge commit to revert
git log --oneline origin/main

# Revert the merge commit
git revert -m 1 <merge-sha>

# Push to main (triggers Vercel auto-deploy)
git push origin main
```

### Step 3: Verify Rollback

```
1. Vercel auto-deploys the revert commit
2. Wait for deployment to reach READY state
3. Verify production URL returns HTTP 200
4. Verify brand identity (SNAD + سند)
5. Run production smoke test (all 6 routes)
6. Record new deployment ID and SHA
```

### Step 4: Document

```
Rollback Issue: <issue-number>
Reverted SHA: <reverted-sha>
Rollback commit SHA: <rollback-sha>
New production deployment ID: <new-deployment-id>
Rollback time: <timestamp>
Verification: PASS/FAIL
```

---

## Who Can Authorize Rollback

```
snadaiapp-png (Owner): YES — full authority
abdulrhmansenan1985-creator (Collaborator): NO — can recommend, owner must approve
```

---

## Rollback Documentation

Every rollback must be documented in:
1. The triggering Issue (with resolution comment)
2. The rollback commit message
3. docs/stage-11/06-ROLLBACK-READINESS.md (append to this file)

---

## Current Rollback Readiness

```
Rollback Readiness: READY
Last stable SHA documented: YES
Rollback procedure documented: YES
Authorization chain clear: YES
Vercel auto-deploy on revert: VERIFIED
```
