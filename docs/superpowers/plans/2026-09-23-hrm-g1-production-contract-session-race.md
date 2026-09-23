# HRM G1 Production Contract + Session Race Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore production HRM G1 recruitment/onboarding screens by reconciling the web client with the canonical Java API contract and eliminate multi-tab refresh-token replay races without weakening backend replay protection.

**Architecture:** Keep the Java controllers/query services as the canonical contract. Normalize cursor-paged backend responses and canonical DTO fields in `apps/web/lib/api/hr-v2-recruitment-api.ts`, so React pages consume stable UI view models. Serialize refresh rotation across browser tabs at the web-client layer while preserving backend replay detection. Fix page-level consumers only where the canonical contract is genuinely plural or requires idempotency/version data.

**Tech Stack:** Next.js/React/TypeScript, Vitest, Spring Boot HRM v2 API, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-07-hrm-g1-recruitment-onboarding-design.md`

## Global Constraints

- PostgreSQL Direct governance remains unchanged; no Docker/Testcontainers path is introduced.
- Backend tenant/RLS authority remains authoritative; no tenant ID is accepted from UI payloads.
- Refresh-token replay protection remains fail-closed; frontend coordination must prevent accidental duplicate rotation rather than weakening backend detection.
- HRM mutations continue to send `Idempotency-Key`; optimistic writes preserve version/If-Match semantics where required.
- No invented PII or denormalized values: compatibility mappings use only canonical fields returned by backend endpoints.

## Review Focus

- Cursor pagination: multiple pages, null cursor, repeated cursor protection, and legacy array compatibility.
- DTO drift: canonical backend field names must map deterministically to UI view models without fabricated values.
- Interview feedback plurality: current participant feedback must be selected from the list and writes must include idempotency/version data.
- Multi-tab auth: concurrent tabs must not rotate the same refresh token simultaneously.
- Production error boundaries: `/hr/recruitment` and `/hr/onboarding` must render normal/empty states rather than local or global error boundaries.

---

### Task 1: Canonical HRM G1 API contract normalization

**Files:**
- Modify: `apps/web/lib/api/hr-v2-recruitment-api.ts`
- Test: `apps/web/lib/api/hr-v2-recruitment-api.contract.test.ts`

**Interfaces:**
- Consumes: backend `CursorPage<T> = { items: T[]; nextCursor: string | null }` and canonical Java DTO field names.
- Produces: existing frontend array-returning list APIs and stable UI view-model DTOs.

- [x] **Step 1: Write failing contract tests for cursor pages and canonical DTO names.**
- [x] **Step 2: Verify RED on the pre-fix branch.**
- [x] **Step 3: Implement cursor traversal and canonical-to-UI mapping at the client boundary.**
- [x] **Step 4: Verify Vitest contract tests pass.**
- [ ] **Step 5: Resolve exact-head TypeScript diagnostics without suppressing type safety.**

### Task 2: Interview feedback consumer reconciliation

**Files:**
- Modify: `apps/web/app/hr/recruitment/interviews/[interviewId]/feedback/page.tsx`
- Test: existing web suite plus TypeScript exact-head check.

**Interfaces:**
- Consumes: `getInterviewFeedback(): Promise<InterviewFeedbackResponse[]>`, authenticated `me.id`, `newIdempotencyKey()`, optional feedback version.
- Produces: participant-specific previous scorecard and canonical PUT request with idempotency/version semantics.

- [ ] **Step 1: Confirm current compile failure for plural feedback contract.**
- [ ] **Step 2: Select the authenticated participant row (`participantId === me.id`) or no previous row.**
- [ ] **Step 3: Submit feedback with a fresh idempotency key and current version when present.**
- [ ] **Step 4: Run web tests + typecheck and confirm PASS.**

### Task 3: Opening detail nullable contract

**Files:**
- Modify: `apps/web/app/hr/recruitment/openings/[openingId]/page.tsx`

**Interfaces:**
- Consumes: optional `complianceDecision?: string | null` from compatibility view model.
- Produces: `string | null` for the label helper.

- [ ] **Step 1: Confirm exact-head TS failure for `undefined`.**
- [ ] **Step 2: Normalize optional value with `?? null`.**
- [ ] **Step 3: Run typecheck and page tests.**

### Task 4: Cross-tab refresh rotation coordination

**Files:**
- Create: `apps/web/lib/auth/cross-tab-refresh-lock.ts`
- Create: `apps/web/lib/auth/cross-tab-refresh-lock.test.ts`
- Modify: `apps/web/lib/auth/auth-provider.tsx`

**Interfaces:**
- Consumes: existing per-provider `SingleFlight<AuthResponse>` refresh operation.
- Produces: origin-wide lock around refresh rotation while preserving backend replay protection.

- [x] **Step 1: Write failing concurrent-tab lock tests.**
- [x] **Step 2: Verify RED.**
- [x] **Step 3: Implement origin-wide refresh lock and wire it around the refresh call.**
- [x] **Step 4: Verify auth reliability tests pass.**

### Task 5: Exact-head CI and production readiness

**Files:**
- No production code unless a failing exact-head gate identifies a root cause.

**Interfaces:**
- Consumes: final branch SHA.
- Produces: exact-head Web CI, core CI, auth reliability, HRM-related checks, and documented remaining unrelated gates.

- [ ] **Step 1: Push final correction SHA to `fix/hrm-g1-production-contract-session-race`.**
- [ ] **Step 2: Require Web CI test/typecheck/build to PASS.**
- [ ] **Step 3: Inspect every failed workflow and separate branch-caused failures from pre-existing/unrelated governance failures.**
- [ ] **Step 4: Keep PR #1135 draft until branch-caused failures are zero.**
- [ ] **Step 5: Do not merge or declare production closure until required review/release gates are satisfied.**
