# SANAD Stage 08 — Master Backlog

**Backlog ID:** `SANAD-ST08-BL-001`
**Stage:** 08 — Scale, Growth & Global Expansion
**Date:** 2026-07-06
**Format:** Epic → Feature → User Story → Task → Acceptance Criteria → Estimate → Priority → Dependency → Owner → Sprint → DoD → Evidence
**Linked Milestone (GitHub):** `SANAD Stage 08 — Scale Phase`

---

## 1. Epics

| Epic ID      | Title                                | Track   | Priority |
|--------------|--------------------------------------|---------|----------|
| ST8-EPIC-01  | Scale Architecture                   | 8.1     | P0       |
| ST8-EPIC-02  | Global Expansion                     | 8.2     | P0       |
| ST8-EPIC-03  | Marketplace                          | 8.3     | P1       |
| ST8-EPIC-04  | Industry Packs                       | 8.4     | P1       |
| ST8-EPIC-05  | AI Agent Ecosystem                   | 8.5     | P0       |
| ST8-EPIC-06  | Enterprise Features                  | 8.6     | P1       |
| ST8-EPIC-07  | Partner Ecosystem                    | 8.7     | P2       |
| ST8-EPIC-08  | Developer Platform                   | 8.8     | P1       |
| ST8-EPIC-09  | Growth Platform                      | 8.9     | P1       |
| ST8-EPIC-10  | Data and Intelligence                | 8.10    | P1       |
| ST8-EPIC-11  | Reliability and Operations           | 8.1     | P0       |
| ST8-EPIC-12  | Stage 07 Technical Debt Closure      | TD      | P0       |

---

## 2. Backlog Items

> Convention: `ST8-<Epic>-<Feature>-<Story>` — estimates in story points (1pt = 1 ideal day).

### ST8-EPIC-01 — Scale Architecture

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-01-F1-S1         | Story  | Capacity baseline instrumentation                    | Metrics emitted for RPS, latency, error rate, queue depth      | 5   | P0  | -   | Infra Owner        | S1     |
| ST8-01-F1-S2         | Story  | Tenant quota enforcement at gateway                  | Quota breach returns 429 with Retry-After                      | 8   | P0  | S1  | Infra Owner        | S1     |
| ST8-01-F2-S1         | Story  | Horizontal autoscaling policy                        | Scale-out on CPU > 70% sustained 5m; scale-in on idle 10m      | 5   | P0  | S1  | Infra Owner        | S1     |
| ST8-01-F3-S1         | Story  | DB connection pool governance                        | Max pool per service bounded; statement timeout enforced       | 3   | P0  | S1  | Infra Owner        | S1     |
| ST8-01-F4-S1         | Story  | Per-tenant rate limit (RPM/RPD)                      | Limits configurable per plan; burst allowance leaky-bucket     | 5   | P0  | S2  | Infra Owner        | S1     |
| ST8-01-F5-S1         | Story  | Circuit breakers per downstream                      | Open on 5 errors/30s; half-open after 30s; alert on open       | 5   | P0  | S2  | Infra Owner        | S1     |
| ST8-01-F6-S1         | Story  | Backpressure on AI agent queue                       | Reject new tasks at 80% depth; emitter retries with backoff    | 5   | P0  | S2  | Infra Owner        | S2     |
| ST8-01-F7-S1         | Story  | Graceful degradation mode                            | Non-critical surfaces return read-only banner under load        | 5   | P1  | S5  | Infra Owner        | S2     |
| ST8-01-F8-S1         | Story  | Load-shedding policy                                 | Low-priority requests shed first; audit records shed count      | 3   | P1  | S5  | Infra Owner        | S2     |

### ST8-EPIC-02 — Global Expansion

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-02-F1-S1         | Story  | Locale model + Arabic/English baseline               | All UI strings localized; RTL/LTR switching verified           | 8   | P0  | -   | System Owner       | S2     |
| ST8-02-F2-S1         | Story  | Multi-currency model                                 | Currencies configurable per tenant; FX rate source documented  | 5   | P0  | S1  | System Owner       | S2     |
| ST8-02-F3-S1         | Story  | Time-zone support                                    | All timestamps stored UTC; display in tenant locale            | 3   | P0  | S1  | System Owner       | S2     |
| ST8-02-F4-S1         | Story  | Country profile model                                | Country entity with locale, currency, tax regime, residency    | 5   | P0  | S2  | System Owner       | S2     |
| ST8-02-F5-S1         | Story  | Localization framework (formatting)                  | Numbers, dates, addresses locale-aware; unit tests cover 10 locales | 5 | P0 | S1 | System Owner       | S2     |
| ST8-02-F6-S1         | Story  | Data-residency zone routing                          | Tenant data persisted only in configured zone                  | 8   | P0  | S4  | Infra Owner        | S3     |

