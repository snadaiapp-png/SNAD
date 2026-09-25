// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import type React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { HrG2Launcher } from "./hr-g2-launcher";

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    locale: "ar",
    direction: "rtl",
    setLocale: vi.fn(),
    t: (key: string) => ({
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
      "hrm.attendance.subtitle": "تسجيل الدخول والخروج وعرض السجل الشهري",
      "hrm.timesheets.subtitle": "مراجعة فترات العمل وإرسال السجلات للاعتماد",
      "hrm.leave.subtitle": "طلب إجازة وعرض الرصيد والموافقات",
      "hrm.teamAttendance.subtitle": "متابعة حضور أعضاء الفريق ضمن نطاق الصلاحية",
      "hrm.timesheets.team.subtitle": "مراجعة سجلات الوقت المرسلة للفريق واعتمادها أو رفضها",
      "hrm.leaveApprovals.subtitle": "معالجة الطلبات الواقعة ضمن خطوة اعتمادك الحالية",
      "hrm.schedules.subtitle": "إدارة أنماط الدوام والجداول التشغيلية",
      "hrm.attendanceAdmin.subtitle": "مراجعة سجلات الحضور مع الحفاظ على مصدر الحقيقة وسجل التتبع",
      "hrm.leavePolicies.subtitle": "إعداد محايد للدولة دون افتراض استحقاقات نظامية غير معتمدة",
      "hrm.attendanceReport.subtitle": "ملخص الحضور والغياب والتأخر ضمن النطاق المصرح",
    } as Record<string, string>)[key] ?? key,
  }),
}));

afterEach(() => cleanup());

describe("HrG2Launcher capability visibility", () => {
  it("shows only employee self-service work areas for SELF capabilities", () => {
    render(<HrG2Launcher capabilities={[
      HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW,
      HRM_CAPABILITIES.TIMESHEET_SELF_VIEW,
      HRM_CAPABILITIES.LEAVE_SELF_VIEW,
    ]} />);

    expect(screen.getByTestId("g2-launcher-self")).toBeVisible();
    expect(screen.queryByTestId("g2-launcher-team")).not.toBeInTheDocument();
    expect(screen.queryByTestId("g2-launcher-hr")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /الحضور والانصراف/ })).toHaveAttribute("href", "/hr/attendance");
    expect(screen.getByRole("link", { name: /سجلات وقتي/ })).toHaveAttribute("href", "/hr/timesheets");
    expect(screen.getByRole("link", { name: /إجازاتي/ })).toHaveAttribute("href", "/hr/leave");
  });

  it("shows only authorized manager/team routes", () => {
    render(<HrG2Launcher capabilities={[
      HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW,
      HRM_CAPABILITIES.TIMESHEET_TEAM_APPROVE,
      HRM_CAPABILITIES.LEAVE_TEAM_APPROVE,
    ]} />);

    expect(screen.queryByTestId("g2-launcher-self")).not.toBeInTheDocument();
    expect(screen.getByTestId("g2-launcher-team")).toBeVisible();
    expect(screen.queryByTestId("g2-launcher-hr")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /حضور الفريق/ })).toHaveAttribute("href", "/hr/team-attendance");
    expect(screen.getByRole("link", { name: /سجلات وقت الفريق/ })).toHaveAttribute("href", "/hr/team-timesheets");
    expect(screen.getByRole("link", { name: /اعتمادات الإجازات/ })).toHaveAttribute("href", "/hr/leave/approvals");
    expect(screen.getByRole("link", { name: /تقرير الحضور/ })).toHaveAttribute("href", "/hr/reports/attendance");
  });

  it("shows HR administration routes without exposing team-only routes", () => {
    render(<HrG2Launcher capabilities={[
      HRM_CAPABILITIES.ATTENDANCE_ADMIN,
      HRM_CAPABILITIES.ATTENDANCE_CORRECT,
      HRM_CAPABILITIES.LEAVE_POLICY_ADMIN,
      HRM_CAPABILITIES.LEAVE_HR_APPROVE,
    ]} />);

    expect(screen.queryByTestId("g2-launcher-self")).not.toBeInTheDocument();
    expect(screen.queryByTestId("g2-launcher-team")).not.toBeInTheDocument();
    expect(screen.getByTestId("g2-launcher-hr")).toBeVisible();
    expect(screen.getByRole("link", { name: /جداول العمل/ })).toHaveAttribute("href", "/hr/schedules");
    expect(screen.getByRole("link", { name: /إدارة الحضور/ })).toHaveAttribute("href", "/hr/attendance/admin");
    expect(screen.getByRole("link", { name: /سياسات الإجازات/ })).toHaveAttribute("href", "/hr/leave/policies");
    expect(screen.getByRole("link", { name: /اعتمادات الإجازات/ })).toHaveAttribute("href", "/hr/leave/approvals");
    expect(screen.getByRole("link", { name: /تقرير الحضور/ })).toHaveAttribute("href", "/hr/reports/attendance");
    expect(screen.queryByRole("link", { name: /حضور الفريق/ })).not.toBeInTheDocument();
  });
});
