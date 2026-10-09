"use client";

/**
 * SNAD Shared Module Shell — the canonical module visual contract primitive.
 *
 * Extracted from the CRM reference architecture (crm-shell.tsx) and
 * generalized: CRM, HR, and every future SNAD module render their product
 * shell through this component. Modules differ only in content, labels,
 * routes, capabilities, and domain data — never in shell geometry, sidebar,
 * header, active-nav treatment, or state surfaces.
 *
 * Capability gating of nav items is resolved BY THE CALLER (the module owns
 * its capability lists); this component only renders what it receives.
 * The backend remains the authoritative RBAC enforcement everywhere.
 */

import Link from "next/link";
import type { ComponentType, ReactNode } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { ModuleAccessNavigation } from "@/components/users/ModuleAccessNavigation";
import styles from "./snad-shell.module.css";

export interface SnadModuleNavItem {
  href: string;
  label: string;
  Icon?: ComponentType;
}

export interface SnadModuleNavSection {
  label: string;
  items: SnadModuleNavItem[];
}

export interface SnadModuleShellProps {
  /** Short brand tile text (e.g. "سند" or the module mark). */
  brandMark: string;
  /** When set, the brand tile links to this href (CRM reference behavior). */
  brandHref?: string;
  brandAriaLabel?: string;
  title: string;
  subtitle?: string;
  /** aria-label for the sidebar navigation landmark. */
  navLabel: string;
  /** Visibility-filtered nav sections (caller resolves capabilities). */
  navSections: SnadModuleNavSection[];
  /** Current pathname used to resolve the active nav item (longest prefix wins). */
  activeHref: string;
  /** id for the <main> content region. */
  contentId?: string;
  /** Direction override; inherit from the document when omitted. */
  dir?: "rtl" | "ltr";
  /** Signed-in user display name shown in the header. */
  user?: string;
  userAriaLabel?: string;
  languageLabel?: string;
  onToggleLanguage?: () => void;
  backLabel?: string;
  onBack?: () => void;
  logoutLabel?: string;
  onLogout?: () => void;
  /** Extra header actions rendered before the standard set. */
  headerTrailing?: ReactNode;
  /** When true, render the shared auth loading surface instead of the shell. */
  authLoading?: boolean;
  authLoadingSubtitle?: string;
  children: ReactNode;
}

function isActiveRoute(activeHref: string, href: string): boolean {
  if (activeHref === href) return true;
  return activeHref.startsWith(`${href}/`);
}

export function SnadModuleShell({
  brandMark,
  brandHref,
  brandAriaLabel,
  title,
  subtitle,
  navLabel,
  navSections,
  activeHref,
  contentId,
  dir,
  user,
  userAriaLabel,
  languageLabel,
  onToggleLanguage,
  backLabel,
  onBack,
  logoutLabel,
  onLogout,
  headerTrailing,
  authLoading,
  authLoadingSubtitle,
  children,
}: SnadModuleShellProps) {
  if (authLoading) {
    return <AuthLoadingState subtitle={authLoadingSubtitle ?? ""} />;
  }

  // Longest-prefix single winner: only the most specific matching item is active.
  const allItems = navSections.flatMap((section) => section.items);
  const activeItemHref = allItems
    .filter((item) => isActiveRoute(activeHref, item.href))
    .sort((a, b) => b.href.length - a.href.length)[0]?.href;

  const brandTile = (
    <>
      {brandMark}
      <span className={styles.brandMarkGold} />
    </>
  );

  return (
    <div className={styles.shell} dir={dir} data-snad-module-shell="true">
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          {brandHref ? (
            <Link href={brandHref} className={styles.brandMark} aria-label={brandAriaLabel ?? title}>
              {brandTile}
            </Link>
          ) : (
            <span className={styles.brandMark} aria-label={brandAriaLabel ?? title}>
              {brandTile}
            </span>
          )}
          <div className={styles.headerTitles}>
            <h1 className={styles.headerTitle}>{title}</h1>
            {subtitle ? <p className={styles.headerSubtitle}>{subtitle}</p> : null}
          </div>
        </div>

        <div className={styles.headerRight}>
          {headerTrailing}
          {user ? (
            <span className={styles.headerUser} aria-label={userAriaLabel ?? user}>
              {user}
            </span>
          ) : null}
          {onToggleLanguage ? (
            <button type="button" className={styles.langBtn} onClick={onToggleLanguage} aria-label={languageLabel}>
              <span className={styles.headerBtnIcon}>
                <LangGlyph />
              </span>
              <span>{languageLabel}</span>
            </button>
          ) : null}
          {onBack ? (
            <button type="button" className={styles.headerBtn} onClick={onBack}>
              <span className={styles.headerBtnIcon}>
                <BackGlyph />
              </span>
              <span>{backLabel}</span>
            </button>
          ) : null}
          {onLogout ? (
            <button type="button" className={styles.headerBtn} onClick={onLogout}>
              <span>{logoutLabel}</span>
            </button>
          ) : null}
        </div>
      </header>

      <div className={styles.body}>
        <aside className={styles.sidebar}>
          <nav className={styles.sidebarNav} aria-label={navLabel}>
            {navSections.map((section, sectionIndex) =>
              section.items.length === 0 ? null : (
                <div key={section.label}>
                  {sectionIndex > 0 ? <div className={styles.sidebarDivider} /> : null}
                  <span className={styles.sidebarSectionLabel}>{section.label}</span>
                  {section.items.map((item) => {
                    const active = activeItemHref === item.href;
                    const Icon = item.Icon;
                    return (
                      <Link
                        key={item.href}
                        href={item.href}
                        aria-current={active ? "page" : undefined}
                        className={`${styles.sidebarItem} ${active ? styles.sidebarItemActive : ""}`}
                      >
                        {Icon ? (
                          <span className={styles.sidebarItemIcon}>
                            <Icon />
                          </span>
                        ) : null}
                        <span className={styles.sidebarItemLabel}>{item.label}</span>
                      </Link>
                    );
                  })}
                </div>
              ),
            )}
            <ModuleAccessNavigation
              routePath={activeHref}
              presentation="sidebar"
              sidebarItemClassName={styles.sidebarItem}
              sidebarIconClassName={styles.sidebarItemIcon}
              sidebarLabelClassName={styles.sidebarItemLabel}
              sidebarDividerClassName={styles.sidebarDivider}
              sidebarSectionLabelClassName={styles.sidebarSectionLabel}
            />
          </nav>
        </aside>

        <main className={styles.content} id={contentId}>
          {children}
        </main>
      </div>
    </div>
  );
}

/* Inline header glyphs are part of the shell contract (16x16, currentColor). */
function LangGlyph() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable={false}>
      <circle cx="8" cy="8" r="5.5" />
      <path d="M2.5 8h11M8 2.5c1.5 1.6 2.2 3.6 2.2 5.5S9.5 12 8 13.5M8 2.5C6.5 4 5.8 6 5.8 7.5S6.5 12 8 13.5" />
    </svg>
  );
}

function BackGlyph() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable={false}>
      <path d="M10 4l-4 4 4 4" />
      <line x1="6" y1="8" x2="14" y2="8" />
    </svg>
  );
}

export default SnadModuleShell;