### ST8-EPIC-03 — Marketplace

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-03-F1-S1         | Story  | Publisher onboarding + identity verification          | Publisher KYC record; signed agreement; status workflow        | 8   | P1  | -   | System Owner       | S3     |
| ST8-03-F2-S1         | Story  | Product submission + version management              | Versioned product manifest; semantic versioning enforced       | 5   | P1  | S1  | System Owner       | S3     |
| ST8-03-F3-S1         | Story  | Security review + technical certification            | SAST + dependency scan + manifest validation; signed packages  | 8   | P0  | S2  | Security Owner     | S3     |
| ST8-03-F4-S1         | Story  | Listing publication + trial support                  | Listing visible to tenants; trial entitlement enforced         | 5   | P1  | S3  | System Owner       | S3     |
| ST8-03-F5-S1         | Story  | Tenant installation + entitlements                   | Install creates entitlement record; license enforced at runtime | 8   | P0  | S4  | System Owner       | S3     |
| ST8-03-F6-S1         | Story  | Update + rollback                                    | Update creates new version; rollback restores previous         | 5   | P0  | S5  | System Owner       | S4     |
| ST8-03-F7-S1         | Story  | Usage metering + revenue sharing                     | Per-install usage metered; revenue share computed monthly      | 8   | P1  | S6  | PM                 | S4     |
| ST8-03-F8-S1         | Story  | Review moderation + abuse reporting                  | Reviews moderated; abuse reports trigger suspension workflow   | 5   | P1  | S7  | System Owner       | S4     |
| ST8-03-F9-S1         | Story  | Emergency revocation + kill switch                   | Revocation propagates to all installs within 60s               | 8   | P0  | S8  | Security Owner     | S4     |

### ST8-EPIC-04 — Industry Packs

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-04-F1-S1         | Story  | Industry metadata schema                             | Schema supports roles, workflows, KPIs, AI skills, seed data  | 8   | P1  | -   | System Owner       | S4     |
| ST8-04-F2-S1         | Story  | Install/upgrade lifecycle                            | Install/upgrade reversible; audit trail complete              | 8   | P1  | S1  | System Owner       | S4     |
| ST8-04-F3-S1         | Story  | Reference pack: Retail                               | Retail pack with seed data, demo data, migration tools         | 8   | P2  | S2  | System Owner       | S5     |
| ST8-04-F4-S1         | Story  | Reference pack: Professional Services                | PS pack with project templates, time tracking, billing         | 8   | P2  | S2  | System Owner       | S5     |
| ST8-04-F5-S1         | Story  | Reference pack: Contracting                          | Contracting pack with progress claims, retention               | 5   | P2  | S2  | System Owner       | S5     |

### ST8-EPIC-05 — AI Agent Ecosystem

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-05-F1-S1         | Story  | Agent registry + version                             | Agent definitions versioned; rollback supported                | 8   | P0  | -   | System Owner       | S5     |
| ST8-05-F2-S1         | Story  | Skills, tools, permissions                           | Tool-level authorization; least privilege default             | 8   | P0  | S1  | System Owner       | S5     |
| ST8-05-F3-S1         | Story  | Execution records + audit                            | Every execution logged with tenant, user, agent, cost, latency | 8   | P0  | S2  | System Owner       | S5     |
| ST8-05-F4-S1         | Story  | Human approval workflow (L2)                         | L2 actions require approval; approval evidence recorded        | 5   | P0  | S3  | System Owner       | S5     |
| ST8-05-F5-S1         | Story  | Evaluation harness                                   | Regression tests per agent; hallucination metric tracked       | 8   | P0  | S4  | System Owner       | S6     |
| ST8-05-F6-S1         | Story  | Cost budgets + rate limits                           | Per-tenant AI token budget; rate limit enforced                | 5   | P0  | S5  | System Owner       | S6     |
| ST8-05-F7-S1         | Story  | Emergency agent disablement                          | Disablement propagates within 30s; in-flight executions drained| 5   | P0  | S6  | Security Owner     | S6     |

