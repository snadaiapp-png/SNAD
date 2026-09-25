# HRM G2 Visual Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a coherent, localized, responsive HRM G2 product surface and a fail-closed Employee/Manager/HR Desktop+Mobile visual-certification gate, then carry the corrected exact main through protected release control to production final closure.

**Architecture:** Keep all G2 backend/RBAC/Workflow Y2 contracts intact and concentrate changes in the existing HR route-scoped frontend and CI acceptance harness. Product UI work is capability-driven and uses the existing `HrWorkspace`, SDS tokens, HR route-scoped i18n augmenter, and `HrDataTable`; stateful business acceptance remains separate from a new read-oriented visual suite. The historical HRM preview workflow is generalized onto the repository's canonical host-native PostgreSQL Direct pattern and publishes exact-SHA evidence artifacts on successful runs.

**Tech Stack:** Next.js 16, React 19, TypeScript 5.9, Vitest 4, Testing Library, Playwright 1.61, Spring Boot/JDK 21, PostgreSQL Direct, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-25-g2-visual-closure-design.md`

## Global Constraints

- PostgreSQL Direct only; no Docker/Testcontainers path may be introduced.
- RLS, tenant isolation, RBAC, scope separation, optimistic locking, audit/outbox, Workflow Y2 ownership, and existing API authorization remain authoritative.
- No manual production deployment.
- No Flyway repair, history mutation, out-of-order production workaround, or force push.
- No fake screenshots, mocked production evidence, skipped mandatory tests, or `continue-on-error` for closure gates.
- Frontend capability checks are UX visibility only; backend authorization remains authoritative.
- Runtime behavior must remain country-neutral where G2 Core is country-neutral.
- Existing G2 API contracts are reused unless a real contract defect is demonstrated by tests.
- SDS tokens only; no hard-coded hex colors.
- Logical CSS only for RTL safety; no physical `left`/`right` layout assumptions.
- Exact-head CI and human approval are invalidated whenever the corrective PR head changes.
- Existing release-control PR #1145 is not merged after this corrective work changes `main`; it is superseded and rebound to the new exact main.

## Review Focus

1. **Locale direction leakage:** English must not inherit the current forced `dir="rtl"` from `HrDataTable`; Arabic must remain RTL through the document context.
2. **Capability combinations:** users with only SELF, only TEAM, or only HR-admin capabilities must see exactly their launcher/navigation surfaces and must not be advertised inaccessible routes.
3. **Dense mobile data:** 375x667 layouts must not create page-level horizontal overflow; dense tables may scroll only inside the explicit table wrapper.
4. **Empty seeded queues:** visual certification must still pass when a role-appropriate queue has zero rows by asserting an explicit empty state instead of requiring fabricated business data.
5. **Evidence provenance:** every screenshot/manifest entry must bind to the exact candidate SHA and role+viewport+route; stale artifacts must never certify a changed head.

---

## File Map

### New files

- `apps/web/app/hr/components/hr-g2-launcher.tsx` — capability-filtered G2 launcher groups used by `/hr`.
- `apps/web/app/hr/components/hr-g2-launcher.test.tsx` — launcher capability and accessibility contract.
- `apps/web/app/hr/g2-visual-closure.regression.test.ts` — localization, direction, hard-coded-copy and visual-harness regression guard.
- `apps/web/e2e/g2-visual-helpers.ts` — fail-closed visual assertions, evidence naming, manifest writer.
- `apps/web/e2e/g2-visual.spec.ts` — role-appropriate read-oriented visual journeys.
- `apps/web/playwright-g2-visual.config.ts` — Desktop/Mobile visual projects with success screenshots and failure traces.

### Existing files modified

- `apps/web/app/hr/page.tsx` — add capability-aware My Workday / My Team / HR Operations launch areas while preserving G0/G1 summary data.
- `apps/web/app/hr/components/hr-workspace.tsx` — localized/capability-aware navigation presentation and mobile-safe nav container.
- `apps/web/app/hr/components/hr-workspace.test.tsx` — preserve visibility/active-route contract and add mobile/nav semantics regression.
- `apps/web/app/hr/components/hr-data-table.tsx` — stop forcing RTL; inherit active document direction while preserving semantic table behavior.
- `apps/web/app/hr/hr.module.css` — G2 launcher/page/header/card/filter/mobile/nav styles using SDS/logical properties only.
- `apps/web/lib/i18n/locales/hrm-g2-i18n.ts` — complete Arabic/English G2 UI dictionary.
- `apps/web/app/hr/attendance/page.tsx`
- `apps/web/app/hr/timesheets/page.tsx`
- `apps/web/app/hr/leave/page.tsx`
- `apps/web/app/hr/team-attendance/page.tsx`
- `apps/web/app/hr/team-timesheets/page.tsx`
- `apps/web/app/hr/leave/approvals/page.tsx`
- `apps/web/app/hr/schedules/page.tsx`
- `apps/web/app/hr/attendance/admin/page.tsx`
- `apps/web/app/hr/leave/policies/page.tsx`
- `apps/web/app/hr/reports/attendance/page.tsx` — remove hard-coded copy, normalize visual hierarchy, locale-aware formatting and explicit ready/empty states.
- `apps/web/playwright.standard.config.ts` — keep stateful and authenticated G2 suites isolated from the generic matrix; add the visual suite to ignores because it owns a dedicated authenticated stack.
- `.github/workflows/hrm-human-preview.yml` — replace PR-914/Docker historical harness with generalized host-native PostgreSQL Direct visual preview.
- `.github/workflows/g2-authenticated-acceptance.yml` — ensure visual-suite files/config changes trigger the functional G2 gate, without merging the two suites.

---

### Task 1: Lock direction, localization and capability regressions before UI changes

**Files:**
- Create: `apps/web/app/hr/g2-visual-closure.regression.test.ts`
- Modify: `apps/web/app/hr/components/hr-data-table.tsx`
- Modify: `apps/web/lib/i18n/locales/hrm-g2-i18n.ts`

**Interfaces:**
- Consumes: existing `HRM_G2_I18N_AR`, `HRM_G2_I18N_EN`, `HrDataTable`.
- Produces: complete key parity contract and direction-inheriting `HrDataTable` used by every later page task.

- [ ] **Step 1: Write the failing G2 closure regression test**

Create `g2-visual-closure.regression.test.ts` with these required keys and source guards:

```ts
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { HRM_G2_I18N_AR, HRM_G2_I18N_EN } from "@/lib/i18n/locales/hrm-g2-i18n";

