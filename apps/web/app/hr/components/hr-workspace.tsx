"use client";

/**
 * Shared HR workspace shell — renders through the SHARED SNAD module shell
 * primitive (components/sds/module) so HR visually belongs to the same
 * product as CRM (SNAD Module Visual Contract, G3 Task 6).
 *
 * Preserved invariants from the pre-unification workspace:
 *   - capability-driven link visibility (backend authorization remains
 *     authoritative; this is UX-only)
 *   - most-specific active route resolution (aria-current="page")
 *   - route-scoped message IDs with the translator prop taking priority and
 *     Arabic defaults preserving provider-free legacy callers
 *   - navigation landmark + main landmark semantics
 *
 * Structural change (authorized by G3 Task 6): the flat horizontal link list
 * is replaced by the shared sidebar shell with sectioned, icon-based,
 * capability-gated navigation, persistent sticky header, user identity,
 * language toggle, and workspace/logout actions.
 */

import { useContext, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { I18nContext } from "@/lib/i18n/I18nProvider";
import {
  SnadModuleShell,
  AdminIcon,
  ApprovalsIcon,
  AssignmentsIcon,
  AttendanceIcon,
  ComplianceIcon,
  ExecutionIcon,
  GoalsIcon,
  InboxIcon,
  JobsIcon,
  LeaveIcon,
  OnboardingIcon,
  OrgIcon,
  OverviewIcon,
  PeopleIcon,
  PolicyIcon,
  PositionsIcon,
  RecruitmentIcon,
  ReportsIcon,
  ReviewsIcon,
  SchedulesIcon,
  TeamIcon,
  TimesheetIcon,
  type SnadModuleNavSection,
} from "@/components/sds/module";

export interface HrWorkspaceLink {
  href: string;
  label: string;
  labelMessageId: string;
  Icon?: typeof OverviewIcon;
  capability?: string;
  capabilitiesAny?: readonly string[];
}

export const HR_WORKSPACE_LINKS: HrWorkspaceLink[] = [
  // OVERVIEW / PEOPLE
  { href: "/hr", label: "الرئيسية", labelMessageId: "hrm.g2.workspace.nav.home", Icon: OverviewIcon },
  { href: "/hr/employees", label: "الموظفون", labelMessageId: "hrm.g2.workspace.nav.employees", Icon: PeopleIcon, capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  { href: "/hr/org-structure", label: "الهيكل التنظيمي", labelMessageId: "hrm.g2.workspace.nav.organizationStructure", Icon: OrgIcon, capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/jobs", label: "الوظائف", labelMessageId: "hrm.g2.workspace.nav.jobs", Icon: JobsIcon, capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/positions", label: "المناصب", labelMessageId: "hrm.g2.workspace.nav.positions", Icon: PositionsIcon, capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/assignments", label: "الإسنادات", labelMessageId: "hrm.g2.workspace.nav.assignments", Icon: AssignmentsIcon, capability: HRM_CAPABILITIES.ASSIGNMENT_VIEW },
  { href: "/hr/compliance", label: "الالتزام", labelMessageId: "hrm.g2.workspace.nav.compliance", Icon: ComplianceIcon, capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  // TALENT
  { href: "/hr/recruitment", label: "التوظيف", labelMessageId: "hrm.g2.workspace.nav.recruitment", Icon: RecruitmentIcon, capabilitiesAny: [HRM_CAPABILITIES.RECRUITMENT_OPENING_VIEW, HRM_CAPABILITIES.RECRUITMENT_CANDIDATE_VIEW, HRM_CAPABILITIES.RECRUITMENT_APPLICATION_MANAGE, HRM_CAPABILITIES.RECRUITMENT_INTERVIEW_MANAGE, HRM_CAPABILITIES.RECRUITMENT_OFFER_MANAGE, HRM_CAPABILITIES.RECRUITMENT_HIRE_CONVERT] },
  { href: "/hr/onboarding", label: "التأهيل", labelMessageId: "hrm.g2.workspace.nav.onboarding", Icon: OnboardingIcon, capabilitiesAny: [HRM_CAPABILITIES.ONBOARDING_PLAN_MANAGE, HRM_CAPABILITIES.ONBOARDING_TASK_COMPLETE, HRM_CAPABILITIES.ONBOARDING_TASK_WAIVE] },
  { href: "/hr/performance/goals", label: "أهداف الأداء", labelMessageId: "hrm.g3.workspace.nav.goals", Icon: GoalsIcon, capabilitiesAny: [HRM_CAPABILITIES.GOAL_SELF_VIEW, HRM_CAPABILITIES.GOAL_TEAM_MANAGE] },
  { href: "/hr/performance/reviews", label: "تقييمات الأداء", labelMessageId: "hrm.g3.workspace.nav.reviews", Icon: ReviewsIcon, capabilitiesAny: [HRM_CAPABILITIES.REVIEW_SELF_VIEW, HRM_CAPABILITIES.REVIEW_TEAM_MANAGE] },
  // TIME & LEAVE
  { href: "/hr/attendance", label: "حضوري", labelMessageId: "hrm.g2.landing.attendance", Icon: AttendanceIcon, capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW, HRM_CAPABILITIES.ATTENDANCE_SELF_RECORD] },
  { href: "/hr/timesheets", label: "سجلات وقتي", labelMessageId: "hrm.g2.landing.timesheets", Icon: TimesheetIcon, capabilitiesAny: [HRM_CAPABILITIES.TIMESHEET_SELF_VIEW, HRM_CAPABILITIES.TIMESHEET_SELF_SUBMIT] },
  { href: "/hr/leave", label: "إجازاتي", labelMessageId: "hrm.g2.landing.leave", Icon: LeaveIcon, capabilitiesAny: [HRM_CAPABILITIES.LEAVE_SELF_VIEW, HRM_CAPABILITIES.LEAVE_SELF_REQUEST] },
  // MANAGEMENT
  { href: "/hr/team-attendance", label: "حضور الفريق", labelMessageId: "hrm.g2.landing.teamAttendance", Icon: TeamIcon, capability: HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW },
  { href: "/hr/team-timesheets", label: "سجلات وقت الفريق", labelMessageId: "hrm.g2.landing.teamTimesheets", Icon: InboxIcon, capability: HRM_CAPABILITIES.TIMESHEET_TEAM_APPROVE },
  { href: "/hr/leave/approvals", label: "اعتمادات الإجازات", labelMessageId: "hrm.g2.landing.leaveApprovals", Icon: ApprovalsIcon, capabilitiesAny: [HRM_CAPABILITIES.LEAVE_TEAM_APPROVE, HRM_CAPABILITIES.LEAVE_HR_APPROVE] },
  // ADMIN / REPORTS
  { href: "/hr/schedules", label: "جداول العمل", labelMessageId: "hrm.g2.landing.schedules", Icon: SchedulesIcon, capability: HRM_CAPABILITIES.ATTENDANCE_ADMIN },
  { href: "/hr/attendance/admin", label: "إدارة الحضور", labelMessageId: "hrm.g2.landing.attendanceAdmin", Icon: AdminIcon, capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_ADMIN, HRM_CAPABILITIES.ATTENDANCE_CORRECT] },
  { href: "/hr/leave/policies", label: "سياسات الإجازات", labelMessageId: "hrm.g2.landing.leavePolicies", Icon: PolicyIcon, capability: HRM_CAPABILITIES.LEAVE_POLICY_ADMIN },
  { href: "/hr/reports/attendance", label: "تقرير الحضور", labelMessageId: "hrm.g2.landing.attendanceReport", Icon: ReportsIcon, capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW, HRM_CAPABILITIES.ATTENDANCE_ADMIN] },
  // GOVERNANCE
  { href: "/hr/execution", label: "لوحة التنفيذ", labelMessageId: "hrm.g2.workspace.nav.execution", Icon: ExecutionIcon },
];

const ARABIC_DEFAULTS: Record<string, string> = Object.fromEntries(
  HR_WORKSPACE_LINKS.map((item) => [item.labelMessageId, item.label]),
);

const ARABIC_SECTION_DEFAULTS: Record<string, string> = {
  "hrm.g2.workspace.title": "مساحة عمل الموارد البشرية",
  "hrm.g2.workspace.subtitle": "إدارة الموظفين والهيكل التنظيمي والتوظيف والتأهيل والإسنادات والعقود والالتزام",
  "hrm.g2.workspace.navLabel": "أقسام الموارد البشرية",
  "hrm.g2.workspace.brandMark": "سند",
  "hrm.g2.workspace.nav.section.overview": "النظرة العامة والأشخاص",
  "hrm.g2.workspace.nav.section.talent": "الموهبة والأداء",
  "hrm.g2.workspace.nav.section.timeLeave": "الوقت والإجازات",
  "hrm.g2.workspace.nav.section.management": "إدارة الفريق",
  "hrm.g2.workspace.nav.section.admin": "الإدارة والتقارير",
  "hrm.g2.workspace.nav.section.governance": "الحوكمة",
  "hrm.g2.workspace.action.language": "تبديل اللغة",
  "hrm.g2.workspace.action.logout": "تسجيل الخروج",
  "hrm.g2.workspace.action.workspace": "مساحة العمل",
};

const SECTION_KEYS = [
  "hrm.g2.workspace.nav.section.overview",
  "hrm.g2.workspace.nav.section.talent",
  "hrm.g2.workspace.nav.section.timeLeave",
  "hrm.g2.workspace.nav.section.management",
  "hrm.g2.workspace.nav.section.admin",
  "hrm.g2.workspace.nav.section.governance",
] as const;

const SECTION_LINKS: Record<(typeof SECTION_KEYS)[number], string[]> = {
  "hrm.g2.workspace.nav.section.overview": ["/hr", "/hr/employees", "/hr/org-structure", "/hr/jobs", "/hr/positions", "/hr/assignments", "/hr/compliance"],
  "hrm.g2.workspace.nav.section.talent": ["/hr/recruitment", "/hr/onboarding", "/hr/performance/goals", "/hr/performance/reviews"],
  "hrm.g2.workspace.nav.section.timeLeave": ["/hr/attendance", "/hr/timesheets", "/hr/leave"],
  "hrm.g2.workspace.nav.section.management": ["/hr/team-attendance", "/hr/team-timesheets", "/hr/leave/approvals"],
  "hrm.g2.workspace.nav.section.admin": ["/hr/schedules", "/hr/attendance/admin", "/hr/leave/policies", "/hr/reports/attendance"],
  "hrm.g2.workspace.nav.section.governance": ["/hr/execution"],
};

export interface HrWorkspaceProps {
  capabilities: string[];
  activeHref: string;
  children: ReactNode;
  /** Optional route-scoped translator. Legacy callers remain Arabic-first. */
  translate?: (messageId: string) => string;
}

function isVisible(item: HrWorkspaceLink, capabilities: string[]): boolean {
  if (item.capability && !capabilities.includes(item.capability)) return false;
  if (item.capabilitiesAny && !item.capabilitiesAny.some((capability) => capabilities.includes(capability))) return false;
  return true;
}

export function HrWorkspace({ capabilities, activeHref, children, translate }: HrWorkspaceProps) {
  const auth = useAuth();
  // Safe context consumption: the workspace supports provider-free legacy
  // callers (tests, embedded surfaces) — outside an I18nProvider it falls
  // back to the Arabic-first defaults and no-op locale switching.
  const i18n = useContext(I18nContext);
  const locale = i18n?.locale ?? "ar";
  const direction = i18n?.direction;
  const setLocale = i18n?.setLocale ?? (() => undefined);

  const label = (messageId: string): string =>
    translate ? translate(messageId) : ARABIC_DEFAULTS[messageId] ?? messageId;
  const generic = (messageId: string): string =>
    translate ? translate(messageId) : ARABIC_SECTION_DEFAULTS[messageId] ?? messageId;

  const visibleLinks = HR_WORKSPACE_LINKS.filter((item) => isVisible(item, capabilities));
  const byHref = new Map(visibleLinks.map((item) => [item.href, item]));

  const navSections: SnadModuleNavSection[] = SECTION_KEYS.map((sectionKey) => ({
    label: generic(sectionKey),
    items: SECTION_LINKS[sectionKey]
      .map((href) => byHref.get(href))
      .filter((item): item is HrWorkspaceLink => Boolean(item))
      .map((item) => ({
        href: item.href,
        label: translate ? translate(item.labelMessageId) : item.label,
        Icon: item.Icon,
      })),
  }));

  function toggleLocale() {
    setLocale(locale === "ar" ? "en" : "ar");
  }

  async function handleLogout() {
    await auth.logout();
    window.location.assign("/");
  }

  const title = generic("hrm.g2.workspace.title");
  const subtitle = generic("hrm.g2.workspace.subtitle");

  return (
    <SnadModuleShell
      brandMark={generic("hrm.g2.workspace.brandMark")}
      brandHref="/hr"
      brandAriaLabel={title}
      title={title}
      subtitle={subtitle}
      navLabel={generic("hrm.g2.workspace.navLabel")}
      navSections={navSections}
      activeHref={activeHref}
      contentId="hr-workspace-main"
      dir={direction ?? undefined}
      user={auth.me?.displayName ?? auth.me?.email}
      userAriaLabel={title}
      languageLabel={generic("hrm.g2.workspace.action.language")}
      onToggleLanguage={toggleLocale}
      backLabel={generic("hrm.g2.workspace.action.workspace")}
      onBack={() => window.location.assign("/workspace")}
      logoutLabel={generic("hrm.g2.workspace.action.logout")}
      onLogout={() => void handleLogout()}
    >
      {children}
    </SnadModuleShell>
  );
}
