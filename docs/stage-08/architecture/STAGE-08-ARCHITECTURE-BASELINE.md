# SANAD Stage 08 — Architecture Baseline

**Document ID:** `SANAD-ST08-ARCH-001`
**Stage:** 08 — Scale, Growth & Global Expansion
**Status:** APPROVED BASELINE
**Date:** 2026-07-06
**Architecture Freeze:** Frozen except through approved ADR

---

## 1. Purpose

This document defines the Stage 08 architecture baseline for SANAD. It extends the Stage 07 commercially deployable product into a scalable, multi-product, globally extensible platform without violating any immutable architectural principle (see Executive Charter §3).

---

## 2. Architecture Principles (Reaffirmed)

| Principle                       | Stage 08 Application                                              |
|---------------------------------|-------------------------------------------------------------------|
| AI-First                        | All product surfaces expose AI Agent capabilities.                |
| Workflow-First                  | Every cross-domain action routes through the Workflow Engine.     |
| Cloud-Native                    | Stateless services, externalized state, declarative infra.        |
| API-First                       | Every feature ships with OpenAPI spec + SDK contract.             |
| Multi-Tenant SaaS               | Tenant context enforced at every layer (DB, cache, AI, MQ).      |
| Security by Design              | Zero Trust, signed packages, least privilege.                    |
| Compliance by Default           | Data residency, audit, retention built-in.                       |
| Event-Driven Integration        | Domain events published; consumers subscribe via bus.            |
| Modular Service-Oriented        | Domains are bounded contexts; no cross-DB coupling.              |
| Centralized Workflow Engine     | Single engine orchestrates cross-domain flows.                   |
| Centralized AI Core             | Single AI Core governs models, prompts, evaluations, costs.      |
| Tenant Isolation by Default     | Row-level + schema-aware + cache namespace + agent memory.       |
| Arabic-First, Global i18n       | Arabic primary; locale-aware formatting everywhere.              |
| Observability by Default        | Logs, metrics, traces, events on every request and agent run.    |
| Automation by Default           | CI/CD, IaC, policy-as-code, drift detection.                     |

---

## 3. Logical View

