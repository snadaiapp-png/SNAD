# Stage 15 — Release Train Management

**Date**: 2026-07-08

---

## Release Cadence

### Weekly (Hotfixes)

```
Trigger: Critical or High severity bug
Process:
  1. Hotfix PR created
  2. CI required checks pass
  3. Vercel Preview verified
  4. PR merged to main
  5. Vercel auto-deploys to production
  6. Production smoke test
  7. Issue closed with resolution

Timeline: Same day (Critical), within 24h (High)
Owner: Project Owner
```

### Bi-Weekly (Minor Releases)

```
Cadence: Every 2 weeks (alternating Wednesdays)
Content:
  - Bug fixes (Medium/Low severity)
  - Small feature improvements
  - Translation key additions
  - Performance optimizations
  - Dependency updates

Process:
  1. Features/fixes merged to develop branch (if created) or direct to PR
  2. Release PR created (cherry-pick or merge from feature branches)
  3. CI checks pass
  4. Vercel Preview verified
  5. Release notes written
  6. PR merged to main
  7. Production deployment + smoke test
  8. Release tagged (e.g., v1.0.X)

Owner: Project Owner
```

### Monthly (Feature Releases)

```
Cadence: First Wednesday of each month
Content:
  - New features (non-breaking)
  - UI/UX improvements
  - New translation keys
  - Module enhancements (CRM, Control Plane)
  - Documentation updates

Process:
  1. Feature branch created
  2. Development + testing (1-2 weeks)
  3. PR created with release notes
  4. CI checks pass
  5. Vercel Preview verified
  6. Independent review (if available)
  7. PR merged to main
  8. Production deployment + smoke test
  9. Release tagged (e.g., v1.X.0)
  10. Customer notification

Owner: Project Owner
```

### Quarterly (Major Releases)

```
Cadence: Quarterly (Q1, Q2, Q3, Q4)
Content:
  - Breaking changes (with migration)
  - Major new modules (AI, ERP, HRM)
  - Architecture changes
  - Database migrations
  - SLA/SLO updates

Process:
  1. Release planning (1 month before)
  2. Feature freeze (1 week before)
  3. RC (Release Candidate) deployment to staging
  4. Full regression testing
  5. Migration scripts tested
  6. Rollback plan documented
  7. Production deployment (maintenance window)
  8. Post-deployment verification
  9. Release tagged (e.g., vX.0.0)
  10. Customer communication + training

Owner: Project Owner
```

## Release Classification

```
Patch (v1.0.X):
  - Bug fixes only
  - No new features
  - No breaking changes
  - Hotfix or bi-weekly

Minor (v1.X.0):
  - New features (non-breaking)
  - Bi-weekly or monthly
  - Release notes required

Major (vX.0.0):
  - Breaking changes
  - Major new functionality
  - Quarterly
  - Migration guide required
  - Customer communication required
```

## Release Gates (All Releases)

```
1. CI required checks pass (Build Next.js Web + provenance)
2. Secret scan pass
3. Security baseline pass
4. Vercel Preview READY
5. Production smoke test pass (all 6 routes)
6. Brand identity verified (SNAD + سند)
7. No open Critical issues
8. Rollback plan documented
9. Governing rule preserved (Gate 8F stays CLOSED)
```
