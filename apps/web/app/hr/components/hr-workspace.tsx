"use client";

/** Shared bilingual HR workspace shell. Backend authorization remains authoritative. */

import Link from "next/link";
import type { ReactNode } from "react";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import styles from "../hr.module.css";
import visualStyles from "./hr-g2-visual.module.css";

export interface HrWorkspaceLink {
  href: string;
  label: string;
  labelEn: string;
  labelMessageId?: string;
  capability?: string;
  capabilitiesAny?: readonly string[];
}

export const HR_WORKSPACE_LINKS: HrWorkspaceLink[] = [
  { href: "/hr", label: "الرئيسية", labelEn: "Home" },
  { href: "/hr/employees", label: "الموظفون", labelEn: "Employees", capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  { href: "/hr/org-structure", label: "الهيكل التنظيمي", labelEn: "Organization Structure", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/jobs", label: "الوظائف", labelEn: "Jobs", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/positions", label: "المناصب", labelEn: "Positions", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/assignments", label: "الإسنادات", labelEn: "Assignments", capability: HRM_CAPABILITIES.ASSIGNMENT_VIEW },
  { href: "/hr/compliance", label: "الالتزام", labelEn: "Compliance", capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  { href: "/hr/recruitment", label: "التوظيف", labelEn: "Recruitment", capabilitiesAny: [HRM_CAPABILITIES.RECRUITMENT_OPENING_VIEW, HRM_CAPABILITIES.RECRUITMENT_CANDIDATE_VIEW, HRM_CAPABILITIES.RECRUITMENT_APPLICATION_MANAGE, HRM_CAPABILITIES.RECRUITMENT_INTERVIEW_MANAGE, HRM_CAPABILITIES.RECRUITMENT_OFFER_MANAGE, HRM_CAPABILITIES.RECRUITMENT_HIRE_CONVERT] },
  { href: "/hr/onboarding", label: "التأهيل", labelEn: "Onboarding", capabilitiesAny: [HRM_CAPABILITIES.ONBOARDING_PLAN_MANAGE, HRM_CAPABILITIES.ONBOARDING_TASK_COMPLETE, HRM_CAPABILITIES.ONBOARDING_TASK_WAIVE] },
  { href: "/hr/attendance", label: "حضوري", labelEn: "Attendance", labelMessageId: "hrm.g2.landing.attendance", capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW, HRM_CAPABILITIES.ATTENDANCE_SELF_RECORD] },
  { href: "/hr/timesheets", label: "سجلات وقتي", labelEn: "My Timesheets", labelMessageId: "hrm.g2.landing.timesheets", capabilitiesAny: [HRM_CAPABILITIES.TIMESHEET_SELF_VIEW, HRM_CAPABILITIES.TIMESHEET_SELF_SUBMIT] },
  { href: "/hr/leave", label: "إجازاتي", labelEn: "My Leave", labelMessageId: "hrm.g2.landing.leave", capabilitiesAny: [HRM_CAPABILITIES.LEAVE_SELF_VIEW, HRM_CAPABILITIES.LEAVE_SELF_REQUEST] },
  { href: "/hr/team-attendance", label: "حضور الفريق", labelEn: "Team Attendance", labelMessageId: "hrm.g2.landing.teamAttendance", capability: HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW },
  { href: "/hr/team-timesheets", label: "سجلات وقت الفريق", labelEn: "Team Timesheets", labelMessageId: "hrm.g2.landing.teamTimesheets", capability: HRM_CAPABILITIES.TIMESHEET_TEAM_APPROVE },
  { href: "/hr/leave/approvals", label: "اعتمادات الإجازات", labelEn: "Leave Approvals", labelMessageId: "hrm.g2.landing.leaveApprovals", capabilitiesAny: [HRM_CAPABILITIES.LEAVE_TEAM_APPROVE, HRM_CAPABILITIES.LEAVE_HR_APPROVE] },
  { href: "/hr/schedules", label: "جداول العمل", labelEn: "Work Schedules", labelMessageId: "hrm.g2.landing.schedules", capability: HRM_CAPABILITIES.ATTENDANCE_ADMIN },
  { href: "/hr/attendance/admin", label: "إدارة الحضور", labelEn: "Attendance Administration", labelMessageId: "hrm.g2.landing.attendanceAdmin", capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_ADMIN, HRM_CAPABILITIES.ATTENDANCE_CORRECT] },
  { href: "/hr/leave/policies", label: "سياسات الإجازات", labelEn: "Leave Policies", labelMessageId: "hrm.g2.landing.leavePolicies", capability: HRM_CAPABILITIES.LEAVE_POLICY_ADMIN },
  { href: "/hr/reports/attendance", label: "تقرير الحضور", labelEn: "Attendance Report", labelMessageId: "hrm.g2.landing.attendanceReport", capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW, HRM_CAPABILITIES.ATTENDANCE_ADMIN] },
  { href: "/hr/execution", label: "لوحة التنفيذ", labelEn: "Execution Board" },
];

export interface HrWorkspaceProps {
  capabilities: string[];
  activeHref: string;
  children: ReactNode;
  /** Optional route-scoped translator. Legacy callers remain provider-free and Arabic-first. */
  translate?: (messageId: string) => string;
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

function workspaceLocale(translate?: (messageId: string) => string): "ar" | "en" {
  if (!translate) return "ar";
  return translate("hrm.g2.landing.myWorkday") === "My Workday" ? "en" : "ar";
}

export function HrWorkspace({ capabilities, activeHref, children, translate }: HrWorkspaceProps) {
  const locale = workspaceLocale(translate);
  const visibleLinks = HR_WORKSPACE_LINKS.filter((link) => isVisible(link, capabilities));
  const activeLinkHref = visibleLinks.filter((link) => matchesRoute(link.href, activeHref)).sort((a, b) => b.href.length - a.href.length)[0]?.href;

  return (
    <div className={styles.workspace}>
      <header className={styles.workspaceHeader}>
        <h1 className={styles.workspaceTitle}>{locale === "ar" ? "مساحة عمل الموارد البشرية" : "Human Resources Workspace"}</h1>
        <p className={styles.workspaceSubtitle}>
          {locale === "ar"
            ? "إدارة الموظفين والهيكل التنظيمي والتوظيف والتأهيل والإسنادات والعقود والالتزام"
            : "Manage employees, organization structure, recruitment, onboarding, assignments, contracts, and compliance"}
        </p>
      </header>
      <nav aria-label={locale === "ar" ? "أقسام الموارد البشرية" : "Human resources sections"} className={`${styles.workspaceNav} ${visualStyles.workspaceNavResponsive}`}>
        <ul className={styles.workspaceNavList}>
          {visibleLinks.map((link) => {
            const active = activeLinkHref === link.href;
            const label = link.labelMessageId && translate ? translate(link.labelMessageId) : locale === "ar" ? link.label : link.labelEn;
            return (
              <li key={link.href} className={styles.workspaceNavItem}>
                <Link href={link.href} aria-current={active ? "page" : undefined} className={active ? `${styles.workspaceNavLink} ${styles.workspaceNavLinkActive}` : styles.workspaceNavLink}>
                  {label}
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