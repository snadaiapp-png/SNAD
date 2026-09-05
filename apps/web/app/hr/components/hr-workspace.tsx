"use client";

/**
 * Shared Arabic-first HR workspace shell — WS5 Task 8.
 *
 * - Renders the authoritative navigation set of the HR workspace.
 * - Capability checks here are UX-only convenience: the backend remains
 *   the authoritative authorization layer, and every page handles backend
 *   403/404/409/422 responses explicitly.
 * - RTL/Arabic-first: logical CSS properties only, no left/right assumptions.
 */

import Link from "next/link";
import type { ReactNode } from "react";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import styles from "../hr.module.css";

export interface HrWorkspaceLink {
  href: string;
  label: string;
  /** UX-only capability gate (backend authorization stays authoritative). */
  capability?: string;
}

/** The authoritative workspace navigation set (plan Task 8 Step 4). */
export const HR_WORKSPACE_LINKS: HrWorkspaceLink[] = [
  { href: "/hr", label: "الرئيسية" },
  { href: "/hr/employees", label: "الموظفون", capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  { href: "/hr/org-structure", label: "الهيكل التنظيمي", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/jobs", label: "الوظائف", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/positions", label: "المناصب", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
  { href: "/hr/assignments", label: "الإسنادات", capability: HRM_CAPABILITIES.ASSIGNMENT_VIEW },
  { href: "/hr/compliance", label: "الالتزام", capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  // The execution dashboard is part of the workspace foundation — always reachable.
  { href: "/hr/execution", label: "لوحة التنفيذ" },
];

export interface HrWorkspaceProps {
  /** Effective capabilities of the current user (from /me) — UX-only. */
  capabilities: string[];
  /** The active workspace route (marked with aria-current="page"). */
  activeHref: string;
  children: ReactNode;
}

export function HrWorkspace({ capabilities, activeHref, children }: HrWorkspaceProps) {
  return (
    <div className={styles.workspace}>
      <header className={styles.workspaceHeader}>
        <h1 className={styles.workspaceTitle}>مساحة عمل الموارد البشرية</h1>
        <p className={styles.workspaceSubtitle}>
          إدارة الموظفين والهيكل التنظيمي والإسنادات والعقود والالتزام
        </p>
      </header>
      <nav aria-label="أقسام الموارد البشرية" className={styles.workspaceNav}>
        <ul className={styles.workspaceNavList}>
          {HR_WORKSPACE_LINKS.map((link) => {
            // UX-only gate: hide sections the user cannot use. Hidden is NOT
            // protected — backend capability checks remain authoritative.
            if (link.capability && !capabilities.includes(link.capability)) {
              return null;
            }
            const isActive = link.href === activeHref;
            return (
              <li key={link.href} className={styles.workspaceNavItem}>
                <Link
                  href={link.href}
                  aria-current={isActive ? "page" : undefined}
                  className={isActive ? `${styles.workspaceNavLink} ${styles.workspaceNavLinkActive}` : styles.workspaceNavLink}
                >
                  {link.label}
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