const REQUIRED_G2_KEYS = [
  "hrm.g2.landing.myWorkday",
  "hrm.g2.landing.myTeam",
  "hrm.g2.landing.hrOperations",
  "hrm.timesheets.title",
  "hrm.timesheets.team.title",
  "hrm.teamAttendance.title",
  "hrm.leaveApprovals.title",
  "hrm.schedules.title",
  "hrm.attendanceAdmin.title",
  "hrm.leavePolicies.title",
  "hrm.attendanceReport.title",
] as const;

const G2_PAGE_FILES = [
  "timesheets/page.tsx",
  "team-attendance/page.tsx",
  "team-timesheets/page.tsx",
  "leave/approvals/page.tsx",
  "schedules/page.tsx",
  "attendance/admin/page.tsx",
  "leave/policies/page.tsx",
  "reports/attendance/page.tsx",
] as const;

const HR_ROOT = resolve(__dirname);

describe("G2 visual closure guards", () => {
  it("keeps the G2 Arabic/English dictionaries in exact parity", () => {
    expect(Object.keys(HRM_G2_I18N_AR).sort()).toEqual(Object.keys(HRM_G2_I18N_EN).sort());
    for (const key of REQUIRED_G2_KEYS) {
      expect(HRM_G2_I18N_AR[key]).toBeTruthy();
      expect(HRM_G2_I18N_EN[key]).toBeTruthy();
    }
  });

  it("removes legacy hard-coded English headings from G2 pages", () => {
    const forbidden = /(?:My Timesheets|Team Attendance|Team Timesheets|Leave Approval Queue|Work Schedules|Attendance Administration|Leave Policies|Monthly Attendance Report)/;
    for (const relative of G2_PAGE_FILES) {
      expect(readFileSync(resolve(HR_ROOT, relative), "utf8"), relative).not.toMatch(forbidden);
    }
  });

  it("does not force the shared HR table wrapper to RTL", () => {
    const table = readFileSync(resolve(HR_ROOT, "components/hr-data-table.tsx"), "utf8");
    expect(table).not.toContain('dir="rtl"');
  });
});
```

- [ ] **Step 2: Run the regression test and confirm RED**

Run:

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

Expected: FAIL because the new G2 keys do not exist, legacy English headings remain, and `HrDataTable` still hard-codes `dir="rtl"`.

- [ ] **Step 3: Remove forced table direction**

Change `HrDataTable` wrapper from:

```tsx
<div className={styles.hrTableWrap} dir="rtl">
```

to:

```tsx
<div className={styles.hrTableWrap}>
```

Direction must inherit from the active application locale.

- [ ] **Step 4: Add the complete G2 dictionary families**

Extend both AR and EN dictionaries with matching keys. Use these canonical families (all keys exist in both objects):

```text
hrm.g2.landing.{myWorkday,myTeam,hrOperations,attendance,timesheets,leave,teamAttendance,teamTimesheets,leaveApprovals,schedules,attendanceAdmin,leavePolicies,attendanceReport}
hrm.timesheets.{title,subtitle,period,state,comment,actions,submit,submitting,submitted,empty}
hrm.timesheets.team.{title,subtitle,caption,approve,reject,approved,rejected,empty}
hrm.teamAttendance.{title,subtitle,caption,date,clockIn,clockOut,worked,state,empty}
hrm.leaveApprovals.{title,subtitle,caption,from,to,days,reason,state,actions,managerApprove,managerReject,hrApprove,hrReject,managerApproved,managerRejected,hrApproved,hrRejected,empty}
hrm.schedules.{title,subtitle,caption,create,code,name,nameAr,nameEn,shift,break,expected,start,end,breakMinutes,overnight,empty,createError}
hrm.attendanceAdmin.{title,subtitle,correctionUnavailable,caption,date,clockIn,clockOut,worked,state,empty}
hrm.leavePolicies.{title,subtitle,caption,code,nameAr,nameEn,paid,attachment,defaultDays,configureViaPolicy,yes,no,empty}
hrm.attendanceReport.{title,subtitle,caption,employee,scheduledDays,workedMinutes,absent,leave,late,earlyDeparture,missingPunch,status,year,month,empty}
```

Use Arabic product copy for AR and concise product English for EN. Preserve existing Attendance/Leave keys; do not rename keys already consumed by functional G2 acceptance.

- [ ] **Step 5: Re-run the focused test**

Run the same Vitest command. Expected: parity and direction assertions PASS; the hard-coded-heading assertion remains RED until Tasks 3–5 complete.

- [ ] **Step 6: Commit the direction/dictionary foundation**

```bash
git add apps/web/app/hr/components/hr-data-table.tsx apps/web/lib/i18n/locales/hrm-g2-i18n.ts apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "test(hr): lock G2 visual closure contracts"
```

---

### Task 2: Build capability-aware G2 launcher and mobile-safe HR navigation

**Files:**
- Create: `apps/web/app/hr/components/hr-g2-launcher.tsx`
- Create: `apps/web/app/hr/components/hr-g2-launcher.test.tsx`
- Modify: `apps/web/app/hr/page.tsx`
- Modify: `apps/web/app/hr/components/hr-workspace.tsx`
- Modify: `apps/web/app/hr/components/hr-workspace.test.tsx`
- Modify: `apps/web/app/hr/hr.module.css`

**Interfaces:**
- Consumes: `HRM_CAPABILITIES`, `useI18n()`, existing HR summary API data.
- Produces: `HrG2Launcher({ capabilities }: { capabilities: string[] })` and stable `data-testid="g2-launcher-*"` evidence selectors.

- [ ] **Step 1: Write failing launcher capability tests**

Create tests that render the launcher with three minimal capability sets:

```tsx
render(<HrG2Launcher capabilities={[
  HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW,
  HRM_CAPABILITIES.TIMESHEET_SELF_VIEW,
  HRM_CAPABILITIES.LEAVE_SELF_VIEW,
]} />);
expect(screen.getByTestId("g2-launcher-self")).toBeVisible();
expect(screen.queryByTestId("g2-launcher-team")).not.toBeInTheDocument();
expect(screen.queryByTestId("g2-launcher-hr")).not.toBeInTheDocument();
```

Add equivalent TEAM and HR-admin cases, plus an assertion that every rendered card is an anchor with its canonical route.

- [ ] **Step 2: Run the launcher tests and confirm RED**

```bash
cd apps/web
npx vitest run app/hr/components/hr-g2-launcher.test.tsx
```

Expected: FAIL because `HrG2Launcher` does not exist.

- [ ] **Step 3: Implement the launcher as data-driven capability groups**

Use a local typed model:

```ts
interface G2LauncherItem {
  href: string;
  labelKey: string;
  descriptionKey: string;
  capabilitiesAny: readonly string[];
  testId: string;
}