```text
┌──────────────────────────────────────────────────────────────────┐
│                      Presentation Layer                          │
│  Next.js 16 (Vercel) — Arabic-first RTL UI                       │
│  Control Plane Console, Tenant UIs, Partner Portal, Dev Portal   │
└──────────────────────────────────────────────────────────────────┘
                              │ HTTPS / OAuth2 / OIDC
┌──────────────────────────────────────────────────────────────────┐
│                         API Gateway / BFF                        │
│  Auth · Rate Limit · Tenant Resolver · Audit · Webhooks          │
└──────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
┌───────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ Core Domains  │   │ Platform Domains │   │ Expansion Domains│
│ Identity      │   │ Workflow Engine  │   │ Marketplace      │
│ Tenancy       │   │ AI Core          │   │ Industry Packs   │
│ RBAC          │   │ Audit            │   │ AI Agents        │
│ CRM           │   │ Notifications    │   │ Enterprise       │
│ ERP           │   │ Integration Bus  │   │ Partner Portal   │
│ Accounting    │   │ Dev Platform     │   │ Growth & Billing │
└───────────────┘   └──────────────────┘   └──────────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              │
┌──────────────────────────────────────────────────────────────────┐
│                       Data & State Layer                         │
│  PostgreSQL (per-tenant row isolation) · Redis (cache+quota)     │
│  Object Store (artifacts, backups, signed packages)              │
│  Event Bus (domain events, webhooks, agent channels)             │
│  Analytics Warehouse (tenant-isolated, PII minimized)            │
└──────────────────────────────────────────────────────────────────┘
                              │
┌──────────────────────────────────────────────────────────────────┐
│                     Cross-Cutting Concerns                       │
│  Observability · Security · Compliance · Cost Governance         │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. Domain Map

| Domain                | Owner Track       | Boundaries                                             |
|-----------------------|-------------------|--------------------------------------------------------|
| Identity & Tenancy    | 8.6 Enterprise    | Users, tenants, legal entities, hierarchy              |
| Workflow Engine       | 8.1 Scale         | Definitions, runs, approvals, SLAs                     |
| AI Core               | 8.5 AI Agents     | Models, prompts, evaluations, cost budgets             |
| AI Agents             | 8.5 AI Agents     | Agent registry, skills, tools, execution, audit        |
| Marketplace           | 8.3 Marketplace   | Publishers, products, listings, entitlements           |
| Industry Packs        | 8.4 Industry      | Pack metadata, install/upgrade lifecycle               |
| CRM / ERP / Acct      | Existing          | Extended via Industry Packs                            |
| Partner Portal        | 8.7 Partners      | Registration, deal registration, tier model            |
| Developer Platform    | 8.8 Developer     | API docs, OAuth clients, webhooks, sandboxes           |
| Growth & Billing      | 8.9 Growth        | Usage metering, pricing, MRR/ARR, customer health      |
| Analytics             | 8.10 Analytics    | Tenant-isolated metrics, semantic layer                |
| Notifications         | Existing          | Email, in-app, webhook delivery                        |
| Integration Bus       | 8.8 Developer     | Event subscriptions, signed payloads, replay defense   |
| Audit                 | Cross-cutting     | Append-only, tenant-scoped, tamper-evident             |

---

## 5. Scaling Strategy

### 5.1 Service-Level Scaling

* Stateless API services horizontally scalable.
* Per-tenant rate limits and quotas enforced at gateway.
* Circuit breakers per downstream service.
* Backpressure for AI Agent execution queues.
* Graceful degradation under load (read-only mode for non-critical surfaces).
* Load-shedding policy for burst traffic.

### 5.2 Database Scaling

* Connection pool governance (max pool, idle timeout, statement timeout).
* Read replicas for analytics workloads (eventually consistent).
* Partitioning for high-volume append-only tables (audit, events, agent runs).
* Query performance budget (p95 < 200ms for tenant-scoped reads).
* Index governance and automated EXPLAIN on slow queries.

### 5.3 Caching Strategy

* Redis namespaces per tenant (`tenant:{id}:*`).
* Cache invalidation via domain events.
* No cross-tenant cache reads.
* TTL governance per data class.

### 5.4 Background Jobs

* Queue per domain (AI agents, notifications, analytics, billing).
* Retry with exponential backoff.
* Dead-letter queue with alerting.
* Idempotency keys for all jobs.

### 5.5 Rate Limiting and Quotas

* Per-tenant API quota (RPM, RPD).
* Per-tenant AI token quota (daily, monthly).
* Per-tenant storage quota.
* Per-tenant webhook delivery quota.
* Burst allowance with leaky-bucket.

### 5.6 Resource Isolation

* Noisy-neighbor protection via per-tenant quotas and circuit breakers.
* Dedicated worker pools for AI agent execution (cost-isolated).
* Tenant-aware scheduler (no tenant can starve others).

---

## 6. Multi-Region Readiness

* Active-active data residency zones for tenant data.
* Configuration-first country model (no hardcoded rules).
* Regional feature flags.
* Data residency matrix documented in `docs/stage-08/globalization/DATA-RESIDENCY-MATRIX.md`.

---

## 7. Resilience and DR

* Multi-AZ deployment for stateless services.
* Database PITR (point-in-time recovery).
* Cross-region backup replication.
* Disaster recovery runbook with RPO/RTO targets.
* Failover drills quarterly.

---

## 8. Observability

* Logs: structured JSON, tenant-scoped, correlation IDs.
* Metrics: RED (rate, errors, duration) + USE (utilization, saturation, errors).
* Traces: OpenTelemetry, end-to-end across domains.
* Events: domain events captured for audit and analytics.
* Dashboards: per-domain + cross-domain executive view.
* Alerting: routed via escalation matrix; on-call schedule published.

---

## 9. Cost Governance

* Per-tenant cost telemetry (compute, storage, AI tokens, email).
* Cost budgets per tenant and per domain.
* Cost alerts on threshold breach.
* Monthly cost review with Finance Owner.

---

## 10. ADRs Required

The following Architecture Decision Records are created in Stage 08 Sprint 0:

| ADR    | Title                                                   |
|--------|---------------------------------------------------------|
| ADR-001| Stage 08 Architecture Baseline Adoption                 |
| ADR-002| Multi-Region Data Residency Model                       |
| ADR-003| Marketplace Package Signing and Verification            |
| ADR-004| AI Agent Permission Model and L0–L4 Autonomy Levels     |
| ADR-005| Enterprise Hierarchy and Delegated Administration       |
| ADR-006| Developer Platform API Versioning and Deprecation       |
| ADR-007| Usage-Based Billing and Metering Architecture           |
| ADR-008| Analytics Tenant Isolation and PII Minimization         |
| ADR-009| Partner Ecosystem Tier and Revenue Share Model          |
| ADR-010| Industry Pack Lifecycle and Reversibility               |

---

## 11. Open Architectural Risks

| Risk                                    | Mitigation                                |
|-----------------------------------------|-------------------------------------------|
| Premature microservices split           | Maintain modular monolith until justified |
| AI cost runaway                         | Per-tenant budgets + circuit breaker      |
| Marketplace supply-chain attack         | Signed packages + security review         |
| Cross-tenant agent memory leakage       | Tenant-scoped memory + audit              |
| Tax rule misconfiguration               | Configuration-first; no hardcoded rules   |

---

## 12. Cross-References

* Executive Charter: `docs/stage-08/STAGE-08-EXECUTIVE-CHARTER.md`
* Scale Architecture: `docs/stage-08/architecture/SCALE-ARCHITECTURE.md`
* Capacity Model: `docs/stage-08/architecture/CAPACITY-MODEL.md`
* Multi-Region Readiness: `docs/stage-08/architecture/MULTI-REGION-READINESS.md`
* Resilience Model: `docs/stage-08/architecture/RESILIENCE-MODEL.md`
* Cost Scaling Model: `docs/stage-08/architecture/COST-SCALING-MODEL.md`
