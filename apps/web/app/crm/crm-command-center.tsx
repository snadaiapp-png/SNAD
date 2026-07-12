"use client";

import { useEffect, useState, type ComponentType } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { CrmI18nProvider, useCrmI18n } from "./crm-i18n";
import { CrmEmptyState } from "./crm-empty-state";
import { CrmOverview } from "./crm-overview";
import { CrmExecutionBoard } from "./crm-execution-board";
import styles from "./crm-command-center.module.css";

/* ============================================================================
 *  Tab definitions
 * ----------------------------------------------------------------------------
 *  16 tabs split across 3 sidebar groups:
 *    • main      — 10 tabs (overview + 9 CRM domain modules)
 *    • advanced   —  5 tabs (mobileSync, callerId, aiCrm, billing, settings)
 *    • execution  —  1 tab  (executionBoard)
 *
 *  Only `overview` and `executionBoard` render real content. Every other tab
 *  renders a CrmEmptyState with a tab-specific subtitle key.
 * ============================================================================ */

type TabId =
  | "overview"
  | "leads"
  | "customers"
  | "contacts"
  | "opportunities"
  | "pipeline"
  | "tasks"
  | "transfers"
  | "employees"
  | "reports"
  | "mobileSync"
  | "callerId"
  | "aiCrm"
  | "billing"
  | "settings"
  | "executionBoard";

interface TabDef {
  id: TabId;
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

function LeadsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="6" cy="5" r="2.5" />
      <path d="M2 13.5c0-2.2 1.8-4 4-4s4 1.8 4 4" />
      <path d="M11 5v4M9 7h4" />
    </svg>
  );
}

function CustomersIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="5.5" cy="5" r="2.2" />
      <circle cx="11" cy="6" r="1.8" />
      <path d="M1.5 13c0-2.2 1.8-4 4-4s4 1.8 4 4" />
      <path d="M9.5 13c0-1.6 1-3 2.5-3.5 1.5.5 2.5 1.9 2.5 3.5" />
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
      <line x1="13" y1="10.5" x2="14.5" y2="10.5" />
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

function PipelineIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="3" width="3.5" height="10" rx="0.5" />
      <rect x="6.5" y="3" width="3" height="7" rx="0.5" />
      <rect x="10.5" y="3" width="3.5" height="12" rx="0.5" />
    </svg>
  );
}

function TasksIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="2" width="12" height="12" rx="1.5" />
      <path d="M5 8l2 2 4-4" />
    </svg>
  );
}

function TransfersIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 6h9M10 4l2 2-2 2" />
      <path d="M13 10H4M6 8l-2 2 2 2" />
    </svg>
  );
}

function EmployeesIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="6" width="10" height="8" rx="1" />
      <path d="M6 6V4.5C6 3.7 6.7 3 7.5 3h1C9.3 3 10 3.7 10 4.5V6" />
      <line x1="3" y1="10" x2="13" y2="10" />
    </svg>
  );
}

function ReportsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="2.5" y1="13.5" x2="13.5" y2="13.5" />
      <rect x="4" y="9" width="2" height="4" />
      <rect x="7" y="6" width="2" height="7" />
      <rect x="10" y="3" width="2" height="10" />
    </svg>
  );
}

function MobileSyncIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="4" y="2" width="8" height="12" rx="1.2" />
      <line x1="4" y1="11" x2="12" y2="11" />
      <path d="M6.5 6.5a2 2 0 1 1 0 3M9.5 9.5a2 2 0 1 1 0-3" />
    </svg>
  );
}

function CallerIdIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 2.5h2.5l1.2 3-1.5 1c.5 1.5 1.8 2.8 3.3 3.3l1-1.5 3 1.2V11.5c0 .8-.7 1.5-1.5 1.5C8 13 3 8 3 4c0-.8.7-1.5 1.5-1.5z" />
    </svg>
  );
}

function AiCrmIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M8 2l1 2.5L11.5 6 9 7.5 8 10l-1-2.5L4.5 6 7 4.5z" />
      <path d="M12.5 9.5l.5 1.2 1.2.5-1.2.5-.5 1.2-.5-1.2L11 11.2l1.2-.5z" />
      <path d="M3.5 10l.4 1 1 .4-1 .4-.4 1-.4-1-1-.4 1-.4z" />
    </svg>
  );
}

function BillingIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="4" width="12" height="9" rx="1.2" />
      <line x1="2" y1="7" x2="14" y2="7" />
      <line x1="5" y1="10" x2="8" y2="10" />
    </svg>
  );
}

function SettingsIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="8" cy="8" r="2" />
      <path d="M8 2v1.5M8 12.5V14M2 8h1.5M12.5 8H14M3.8 3.8l1 1M11.2 11.2l1 1M12.2 3.8l-1 1M4.8 11.2l-1 1" />
    </svg>
  );
}

