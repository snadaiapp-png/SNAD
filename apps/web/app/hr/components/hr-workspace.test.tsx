// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import type React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { HrWorkspace } from "./hr-workspace";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";

const { useRouterMock } = vi.hoisted(() => ({ useRouterMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: useRouterMock,
  usePathname: () => "/hr",
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

const G2_LABELS: Record<string, string> = {
  "hrm.g2.landing.attendance": "حضوري",
  "hrm.g2.landing.timesheets": "سجلات وقتي",
  "hrm.g2.landing.leave": "إجازاتي",
  "hrm.g2.landing.teamAttendance": "حضور الفريق",
  "hrm.g2.landing.teamTimesheets": "سجلات وقت الفريق",
  "hrm.g2.landing.leaveApprovals": "اعتمادات الإجازات",
  "hrm.g2.landing.schedules": "جداول العمل",
  "hrm.g2.landing.attendanceAdmin": "إدارة الحضور",
  "hrm.g2.landing.leavePolicies": "سياسات الإجازات",
  "hrm.g2.landing.attendanceReport": "تقرير الحضور",
};

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    locale: "ar",
    direction: "rtl",
    setLocale: vi.fn(),
    t: (key: string) => G2_LABELS[key] ?? key,
  }),
}));

const FULL_CAPS = Object.values(HRM_CAPABILITIES);
const NO_HR_CAPS: string[] = [];

afterEach(() => cleanup());

describe("HrWorkspace navigation", () => {
  it("renders every authoritative workspace link for a fully privileged user", () => {
    render(
      <HrWorkspace capabilities={FULL_CAPS} activeHref="/hr">
        <p>المحتوى</p>
      </HrWorkspace>,
    );

    const expected = [
      ["/hr", "الرئيسية"],
      ["/hr/employees", "الموظفون"],
      ["/hr/org-structure", "الهيكل التنظيمي"],
      ["/hr/jobs", "الوظائف"],
      ["/hr/positions", "المناصب"],
      ["/hr/assignments", "الإسنادات"],
      ["/hr/compliance", "الالتزام"],
      ["/hr/attendance", "حضوري"],
      ["/hr/timesheets", "سجلات وقتي"],
      ["/hr/leave", "إجازاتي"],
      ["/hr/team-attendance", "حضور الفريق"],
      ["/hr/team-timesheets", "سجلات وقت الفريق"],
      ["/hr/leave/approvals", "اعتمادات الإجازات"],
      ["/hr/schedules", "جداول العمل"],
      ["/hr/attendance/admin", "إدارة الحضور"],
      ["/hr/leave/policies", "سياسات الإجازات"],
      ["/hr/reports/attendance", "تقرير الحضور"],
      ["/hr/execution", "لوحة التنفيذ"],
    ] as const;

    for (const [href, name] of expected) {
      expect(screen.getByRole("link", { name })).toHaveAttribute("href", href);
    }
  });

  it("shows employee self-service links only for SELF capabilities", () => {
    render(
      <HrWorkspace capabilities={[
        HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW,
        HRM_CAPABILITIES.TIMESHEET_SELF_VIEW,
        HRM_CAPABILITIES.LEAVE_SELF_VIEW,
      ]} activeHref="/hr/attendance">
        <p>المحتوى</p>
      </HrWorkspace>,
    );

    expect(screen.getByRole("link", { name: "حضوري" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "سجلات وقتي" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "إجازاتي" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "حضور الفريق" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "إدارة الحضور" })).not.toBeInTheDocument();
  });

  it("shows manager team links without exposing HR administration", () => {
    render(
      <HrWorkspace capabilities={[
        HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW,
        HRM_CAPABILITIES.TIMESHEET_TEAM_APPROVE,
        HRM_CAPABILITIES.LEAVE_TEAM_APPROVE,
      ]} activeHref="/hr/team-attendance">
        <p>المحتوى</p>
      </HrWorkspace>,
    );

    expect(screen.getByRole("link", { name: "حضور الفريق" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "سجلات وقت الفريق" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "اعتمادات الإجازات" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "جداول العمل" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "إدارة الحضور" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "سياسات الإجازات" })).not.toBeInTheDocument();
  });

  it("shows HR administration surfaces without requiring TEAM scope", () => {
    render(
      <HrWorkspace capabilities={[
        HRM_CAPABILITIES.ATTENDANCE_ADMIN,
        HRM_CAPABILITIES.ATTENDANCE_CORRECT,
        HRM_CAPABILITIES.LEAVE_POLICY_ADMIN,
        HRM_CAPABILITIES.LEAVE_HR_APPROVE,
      ]} activeHref="/hr/attendance/admin">
        <p>المحتوى</p>
      </HrWorkspace>,
    );

    expect(screen.getByRole("link", { name: "جداول العمل" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "إدارة الحضور" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "سياسات الإجازات" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "تقرير الحضور" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "اعتمادات الإجازات" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "حضور الفريق" })).not.toBeInTheDocument();
  });

  it("marks only the most specific nested route as active", () => {
    render(
      <HrWorkspace capabilities={FULL_CAPS} activeHref="/hr/attendance/admin">
        <p>المحتوى</p>
      </HrWorkspace>,
    );

    expect(screen.getByRole("link", { name: "إدارة الحضور" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "حضوري" })).not.toHaveAttribute("aria-current");
  });

  it("keeps the execution dashboard reachable regardless of capabilities", () => {
    render(
      <HrWorkspace capabilities={NO_HR_CAPS} activeHref="/hr">
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    expect(screen.getByRole("link", { name: "لوحة التنفيذ" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "الرئيسية" })).toBeInTheDocument();
  });

  it("hides capability-gated sections from users without the matching UX capability", () => {
    render(
      <HrWorkspace capabilities={NO_HR_CAPS} activeHref="/hr">
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    expect(screen.queryByRole("link", { name: "الموظفون" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "الالتزام" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "حضوري" })).not.toBeInTheDocument();
  });

  it("exposes navigation as a labelled landmark and renders children in main", () => {
    render(
      <HrWorkspace capabilities={FULL_CAPS} activeHref="/hr">
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    expect(screen.getByRole("navigation", { name: "أقسام الموارد البشرية" })).toBeInTheDocument();
    expect(screen.getByRole("main")).toContainElement(screen.getByText("المحتوى"));
  });
});
