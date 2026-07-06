# SANAD Stage 08 — Operations and Support Model

**Document ID:** `SANAD-ST08-OPS-001`
**Stage:** 08
**Date:** 2026-07-06

---

## 1. On-Call Model

* Primary on-call (weekly rotation).
* Secondary on-call (escalation).
* PM escalation for SEV-1.
* On-call schedule published.

---

## 2. Escalation Matrix

| Severity | Response  | Escalation                       |
|----------|-----------|----------------------------------|
| SEV-1    | 5 min     | Primary → Secondary → PM         |
| SEV-2    | 30 min    | Primary → Secondary              |
| SEV-3    | 4 hours   | Primary                          |
| SEV-4    | 24 hours  | Ticket-based                     |

---

## 3. Runbooks

* Service degradation.
* Database failover.
* AI provider outage.
* Email provider outage.
* Webhook delivery failure.
* Marketplace revocation.
* Agent disablement.
* Backup restore.
* Region failover.

---

## 4. Incident Response Process

1. Detection (alert or report).
2. Triage (severity assignment).
3. Mitigation (restore service).
4. Communication (status page).
5. Resolution (root cause fixed).
6. Postmortem (within 5 business days).
7. Action items tracked to closure.

---

## 5. Capacity Review

* Weekly capacity dashboard review.
* Monthly capacity planning.
* Quarterly capacity model re-baseline.

---

## 6. Cost Review

* Weekly cost anomaly review.
* Monthly cost review with PM.
* Quarterly cost optimization review.

---

## 7. Change Management

* All production changes via PR.
* High-risk changes require PM approval.
* Rollback plan documented per change.
