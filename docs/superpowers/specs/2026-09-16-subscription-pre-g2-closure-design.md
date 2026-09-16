# Subscription PRE-G2 Exact-SHA Closure Design

**Date:** 2026-09-16  
**Repository:** snadaiapp-png/SNAD  
**Branch:** feat/multi-org-subscription-billing-execution  
**Approved approach:** Option A — Exact-SHA Closure Pipeline

## Goal

Close the Subscription/SCP PRE-G2 review with one authoritative commit SHA that proves backend correctness, PostgreSQL Direct behavior, frontend correctness, dedicated authenticated browser acceptance, security, performance, and release readiness before merge or production deployment.

## Current baseline

At design approval the branch advanced to `92609e3b89a5114f52d6875ef07bbadd15b3cecb`, with `main` at `ab06bfa4e47a1abfed1609b9838afb4cab42bfbd`, `behind=0`.

The branch already contains:
- dedicated Subscription acceptance seed
- `playwright.subscription-acceptance.config.ts`
- `subscription-executive-acceptance.spec.ts`
- a dedicated PostgreSQL Direct + Spring Boot + Next.js Subscription E2E job
- governed cancellation coverage
- durable detail/audit readback coverage
- actor identity preservation in governed command ledger

The dedicated Subscription E2E job passes on `92609e3b...`.

## Governing constraints

1. PostgreSQL Direct only. Docker/Testcontainers are out of scope for Subscription certification.
2. Every final claim must be bound to one exact immutable SHA.
3. Any code/config/doc commit after a certification run invalidates that run as final evidence.
4. Do not merge or deploy while any required gate is failed, pending, queued, cancelled, or stale.
5. No random reruns. Classify the failure first; change code/config only for a confirmed cause.
6. Mandatory test skips must be zero for Subscription-specific certification.
7. Generic CI must not execute stateful authenticated acceptance suites that have dedicated fixture-owning jobs.
8. Production deployment is allowed only after PRE-G2 PASS on the exact merge candidate SHA.

## Architecture

### 1. Backend certification

The Maven suite remains the broad backend authority. Subscription-specific PostgreSQL Direct tests provide focused proof for:
- lifecycle command boundaries
- application catalog lifecycle
- plan/version ownership
- price ownership
- provisioning truthfulness
- entitlement isolation
- billing state consistency
- usage metering
- subscription read models
- RBAC/403 forensic behavior

### 2. Browser certification

Two different Playwright surfaces have distinct ownership:

- **Generic E2E/Visual matrix**: stateless/theme/visual/global smoke only.
- **Subscription Authenticated E2E**: stateful Subscription journey using its own PostgreSQL database, acceptance seed, backend, frontend, credentials, and exact-SHA checkout.

`subscription-executive-acceptance.spec.ts` belongs exclusively to the dedicated Subscription job. It must be excluded from the generic standard matrix because the generic job intentionally has no Subscription acceptance credentials or database fixture.

### 3. Exact-SHA gate

A candidate SHA is certifiable only when all required workflows on that same SHA are terminal and green:
- CI / Maven
- PostgreSQL acceptance
- Web CI
- Typecheck
- Build
- Security Baseline
- Pre-Merge Operational Smoke
- Playwright generic matrix
- Subscription Authenticated E2E
- Performance Baseline
- Compile Diagnostics
- relevant architecture/API/governance gates

### 4. Merge and production

Only after exact-SHA PRE-G2 PASS:
1. update PR governance metadata
2. mark ready for review
3. merge exact certified SHA to main
4. certify exact merged main SHA
5. apply Flyway migrations through the normal backend release
6. deploy backend exact SHA
7. deploy Vercel exact main
8. run production smoke and isolated Subscription production acceptance
9. rollback on any failed release gate

## Success criteria

PRE-G2 may be declared PASS only when:
- branch is `behind main = 0`
- no unresolved PR review threads remain
- Maven = PASS
- Subscription PostgreSQL Direct focused suite = PASS
- Subscription Authenticated E2E = PASS
- generic Playwright = PASS
- Web CI = PASS
- Typecheck = PASS
- Security = PASS
- Performance = PASS
- Operational Smoke = PASS
- no mandatory skips
- all evidence refers to the same exact SHA

Production completion is a separate gate and must not be inferred from repository or PR success.