interface G2LauncherGroup {
  id: "self" | "team" | "hr";
  titleKey: string;
  items: readonly G2LauncherItem[];
}
```

Required routes:

```text
self: /hr/attendance, /hr/timesheets, /hr/leave
team: /hr/team-attendance, /hr/team-timesheets, /hr/leave/approvals, /hr/reports/attendance
hr:   /hr/schedules, /hr/attendance/admin, /hr/leave/policies, /hr/leave/approvals, /hr/reports/attendance
```

Filter each item with `capabilitiesAny.some(cap => capabilities.includes(cap))`; render a group only if at least one item remains.

- [ ] **Step 4: Integrate launcher into `/hr` without deleting existing G0/G1 summaries**

Place `<HrG2Launcher capabilities={capabilities} />` after the existing operational stats and before the legacy quick links. Add `data-testid="hr-landing-ready"` on the loaded landing content.

- [ ] **Step 5: Make workspace navigation mobile-safe without hiding routes**

Keep `HR_WORKSPACE_LINKS` as the authority. Add a CSS wrapper behavior that allows the nav itself to scroll inline on narrow viewports while the page does not overflow:

```css
.workspaceNav {
  margin-block-end: 1.5rem;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  scrollbar-width: thin;
}

.workspaceNavList {
  min-inline-size: max-content;
}