### ST8-EPIC-06 — Enterprise Features

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-06-F1-S1         | Story  | Enterprise hierarchy + multi legal entity            | Hierarchy supports groups, subsidiaries, shared-service centers | 8   | P1  | -   | Infra Owner        | S6     |
| ST8-06-F2-S1         | Story  | Delegated administration                             | Admin delegation scoped; revocation supported                  | 5   | P1  | S1  | Infra Owner        | S6     |
| ST8-06-F3-S1         | Story  | SSO (SAML, OIDC)                                     | SAML and OIDC providers supported; session policy enforced     | 8   | P1  | S2  | Infra Owner        | S6     |
| ST8-06-F4-S1         | Story  | SCIM user provisioning                               | SCIM 2.0 endpoints; user lifecycle automated                  | 5   | P2  | S3  | Infra Owner        | S7     |
| ST8-06-F5-S1         | Story  | Segregation of duties                                | SoD matrix enforced; conflicting roles blocked                | 5   | P1  | S4  | Security Owner     | S7     |
| ST8-06-F6-S1         | Story  | Privileged access review + recertification           | Quarterly recertification; access review dashboard             | 5   | P1  | S5  | Security Owner     | S7     |

### ST8-EPIC-07 — Partner Ecosystem

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-07-F1-S1         | Story  | Partner registration + due diligence                 | Partner KYC; agreement signed; tier assigned                   | 5   | P2  | -   | PM                 | S7     |
| ST8-07-F2-S1         | Story  | Deal registration                                    | Deal records with conflict check; commission computed          | 5   | P2  | S1  | PM                 | S7     |
| ST8-07-F3-S1         | Story  | Partner portal + analytics                           | Portal shows pipeline, commissions, performance scorecard      | 8   | P2  | S2  | PM                 | S7     |
| ST8-07-F4-S1         | Story  | Partner certification + training                     | Certification tracks; training materials published              | 5   | P2  | S3  | PM                 | S8     |

### ST8-EPIC-08 — Developer Platform

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-08-F1-S1         | Story  | Developer portal + OpenAPI docs                      | All public APIs documented; interactive docs available         | 5   | P1  | -   | System Owner       | S7     |
| ST8-08-F2-S1         | Story  | API keys + OAuth clients                             | Keys rotation; scopes enforced; revocation supported           | 8   | P1  | S1  | System Owner       | S7     |
| ST8-08-F3-S1         | Story  | Webhooks (signed, replay-protected)                  | HMAC signatures; nonce store; dead-letter queue                | 8   | P0  | S2  | System Owner       | S7     |
| ST8-08-F4-S1         | Story  | Sandbox tenants + test credentials                   | Sandboxes isolated; auto-expiry; reset on demand               | 5   | P1  | S3  | System Owner       | S7     |
| ST8-08-F5-S1         | Story  | Rate limits + quotas + analytics                     | Per-key limits; usage analytics dashboard                       | 5   | P1  | S4  | System Owner       | S8     |
| ST8-08-F6-S1         | Story  | Versioning + deprecation policy                      | Versioning scheme documented; sunset timeline enforced         | 3   | P1  | S5  | System Owner       | S8     |

### ST8-EPIC-09 — Growth Platform

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-09-F1-S1         | Story  | Usage metering + metered billing                     | Meter events captured; billing computed from meter             | 8   | P1  | -   | PM                 | S8     |
| ST8-09-F2-S1         | Story  | Trial lifecycle + conversion                         | Trial start/end tracked; conversion funnel reported            | 5   | P1  | S1  | PM                 | S8     |
| ST8-09-F3-S1         | Story  | Customer health score                                | Health score composite metric; playbook triggers per score     | 5   | P1  | S2  | PM                 | S8     |
| ST8-09-F4-S1         | Story  | Pricing experiments + promotion governance           | Experiments tracked; promotions require approval               | 5   | P2  | S3  | PM                 | S9     |

### ST8-EPIC-10 — Data and Intelligence

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-10-F1-S1         | Story  | Tenant-isolated analytics warehouse                  | Per-tenant schemas; cross-tenant queries blocked               | 8   | P1  | -   | System Owner       | S8     |
| ST8-10-F2-S1         | Story  | Metric catalog + semantic layer                      | Catalog published; metric ownership assigned                   | 5   | P1  | S1  | System Owner       | S8     |
| ST8-10-F3-S1         | Story  | Executive dashboards                                 | Dashboards for MRR, churn, adoption, AI cost, capacity          | 5   | P1  | S2  | System Owner       | S9     |
| ST8-10-F4-S1         | Story  | Data lineage + retention                             | Lineage tracked; retention policies enforced                   | 5   | P1  | S3  | System Owner       | S9     |

