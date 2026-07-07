'use client';

/*
 * ============================================================================
 *  ExecutiveShell — Global authenticated header for admin surfaces
 * ----------------------------------------------------------------------------
 *  PURPOSE
 *  -------
 *  Provides the persistent top-of-page header for every authenticated
 *  executive/admin surface (workspace dashboard, control plane, CRM, etc.).
 *  The header carries:
 *    • The SNAD logo (compact variant) linking to `/workspace`
 *    • Optional slots for menu button, organization name, search,
 *      notifications, AI assistant, and user profile
 *
 *  BEHAVIOR
 *  --------
 *    • `position: sticky; top: 0` — the header stays visible during scroll
 *    • `z-index: var(--snad-z-sticky)` — sits above page content but below
 *      modals, toasts, and tooltips
 *    • Height: 64px desktop, 56px mobile (via clamp)
 *    • Logical-property based: brand area appears on the inline-start edge
 *      (right in RTL, left in LTR)
 *    • Only ONE primary logo is rendered
 *
 *  ACCESSIBILITY (WCAG 2.2 AA)
 *  ---------------------------
 *    • Logo link has `aria-label` so screen readers announce the destination
 *    • Visible `:focus-visible` ring on every interactive slot
 *    • All slot buttons expose an `aria-label` (overridable by consumers)
 *    • Minimum 44x44 touch target on every interactive element
 *
 *  TOKENS
 *  ------
 *  Every visual value references an `--snad-*` token. No hardcoded colors,
 *  font families, or z-index values.
 * ============================================================================
 */

import { forwardRef, type ReactNode } from 'react';

import { SnadLogo } from '@/components/sds';
import styles from './ExecutiveShell.module.css';

export interface ExecutiveShellProps {
  /** Page content rendered below the header. */
  children: ReactNode;
  /** Optional hamburger / sidebar-toggle button (inline-start edge). */
  menuButton?: ReactNode;
  /** Organization name shown in the center-inline area. */
  organizationName?: ReactNode;
  /** Optional search control (inline-end cluster). */
  search?: ReactNode;
  /** Optional notifications bell with badge. */
  notifications?: ReactNode;
  /** Optional AI assistant trigger. */
  aiAssistant?: ReactNode;
  /** Optional user profile menu trigger (inline-end edge). */
  userProfile?: ReactNode;
  /** Override the logo's link destination. @default '/workspace' */
  logoHref?: string;
  /** Accessible label for the logo link. @default 'الذهاب إلى مساحة العمل' */
  logoAriaLabel?: string;
  /** Additional className on the root header element. */
  className?: string;
}

/**
 * ExecutiveShell.
 *
 * @example
 * ```tsx
 * <ExecutiveShell
 *   menuButton={<SidebarToggle />}
 *   organizationName="ACME Corp"
 *   search={<GlobalSearch />}
 *   notifications={<NotificationsBell />}
 *   aiAssistant={<AIAssistantButton />}
 *   userProfile={<UserMenu />}
 * >
 *   <WorkspaceDashboard />
 * </ExecutiveShell>
 * ```
 */
export const ExecutiveShell = forwardRef<HTMLElement, ExecutiveShellProps>(
  function ExecutiveShell(
    {
      children,
      menuButton,
      organizationName,
      search,
      notifications,
      aiAssistant,
      userProfile,
      logoHref = '/workspace',
      logoAriaLabel = 'الذهاب إلى مساحة العمل',
      className,
    },
    ref,
  ) {
    const headerClass = [styles.shell, className ?? '']
      .filter(Boolean)
      .join(' ');

    return (
      <div className={styles.layout}>
        <header ref={ref} className={headerClass} role="banner">
          <div className={styles.bar}>
            {/* Inline-start cluster: menu button + brand logo */}
            <div className={styles.startCluster}>
              {menuButton ? (
                <div className={styles.menuSlot}>{menuButton}</div>
              ) : null}
              <a
                href={logoHref}
                className={styles.logoLink}
                aria-label={logoAriaLabel}
              >
                <SnadLogo
                  variant="compact"
                  size="md"
                  alt={logoAriaLabel}
                />
              </a>
            </div>

            {/* Center: organization name */}
            {organizationName ? (
              <div className={styles.orgSlot}>
                <span className={styles.orgName}>{organizationName}</span>
              </div>
            ) : null}

            {/* Inline-end cluster: search, notifications, AI, profile */}
            <div className={styles.endCluster}>
              {search ? (
                <div className={styles.searchSlot}>{search}</div>
              ) : null}
              {notifications ? (
                <div className={styles.iconSlot}>{notifications}</div>
              ) : null}
              {aiAssistant ? (
                <div className={styles.iconSlot}>{aiAssistant}</div>
              ) : null}
              {userProfile ? (
                <div className={styles.profileSlot}>{userProfile}</div>
              ) : null}
            </div>
          </div>
        </header>

        {/* Main content area — sits below the sticky header. */}
        <main id="main-content" className={styles.main} role="main">
          {children}
        </main>
      </div>
    );
  },
);

ExecutiveShell.displayName = 'ExecutiveShell';
