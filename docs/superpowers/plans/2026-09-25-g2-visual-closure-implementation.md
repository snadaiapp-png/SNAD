# HRM G2 Visual Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a coherent, localized, responsive HRM G2 product surface and a fail-closed Employee/Manager/HR Desktop+Mobile visual-certification gate, then carry the corrected exact main through protected release control to production final closure.

**Architecture:** Keep all G2 backend/RBAC/Workflow Y2 contracts intact and concentrate changes in the existing HR route-scoped frontend and CI acceptance harness. Product UI remains capability-driven, stateful business acceptance remains separate from a new read-oriented visual suite, and the historical HRM preview workflow is generalized onto host-native PostgreSQL Direct. Exact-SHA screenshots, report and manifest artifacts become a required release input rather than optional diagnostics.

**Tech Stack:** Next.js 16, React 19, TypeScript 5.9, Vitest 4, Testing Library, Playwright 1.61, Spring Boot/JDK 21, PostgreSQL Direct, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-25-g2-visual-closure-design.md`

## Global Constraints

- PostgreSQL Direct only; no Docker/Testcontainers path may be introduced.
- RLS, tenant isolation, RBAC, scope separation, optimistic locking, audit/outbox, Workflow Y2 ownership, and existing API authorization remain authoritative.
- No manual production deployment.
- No Flyway repair, history mutation, production out-of-order workaround, force push, branch-protection bypass, fake screenshot, skipped mandatory gate, or `continue-on-error` closure path.
- Frontend capability checks are UX visibility only; backend authorization remains authoritative.
- G2 Core remains country-neutral where it is country-neutral today.
- Existing G2 API contracts are reused unless a failing test proves a real contract defect.
- SDS tokens only; no hard-coded hex colors.
- Logical CSS only; no physical `left`/`right` layout assumptions.
- Exact-head CI and human approval are invalidated by any head change.
- PR #1145 must not be merged after the visual corrective merge changes `main`; it is closed as superseded and release control is rebound to the new exact main.

## Review Focus

1. **Locale direction leakage:** English must not inherit the current forced RTL table wrapper; Arabic must remain RTL via the application locale context.
2. **Capability combinations:** SELF-only, TEAM-only and HR-admin-only users must see exactly their allowed launcher/navigation surfaces.
3. **Dense mobile data:** 375x667 must have no page-level horizontal overflow; only the explicit table wrapper may scroll inline.
4. **Empty seeded queues:** a legitimate zero-row queue must render an explicit empty state and still be visually certifiable.
5. **Evidence provenance:** every artifact entry must bind to the exact PR head SHA, role, viewport and route; pull-request merge-ref SHA must never be mistaken for the candidate head SHA.

---

## File Map

### New files
- `apps/web/app/hr/components/hr-g2-launcher.tsx`
- `apps/web/app/hr/components/hr-g2-launcher.test.tsx`
- `apps/web/app/hr/g2-visual-closure.regression.test.ts`
- `apps/web/e2e/g2-visual-helpers.ts`
- `apps/web/e2e/g2-visual.spec.ts`
- `apps/web/playwright-g2-visual.config.ts`

### Modified files
- `apps/web/app/hr/page.tsx`
- `apps/web/app/hr/components/hr-workspace.tsx`
- `apps/web/app/hr/components/hr-workspace.test.tsx`
- `apps/web/app/hr/components/hr-data-table.tsx`
- `apps/web/app/hr/hr.module.css`
- `apps/web/lib/i18n/locales/hrm-g2-i18n.ts`
- `apps/web/app/hr/attendance/page.tsx`
- `apps/web/app/hr/timesheets/page.tsx`
- `apps/web/app/hr/leave/page.tsx`
- `apps/web/app/hr/team-attendance/page.tsx`
- `apps/web/app/hr/team-timesheets/page.tsx`
- `apps/web/app/hr/leave/approvals/page.tsx`
- `apps/web/app/hr/schedules/page.tsx`
- `apps/web/app/hr/attendance/admin/page.tsx`
- `apps/web/app/hr/leave/policies/page.tsx`
- `apps/web/app/hr/reports/attendance/page.tsx`
- `apps/web/playwright.standard.config.ts`
- `.github/workflows/hrm-human-preview.yml`
- `.github/workflows/g2-authenticated-acceptance.yml`

---

### Task 1: Direction and G2 dictionary foundation

**Files:**
- Create: `apps/web/app/hr/g2-visual-closure.regression.test.ts`
- Modify: `apps/web/app/hr/components/hr-data-table.tsx`
- Modify: `apps/web/lib/i18n/locales/hrm-g2-i18n.ts`

**Interfaces:**
- Consumes: `HRM_G2_I18N_AR`, `HRM_G2_I18N_EN`, `HrDataTable`.
- Produces: exact AR/EN parity and direction-inheriting tables for all later tasks.

- [ ] **Step 1: Write the failing foundation test**

```ts
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { HRM_G2_I18N_AR, HRM_G2_I18N_EN } from "@/lib/i18n/locales/hrm-g2-i18n";

