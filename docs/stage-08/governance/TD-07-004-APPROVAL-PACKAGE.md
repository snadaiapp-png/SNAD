# TD-07-004 — Commercial Infrastructure Paid Production Plan: Approval Package

**Report ID:** `SANAD-TDR-ST07-004-APPROVAL-PACKAGE`
**Date:** 2026-07-07
**Status:** AWAITING DECISION FROM: Project Manager / Financial Owner

---

## 1. Current Free-Tier Risks

| Risk | Impact | Severity |
|------|--------|----------|
| Service sleeps after 15 min idle | Cold start 30-60s for first request | HIGH |
| 512 MB RAM limit | OOM under concurrent load | HIGH |
| 0.1 CPU | Slow response under any load | HIGH |
| Database 1 GB limit | Data growth will exhaust storage | MEDIUM |
| No high availability | Single instance, no failover | HIGH |
| No autoscaling | Cannot handle traffic spikes | HIGH |
| No SLA | No uptime guarantee | HIGH |
| No backup support | No managed backups | CRITICAL |

---

## 2. Recommended Paid Plan

### Option A: Render Standard (Recommended)
| Resource | Value |
|----------|-------|
| Plan | Render Standard |
| CPU | 1 vCPU |
| Memory | 2 GB RAM |
| Storage | 50 GB SSD |
| Price/month | $25 |
| Price/year | $300 |
| Sleep | No (always on) |
| Autoscaling | Manual (upgrade plan) |
| SLA | 99.9% uptime |
| Backup | Daily automated |

### Option B: Render Pro
| Resource | Value |
|----------|-------|
| Plan | Render Pro |
| CPU | 2 vCPU |
| Memory | 4 GB RAM |
| Storage | 100 GB SSD |
| Price/month | $75 |
| Price/year | $900 |
| Sleep | No |
| Autoscaling | Yes (up to 4 instances) |
| SLA | 99.95% uptime |
| Backup | Daily + on-demand |

### Database: Render PostgreSQL Standard
| Resource | Value |
|----------|-------|
| CPU | 1 vCPU |
| Memory | 1 GB RAM |
| Storage | 10 GB (scalable) |
| Price/month | $14 |
| Connections | 100 max |
| Backup | Daily automated |
| PITR | 7 days |

---

## 3. Cost Summary

| Component | Monthly | Annual |
|-----------|---------|--------|
| Render Standard (API) | $25 | $300 |
| Render PostgreSQL Standard | $14 | $168 |
| **Option A Total** | **$39/month** | **$468/year** |
| Render Pro (API) | $75 | $900 |
| Render PostgreSQL Standard | $14 | $168 |
| **Option B Total** | **$89/month** | **$1,068/year** |

---

## 4. Migration Plan

1. Provision new paid Render service
2. Configure environment variables (copy from Free Tier)
3. Deploy current main SHA to new service
4. Run smoke tests against new service
5. Update DNS/URL to point to new service
6. Monitor for 24 hours
7. Decommission Free Tier service

**Estimated migration time:** 2 hours
**Downtime:** Zero (if using blue-green deployment)

---

## 5. Rollback Plan

1. Revert DNS/URL to Free Tier service
2. Free Tier service remains operational for 7 days as fallback
3. No data loss (database is separate from compute)

---

## 6. Required Financial Approval

```text
AWAITING DECISION FROM:
  Project Manager / Financial Owner

REQUIRED DECISION:
  Approve selected production plan and budget

RECOMMENDED PLAN:
  Option A: Render Standard ($39/month, $468/year)

IMPACT IF NOT APPROVED:
  - Production remains on Free Tier
  - TD-07-004 remains OPEN — BLOCKING FINAL CLOSURE
  - Cold starts continue
  - No SLA guarantee
  - No managed backups
  - Cannot declare production verified
```

---

## 7. Cross-References

- TD-07-004 Issue: https://github.com/snadaiapp-png/SNAD/issues/295
- Stage 07 Debt Register: `docs/technical-debt/STAGE-07-DEFERRED-TECHNICAL-DEBT-REGISTER.md`
- Capacity Model: `docs/stage-08/architecture/CAPACITY-MODEL.md`
