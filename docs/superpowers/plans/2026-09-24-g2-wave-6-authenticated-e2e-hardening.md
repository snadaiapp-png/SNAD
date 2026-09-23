# G2 Wave 6 — Authenticated E2E Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the dedicated G2 Playwright suite certify real product behavior without transport bypasses, selector ambiguity, race-based fallbacks, or network-idle heuristics.

**Architecture:** E2E must drive the same UI and authenticated API path used by users. It may observe network responses for assertions, but required business actions must be executed through product controls unless the test explicitly validates an API contract.

**Tech Stack:** Playwright, Next.js, Spring Boot, GitHub Actions.

**Spec:** `docs/superpowers/plans/2026-09-24-g2-canonical-remediation-master.md`

## Global Constraints
- No `test.skip`, swallowed errors, or retry-based soft success for required certification paths.
- No raw `page.evaluate(fetch(...))` to compensate for missing UI behavior.
- No `waitForLoadState("networkidle")` as a business-readiness gate.
- One worker, one dedicated G2 project, no state-colliding locale matrix.

## Review Focus
- Login identity assertion uses stable user UI, not an email substring assumption.
- Page titles are selected by stable semantic/test-id anchors.
- Cross-role leave journey observes the same request ID through product behavior.
- Mobile attendance mutates a real persisted state deterministically.
- Logout is deterministic and returns to the auth surface.

---

### Task 1: Rewrite shared G2 auth/session helpers

**Files:**
- Modify: `apps/web/e2e/g2-auth-session.ts`

- [ ] **Step 1: Replace broad logout text selectors** with canonical `data-testid="logout"`.
- [ ] **Step 2: Add a helper that waits for authenticated workspace identity** using a stable identity element rather than `body` substring matching.
- [ ] **Step 3: Keep fail-closed credential environment validation.**
- [ ] **Step 4: Run Playwright `--list` and helper TypeScript checks.**
- [ ] **Step 5: Commit** `test(hr): harden G2 authenticated session helpers`.

### Task 2: Rewrite desktop leave journey around product actions

**Files:**
- Modify: `apps/web/e2e/g2-authenticated.spec.ts`

- [ ] **Step 1: Remove the `Promise.race` submit fallback and all raw `page.evaluate(fetch)` calls.**
- [ ] **Step 2: Use the leave form's real create→submit action and observe the matching API responses only for evidence.**
- [ ] **Step 3: Use stable page-title and row/test-id selectors.**
- [ ] **Step 4: Verify Manager sees manager action but no HR final action; HR sees HR final action; final approved state is visible through a product page or typed acceptance endpoint reached by UI.**
- [ ] **Step 5: Commit** `test(hr): certify real G2 leave approval journey`.

### Task 3: Rewrite Manager/HR navigation checks

**Files:**
- Modify: `apps/web/e2e/g2-authenticated.spec.ts`

- [ ] **Step 1: Replace every `locator("h1").first()` assertion** with stable G2 content-title selectors.
- [ ] **Step 2: Assert role-appropriate navigation/control visibility on each visited page.**
- [ ] **Step 3: Commit** `test(hr): stabilize G2 manager and HR navigation journeys`.

### Task 4: Rewrite mobile attendance mutation

**Files:**
- Modify: `apps/web/e2e/g2-authenticated.spec.ts`

- [ ] **Step 1: Remove `networkidle`.**
- [ ] **Step 2: Wait for explicit attendance-ready marker and current-state control.**
- [ ] **Step 3: Click exactly one legal action, wait for the matching 2xx API response, and verify persisted opposite state after reload.**
- [ ] **Step 4: Assert the mobile viewport does not hide the required action.**
- [ ] **Step 5: Commit** `test(hr): make mobile attendance acceptance deterministic`.

### Task 5: Verify dedicated routing and artifacts

**Files:**
- Modify only if required: `apps/web/playwright-g2.config.ts`
- Modify only if required: `.github/workflows/g2-authenticated-acceptance.yml`

- [ ] **Step 1: Run `npx playwright test --config=playwright.standard.config.ts --list`** and prove G2 spec count is zero.
- [ ] **Step 2: Run `npx playwright test --config=playwright-g2.config.ts --list`** and prove all required G2 tests are present exactly once.
- [ ] **Step 3: Preserve trace/screenshot/video on failure and fail-closed credential provisioning.**
- [ ] **Step 4: Commit only if config/workflow changes are actually required.**

## Wave Exit Gate

- No raw API workaround remains in required product journeys.
- No network-idle dependency remains.
- Desktop Manager/HR/Employee and mobile Employee flows are deterministic.
- Dedicated G2 Playwright routing remains isolated.