### ST8-EPIC-11 — Reliability and Operations

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-11-F1-S1         | Story  | Production dashboards                                | Dashboards per domain online; alerts routed                    | 5   | P0  | -   | Infra Owner        | S1     |
| ST8-11-F2-S1         | Story  | On-call + escalation matrix                          | On-call schedule published; escalation tested                  | 3   | P0  | S1  | Infra Owner        | S1     |
| ST8-11-F3-S1         | Story  | Incident response runbooks                           | Runbooks per domain; reviewed quarterly                        | 5   | P0  | S2  | Infra Owner        | S2     |
| ST8-11-F4-S1         | Story  | Synthetic uptime monitoring                          | Synthetic probes per region; alert on breach                   | 3   | P0  | S3  | Infra Owner        | S2     |

### ST8-EPIC-12 — Stage 07 Technical Debt Closure

| ID                   | Type   | Title                                                | AC Summary                                                     | Est | Pri | Dep | Owner              | Sprint |
|----------------------|--------|------------------------------------------------------|----------------------------------------------------------------|-----|-----|-----|--------------------|--------|
| ST8-12-F1-S1         | Story  | TD-07-001 OWASP final assessment                     | All evidence attached; Critical/High closed                     | 13  | P0  | -   | Security Owner     | S0–S9  |
| ST8-12-F2-S1         | Story  | TD-07-002 Production backup/restore                  | Restore into isolated env; RPO/RTO approved                     | 8   | P0  | -   | Infra Owner        | S0–S9  |
| ST8-12-F3-S1         | Story  | TD-07-003 Monitoring + alerting + IR                 | Dashboards, alerts, runbooks live                              | 8   | P0  | -   | Infra Owner        | S0–S9  |
| ST8-12-F4-S1         | Story  | TD-07-004 Commercial paid plan                      | Free Tier removed from prod path; financial approval           | 8   | P0  | -   | Infra Owner + PM   | S0–S9  |
| ST8-12-F5-S1         | Story  | TD-07-005 Fail-closed workflow completion            | Workflow end-to-end PASS with NO-GO and GO paths               | 8   | P0  | -   | QA & Release Owner | S0–S9  |
| ST8-12-F6-S1         | Story  | TD-07-006 Email evidence hardening                  | Governance rejects non-delivered; recovery E2E PASS             | 5   | P0  | -   | QA & Release Owner | S0–S9  |
| ST8-12-F7-S1         | Story  | TD-07-007 Independent human approvals                | 5 distinct accounts recorded                                   | 5   | P0  | -   | PM                 | S0–S9  |
| ST8-12-F8-S1         | Story  | TD-07-008 Issue evidence reconciliation              | All 5 issues reconciled; reopened if needed                    | 5   | P0  | -   | PM                 | S0–S9  |

---

## 3. Total Estimates

| Epic              | Stories | Story Points |
|-------------------|---------|--------------|
| ST8-EPIC-01       | 9       | 49           |
| ST8-EPIC-02       | 6       | 34           |
| ST8-EPIC-03       | 9       | 60           |
| ST8-EPIC-04       | 5       | 37           |
| ST8-EPIC-05       | 7       | 47           |
| ST8-EPIC-06       | 6       | 36           |
| ST8-EPIC-07       | 4       | 23           |
| ST8-EPIC-08       | 6       | 34           |
| ST8-EPIC-09       | 4       | 23           |
| ST8-EPIC-10       | 4       | 23           |
| ST8-EPIC-11       | 4       | 16           |
| ST8-EPIC-12       | 8       | 60           |
| **TOTAL**         | **72**  | **442**      |

---

## 4. Cross-References

* Dependency Matrix: `docs/stage-08/backlog/STAGE-08-DEPENDENCY-MATRIX.md`
* Traceability Matrix: `docs/stage-08/backlog/STAGE-08-TRACEABILITY-MATRIX.md`
* Sprint Plan: `docs/stage-08/sprint-plan/STAGE-08-SPRINT-PLAN.md`
* Risk Register: `docs/stage-08/risk/STAGE-08-RISK-REGISTER.md`