const HR_ROOT = resolve(__dirname);
const REQUIRED_FOUNDATION_KEYS = [
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

describe("G2 visual closure foundation", () => {
  it("keeps route-scoped G2 dictionaries in exact parity", () => {
    expect(Object.keys(HRM_G2_I18N_AR).sort()).toEqual(Object.keys(HRM_G2_I18N_EN).sort());
    for (const key of REQUIRED_FOUNDATION_KEYS) {
      expect(HRM_G2_I18N_AR[key]).toBeTruthy();
      expect(HRM_G2_I18N_EN[key]).toBeTruthy();
    }
  });

  it("inherits direction instead of forcing all HR tables to RTL", () => {
    const table = readFileSync(resolve(HR_ROOT, "components/hr-data-table.tsx"), "utf8");
    expect(table).not.toContain('dir="rtl"');
  });
});
```

- [ ] **Step 2: Prove RED**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

Expected: FAIL for missing keys and forced `dir="rtl"`.

- [ ] **Step 3: Remove forced direction**

Change:

```tsx
<div className={styles.hrTableWrap} dir="rtl">
```

to:

```tsx
<div className={styles.hrTableWrap}>
```

- [ ] **Step 4: Add exact AR/EN dictionary copy**

Add the following matched keys. Existing Attendance and Leave keys stay unchanged.

```ts
// Arabic
"hrm.g2.landing.myWorkday": "يومي العملي",
"hrm.g2.landing.myTeam": "فريقي",
"hrm.g2.landing.hrOperations": "عمليات الموارد البشرية",
"hrm.g2.landing.attendance": "الحضور والانصراف",
"hrm.g2.landing.timesheets": "سجلات وقتي",
"hrm.g2.landing.leave": "إجازاتي",
"hrm.g2.landing.teamAttendance": "حضور الفريق",
"hrm.g2.landing.teamTimesheets": "سجلات وقت الفريق",
"hrm.g2.landing.leaveApprovals": "اعتمادات الإجازات",
"hrm.g2.landing.schedules": "جداول العمل",
"hrm.g2.landing.attendanceAdmin": "إدارة الحضور",
"hrm.g2.landing.leavePolicies": "سياسات الإجازات",
"hrm.g2.landing.attendanceReport": "تقرير الحضور",
"hrm.timesheets.title": "سجلات وقتي",
"hrm.timesheets.subtitle": "مراجعة فترات العمل وإرسال السجلات للاعتماد",
"hrm.timesheets.period": "الفترة",
"hrm.timesheets.state": "الحالة",
"hrm.timesheets.comment": "التعليق",
"hrm.timesheets.actions": "الإجراءات",
"hrm.timesheets.submit": "إرسال",
"hrm.timesheets.submitting": "جارٍ الإرسال…",
"hrm.timesheets.submitted": "تم إرسال سجل الوقت",
"hrm.timesheets.empty": "لا توجد سجلات وقت",
"hrm.timesheets.team.title": "سجلات وقت الفريق",
"hrm.timesheets.team.subtitle": "مراجعة سجلات الوقت المرسلة للفريق واعتمادها أو رفضها",
"hrm.timesheets.team.caption": "سجلات الوقت بانتظار الاعتماد",
"hrm.timesheets.team.approve": "اعتماد",
"hrm.timesheets.team.reject": "رفض",
"hrm.timesheets.team.approved": "تم اعتماد سجل الوقت",
"hrm.timesheets.team.rejected": "تم رفض سجل الوقت",
"hrm.timesheets.team.empty": "لا توجد سجلات وقت بانتظار الاعتماد",
"hrm.teamAttendance.title": "حضور الفريق",
"hrm.teamAttendance.subtitle": "متابعة حضور أعضاء الفريق ضمن نطاق الصلاحية",
"hrm.teamAttendance.caption": "سجلات حضور الفريق",
"hrm.teamAttendance.date": "التاريخ",
"hrm.teamAttendance.clockIn": "دخول",
"hrm.teamAttendance.clockOut": "خروج",
"hrm.teamAttendance.worked": "العمل المنجز",
"hrm.teamAttendance.state": "الحالة",
"hrm.teamAttendance.empty": "لا توجد سجلات حضور للفريق",
"hrm.leaveApprovals.title": "اعتمادات الإجازات",
"hrm.leaveApprovals.subtitle": "معالجة الطلبات الواقعة ضمن خطوة اعتمادك الحالية",
"hrm.leaveApprovals.caption": "طلبات الإجازة بانتظار الاعتماد",
"hrm.leaveApprovals.from": "من",
"hrm.leaveApprovals.to": "إلى",
"hrm.leaveApprovals.days": "الأيام",
"hrm.leaveApprovals.reason": "السبب",
"hrm.leaveApprovals.state": "الحالة",
"hrm.leaveApprovals.actions": "الإجراءات",
"hrm.leaveApprovals.managerApprove": "اعتماد المدير",
"hrm.leaveApprovals.managerReject": "رفض المدير",
"hrm.leaveApprovals.hrApprove": "اعتماد الموارد البشرية",
"hrm.leaveApprovals.hrReject": "رفض الموارد البشرية",
"hrm.leaveApprovals.managerApproved": "اعتمد المدير الطلب وتم تحويله للموارد البشرية",
"hrm.leaveApprovals.managerRejected": "رفض المدير الطلب",
"hrm.leaveApprovals.hrApproved": "اعتمدت الموارد البشرية الطلب",
"hrm.leaveApprovals.hrRejected": "رفضت الموارد البشرية الطلب",
"hrm.leaveApprovals.empty": "لا توجد طلبات إجازة بانتظار اعتمادك",
"hrm.schedules.title": "جداول العمل",
"hrm.schedules.subtitle": "إدارة أنماط الدوام والجداول التشغيلية",
"hrm.schedules.caption": "جداول العمل",
"hrm.schedules.create": "إنشاء جدول",
"hrm.schedules.code": "الرمز",
"hrm.schedules.name": "الاسم",
"hrm.schedules.nameAr": "الاسم بالعربية",
"hrm.schedules.nameEn": "الاسم بالإنجليزية",
"hrm.schedules.shift": "الوردية",
"hrm.schedules.break": "الاستراحة",
"hrm.schedules.expected": "الوقت المتوقع",
"hrm.schedules.start": "البداية",
"hrm.schedules.end": "النهاية",
"hrm.schedules.breakMinutes": "دقائق الاستراحة",
"hrm.schedules.overnight": "عبر منتصف الليل",
"hrm.schedules.empty": "لا توجد جداول عمل",
"hrm.attendanceAdmin.title": "إدارة الحضور",
"hrm.attendanceAdmin.subtitle": "مراجعة سجلات الحضور مع الحفاظ على مصدر الحقيقة وسجل التتبع",
"hrm.attendanceAdmin.correctionUnavailable": "تصحيح الحضور غير متاح حتى تفعيل أمر التصحيح المعتمد؛ لن يُستخدم تسجيل الموظف كبديل إداري.",
"hrm.attendanceAdmin.caption": "سجلات الحضور الإدارية",
"hrm.attendanceAdmin.date": "التاريخ",
"hrm.attendanceAdmin.clockIn": "دخول",
"hrm.attendanceAdmin.clockOut": "خروج",
"hrm.attendanceAdmin.worked": "العمل المنجز",
"hrm.attendanceAdmin.state": "الحالة",
"hrm.attendanceAdmin.empty": "لا توجد سجلات حضور",
"hrm.leavePolicies.title": "سياسات الإجازات",
"hrm.leavePolicies.subtitle": "إعداد محايد للدولة دون افتراض استحقاقات نظامية غير معتمدة",
"hrm.leavePolicies.caption": "أنواع وسياسات الإجازات",
"hrm.leavePolicies.code": "الرمز",
"hrm.leavePolicies.nameAr": "الاسم بالعربية",
"hrm.leavePolicies.nameEn": "الاسم بالإنجليزية",
"hrm.leavePolicies.paid": "مدفوعة",
"hrm.leavePolicies.attachment": "يتطلب مرفقًا",
"hrm.leavePolicies.defaultDays": "الأيام الافتراضية",
"hrm.leavePolicies.configureViaPolicy": "يُضبط عبر السياسة",
"hrm.leavePolicies.yes": "نعم",
"hrm.leavePolicies.no": "لا",
"hrm.leavePolicies.empty": "لا توجد أنواع إجازات",
"hrm.attendanceReport.title": "تقرير الحضور الشهري",
"hrm.attendanceReport.subtitle": "ملخص الحضور والغياب والتأخر ضمن النطاق المصرح",
"hrm.attendanceReport.employee": "الموظف",
"hrm.attendanceReport.scheduledDays": "الأيام المجدولة",
"hrm.attendanceReport.workedMinutes": "دقائق العمل",
"hrm.attendanceReport.absent": "الغياب",
"hrm.attendanceReport.leave": "الإجازة",
"hrm.attendanceReport.late": "التأخر",
"hrm.attendanceReport.earlyDeparture": "الخروج المبكر",
"hrm.attendanceReport.missingPunch": "بصمة مفقودة",
"hrm.attendanceReport.status": "الحالة",
"hrm.attendanceReport.year": "السنة",
"hrm.attendanceReport.month": "الشهر",
"hrm.attendanceReport.empty": "لا توجد بيانات لهذه الفترة",

// English — exact same keys
"hrm.g2.landing.myWorkday": "My Workday",
"hrm.g2.landing.myTeam": "My Team",
"hrm.g2.landing.hrOperations": "HR Operations",
"hrm.g2.landing.attendance": "Attendance",
"hrm.g2.landing.timesheets": "My Timesheets",
"hrm.g2.landing.leave": "My Leave",
"hrm.g2.landing.teamAttendance": "Team Attendance",
"hrm.g2.landing.teamTimesheets": "Team Timesheets",
"hrm.g2.landing.leaveApprovals": "Leave Approvals",
"hrm.g2.landing.schedules": "Work Schedules",
"hrm.g2.landing.attendanceAdmin": "Attendance Administration",
"hrm.g2.landing.leavePolicies": "Leave Policies",
"hrm.g2.landing.attendanceReport": "Attendance Report",
"hrm.timesheets.title": "My Timesheets",
"hrm.timesheets.subtitle": "Review work periods and submit timesheets for approval",
"hrm.timesheets.period": "Period",
"hrm.timesheets.state": "State",
"hrm.timesheets.comment": "Comment",
"hrm.timesheets.actions": "Actions",
"hrm.timesheets.submit": "Submit",
"hrm.timesheets.submitting": "Submitting…",
"hrm.timesheets.submitted": "Timesheet submitted",
"hrm.timesheets.empty": "No timesheets",
"hrm.timesheets.team.title": "Team Timesheets",
"hrm.timesheets.team.subtitle": "Review submitted team timesheets and approve or reject them",
"hrm.timesheets.team.caption": "Timesheets awaiting approval",
"hrm.timesheets.team.approve": "Approve",
"hrm.timesheets.team.reject": "Reject",
"hrm.timesheets.team.approved": "Timesheet approved",
"hrm.timesheets.team.rejected": "Timesheet rejected",
"hrm.timesheets.team.empty": "No timesheets awaiting approval",
"hrm.teamAttendance.title": "Team Attendance",
"hrm.teamAttendance.subtitle": "Review team attendance within your authorized scope",
"hrm.teamAttendance.caption": "Team attendance records",
"hrm.teamAttendance.date": "Date",
"hrm.teamAttendance.clockIn": "Clock In",
"hrm.teamAttendance.clockOut": "Clock Out",
"hrm.teamAttendance.worked": "Worked",
"hrm.teamAttendance.state": "State",
"hrm.teamAttendance.empty": "No team attendance records",
"hrm.leaveApprovals.title": "Leave Approvals",
"hrm.leaveApprovals.subtitle": "Process requests currently assigned to your approval step",
"hrm.leaveApprovals.caption": "Leave requests awaiting approval",
"hrm.leaveApprovals.from": "From",
"hrm.leaveApprovals.to": "To",
"hrm.leaveApprovals.days": "Days",
"hrm.leaveApprovals.reason": "Reason",
"hrm.leaveApprovals.state": "State",
"hrm.leaveApprovals.actions": "Actions",
"hrm.leaveApprovals.managerApprove": "Manager Approve",
"hrm.leaveApprovals.managerReject": "Manager Reject",
"hrm.leaveApprovals.hrApprove": "HR Approve",
"hrm.leaveApprovals.hrReject": "HR Reject",
"hrm.leaveApprovals.managerApproved": "Manager approved; request escalated to HR",
"hrm.leaveApprovals.managerRejected": "Manager rejected the request",
"hrm.leaveApprovals.hrApproved": "HR approved the request",
"hrm.leaveApprovals.hrRejected": "HR rejected the request",
"hrm.leaveApprovals.empty": "No leave requests awaiting your approval",
"hrm.schedules.title": "Work Schedules",
"hrm.schedules.subtitle": "Manage operational work patterns and schedules",
"hrm.schedules.caption": "Work Schedules",
"hrm.schedules.create": "Create Schedule",
"hrm.schedules.code": "Code",
"hrm.schedules.name": "Name",
"hrm.schedules.nameAr": "Name (Arabic)",
"hrm.schedules.nameEn": "Name (English)",
"hrm.schedules.shift": "Shift",
"hrm.schedules.break": "Break",
"hrm.schedules.expected": "Expected",
"hrm.schedules.start": "Start",
"hrm.schedules.end": "End",
"hrm.schedules.breakMinutes": "Break minutes",
"hrm.schedules.overnight": "Overnight",
"hrm.schedules.empty": "No work schedules",
"hrm.attendanceAdmin.title": "Attendance Administration",
"hrm.attendanceAdmin.subtitle": "Review attendance records while preserving provenance and audit truth",
"hrm.attendanceAdmin.correctionUnavailable": "Attendance correction is unavailable until the canonical correction command is active; employee self-clock mutation is not used as an administrative fallback.",
"hrm.attendanceAdmin.caption": "Attendance records (administration)",
"hrm.attendanceAdmin.date": "Date",
"hrm.attendanceAdmin.clockIn": "Clock In",
"hrm.attendanceAdmin.clockOut": "Clock Out",
"hrm.attendanceAdmin.worked": "Worked",
"hrm.attendanceAdmin.state": "State",
"hrm.attendanceAdmin.empty": "No attendance records",
"hrm.leavePolicies.title": "Leave Policies",
"hrm.leavePolicies.subtitle": "Country-neutral configuration without unapproved statutory entitlement assumptions",
"hrm.leavePolicies.caption": "Leave types and policies",
"hrm.leavePolicies.code": "Code",
"hrm.leavePolicies.nameAr": "Name (Arabic)",
"hrm.leavePolicies.nameEn": "Name (English)",
"hrm.leavePolicies.paid": "Paid",
"hrm.leavePolicies.attachment": "Requires attachment",
"hrm.leavePolicies.defaultDays": "Default days",
"hrm.leavePolicies.configureViaPolicy": "Configure via policy",
"hrm.leavePolicies.yes": "Yes",
"hrm.leavePolicies.no": "No",
"hrm.leavePolicies.empty": "No leave types",
"hrm.attendanceReport.title": "Monthly Attendance Report",
"hrm.attendanceReport.subtitle": "Attendance, absence and lateness summary within the authorized scope",
"hrm.attendanceReport.employee": "Employee",
"hrm.attendanceReport.scheduledDays": "Scheduled Days",
"hrm.attendanceReport.workedMinutes": "Worked Minutes",
"hrm.attendanceReport.absent": "Absent",
"hrm.attendanceReport.leave": "Leave",
"hrm.attendanceReport.late": "Late",
"hrm.attendanceReport.earlyDeparture": "Early Departure",
"hrm.attendanceReport.missingPunch": "Missing Punch",
"hrm.attendanceReport.status": "Status",
"hrm.attendanceReport.year": "Year",
"hrm.attendanceReport.month": "Month",
"hrm.attendanceReport.empty": "No data for this period",
```

For launcher descriptions, reuse the corresponding page subtitle key rather than create duplicate prose keys.

- [ ] **Step 5: Prove GREEN and commit**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
npm run typecheck
cd ../..
git add apps/web/app/hr/components/hr-data-table.tsx apps/web/lib/i18n/locales/hrm-g2-i18n.ts apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "test(hr): lock G2 visual closure foundation"
```

Expected: focused test PASS and typecheck PASS before commit.

---

### Task 2: Capability-aware G2 launcher and mobile-safe HR navigation

**Files:**
- Create: `apps/web/app/hr/components/hr-g2-launcher.tsx`
- Create: `apps/web/app/hr/components/hr-g2-launcher.test.tsx`
- Modify: `apps/web/app/hr/page.tsx`
- Modify: `apps/web/app/hr/components/hr-workspace.tsx`
- Modify: `apps/web/app/hr/components/hr-workspace.test.tsx`
- Modify: `apps/web/app/hr/hr.module.css`

**Interfaces:**
- Consumes: `HRM_CAPABILITIES`, `useI18n()`, existing HR landing summary state.
- Produces: `HrG2Launcher({ capabilities }: { capabilities: string[] })`, `data-testid="g2-launcher-self|team|hr"`, and `data-testid="hr-landing-ready"`.

- [ ] **Step 1: Write failing launcher tests**

Test minimal SELF, TEAM and HR capability sets. Example SELF assertion:

```tsx
render(<HrG2Launcher capabilities={[
  HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW,
  HRM_CAPABILITIES.TIMESHEET_SELF_VIEW,
  HRM_CAPABILITIES.LEAVE_SELF_VIEW,
]} />);
expect(screen.getByTestId("g2-launcher-self")).toBeVisible();
expect(screen.queryByTestId("g2-launcher-team")).not.toBeInTheDocument();
expect(screen.queryByTestId("g2-launcher-hr")).not.toBeInTheDocument();
expect(screen.getByRole("link", { name: /الحضور والانصراف|Attendance/ })).toHaveAttribute("href", "/hr/attendance");
```

TEAM must expose `/hr/team-attendance`, `/hr/team-timesheets`, `/hr/leave/approvals`, `/hr/reports/attendance` only when matching capabilities exist. HR must expose `/hr/schedules`, `/hr/attendance/admin`, `/hr/leave/policies`, `/hr/leave/approvals`, `/hr/reports/attendance` only when matching capabilities exist.

- [ ] **Step 2: Prove RED**

```bash
cd apps/web
npx vitest run app/hr/components/hr-g2-launcher.test.tsx
```

Expected: module missing.

- [ ] **Step 3: Implement a data-driven launcher**

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

Filter each item with `capabilitiesAny.some(cap => capabilities.includes(cap))`; render a section only when at least one item remains. Use existing `HRM_CAPABILITIES` constants, never literal privilege semantics in backend calls.

- [ ] **Step 4: Integrate into `/hr`**

Keep existing employment/position/compliance summary cards. Insert `<HrG2Launcher capabilities={capabilities} />` before existing legacy quick links and put `data-testid="hr-landing-ready"` on the fully loaded landing content.

- [ ] **Step 5: Make workspace navigation narrow-screen safe**

Use:

```css
.workspaceNav {
  margin-block-end: 1.5rem;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  scrollbar-width: thin;
}
.workspaceNavList { min-inline-size: max-content; }
@media (min-width: 48rem) {
  .workspaceNav { overflow-x: visible; }
  .workspaceNavList { min-inline-size: 0; flex-wrap: wrap; }
}
```

Add launcher styles with SDS tokens and `grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr));`.

- [ ] **Step 6: Prove GREEN**

```bash
cd apps/web
npx vitest run app/hr/components/hr-g2-launcher.test.tsx app/hr/components/hr-workspace.test.tsx
npm run typecheck
```

- [ ] **Step 7: Commit**

```bash
cd ../..
git add apps/web/app/hr/page.tsx apps/web/app/hr/components/hr-g2-launcher.tsx apps/web/app/hr/components/hr-g2-launcher.test.tsx apps/web/app/hr/components/hr-workspace.tsx apps/web/app/hr/components/hr-workspace.test.tsx apps/web/app/hr/hr.module.css
git commit -m "feat(hr): expose G2 work areas on HR landing"
```

---

### Task 3: Employee G2 UI closure

**Files:**
- Modify: `apps/web/app/hr/attendance/page.tsx`
- Modify: `apps/web/app/hr/timesheets/page.tsx`
- Modify: `apps/web/app/hr/leave/page.tsx`
- Modify: `apps/web/app/hr/g2-visual-closure.regression.test.ts`
- Modify: `apps/web/app/hr/hr.module.css`

**Interfaces:**
- Consumes: current `hrG2Api` methods and Task 1 dictionary.
- Produces: localized Employee surfaces with stable ready selectors and locale-aware time formatting.

- [ ] **Step 1: Add failing source guards**

Extend the regression test so Timesheets must contain `t("hrm.timesheets.title")`, `t("hrm.timesheets.period")`, `t("hrm.timesheets.submit")`, and `data-testid="timesheets-ready"`; keep Attendance `attendance-ready` and Leave `leave-ready` assertions.

- [ ] **Step 2: Prove RED**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

- [ ] **Step 3: Replace all Timesheet literals with dictionary keys**

Keep existing `DRAFT -> submit` behavior and `hrG2Api.submitTimesheet()` unchanged. Use `t("hrm.timesheets.submitted")` for success and `t("hrm.timesheets.empty")` for empty state.

- [ ] **Step 4: Normalize Employee page visual hierarchy without changing test IDs**

```tsx
<header className={styles.g2PageHeader}>
  <div>
    <h1 data-testid="g2-page-title">{t("...")}</h1>
    <p className={styles.kpiHint}>{t("...subtitle")}</p>
  </div>
</header>
```

Retain functional selectors `attendance-clock-in`, `attendance-clock-out`, `request-leave-toggle`, `leave-request-form`, `leave-submit`, `attendance-ready`, `timesheets-ready`, `leave-ready`.

- [ ] **Step 5: Fix locale-aware time formatting**

Attendance uses `const { t, locale } = useI18n()` and `toLocaleTimeString(locale === "ar" ? "ar-SA" : "en-US", { hour: "2-digit", minute: "2-digit" })` rather than hard-coded `"ar"`.

- [ ] **Step 6: Prove GREEN and commit**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
npm run typecheck
cd ../..
git add apps/web/app/hr/attendance/page.tsx apps/web/app/hr/timesheets/page.tsx apps/web/app/hr/leave/page.tsx apps/web/app/hr/g2-visual-closure.regression.test.ts apps/web/app/hr/hr.module.css
git commit -m "feat(hr): close G2 employee visual surfaces"
```

---

### Task 4: Manager/Team G2 UI closure

**Files:**
- Modify: `apps/web/app/hr/team-attendance/page.tsx`
- Modify: `apps/web/app/hr/team-timesheets/page.tsx`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Modify: `apps/web/app/hr/reports/attendance/page.tsx`
- Modify: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Consumes: TEAM-scoped G2 APIs and capabilities.
- Produces: localized team surfaces with `team-attendance-ready`, `team-timesheets-ready`, `leave-approvals-ready`, `attendance-report-ready`.

- [ ] **Step 1: Add failing source guards**

Require each ready selector and these calls in source: `t("hrm.teamAttendance.title")`, `t("hrm.timesheets.team.title")`, `t("hrm.leaveApprovals.title")`, `t("hrm.attendanceReport.title")`. Also forbid the legacy hard-coded headings in these files.

- [ ] **Step 2: Prove RED**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

- [ ] **Step 3: Localize Team Attendance and Team Timesheets**

Replace headings, table headers, captions, actions, success and empty copy with Task 1 keys. Keep `listTeamAttendance()`, `listTeamTimesheets("SUBMITTED")`, approve/reject calls and capability checks unchanged. Use active locale for clock-time rendering.

- [ ] **Step 4: Localize Leave Approvals while preserving Workflow Y2 contract**

Keep states `PENDING_MANAGER` and `PENDING_HR`, API calls and request-ID selectors unchanged:

```tsx
data-testid={`manager-approve-${r.id}`}
data-testid={`manager-reject-${r.id}`}
data-testid={`hr-approve-${r.id}`}
data-testid={`hr-reject-${r.id}`}
```

Only presentation copy changes. Manager-only identities still render zero HR-only buttons.

- [ ] **Step 5: Localize Attendance Report**

Use `hrm.attendanceReport.*` for every label/caption/empty state while preserving:

```ts
canAdmin
  ? hrG2Api.adminMonthlyAttendanceReport(year, month)
  : hrG2Api.teamMonthlyAttendanceReport(year, month)
```

- [ ] **Step 6: Prove GREEN and commit**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
npm run typecheck
cd ../..
git add apps/web/app/hr/team-attendance/page.tsx apps/web/app/hr/team-timesheets/page.tsx apps/web/app/hr/leave/approvals/page.tsx apps/web/app/hr/reports/attendance/page.tsx apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "feat(hr): close G2 manager visual surfaces"
```

---

### Task 5: HR Administration G2 UI closure

**Files:**
- Modify: `apps/web/app/hr/schedules/page.tsx`
- Modify: `apps/web/app/hr/attendance/admin/page.tsx`
- Modify: `apps/web/app/hr/leave/policies/page.tsx`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Modify: `apps/web/app/hr/reports/attendance/page.tsx`
- Modify: `apps/web/app/hr/g2-visual-closure.regression.test.ts`
- Modify: `apps/web/app/hr/hr.module.css`

**Interfaces:**
- Consumes: HR-admin G2 APIs/capabilities.
- Produces: localized HR-admin surfaces with `schedules-ready`, `attendance-admin-ready`, `leave-policies-ready`, plus shared approval/report ready selectors.

- [ ] **Step 1: Add failing admin guards**

Require the three ready selectors and `t("hrm.schedules.title")`, `t("hrm.attendanceAdmin.title")`, `t("hrm.leavePolicies.title")`. Forbid legacy English headings and policy-column literals.

- [ ] **Step 2: Prove RED**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

- [ ] **Step 3: Localize Work Schedules**

Use `hrm.schedules.*` for all page, table and form copy. Preserve the existing schedule API. The current `timezone: "Asia/Riyadh"` request value remains operational configuration only; do not present it as a statutory/legal default.

- [ ] **Step 4: Localize Attendance Administration**

Use `hrm.attendanceAdmin.*`. Preserve the fail-closed rule that an unavailable canonical correction command is not replaced by employee self-clock mutation.

- [ ] **Step 5: Localize Leave Policies**

Use `hrm.leavePolicies.*`; render booleans with localized yes/no, and render null default days as `configureViaPolicy`. Never invent entitlement values.

- [ ] **Step 6: Add responsive form/filter rules**

```css
.g2PageHeader { display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap; margin-block-end: 1rem; }
.g2FormGrid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr)); gap: .75rem; }
.g2FilterBar { display: flex; flex-wrap: wrap; gap: .75rem; align-items: end; }
```

Use existing SDS variables for surfaces/borders/text and preserve the existing `.hrTableWrap { overflow-x: auto; }` narrow-screen containment.

- [ ] **Step 7: Run full frontend gate and commit**

```bash
cd apps/web
npm test
npm run typecheck
npm run lint
npm run build
cd ../..
git add apps/web/app/hr/schedules/page.tsx apps/web/app/hr/attendance/admin/page.tsx apps/web/app/hr/leave/policies/page.tsx apps/web/app/hr/leave/approvals/page.tsx apps/web/app/hr/reports/attendance/page.tsx apps/web/app/hr/g2-visual-closure.regression.test.ts apps/web/app/hr/hr.module.css apps/web/lib/i18n/locales/hrm-g2-i18n.ts
git commit -m "feat(hr): close G2 HR administration visual surfaces"
```

Expected: unit suite PASS, typecheck 0 errors, lint gate PASS, production build PASS.

---

### Task 6: Role-by-device visual evidence suite

**Files:**
- Create: `apps/web/e2e/g2-visual-helpers.ts`
- Create: `apps/web/e2e/g2-visual.spec.ts`
- Create: `apps/web/playwright-g2-visual.config.ts`
- Modify: `apps/web/playwright.standard.config.ts`
- Modify: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Consumes: `loginThroughUi`, `roleEmail`, ready selectors from Tasks 2–5.
- Produces: exact-SHA screenshots and `manifest.ndjson` under `test-results/g2-visual-evidence/`.

- [ ] **Step 1: Add failing harness guards**

Require `playwright-g2-visual.config.ts`, project names `g2-visual-desktop` / `g2-visual-mobile`, `screenshot: "on"`, and all three role names in the visual spec.

- [ ] **Step 2: Prove RED**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
```

- [ ] **Step 3: Implement fail-closed helper interfaces**

```ts
export type G2Role = "employee" | "manager" | "hr";
export interface VisualSurface { route: string; readyTestId: string; }
export async function assertVisualSurface(page: Page, surface: VisualSurface): Promise<void>;
export async function captureVisualEvidence(page: Page, role: G2Role, route: string, projectName: string): Promise<void>;
```

`assertVisualSurface` waits for the ready element, fails on captured `pageerror`, fails on HR API response status >=400, and asserts:

```ts
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
expect(overflow, `page-level horizontal overflow on ${surface.route}`).toBeLessThanOrEqual(1);
```

`captureVisualEvidence` must use `const candidateSha = process.env.GITHUB_HEAD_SHA ?? process.env.GITHUB_SHA ?? "local";` — **head SHA first** — then write a full-page PNG and append exactly:

```ts
{ sha: candidateSha, role, viewport: projectName, route, screenshot: path, result: "PASS" }
```

to `test-results/g2-visual-evidence/manifest.ndjson`.

- [ ] **Step 4: Use exact role route matrices**

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

Each role test logs in via the real UI, asserts `roleEmail(role)`, visits each route, asserts the role-appropriate launcher/nav item, calls `assertVisualSurface`, then captures evidence. Before login, use `page.addInitScript(() => localStorage.setItem("snad.locale", "ar"));` so primary visual proof exercises Arabic/RTL.

- [ ] **Step 5: Create exact two-project config**

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

- [ ] **Step 6: Isolate from generic Playwright and prove routing**

Add `"**/g2-visual.spec.ts"` to `playwright.standard.config.ts` `testIgnore`.

```bash
cd apps/web
STD_COUNT=$(npx playwright test --config=playwright.standard.config.ts --list 2>&1 | grep -c "g2-visual.spec.ts" || true)
test "$STD_COUNT" = "0"
npx playwright test --config=playwright-g2-visual.config.ts --list
```

Expected: dedicated list contains Employee, Manager and HR in both projects.

- [ ] **Step 7: Prove GREEN and commit**

```bash
npx vitest run app/hr/g2-visual-closure.regression.test.ts
npm run typecheck
cd ../..
git add apps/web/e2e/g2-visual-helpers.ts apps/web/e2e/g2-visual.spec.ts apps/web/playwright-g2-visual.config.ts apps/web/playwright.standard.config.ts apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "test(hr): add G2 role-device visual evidence matrix"
```

---

### Task 7: Generalized PostgreSQL Direct HRM Human Preview

**Files:**
- Modify: `.github/workflows/hrm-human-preview.yml`
- Modify: `.github/workflows/g2-authenticated-acceptance.yml`
- Modify: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Consumes: host-native DB pattern and seed from existing G2 functional workflow, Task 6 visual config.
- Produces: `Full-stack HRM human preview` check and artifact `g2-visual-evidence-<exact-head>`.

- [ ] **Step 1: Add failing workflow guards**

```ts
const workflow = readFileSync(resolve(REPO_ROOT, ".github/workflows/hrm-human-preview.yml"), "utf8");
expect(workflow).not.toContain("github.event.pull_request.number == 914");
expect(workflow).not.toMatch(/services:\s*\n\s+postgres:/);
expect(workflow).toContain("playwright-g2-visual.config.ts");
expect(workflow).toContain("actions/upload-artifact@v4");
expect(workflow).toContain("if-no-files-found: error");
```

- [ ] **Step 2: Prove RED**

Historical workflow must fail both the PR-914 and container-service guards.

- [ ] **Step 3: Provision host-native PostgreSQL Direct**

Use exact candidate checkout and fail closed:

```yaml
- uses: actions/checkout@v4
  with:
    ref: ${{ github.event.pull_request.head.sha || github.sha }}
    fetch-depth: 0
- name: Bind exact candidate SHA
  shell: bash
  run: |
    set -euo pipefail
    CANDIDATE_SHA="${{ github.event.pull_request.head.sha || github.sha }}"
    test "$(git rev-parse HEAD)" = "$CANDIDATE_SHA"
    echo "GITHUB_HEAD_SHA=$CANDIDATE_SHA" >> "$GITHUB_ENV"
```

Start host PostgreSQL with `sudo systemctl start postgresql`; create an ephemeral credential using `openssl rand -hex 24`; create a login role with `NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`; create the preview database owned by that role; apply Flyway through Maven; execute `apps/sanad-platform/src/test/resources/sql/g2-acceptance-seed.sql` with the runtime password variable. No `services:` block is permitted.

- [ ] **Step 4: Start exact backend/frontend and run visual suite**

Use JDK 21 + Node 24, build the backend JAR and Next.js app, start Spring Boot on 8080 and Next.js on 3001, poll `/actuator/health` and the frontend root, then:

```bash
cd apps/web
npx playwright test --config=playwright-g2-visual.config.ts
```

The stateful `g2-authenticated.spec.ts` stays in its own existing workflow and is not multiplied into this visual matrix.

- [ ] **Step 5: Publish exact-SHA artifacts even on diagnostic failure without soft-passing the job**

After Playwright, upload the report/evidence step with `if: always()` and `if-no-files-found: error`, while the Playwright command itself remains fail-fast. Artifact name:

```yaml
name: g2-visual-evidence-${{ env.GITHUB_HEAD_SHA }}
path: |
  apps/web/test-results/g2-visual-evidence
  apps/web/playwright-g2-visual-report
if-no-files-found: error
```

- [ ] **Step 6: Expand relevant path triggers**

`hrm-human-preview.yml` triggers on HR backend/web/e2e/config/workflow changes. Add Task 6 visual helper/spec/config paths to `g2-authenticated-acceptance.yml` triggers so shared G2 frontend/acceptance changes recertify functional E2E too.

- [ ] **Step 7: Prove local source/frontend gates and commit**

```bash
cd apps/web
npx vitest run app/hr/g2-visual-closure.regression.test.ts
npm test
npm run typecheck
npm run lint
npm run build
cd ../..
git add .github/workflows/hrm-human-preview.yml .github/workflows/g2-authenticated-acceptance.yml apps/web/app/hr/g2-visual-closure.regression.test.ts
git commit -m "ci(hr): generalize G2 human visual preview"
```

---

### Task 8: Corrective PR exact-head certification and merge

**Files:** no new product files unless a gate reveals a test-proven defect.

**Interfaces:**
- Consumes: final Task 7 branch head.
- Produces: merged corrective PR with exact-head CI and independent approval.

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

- [ ] **Step 2: Open one corrective PR**

Title:

```text
fix(hr): close G2 product UI and visual certification
```

Body records actual observed values and these invariants:

```text
BASELINE_MAIN=<observed 40-char main SHA>
CANDIDATE_SHA=<observed 40-char PR head SHA>
RLS_RBAC_TENANT_ISOLATION=UNCHANGED
WORKFLOW_Y2_OWNERSHIP=UNCHANGED
POSTGRESQL_DIRECT=REQUIRED
VISUAL_MATRIX=Employee+Manager+HR x Desktop+Mobile
RELEASE_CONTROL_PR_1145=SUPERSEDED_AFTER_CORRECTIVE_MERGE
MERGE_AUTHORIZATION=NO
FINAL_GATE=OPEN
```

The angle-bracket fields are filled with live GitHub values at PR creation; they are not committed placeholders.

- [ ] **Step 3: Certify exact head only**

Require terminal PASS on the current SHA for at least Maven Test Suite, PostgreSQL Acceptance Tests, G2 Authenticated Acceptance, Full-stack HRM human preview, Security Gate Summary, Pre-Merge Operational Smoke Summary, Next.js/web build and Generic Playwright. Any failure, pending, skipped mandatory or stale-head result blocks merge.

- [ ] **Step 4: Inspect artifact contents**

Download `g2-visual-evidence-<exact-head>` and verify:

```text
Employee Desktop = 4 routes
Employee Mobile  = 4 routes
Manager Desktop  = 5 routes
Manager Mobile   = 5 routes
HR Desktop       = 6 routes
HR Mobile        = 6 routes
all manifest rows: sha=<exact-head>, result=PASS
```

If the seeded Manager lacks `ATTENDANCE_TEAM_VIEW`, stop and reconcile the seed/capability contract; do not silently reduce expected evidence.

- [ ] **Step 5: Obtain independent approval on exact head**

Require an `APPROVED` review from a GitHub identity different from PR author with review commit ID equal to the current head. Any new commit invalidates it.

- [ ] **Step 6: Merge only while all invariants still match**

Immediately re-read PR head, main, required checks and review SHA. If unchanged and user merge authorization remains active, squash-merge. If any invariant changed, STOP and recertify.

---

### Task 9: Rebind release control and complete production final closure

**Files:** one new inert `.release/` authorization file on a fresh release-control branch only.

**Interfaces:**
- Consumes: corrected squash-merge exact main SHA.
- Produces: canonical production release and final G2 closure evidence.

- [ ] **Step 1: Verify corrective merge is exact `main` and post-merge checks are terminal**

Record actual `CORRECTIVE_MERGE_SHA` and do not advance with a running/failing required post-merge gate.

- [ ] **Step 2: Close #1145 as superseded**

Comment that its baseline `eb19a2dd9b476bc3a5af0c9b0755489fc1865c0f` is stale after the corrective merge, then close it unmerged. Do not rewrite it into a mixed runtime/release-control PR.

- [ ] **Step 3: Create fresh inert Release-Control PR from corrected main**

Only one new `.release/` record changes, containing the corrected exact main SHA and:

```text
Runtime application code change = NONE
Database migration change        = NONE
Security/RBAC/RLS change         = NONE
Workflow Y2 ownership            = UNCHANGED
PostgreSQL Direct                = REQUIRED
```

- [ ] **Step 4: Certify and merge release control**

Require terminal exact-head CI, no main drift, exact-head independent approval, and a protected squash merge message containing exactly:

```text
PRODUCTION-RELEASE-AUTHORIZED
```

- [ ] **Step 5: Run canonical production release**

Use only repository `production-release.yml` with `rollback_on_failure=true`. No manual Render/Vercel deployment substitutes for this workflow.

- [ ] **Step 6: Verify production closure evidence**

Require exact-image parity, backend readiness, Flyway/runtime invariants, RLS/tenant isolation/RBAC gates, Workflow Y2 orchestrator PASS, G2 authenticated production smoke, read-oriented Employee/Manager/HR Desktop+Mobile production visual smoke, Vercel Control Plane/BFF evidence and post-merge verification PASS.

- [ ] **Step 7: Declare closure only from observed evidence**

```text
G2_PRODUCT_UI                = PASS
G2_I18N_PARITY               = PASS
G2_NAV_DISCOVERABILITY       = PASS
G2_AUTHENTICATED_E2E         = PASS
G2_VISUAL_EMPLOYEE_DESKTOP   = PASS
G2_VISUAL_EMPLOYEE_MOBILE    = PASS
G2_VISUAL_MANAGER_DESKTOP    = PASS
G2_VISUAL_MANAGER_MOBILE     = PASS
G2_VISUAL_HR_DESKTOP         = PASS
G2_VISUAL_HR_MOBILE          = PASS
HRM_HUMAN_PREVIEW            = PASS
EXACT_HEAD_CI                = PASS
INDEPENDENT_APPROVAL         = PASS
CORRECTIVE_PR_MERGED         = YES
RELEASE_CONTROL_REBOUND      = YES
PRODUCTION_RELEASE           = PASS
POST_MERGE_VERIFICATION      = PASS
PRODUCTION_VISUAL_SMOKE      = PASS
G2_FINAL_CLOSURE             = PASS
```

Any failed, skipped mandatory, queued, stale-head or unverified production gate keeps `G2_FINAL_CLOSURE = OPEN`.

---

## Plan Self-Review

- **Spec coverage:** landing/discoverability, all 10 G2 routes, AR/EN parity, RTL/LTR direction, mobile overflow, functional-vs-visual separation, generalized PostgreSQL Direct preview, success artifacts, exact-head review, corrective merge, release-control rebound and production closure each map to Tasks 1–9.
- **Placeholder scan:** no implementation step contains `TBD`, `TODO`, "implement later", or an unspecified error-handling instruction. Live SHA fields appear only in the PR-operation task and are explicitly populated from GitHub at execution time.
- **Type consistency:** `G2Role`, `VisualSurface`, `assertVisualSurface`, `captureVisualEvidence`, launcher interfaces and ready selectors are defined once and consumed with identical names in later tasks.
- **Review Focus coverage:** locale direction is tested in Task 1; capability isolation in Task 2; mobile overflow in Task 6; empty queues through ready/empty rendering in Tasks 3–6; exact-head provenance in Tasks 6–9.
