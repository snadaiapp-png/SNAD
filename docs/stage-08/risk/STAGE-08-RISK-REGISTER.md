# SANAD Stage 08 — Risk Register

**Register ID:** `SANAD-ST08-RISK-001`
**Stage:** 08 — Scale, Growth & Global Expansion
**Date:** 2026-07-06
**Owner:** SANAD Project Manager

---

## 1. Purpose

This register records the principal risks for Stage 08 execution. Each risk has probability, impact, severity, owner, mitigation, contingency, trigger, status, and evidence.

---

## 2. Risk Inventory

| ID         | Risk                                          | Prob | Impact | Sev  | Owner              | Mitigation                                          | Contingency                                  | Trigger                                  | Status   |
|------------|-----------------------------------------------|------|--------|------|--------------------|-----------------------------------------------------|----------------------------------------------|------------------------------------------|----------|
| R-08-001   | Scaling cost growth                           | Med  | High   | High | Infra Owner        | Per-tenant cost budgets; alerts on breach           | Throttle non-essential services              | Monthly cost > 120% baseline             | Open     |
| R-08-002   | Data residency conflict                       | Med  | High   | High | Infra Owner        | Configuration-first country model; ADR-002          | Block tenant onboarding in conflicting region| Legal complaint or regulator inquiry     | Open     |
| R-08-003   | Marketplace supply-chain risk                 | Med  | High   | High | Security Owner     | Signed packages; security review; kill switch       | Emergency revocation                         | Security report of malicious package     | Open     |
| R-08-004   | Malicious partner applications                | Med  | High   | High | Security Owner     | Publisher KYC; security review; runtime sandbox     | Suspend publisher; revoke installs           | Abuse report or anomaly detection        | Open     |
| R-08-005   | AI hallucination                              | High | Med    | High | System Owner       | Grounded responses; citations; evaluation harness   | Disable agent; revert to L0 suggest-only     | Hallucination rate > threshold           | Open     |
| R-08-006   | Prompt injection                              | High | High   | Crit | Security Owner     | Input sanitization; output filtering; tool authz    | Disable agent; security incident             | Detection of injection attempt           | Open     |
| R-08-007   | AI cost overrun                               | Med  | High   | High | System Owner       | Per-tenant budgets; circuit breaker; model policy   | Throttle AI features; switch to cheaper model| Cost > 110% budget                       | Open     |
| R-08-008   | Cross-tenant agent leakage                    | Low  | Crit   | High | Security Owner     | Tenant-scoped memory; audit; isolation tests        | Disable agent; security incident             | Cross-tenant data detected in agent run  | Open     |
| R-08-009   | Industry-pack upgrade conflicts               | Med  | Med    | Med  | System Owner       | Versioned packs; compatibility matrix; rollback     | Rollback to previous version                 | Upgrade test failure                     | Open     |
| R-08-010   | Localization inconsistency                    | Med  | Med    | Med  | System Owner       | Locale regression tests; translation review         | Fall back to English; remediate in patch     | Missing translation detected             | Open     |
| R-08-011   | Tax-rule misconfiguration                     | Med  | High   | High | System Owner       | Configuration-first; no hardcoded rules; ADR-002    | Disable affected tax rule; manual calc       | Tax audit failure                        | Open     |
| R-08-012   | Partner fraud                                 | Low  | High   | Med  | PM                 | Due diligence; deal conflict check; audit           | Suspend partner; legal action                | Fraud report                             | Open     |
| R-08-013   | Revenue-share errors                          | Med  | Med    | Med  | PM                 | Automated settlement; reconciliation reports        | Manual correction; back-payment              | Settlement variance > threshold          | Open     |
| R-08-014   | API abuse                                     | Med  | Med    | Med  | System Owner       | Rate limits; quotas; abuse detection               | Suspend API key                              | Abuse pattern detected                   | Open     |
| R-08-015   | Webhook replay                                | Low  | Med    | Med  | System Owner       | Nonce store; timestamp window; HMAC signatures      | Rotate webhook secret; replay audit          | Replay attempt detected                  | Open     |
| R-08-016   | Enterprise privilege escalation               | Low  | Crit   | High | Security Owner     | SoD; recertification; admin monitoring              | Revoke access; security incident             | Escalation detected                      | Open     |
| R-08-017   | Technical debt accumulation                   | Med  | High   | High | PM                 | Sprint debt budget; mandatory DoD                   | Pause new features; debt-only sprint         | Debt-to-feature ratio > threshold        | Open     |
| R-08-018   | Premature global expansion                    | Med  | High   | High | PM                 | Country onboarding framework; staged rollout        | Retreat from market; revert config           | Market performance below threshold       | Open     |
| R-08-019   | Stage 07 closure debt delay                   | Med  | Crit   | Crit | PM                 | Parallel remediation; weekly debt review            | Block Stage 08 final gate                    | Debt closure slips > 2 sprints           | Open     |

---

## 3. Risk Review Cadence

* Weekly: Project Manager reviews open risks.
* Bi-weekly: Cross-owner risk review meeting.
* Per-sprint: Risk burn-down chart updated.
* Per-gate: Risk register reviewed before gate acceptance.

---

## 4. Escalation

```text
LOW severity:    Track in register
MEDIUM severity: Owner action within 1 sprint
HIGH severity:   Cross-owner action within 1 week; PM informed
CRITICAL severity: Immediate PM action; possible stage hold
```
