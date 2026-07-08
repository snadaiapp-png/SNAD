# Stage 11 — Incident Triage & Escalation Policy

**Date**: 2026-07-08
**Issue**: #370

---

## Severity Classification

### Critical

**Definition**: Production down, login failure, or core route failure.

**Examples**:
- HTTP 5xx on `/` (login screen)
- Vercel deployment fails to reach READY state
- Brand identity missing (no SNAD or سند)
- Authentication backend unreachable
- Database connection failure

**Response**:
1. Open Issue immediately with `critical` label
2. Create Hotfix PR within 1 hour
3. Consider rollback if fix takes > 2 hours
4. Owner risk acceptance required if production stays degraded > 4 hours
5. Post-incident review within 24 hours

### High

**Definition**: Error affecting user experience, security, or data.

**Examples**:
- Theme switcher broken
- Language switcher not persisting
- Protected route redirect loop
- Console errors (non-blocking)
- Visual regression diff exceeds threshold

**Response**:
1. Open Issue with `high` label
2. Create Hotfix PR within 24 hours
3. No rollback required unless user impact is severe
4. Fix merged via standard PR process

### Medium

**Definition**: Limited issue, fixable without production disruption.

**Examples**:
- Minor UI glitch on specific browser
- Translation key missing for non-critical string
- Performance regression < 20%
- Accessibility issue on non-core element

**Response**:
1. Open Issue with `medium` label
2. Schedule fix in next sprint
3. No rollback required
4. Standard PR process

### Low

**Definition**: Cosmetic or improvement, non-disruptive.

**Examples**:
- Spelling error in translation
- Color slightly off from design token
- Minor spacing inconsistency
- Documentation typo

**Response**:
1. Open Issue with `low` label
2. Add to backlog
3. Fix when convenient
4. Standard PR process

---

## Escalation Matrix

| Severity | Initial Response | Fix Deadline | Rollback Threshold | Owner Notification |
|----------|-----------------|--------------|-------------------|-------------------|
| Critical | Immediate | 1 hour | 2 hours | Immediate |
| High | 4 hours | 24 hours | N/A | 24 hours |
| Medium | 1 business day | 1 sprint | N/A | Weekly review |
| Low | 1 sprint | Backlog | N/A | Monthly review |

---

## Issue Opening Criteria

Open an Issue when:
- Production smoke test fails
- Post-Merge Verification fails
- Vercel deployment fails
- User reports production issue
- Monitoring detects anomaly
- Security scan finds new finding

## Hotfix PR Criteria

Create a Hotfix PR when:
- Severity is Critical or High
- Fix is ready and tested locally
- CI checks pass on PR
- Rollback plan documented

## Rollback Criteria

Execute rollback when:
- Critical issue cannot be fixed within 2 hours
- Production data integrity at risk
- Security breach detected
- Owner approves rollback decision

Rollback procedure:
1. `git revert -m 1 <merge-sha>` → push to main
2. Vercel auto-deploys the revert
3. Verify production returns to previous stable state
4. Document rollback decision in Issue

## Owner Risk Acceptance

Owner risk acceptance required when:
- Production stays degraded > 4 hours (Critical)
- Security finding accepted without fix
- Rollback deferred by owner decision
- Known issue shipped to production

Documentation: Owner signs off in the Issue with explicit risk acceptance
language referencing SANAD-ST08-GOV-AMENDMENT-002.

---

## Incident Response Process

```
1. Detect (monitoring, user report, CI failure)
2. Classify (Critical / High / Medium / Low)
3. Open Issue with severity label
4. Assign responder
5. Investigate root cause
6. Implement fix or rollback
7. Verify production recovery
8. Post-incident review (Critical/High only)
9. Close Issue with resolution documentation
```