@media (min-width: 48rem) {
  .workspaceNav { overflow-x: visible; }
  .workspaceNavList { min-inline-size: 0; flex-wrap: wrap; }
}
```

Do not introduce physical `left`/`right` declarations.

- [ ] **Step 6: Add launcher/card styles using SDS tokens**

Add `.g2Launcher`, `.g2LauncherSection`, `.g2LauncherGrid`, `.g2LauncherCard`, `.g2LauncherTitle`, `.g2LauncherDescription`, `.g2PageHeader` using existing `--snad-*` variables only. Use `grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr));` so 375px layouts collapse naturally.

- [ ] **Step 7: Run navigation + launcher tests**

```bash
cd apps/web
npx vitest run app/hr/components/hr-workspace.test.tsx app/hr/components/hr-g2-launcher.test.tsx
```

Expected: PASS, including SELF/TEAM/HR isolation and most-specific active route.

- [ ] **Step 8: Commit the discoverability slice**

```bash
git add apps/web/app/hr/page.tsx apps/web/app/hr/components/hr-g2-launcher.tsx apps/web/app/hr/components/hr-g2-launcher.test.tsx apps/web/app/hr/components/hr-workspace.tsx apps/web/app/hr/components/hr-workspace.test.tsx apps/web/app/hr/hr.module.css
git commit -m "feat(hr): expose G2 work areas on HR landing"
```

---

### Task 3: Close Employee G2 product UI and localization

**Files:**
- Modify: `apps/web/app/hr/attendance/page.tsx`
- Modify: `apps/web/app/hr/timesheets/page.tsx`
- Modify: `apps/web/app/hr/leave/page.tsx`
- Modify: `apps/web/app/hr/hr.module.css`
- Test: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Consumes: existing `hrG2Api`, existing Attendance/Leave i18n keys plus Task 1 Timesheet keys.
- Produces: Employee routes with explicit `*-ready` evidence roots, localized labels, locale-aware dates/times, and no page-level overflow.

- [ ] **Step 1: Extend the regression test with Employee source assertions**

Assert the Timesheets page uses `t("hrm.timesheets.title")`, `t("hrm.timesheets.period")`, `t("hrm.timesheets.submit")`, and contains `data-testid="timesheets-ready"`; assert Attendance/Leave retain their existing `g2-page-title`, `attendance-ready`, and `leave-ready` selectors.

- [ ] **Step 2: Run focused tests and confirm RED for Timesheets copy**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

Expected: FAIL on hard-coded Timesheet strings.

- [ ] **Step 3: Localize and normalize `/hr/timesheets`**

Replace literals such as `My Timesheets`, `Period`, `State`, `Comment`, `Actions`, `Submit`, `No timesheets`, and success copy with Task 1 translation keys. Keep the current `DRAFT -> submit` behavior and backend method unchanged.

Use locale-aware date formatting already available through HR helpers; do not introduce a second timesheet state machine.

- [ ] **Step 4: Normalize Employee page headers and evidence roots**

Use the common visual hierarchy:

```tsx
<header className={styles.g2PageHeader}>
  <div>
    <h1 data-testid="g2-page-title">{t("...")}</h1>
    <p className={styles.kpiHint}>{t("...subtitle")}</p>
  </div>
</header>
```

Keep business actions below the header and retain current functional `data-testid` selectors used by `g2-authenticated.spec.ts`.

- [ ] **Step 5: Make time rendering locale-aware**

For Attendance, derive locale from `useI18n()` and format with `toLocaleTimeString(locale === "ar" ? "ar-SA" : "en-US", ...)` instead of always using `"ar"`.

- [ ] **Step 6: Run Employee regression + existing HR tests**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts app/hr/components/hr-workspace.test.tsx
npm run typecheck
```

Expected: no Employee hard-coded-heading failures and zero TypeScript errors.

- [ ] **Step 7: Commit Employee UI closure**

```bash
git add apps/web/app/hr/attendance/page.tsx apps/web/app/hr/timesheets/page.tsx apps/web/app/hr/leave/page.tsx apps/web/app/hr/hr.module.css apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "feat(hr): close G2 employee visual surfaces"
```

---

### Task 4: Close Manager/Team G2 product UI and localization

**Files:**
- Modify: `apps/web/app/hr/team-attendance/page.tsx`
- Modify: `apps/web/app/hr/team-timesheets/page.tsx`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Modify: `apps/web/app/hr/reports/attendance/page.tsx`
- Modify: `apps/web/app/hr/hr.module.css`
- Test: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Consumes: TEAM capabilities and existing team-scoped G2 APIs.
- Produces: localized team queues/reports with explicit ready/empty regions; no HR-only action leakage.

- [ ] **Step 1: Add failing source-contract assertions**

Require these selectors/calls in the respective pages:

```text
team-attendance-ready -> t("hrm.teamAttendance.title")
team-timesheets-ready -> t("hrm.timesheets.team.title")
leave-approvals-ready -> t("hrm.leaveApprovals.title")
attendance-report-ready -> t("hrm.attendanceReport.title")
```

Also assert approval action labels are translated and existing request-id `data-testid` selectors remain unchanged.

- [ ] **Step 2: Run focused RED test**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

Expected: FAIL because manager pages currently contain English literals.

- [ ] **Step 3: Localize Team Attendance and Team Timesheets**

Replace all table headers, captions, empty states and action/success copy with the Task 1 dictionaries. Preserve `listTeamAttendance()`, `listTeamTimesheets("SUBMITTED")`, approve/reject methods and capability checks exactly.

- [ ] **Step 4: Localize Leave Approval Queue without changing Workflow Y2 ownership**

Keep the existing API calls and states `PENDING_MANAGER` / `PENDING_HR`. Replace only presentation copy. Keep:

```tsx
data-testid={`manager-approve-${r.id}`}
data-testid={`manager-reject-${r.id}`}
data-testid={`hr-approve-${r.id}`}
data-testid={`hr-reject-${r.id}`}
```

Manager-only identities must not render HR buttons; the existing functional acceptance assertion remains authoritative.

- [ ] **Step 5: Localize team attendance report**

Replace `Employee`, `Sched. Days`, `Worked Min`, `Absent`, `Leave`, `Late`, `Early Dep.`, `Missing Punch`, `Status`, `Year`, `Month` and caption/empty state with `hrm.attendanceReport.*` keys. Keep API scope selection:

```ts
canAdmin
  ? hrG2Api.adminMonthlyAttendanceReport(year, month)
  : hrG2Api.teamMonthlyAttendanceReport(year, month)
```

- [ ] **Step 6: Add explicit ready roots**

