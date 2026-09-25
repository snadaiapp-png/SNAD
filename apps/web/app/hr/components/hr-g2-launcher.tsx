"use client";

import Link from "next/link";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "./hr-g2-visual.module.css";

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

const GROUPS: readonly G2LauncherGroup[] = [
  {
    id: "self",
    titleKey: "hrm.g2.landing.myWorkday",
    items: [
      {
        href: "/hr/attendance",
        labelKey: "hrm.g2.landing.attendance",
        descriptionKey: "hrm.attendance.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW, HRM_CAPABILITIES.ATTENDANCE_SELF_RECORD],
        testId: "g2-launcher-attendance",
      },
      {
        href: "/hr/timesheets",
        labelKey: "hrm.g2.landing.timesheets",
        descriptionKey: "hrm.timesheets.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.TIMESHEET_SELF_VIEW, HRM_CAPABILITIES.TIMESHEET_SELF_SUBMIT],
        testId: "g2-launcher-timesheets",
      },
      {
        href: "/hr/leave",
        labelKey: "hrm.g2.landing.leave",
        descriptionKey: "hrm.leave.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.LEAVE_SELF_VIEW, HRM_CAPABILITIES.LEAVE_SELF_REQUEST],
        testId: "g2-launcher-leave",
      },
    ],
  },
  {
    id: "team",
    titleKey: "hrm.g2.landing.myTeam",
    items: [
      {
        href: "/hr/team-attendance",
        labelKey: "hrm.g2.landing.teamAttendance",
        descriptionKey: "hrm.teamAttendance.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW],
        testId: "g2-launcher-team-attendance",
      },
      {
        href: "/hr/team-timesheets",
        labelKey: "hrm.g2.landing.teamTimesheets",
        descriptionKey: "hrm.timesheets.team.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.TIMESHEET_TEAM_APPROVE],
        testId: "g2-launcher-team-timesheets",
      },
      {
        href: "/hr/leave/approvals",
        labelKey: "hrm.g2.landing.leaveApprovals",
        descriptionKey: "hrm.leaveApprovals.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.LEAVE_TEAM_APPROVE],
        testId: "g2-launcher-team-leave-approvals",
      },
      {
        href: "/hr/reports/attendance",
        labelKey: "hrm.g2.landing.attendanceReport",
        descriptionKey: "hrm.attendanceReport.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_TEAM_VIEW],
        testId: "g2-launcher-team-attendance-report",
      },
    ],
  },
  {
    id: "hr",
    titleKey: "hrm.g2.landing.hrOperations",
    items: [
      {
        href: "/hr/schedules",
        labelKey: "hrm.g2.landing.schedules",
        descriptionKey: "hrm.schedules.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_ADMIN],
        testId: "g2-launcher-schedules",
      },
      {
        href: "/hr/attendance/admin",
        labelKey: "hrm.g2.landing.attendanceAdmin",
        descriptionKey: "hrm.attendanceAdmin.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_ADMIN, HRM_CAPABILITIES.ATTENDANCE_CORRECT],
        testId: "g2-launcher-attendance-admin",
      },
      {
        href: "/hr/leave/policies",
        labelKey: "hrm.g2.landing.leavePolicies",
        descriptionKey: "hrm.leavePolicies.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.LEAVE_POLICY_ADMIN],
        testId: "g2-launcher-leave-policies",
      },
      {
        href: "/hr/leave/approvals",
        labelKey: "hrm.g2.landing.leaveApprovals",
        descriptionKey: "hrm.leaveApprovals.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.LEAVE_HR_APPROVE],
        testId: "g2-launcher-hr-leave-approvals",
      },
      {
        href: "/hr/reports/attendance",
        labelKey: "hrm.g2.landing.attendanceReport",
        descriptionKey: "hrm.attendanceReport.subtitle",
        capabilitiesAny: [HRM_CAPABILITIES.ATTENDANCE_ADMIN],
        testId: "g2-launcher-hr-attendance-report",
      },
    ],
  },
];

function isVisible(item: G2LauncherItem, capabilities: string[]): boolean {
  return item.capabilitiesAny.some((capability) => capabilities.includes(capability));
}

export function HrG2Launcher({ capabilities }: { capabilities: string[] }) {
  const { t } = useI18n();

  const visibleGroups = GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => isVisible(item, capabilities)),
  })).filter((group) => group.items.length > 0);

  if (visibleGroups.length === 0) return null;

  return (
    <section className={styles.launcher} aria-label={t("hrm.g2.landing.myWorkday")}>
      {visibleGroups.map((group) => (
        <section key={group.id} className={styles.launcherSection} data-testid={`g2-launcher-${group.id}`}>
          <h2 className={styles.launcherHeading}>{t(group.titleKey)}</h2>
          <div className={styles.launcherGrid}>
            {group.items.map((item) => (
              <Link key={item.href} href={item.href} className={styles.launcherCard} data-testid={item.testId}>
                <span className={styles.launcherCardTitle}>{t(item.labelKey)}</span>
                <p className={styles.launcherCardDescription}>{t(item.descriptionKey)}</p>
              </Link>
            ))}
          </div>
        </section>
      ))}
    </section>
  );
}