function ExecutionBoardIcon() {
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

const MAIN_TABS: TabDef[] = [
  { id: "overview", labelKey: "tab.overview", Icon: OverviewIcon },
  { id: "leads", labelKey: "tab.leads", Icon: LeadsIcon },
  { id: "customers", labelKey: "tab.customers", Icon: CustomersIcon },
  { id: "contacts", labelKey: "tab.contacts", Icon: ContactsIcon },
  { id: "opportunities", labelKey: "tab.opportunities", Icon: OpportunitiesIcon },
  { id: "pipeline", labelKey: "tab.pipeline", Icon: PipelineIcon },
  { id: "tasks", labelKey: "tab.tasks", Icon: TasksIcon },
  { id: "transfers", labelKey: "tab.transfers", Icon: TransfersIcon },
  { id: "employees", labelKey: "tab.employees", Icon: EmployeesIcon },
  { id: "reports", labelKey: "tab.reports", Icon: ReportsIcon },
];

const ADVANCED_TABS: TabDef[] = [
  { id: "mobileSync", labelKey: "tab.mobileSync", Icon: MobileSyncIcon },
  { id: "callerId", labelKey: "tab.callerId", Icon: CallerIdIcon },
  { id: "aiCrm", labelKey: "tab.aiCrm", Icon: AiCrmIcon },
  { id: "billing", labelKey: "tab.billing", Icon: BillingIcon },
  { id: "settings", labelKey: "tab.settings", Icon: SettingsIcon },
];

const EXECUTION_TABS: TabDef[] = [
  { id: "executionBoard", labelKey: "tab.executionBoard", Icon: ExecutionBoardIcon },
];

/* ============================================================================
 *  Sidebar item
 * ============================================================================ */

interface SidebarItemProps {
  tab: TabDef;
  active: boolean;
  onSelect: (id: TabId) => void;
  label: string;
}

function SidebarItem({ tab, active, onSelect, label }: SidebarItemProps) {
  const { Icon } = tab;
  return (
    <button
      type="button"
      className={`${styles.sidebarItem} ${active ? styles.sidebarItemActive : ""}`}
      onClick={() => onSelect(tab.id)}
      aria-current={active ? "page" : undefined}
    >
      <span className={styles.sidebarItemIcon}>
        <Icon />
      </span>
      <span className={styles.sidebarItemLabel}>{label}</span>
    </button>
  );
}

/* ============================================================================
 *  Inner shell — lives inside CrmI18nProvider so it can read i18n state
 * ============================================================================ */

function CrmCommandCenterInner() {
  const { state } = useAuth();
  const { t, dir, toggleLang } = useCrmI18n();
  const router = useRouter();
  const [tab, setTab] = useState<TabId>("overview");

  // Redirect to the workspace when the session is definitively gone.
  // While INITIALIZING / REFRESHING we show the AuthLoadingState.
  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/");
    }
  }, [router, state]);

  if (state !== "AUTHENTICATED") {
    return <AuthLoadingState />;
  }

  function renderContent() {
    switch (tab) {
      case "overview":
        return <CrmOverview />;
      case "executionBoard":
        return <CrmExecutionBoard />;
      default:
        // Every unbuilt tab gets a tab-specific subtitle.
        return <CrmEmptyState subtitleKey={`empty.${tab}`} />;
    }
  }

  return (
    <div className={styles.shell} dir={dir}>
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.brandMark} aria-hidden="true">
            SNAD
            <span className={styles.brandMarkGold} />
          </span>
          <div className={styles.headerTitles}>
            <h1 className={styles.headerTitle}>{t("crm.title.ar")}</h1>
            <p className={styles.headerSubtitle}>{t("crm.subtitle")}</p>
          </div>
        </div>

        <div className={styles.headerRight}>
          <button
            type="button"
            className={styles.langBtn}
            onClick={toggleLang}
            aria-label={t("crm.langToggle")}
          >
            <LangIcon />
            <span>{t("crm.langToggle")}</span>
          </button>
          <button
            type="button"
            className={styles.headerBtn}
            onClick={() => router.push("/workspace")}
          >
            <BackIcon />
            <span>{t("crm.backToWorkspace")}</span>
          </button>
        </div>
      </header>

      {/* ── Body: sidebar + content ────────────────────────────────────── */}
      <div className={styles.body}>
        <aside className={styles.sidebar} aria-label={t("sidebar.main")}>
          <nav className={styles.sidebarNav}>
            <span className={styles.sidebarSectionLabel}>{t("sidebar.main")}</span>
            {MAIN_TABS.map((tabDef) => (
              <SidebarItem
                key={tabDef.id}
                tab={tabDef}
                active={tab === tabDef.id}
                onSelect={setTab}
                label={t(tabDef.labelKey)}
              />
            ))}

            <div className={styles.sidebarDivider} />

            <span className={styles.sidebarSectionLabel}>{t("sidebar.advanced")}</span>
            {ADVANCED_TABS.map((tabDef) => (
              <SidebarItem
                key={tabDef.id}
                tab={tabDef}
                active={tab === tabDef.id}
                onSelect={setTab}
                label={t(tabDef.labelKey)}
              />
            ))}

            <div className={styles.sidebarDivider} />

            <span className={styles.sidebarSectionLabel}>{t("sidebar.execution")}</span>
            {EXECUTION_TABS.map((tabDef) => (
              <SidebarItem
                key={tabDef.id}
                tab={tabDef}
                active={tab === tabDef.id}
                onSelect={setTab}
                label={t(tabDef.labelKey)}
              />
            ))}
          </nav>
        </aside>

        <main className={styles.content} id="crm-command-center-content">
          {renderContent()}
        </main>
      </div>
    </div>
  );
}

/**
 * CRM Command Center Page (default export)
 * ----------------------------------------
 * Wraps the inner shell in CrmI18nProvider so all child components can read
 * the active language/direction via useCrmI18n(). Auth gating happens inside
 * the inner shell via useAuth().
 */
export default function CrmCommandCenterPage() {
  return (
    <CrmI18nProvider>
      <CrmCommandCenterInner />
    </CrmI18nProvider>
  );
}