Wrap successful loaded regions with the selectors from Step 1 so visual acceptance can distinguish a real data/empty state from a partially rendered shell.

- [ ] **Step 7: Run focused test + typecheck**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
npm run typecheck
```

Expected: Manager literals removed, selectors present, typecheck PASS.

- [ ] **Step 8: Commit Manager visual closure**

```bash
git add apps/web/app/hr/team-attendance/page.tsx apps/web/app/hr/team-timesheets/page.tsx apps/web/app/hr/leave/approvals/page.tsx apps/web/app/hr/reports/attendance/page.tsx apps/web/app/hr/hr.module.css apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "feat(hr): close G2 manager visual surfaces"
```

---

### Task 5: Close HR Administration G2 product UI and localization

**Files:**
- Modify: `apps/web/app/hr/schedules/page.tsx`
- Modify: `apps/web/app/hr/attendance/admin/page.tsx`
- Modify: `apps/web/app/hr/leave/policies/page.tsx`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Modify: `apps/web/app/hr/reports/attendance/page.tsx`
- Modify: `apps/web/app/hr/hr.module.css`
- Test: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Consumes: HR admin capabilities and existing admin-scoped APIs only.
- Produces: localized schedule/admin/policy surfaces and visual selectors with country-neutral policy messaging preserved.

- [ ] **Step 1: Extend regression guards for admin selectors**

Require:

```text
schedules-ready
attendance-admin-ready
leave-policies-ready
leave-approvals-ready
attendance-report-ready
```

and `t("hrm.schedules.title")`, `t("hrm.attendanceAdmin.title")`, `t("hrm.leavePolicies.title")` in their source files.

- [ ] **Step 2: Run RED test**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

- [ ] **Step 3: Localize Work Schedules**

Replace hard-coded `Work Schedules`, create-form labels, units and empty copy. Preserve `timezone: "Asia/Riyadh"` only as the existing operational value sent by this screen; do not present it as a statutory/legal default and do not alter country-neutral leave policy behavior.

- [ ] **Step 4: Localize Attendance Administration**

Translate title, provenance subtitle, correction-unavailable message, table headers/caption and empty state. Preserve the current fail-closed behavior: do not substitute employee self-clock mutations for the unavailable canonical correction command.

- [ ] **Step 5: Localize Leave Policies**

Translate all columns and country-neutral explanatory copy. Render booleans with localized `yes` / `no`; retain `defaultDaysPerYear === null` as an explicit configure-via-policy state, never invent entitlement days.

- [ ] **Step 6: Add responsive form/filter CSS**

Use logical/responsive rules:

```css
.g2FormGrid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr));
  gap: 0.75rem;
}

.g2FilterBar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: end;
}
```

No physical-direction properties and no new colors outside SDS variables.

- [ ] **Step 7: Run complete frontend unit/type/lint/build gate**

```bash
cd apps/web
npm test
npm run typecheck
npm run lint
npm run build
```

Expected: all tests PASS, typecheck 0 errors, lint 0 errors (existing repository warnings may remain if current gate allows them), production build PASS.

- [ ] **Step 8: Re-run the G2 closure regression test and require full GREEN**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

Expected: PASS — i18n parity, no legacy hard-coded G2 headings, inherited table direction, required visual selectors present.

- [ ] **Step 9: Commit HR admin visual closure**

```bash
git add apps/web/app/hr/schedules/page.tsx apps/web/app/hr/attendance/admin/page.tsx apps/web/app/hr/leave/policies/page.tsx apps/web/app/hr/leave/approvals/page.tsx apps/web/app/hr/reports/attendance/page.tsx apps/web/app/hr/hr.module.css apps/web/app/hr/g2-visual-closure.regression.test.ts apps/web/lib/i18n/locales/hrm-g2-i18n.ts
git commit -m "feat(hr): close G2 HR administration visual surfaces"
```

---

### Task 6: Add deterministic role-by-device visual evidence suite

**Files:**
- Create: `apps/web/e2e/g2-visual-helpers.ts`
- Create: `apps/web/e2e/g2-visual.spec.ts`
- Create: `apps/web/playwright-g2-visual.config.ts`
- Modify: `apps/web/playwright.standard.config.ts`
- Test: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Consumes: `loginThroughUi`, `roleEmail`, exact G2 routes, visual-ready selectors from Tasks 2–5.
- Produces: screenshots under `test-results/g2-visual-evidence/` and `manifest.ndjson` entries `{sha, role, viewport, route, screenshot, result}`.

- [ ] **Step 1: Add failing source guards for the visual harness**

Require the regression test to assert:

```ts
const visualConfig = readFileSync(resolve(REPO_ROOT, "apps/web/playwright-g2-visual.config.ts"), "utf8");
expect(visualConfig).toContain('name: "g2-visual-desktop"');
expect(visualConfig).toContain('name: "g2-visual-mobile"');
expect(visualConfig).toContain('screenshot: "on"');
```

and assert the visual spec names all three roles.

- [ ] **Step 2: Run RED test**

Expected: missing visual config/spec.

- [ ] **Step 3: Implement fail-closed visual helpers**

`g2-visual-helpers.ts` must export:

```ts
export type G2Role = "employee" | "manager" | "hr";

export interface VisualSurface {
  route: string;
  readyTestId: string;
}

