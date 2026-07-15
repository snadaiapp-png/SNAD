"use client";

import { useEffect, type ComponentType, type ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "../crm-command-center.module.css";

interface NavItem {
  href: string;
  labelKey: string;
  Icon: ComponentType;
}

/* ============================================================================
 *  Inline SVG icons (16x16, stroke="currentColor")
 * ============================================================================ */

function OverviewIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="2" width="5" height="5" rx="1" />
      <rect x="9" y="2" width="5" height="3" rx="1" />
      <rect x="9" y="7" width="5" height="7" rx="1" />
      <rect x="2" y="9" width="5" height="5" rx="1" />
    </svg>
  );
}

function AccountsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 13.5c0-2.2 1.8-4 4-4s4 1.8 4 4" />
      <circle cx="7" cy="5" r="2.5" />
      <path d="M11 5v3M9.5 6.5h3" />
    </svg>
  );
}

function ContactsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="2" width="11" height="12" rx="1.5" />
      <circle cx="7.5" cy="6" r="1.8" />
      <path d="M4 11.5c0-1.9 1.6-3 3.5-3s3.5 1.1 3.5 3" />
      <line x1="13" y1="4.5" x2="14.5" y2="4.5" />
      <line x1="13" y1="7.5" x2="14.5" y2="7.5" />
    </svg>
  );
}

function LeadsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 2l1.5 11 4.5-2 4.5 2L14 2z" />
      <path d="M5 5h6M5 8h4" />
    </svg>
  );
}

function PipelinesIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="3" width="3.5" height="10" rx="0.5" />
      <rect x="6.5" y="3" width="3" height="7" rx="0.5" />
      <rect x="10.5" y="3" width="3.5" height="12" rx="0.5" />
    </svg>
  );
}

function OpportunitiesIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="8" cy="8" r="6" />
      <circle cx="8" cy="8" r="3.5" />
      <circle cx="8" cy="8" r="1" fill="currentColor" />
    </svg>
  );
}

function ActivitiesIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="2" width="12" height="12" rx="1.5" />
      <path d="M5 8l2 2 4-4" />
    </svg>
  );
}

function TagsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 8l5-5h6v6l-5 5z" />
      <circle cx="10" cy="5" r="1" fill="currentColor" />
    </svg>
  );
}

function ImportsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M8 2v8M4.5 6.5L8 10l3.5-3.5" />
      <path d="M2 12v1.5A.5.5 0 0 0 2.5 14h11a.5.5 0 0 0 .5-.5V12" />
    </svg>
  );
}

function CustomFieldsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="3" width="12" height="10" rx="1.5" />
      <path d="M2 6h12M5 9h2M5 11h4" />
    </svg>
  );
}

function CommandCenterIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="2.5" width="12" height="11" rx="1" />
      <path d="M5 2.5v-1h6v1" />
      <path d="M5 7h6M5 9.5h6M5 12h4" />
    </svg>
  );
}

function LangIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="8" cy="8" r="5.5" />
      <path d="M2.5 8h11M8 2.5c1.5 1.6 2.2 3.6 2.2 5.5S9.5 12 8 13.5M8 2.5C6.5 4 5.8 6 5.8 7.5S6.5 12 8 13.5" />
    </svg>
  );
}

function BackIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M10 4l-4 4 4 4" />
      <line x1="6" y1="8" x2="14" y2="8" />
    </svg>
  );
}

const MAIN_NAV: NavItem[] = [
  { href: "/crm/overview", labelKey: "crm.nav.overview", Icon: OverviewIcon },
  { href: "/crm/accounts", labelKey: "crm.nav.accounts", Icon: AccountsIcon },
  { href: "/crm/contacts", labelKey: "crm.nav.contacts", Icon: ContactsIcon },
  { href: "/crm/leads", labelKey: "crm.nav.leads", Icon: LeadsIcon },
  { href: "/crm/pipelines", labelKey: "crm.nav.pipelines", Icon: PipelinesIcon },
  { href: "/crm/opportunities", labelKey: "crm.nav.opportunities", Icon: OpportunitiesIcon },
  { href: "/crm/activities", labelKey: "crm.nav.activities", Icon: ActivitiesIcon },
  { href: "/crm/tags", labelKey: "crm.nav.tags", Icon: TagsIcon },
];

const ADMIN_NAV: NavItem[] = [
  { href: "/crm/imports", labelKey: "crm.nav.imports", Icon: ImportsIcon },
  { href: "/crm/settings/custom-fields", labelKey: "crm.nav.customFields", Icon: CustomFieldsIcon },
];

const GOVERNANCE_NAV: NavItem[] = [
  { href: "/crm/command-center", labelKey: "crm.nav.commandCenter", Icon: CommandCenterIcon },
];

interface CrmShellProps {
  /** The page content. */
  children: ReactNode;
}

