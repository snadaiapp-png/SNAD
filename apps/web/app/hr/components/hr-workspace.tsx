"use client";

/** Shared Arabic-first HR workspace shell. Backend authorization remains authoritative. */

import Link from "next/link";
import type { ReactNode } from "react";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "../hr.module.css";
import visualStyles from "./hr-g2-visual.module.css";

export interface HrWorkspaceLink {
  href: string;
  label: string;
  labelKey?: string;
  capability?: string;
  capabilitiesAny?: readonly string[];
}

export const HR_WORKSPACE_LINKS: HrWorkspaceLink[] = [
  { href: "/hr", label: "الرئيسية" },
  { href: "/hr/employees", label: "الموظفون", capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  { href: "/hr/org-structure", label: "الهيكل التنظيمي", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/jobs", label: "الوظائف", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/positions", label: "المناصب", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/assignments", label: "الإسنادات", capability: HRM_CAPABILITIES.ASSIGNMENT_VIEW },
  { href: "/hr/compliance", label: "الالتزام", capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  {
    href: "/hr/recruitment",
    label: "التوظيف",
    capabilitiesAny: [
      HRM_CAPABILITIES.RECRUITMENT_OPENING_VIEW,
      HRM_CAPABILITIES.RECRUITMENT_CANDIDATE_VIEW,
      HRM_CAPABILITIES.RECRUITMENT_APPLICATION_MANAGE,
      HRM_CAPABILITIES.RECRUITMENT_INTERVIEW_MANAGE,
      HRM_CAPABILITIES.RECRUITMENT_OFFER_MANAGE,
      HRM_CAPABILITIES.RECRUITMENT_HIRE_CONVERT,
    ],
  },
  {
    href: "/hr/onboarding",
    label: "التأهيل",
    capabilitiesAny: [
      HRM_CAPABILITIES.ONBOARDING_PLAN_MANAGE,
      HRM_CAPABILITIES.ONBOARDING_TASK_COMPLETE,
      HRM_CAPABILITIES.ONBOARDING_TASK_WAIVE,
    ],
  },

  // G2 employee self-service surfaces.
  {
    href: "/hr/attendance",
    label: "حضوري",
    labelKey: "hrm.g2.landing.attendance",
    capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW, HRM_CAPABILITIES.ATTENDANCE_SELF_RECORD],
  },
  {
    href: "/hr/timesheets",
    label: "سجلات وقتي",
    labelKey: "hrm.g2.landing.timesheets",
    capabilitiesAny: [HRM_CAPABILITIES.TIMESHEET_SELF_VIEW, HRM_CAPABILITIES.TIMESHEET_SELF_SUBMIT],
  },
  {
    href: "/hr/leave",
    label: "إجازاتي",
    labelKey: "hrm.g2.landing.leave",
    capabilitiesAny: [HRM_CAPABILITIES.LEAVE_SELF_VIEW, HRM_CAPABILITIES.LEAVE_SELF_REQUEST],
  },

  // G2 manager/team surfaces.
  { href: "/hr/team-attendance", label: "حضور الفريق", labelKey: "hrm.g2.landing.teamAttendance", capability: HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW },
  { href: "/hr/team-timesheets", label: "سجلات وقت الفريق", labelKey: "hrm.g2.landing.teamTimesheets", capability: HRM_CAPABILITIES.TIMESHEET_TEAM_APPROVE },
  {
    href: "/hr/leave/approvals",
    label: "اعتمادات الإجازات",
    labelKey: "hrm.g2.landing.leaveApprovals",
    capabilitiesAny: [HRM_CAPABILITIES.LEAVE_TEAM_APPROVE, HRM_CAPABILITIES.LEAVE_HR_APPROVE],
  },

  // G2 HR administration surfaces.
  { href: "/hr/schedules", label: "جداول العمل", labelKey: "hrm.g2.landing.schedules", capability: HRM_CAPABILITIES.ATTENDANCE_ADMIN },
  {
    href: "/hr/attendance/admin",
    label: "إدارة الحضور",
    labelKey: "hrm.g2.landing.attendanceAdmin",
    capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_ADMIN, HRM_CAPABILITIES.ATTENDANCE_CORRECT],
  },
  { href: "/hr/leave/policies", label: "سياسات الإجازات", labelKey: "hrm.g2.landing.leavePolicies", capability: HRM_CAPABILITIES.LEAVE_POLICY_ADMIN },
  {
    href: "/hr/reports/attendance",
    label: "تقرير الحضور",
    labelKey: "hrm.g2.landing.attendanceReport",
    capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW, HRM_CAPABILITIES.ATTENDANCE_ADMIN],
  },

  { href: "/hr/execution", label: "لوحة التنفيذ" },
];

export interface HrWorkspaceProps {
  capabilities: string[];
  activeHref: string;
  children: ReactNode;
}

function isVisible(link: HrWorkspaceLink, capabilities: string[]): boolean {
  if (link.capability && !capabilities.includes(link.capability)) return false;
  if (link.capabilitiesAny && !link.capabilitiesAny.some((capability) => capabilities.includes(capability))) return false;
  return true;
}

function matchesRoute(linkHref: string, activeHref: string): boolean {
  if (linkHref === "/hr") return activeHref === "/hr";
  return activeHref === linkHref || activeHref.startsWith(`${linkHref}/`);
}

export function HrWorkspace({ capabilities, activeHref, children }: HrWorkspaceProps) {
  const { t } = useI18n();
  const visibleLinks = HR_WORKSPACE_LINKS.filter((link) => isVisible(link, capabilities));
  const activeLinkHref = visibleLinks
    .filter((link) => matchesRoute(link.href, activeHref))
    .sort((a, b) => b.href.length - a.href.length)[0]?.href;

  return (
    <div className={styles.workspace}>
      <header className={styles.workspaceHeader}>
        <h1 className={styles.workspaceTitle}>مساحة عمل الموارد البشرية</h1>
        <p className={styles.workspaceSubtitle}>
          إدارة الموظفين والهيكل التنظيمي والتوظيف والتأهيل والإسنادات والعقود والالتزام
        </p>
      </header>
      <nav aria-label="أقسام الموارد البشرية" className={`${styles.workspaceNav} ${visualStyles.workspaceNavResponsive}`}>
        <ul className={styles.workspaceNavList}>
          {visibleLinks.map((link) => {
            const active = activeLinkHref === link.href;
            return (
              <li key={link.href} className={styles.workspaceNavItem}>
                <Link
                  href={link.href}
                  aria-current={active ? "page" : undefined}
                  className={active ? `${styles.workspaceNavLink} ${styles.workspaceNavLinkActive}` : styles.workspaceNavLink}
                >
                  {link.labelKey ? t(link.labelKey) : link.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <main className={styles.workspaceMain}>{children}</main>
    </div>
  );
}