export async function assertVisualSurface(page: Page, surface: VisualSurface): Promise<void>;
export async function captureVisualEvidence(page: Page, role: G2Role, route: string, projectName: string): Promise<void>;
```

`assertVisualSurface` must:

```ts
await expect(page.getByTestId(surface.readyTestId)).toBeVisible({ timeout: 15_000 });
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
expect(overflow, `page-level horizontal overflow on ${surface.route}`).toBeLessThanOrEqual(1);
```

Register `page.on("pageerror", ...)` and response listeners before navigation; after the ready assertion, fail if an uncaught page error or HR API response status >= 400 was observed.

`captureVisualEvidence` must sanitize the route to a stable filename, call `page.screenshot({ fullPage: true, path })`, and append one JSON line to `test-results/g2-visual-evidence/manifest.ndjson` with `process.env.GITHUB_SHA ?? process.env.GITHUB_HEAD_SHA ?? "local"`.

- [ ] **Step 4: Implement role route matrices**

Use exact matrices:

```ts
const SURFACES = {
  employee: [
    { route: "/hr", readyTestId: "hr-landing-ready" },
    { route: "/hr/attendance", readyTestId: "attendance-ready" },
    { route: "/hr/timesheets", readyTestId: "timesheets-ready" },
    { route: "/hr/leave", readyTestId: "leave-ready" },
  ],
  manager: [
    { route: "/hr", readyTestId: "hr-landing-ready" },
    { route: "/hr/team-attendance", readyTestId: "team-attendance-ready" },
    { route: "/hr/team-timesheets", readyTestId: "team-timesheets-ready" },
    { route: "/hr/leave/approvals", readyTestId: "leave-approvals-ready" },
    { route: "/hr/reports/attendance", readyTestId: "attendance-report-ready" },
  ],
  hr: [
    { route: "/hr", readyTestId: "hr-landing-ready" },
    { route: "/hr/schedules", readyTestId: "schedules-ready" },
    { route: "/hr/attendance/admin", readyTestId: "attendance-admin-ready" },
    { route: "/hr/leave/policies", readyTestId: "leave-policies-ready" },
    { route: "/hr/leave/approvals", readyTestId: "leave-approvals-ready" },
    { route: "/hr/reports/attendance", readyTestId: "attendance-report-ready" },
  ],
} satisfies Record<G2Role, readonly VisualSurface[]>;
```

Each test logs in with the real UI, verifies `roleEmail(role)`, visits every role-appropriate route, asserts the expected launcher/nav item, calls `assertVisualSurface`, then `captureVisualEvidence`.

- [ ] **Step 5: Create a two-project visual config**

Use:

```ts
export default defineConfig({
  testDir: "./e2e",
  testMatch: /g2-visual\.spec\.ts$/,
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["html", { outputFolder: "playwright-g2-visual-report" }], ["list"]],
  use: {
    baseURL: BASE_URL,
    channel: "chrome",
    trace: "retain-on-failure",
    screenshot: "on",
    video: "retain-on-failure",
  },
  projects: [
    { name: "g2-visual-desktop", use: { ...devices["Desktop Chrome"], locale: "ar" } },
    { name: "g2-visual-mobile", use: { ...devices["Pixel 5"], viewport: { width: 375, height: 667 }, locale: "ar" } },
  ],
});
```

Before login, set `snad.locale=ar` in the browser origin so the primary visual proof exercises RTL. English completeness remains pinned by dictionary parity/unit tests.

- [ ] **Step 6: Keep the generic matrix isolated**

Add `"**/g2-visual.spec.ts"` to `playwright.standard.config.ts` `testIgnore`, with a comment that it requires the authenticated G2 PostgreSQL Direct stack and runs only via `playwright-g2-visual.config.ts`.

- [ ] **Step 7: Verify test routing without needing credentials**

```bash
cd apps/web
npx playwright test --config=playwright.standard.config.ts --list | grep -c "g2-visual.spec.ts" || true
npx playwright test --config=playwright-g2-visual.config.ts --list
```

Expected: generic count `0`; dedicated list contains Employee, Manager and HR tests for both projects.

- [ ] **Step 8: Commit the visual evidence harness**

```bash
git add apps/web/e2e/g2-visual-helpers.ts apps/web/e2e/g2-visual.spec.ts apps/web/playwright-g2-visual.config.ts apps/web/playwright.standard.config.ts apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "test(hr): add G2 role-device visual evidence matrix"
```

---

### Task 7: Generalize HRM Human Preview onto PostgreSQL Direct and publish PASS artifacts

**Files:**
- Modify: `.github/workflows/hrm-human-preview.yml`
- Modify: `.github/workflows/g2-authenticated-acceptance.yml`
- Test: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Consumes: host-native PostgreSQL pattern from `g2-authenticated-acceptance.yml`, `g2-acceptance-seed.sql`, visual config from Task 6.
- Produces: required `Full-stack HRM human preview` check and artifact bundle `g2-visual-evidence-${candidate_sha}`.

- [ ] **Step 1: Add a failing workflow-regression assertion**

Read `.github/workflows/hrm-human-preview.yml` and assert:

```ts
expect(workflow).not.toContain("github.event.pull_request.number == 914");
expect(workflow).not.toContain("services:\n      postgres:");
expect(workflow).toContain("playwright-g2-visual.config.ts");
expect(workflow).toContain("actions/upload-artifact@v4");
expect(workflow).toContain("if-no-files-found: error");
```

- [ ] **Step 2: Run RED regression test**

Expected: FAIL because the historical workflow is pinned to PR #914 and uses a PostgreSQL container service.

- [ ] **Step 3: Replace historical preview provisioning with host-native PostgreSQL Direct**

The workflow must start PostgreSQL with:

```yaml
- name: Start host-native PostgreSQL Direct
  shell: bash
  run: |
    set -euo pipefail
    command -v psql >/dev/null
    command -v pg_isready >/dev/null
    sudo systemctl start postgresql
    for i in $(seq 1 30); do
      pg_isready -h 127.0.0.1 -p 5432 && break
      sleep 1
    done
    pg_isready -h 127.0.0.1 -p 5432