/**
 * CrmShell — Persistent CRM layout with header + sidebar + content slot.
 *
 * Auth gating:
 *   - Reads useAuth() and redirects to "/" if the session is gone.
 *   - Shows AuthLoadingState while INITIALIZING / REFRESHING / LOGGING_OUT.
 *
 * Routing:
 *   - Uses usePathname() to highlight the active sidebar link.
 *   - Sidebar links use Next.js <Link> for client-side navigation.
 *
 * i18n:
 *   - Uses useI18n() from @/lib/i18n/I18nProvider for all labels.
 *   - Language toggle flips ar ↔ en and persists via the i18n provider.
 */
export function CrmShell({ children }: CrmShellProps) {
  const { state, me, logout } = useAuth();
  const { t, locale, setLocale, direction } = useI18n();
  const router = useRouter();
  const pathname = usePathname();

  // Redirect to the root login flow when the session is definitively gone.
  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/");
    }
  }, [router, state]);

  if (
    state === "INITIALIZING" ||
    state === "REFRESHING" ||
    state === "LOGGING_OUT" ||
    state === "AUTHENTICATING"
  ) {
    return <AuthLoadingState subtitle={t("crm.shell.loading")} />;
  }

  if (state !== "AUTHENTICATED") {
    return <AuthLoadingState subtitle={t("crm.shell.loading")} />;
  }

  function isActive(href: string): boolean {
    if (!pathname) return false;
    // /crm/accounts/[accountId] should still highlight "Accounts".
    if (href === "/crm/accounts" && pathname.startsWith("/crm/accounts")) return true;
    if (href === "/crm/settings/custom-fields" && pathname.startsWith("/crm/settings/custom-fields")) return true;
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  function toggleLocale() {
    setLocale(locale === "ar" ? "en" : "ar");
  }

  async function handleLogout() {
    await logout();
    router.replace("/");
  }

  const displayName = me?.displayName ?? me?.email ?? t("crm.shell.user");

  return (
    <div className={styles.shell} dir={direction}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <Link href="/crm/overview" className={styles.brandMark} aria-label={t("crm.shell.title")}>
            {t("crm.shell.brandMark")}
            <span className={styles.brandMarkGold} />
          </Link>
          <div className={styles.headerTitles}>
            <h1 className={styles.headerTitle}>{t("crm.shell.title")}</h1>
            <p className={styles.headerSubtitle}>{t("crm.shell.subtitle")}</p>
          </div>
        </div>

        <div className={styles.headerRight}>
          <span className={styles.headerSubtitle} aria-label={t("crm.shell.user")}>
            {displayName}
          </span>
          <button
            type="button"
            className={styles.langBtn}
            onClick={toggleLocale}
            aria-label={t("crm.shell.languageToggle")}
          >
            <LangIcon />
            <span>{t("crm.shell.languageToggle")}</span>
          </button>
          <button
            type="button"
            className={styles.headerBtn}
            onClick={() => router.push("/workspace")}
          >
            <BackIcon />
            <span>{t("crm.shell.workspace")}</span>
          </button>
          <button
            type="button"
            className={styles.headerBtn}
            onClick={() => void handleLogout()}
          >
            <span>{t("crm.shell.logout")}</span>
          </button>
        </div>
      </header>

      <div className={styles.body}>
        <aside className={styles.sidebar} aria-label={t("crm.shell.sidebar")}>
          <nav className={styles.sidebarNav}>
            <span className={styles.sidebarSectionLabel}>{t("crm.shell.sidebar.main")}</span>
            {MAIN_NAV.map((item) => (
              <SidebarLink key={item.href} item={item} active={isActive(item.href)} label={t(item.labelKey)} />
            ))}

            <div className={styles.sidebarDivider} />

            <span className={styles.sidebarSectionLabel}>{t("crm.shell.sidebar.admin")}</span>
            {ADMIN_NAV.map((item) => (
              <SidebarLink key={item.href} item={item} active={isActive(item.href)} label={t(item.labelKey)} />
            ))}

            <div className={styles.sidebarDivider} />

            <span className={styles.sidebarSectionLabel}>{t("crm.shell.sidebar.governance")}</span>
            {GOVERNANCE_NAV.map((item) => (
              <SidebarLink key={item.href} item={item} active={isActive(item.href)} label={t(item.labelKey)} />
            ))}
          </nav>
        </aside>

        <main className={styles.content} id="crm-operational-content">
          {children}
        </main>
      </div>
    </div>
  );
}

interface SidebarLinkProps {
  item: NavItem;
  active: boolean;
  label: string;
}

function SidebarLink({ item, active, label }: SidebarLinkProps) {
  const { Icon } = item;
  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      className={`${styles.sidebarItem} ${active ? styles.sidebarItemActive : ""}`}
    >
      <span className={styles.sidebarItemIcon}>
        <Icon />
      </span>
      <span className={styles.sidebarItemLabel}>{label}</span>
    </Link>
  );
}