```

Generate random DB and E2E credentials with `openssl rand -hex 24`, mask them, create a least-privilege role with `NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`, create the preview database, run Flyway, and execute `apps/sanad-platform/src/test/resources/sql/g2-acceptance-seed.sql` using the runtime credential variable exactly as the G2 functional workflow does.

- [ ] **Step 4: Build and start exact candidate backend/frontend**

Checkout:

```yaml
ref: ${{ github.event.pull_request.head.sha || github.sha }}
fetch-depth: 0
```

Export `CANDIDATE_SHA` and assert `git rev-parse HEAD` equals it. Build backend on JDK 21; build frontend on Node 24. Start Spring Boot on 8080 and Next.js on 3001; poll `/actuator/health` and frontend root fail-closed before Playwright.

- [ ] **Step 5: Run functional G2 acceptance separately, then visual preview**

Do not duplicate the mutable functional suite inside the visual matrix. The generalized preview job runs only:

```bash
cd apps/web
npx playwright test --config=playwright-g2-visual.config.ts
```

The existing dedicated `g2-authenticated-acceptance.yml` remains the stateful business gate. Add Task 6 visual files/config to its path trigger so both gates are re-evaluated when shared UI/e2e helpers change.

- [ ] **Step 6: Build a deterministic manifest artifact**

After Playwright succeeds:

```bash
mkdir -p "$RUNNER_TEMP/g2-visual-bundle"
cp -R apps/web/test-results/g2-visual-evidence "$RUNNER_TEMP/g2-visual-bundle/evidence"
cp -R apps/web/playwright-g2-visual-report "$RUNNER_TEMP/g2-visual-bundle/report"
printf '%s\n' "$CANDIDATE_SHA" > "$RUNNER_TEMP/g2-visual-bundle/candidate-sha.txt"
```

Upload on `if: always()` but set `if-no-files-found: error`; the job itself must still fail if Playwright failed. Artifact name:

```yaml
name: g2-visual-evidence-${{ github.event.pull_request.head.sha || github.sha }}
```

- [ ] **Step 7: Run local workflow/source regression and full frontend gates**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
npm test
npm run typecheck
npm run lint
npm run build
```

Expected: PASS.

- [ ] **Step 8: Commit the generalized preview gate**

```bash
git add .github/workflows/hrm-human-preview.yml .github/workflows/g2-authenticated-acceptance.yml apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "ci(hr): generalize G2 human visual preview"
```

---

### Task 8: Verify exact branch, open corrective PR, and obtain exact-head certification

**Files:**
- No product files unless a gate exposes a real defect.
- Update evidence docs only after observed results; never predeclare PASS.

**Interfaces:**
- Consumes: Tasks 1–7 branch head.
- Produces: one corrective PR to `main`, exact-head CI evidence, exact-head independent human approval.

- [ ] **Step 1: Run pre-push verification**

```bash
git diff --check
cd apps/web
npm test
npm run typecheck
npm run lint
npm run build
npx playwright test --config=playwright.standard.config.ts --list
npx playwright test --config=playwright-g2.config.ts --list
npx playwright test --config=playwright-g2-visual.config.ts --list
```

Expected: no whitespace errors; frontend gates PASS; G2 functional spec present only in dedicated functional config; G2 visual spec present only in dedicated visual config.

- [ ] **Step 2: Push the exact branch and open one corrective PR**

PR title:

```text
fix(hr): close G2 product UI and visual certification
```

PR body must state:

```text
BASELINE_MAIN=<observed main SHA>
CANDIDATE_SHA=<observed PR head SHA>
G2_BACKEND_CONTRACT_CHANGE=NONE unless a test-proven defect required one
RLS_RBAC_TENANT_ISOLATION=UNCHANGED
WORKFLOW_Y2_OWNERSHIP=UNCHANGED
POSTGRESQL_DIRECT=REQUIRED
VISUAL_MATRIX=Employee+Manager+HR x Desktop+Mobile
RELEASE_CONTROL_PR_1145=SUPERSEDED_AFTER_CORRECTIVE_MERGE
MERGE_AUTHORIZATION=NO
FINAL_GATE=OPEN
```

- [ ] **Step 3: Verify every required check belongs to the exact PR head**

Required terminal PASS includes at least:

```text
Maven Test Suite
PostgreSQL Acceptance Tests
G2 Authenticated Acceptance
Full-stack HRM human preview
Security Gate Summary
Pre-Merge Operational Smoke Summary
Build Next.js Web / web build gate
Generic Playwright
```

No failure, queued, in-progress, cancelled mandatory gate, or stale-head run may certify the PR.

- [ ] **Step 4: Inspect visual artifacts rather than trusting check status alone**

Download the exact-head `g2-visual-evidence-<SHA>` artifact. Verify:

```text
candidate-sha.txt == PR exact head
Employee desktop screenshots = 4 expected surfaces
Employee mobile screenshots = 4 expected surfaces
Manager desktop screenshots = 5 expected surfaces
Manager mobile screenshots = 5 expected surfaces
HR desktop screenshots = 6 expected surfaces
HR mobile screenshots = 6 expected surfaces
manifest entries all result=PASS and sha=<exact head>
```

If a capability intentionally removes the manager report, update neither counts nor code silently: treat that as a contract discrepancy and reconcile against the seeded capabilities before proceeding.

- [ ] **Step 5: Request independent human review on the exact head**

Require `APPROVED` from a reviewer identity different from PR author and anchored to the exact candidate SHA. Any new commit invalidates the approval and returns to Step 3.

- [ ] **Step 6: Record gate result without self-approving**

Only after Steps 3–5 are proven:

```text
G2_PRODUCT_UI              = PASS
G2_I18N_PARITY             = PASS
G2_NAV_DISCOVERABILITY     = PASS
G2_AUTHENTICATED_E2E       = PASS
G2_VISUAL_EMPLOYEE_DESKTOP = PASS
G2_VISUAL_EMPLOYEE_MOBILE  = PASS
G2_VISUAL_MANAGER_DESKTOP  = PASS
G2_VISUAL_MANAGER_MOBILE   = PASS
G2_VISUAL_HR_DESKTOP       = PASS
G2_VISUAL_HR_MOBILE        = PASS
HRM_HUMAN_PREVIEW          = PASS
EXACT_HEAD_CI              = PASS
INDEPENDENT_APPROVAL       = PASS
```

- [ ] **Step 7: Squash-merge only after the user-authorized merge gate remains valid**

Immediately before merge re-fetch PR head, all required checks, review SHA, and `main`. If any invariant changed, STOP and recertify. Do not close the PR as a substitute for merge.

---

### Task 9: Rebind release control to corrected main and complete production final closure

**Files:**
- Create one inert `.release/` authorization file only on the new release-control branch, following repository release-control conventions.
- Do not carry frontend/runtime changes in the release-control PR.

**Interfaces:**
- Consumes: exact squash-merge SHA from Task 8.
- Produces: corrected production release through canonical Workflow Y2/release workflow and final evidence.

- [ ] **Step 1: Verify the corrective squash commit is the new exact `main`**

Record:

```text
CORRECTIVE_MERGE_SHA=<actual 40-char main SHA>
PR_MERGED=YES
MAIN_DRIFT=NO
```

Run/verify all post-merge checks required by repository governance before production authorization.

- [ ] **Step 2: Supersede PR #1145 instead of merging its stale baseline**

Close #1145 with a comment that its bound main `eb19a2dd9b476bc3a5af0c9b0755489fc1865c0f` was superseded by the G2 Visual Closure corrective merge. Do not rewrite its branch into a mixed runtime/release-control PR.

- [ ] **Step 3: Create a fresh inert Release-Control branch from the corrected exact main**

The only file change is a new authorization record containing the corrected main SHA and the same safety invariants:

```text
Runtime application code change = NONE
Database migration change        = NONE
Security/RBAC/RLS change         = NONE
Workflow Y2 ownership            = UNCHANGED
PostgreSQL Direct                = REQUIRED
```

Open a new release-control PR to `main`.

- [ ] **Step 4: Certify the release-control exact head**

Require terminal exact-head CI, no main drift, and independent human approval. The protected squash merge commit message MUST contain exactly:

```text
PRODUCTION-RELEASE-AUTHORIZED
```

- [ ] **Step 5: Merge release control and verify immutable artifact publication**

After merge, verify the repository's immutable backend image publication binds to the exact release main SHA. Never substitute a mutable/latest image tag as evidence.

- [ ] **Step 6: Run canonical Workflow Y2 production release**

Dispatch only the canonical `production-release.yml` path with:

```text
rollback_on_failure=true
```

No manual Render/Vercel deployment may substitute for the canonical workflow.

- [ ] **Step 7: Verify production invariants and visual smoke**

Require terminal evidence for:

```text
exact-image parity
backend readiness
Flyway/runtime invariants
RLS + tenant isolation + RBAC security gates
G2 authenticated production smoke
Employee/Manager/HR role-appropriate production route smoke
Desktop + Mobile production visual smoke
Vercel Control Plane/BFF evidence
Workflow Y2 orchestrator PASS
post-merge verification PASS
```

Do not perform write-heavy visual mutations merely to capture screenshots; production visual smoke is read-oriented unless an existing governed production acceptance explicitly owns the mutation.

- [ ] **Step 8: Declare final closure only from observed terminal evidence**

The final state must be exactly:

```text
CORRECTIVE_PR_MERGED         = YES
RELEASE_CONTROL_REBOUND      = YES
PRODUCTION_RELEASE           = PASS
POST_MERGE_VERIFICATION      = PASS
PRODUCTION_VISUAL_SMOKE      = PASS
G2_FINAL_CLOSURE             = PASS
```

Any failed, skipped mandatory, queued, stale-head, or unverified production gate keeps `G2_FINAL_CLOSURE = OPEN`.
