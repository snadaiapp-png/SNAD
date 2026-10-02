"use client";

import { useEffect, useMemo, type ComponentType, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { hasAnyCapability, hasCapability } from "@/lib/auth/capabilities";
import type { MeResponse } from "@/lib/api/auth";
import { useI18n } from "@/lib/i18n/I18nProvider";
import {
  SnadModuleShell,
  AccountsIcon,
  ActivitiesIcon,
  CasesIcon,
  ContactsIcon,
  CustomFieldsIcon,
  ExecutionIcon,
  ImportsIcon,
  IntelligenceIcon,
  LeadsIcon,
  NotesIcon,
  OpportunitiesIcon,
  OverviewIcon,
  PipelinesIcon,
  ReportsIcon,
  SearchIcon,
  TagsIcon,
  TasksIcon,
} from "@/components/sds/module";

interface NavItem {
  href: string;
  labelKey: string;
  Icon: ComponentType;
  /** Optional single capability required to show this nav item. Backend is always authoritative. */
  capability?: string;
  /**
   * Optional ANY-OF capability list. When present, the nav item is shown if the
   * user has AT LEAST ONE of the listed capabilities. Takes precedence over
   * `capability` (single). Used for surfaces that should be discoverable to any
   * legitimate CRM operational reader (e.g. Execution Board).
   */
  capabilities?: string[];
}

const MAIN_NAV: NavItem[] = [
  { href: "/crm/overview", labelKey: "crm.nav.overview", Icon: OverviewIcon },
  { href: "/crm/accounts", labelKey: "crm.nav.accounts", Icon: AccountsIcon, capability: "CRM.ACCOUNT.READ" },
  { href: "/crm/contacts", labelKey: "crm.nav.contacts", Icon: ContactsIcon, capability: "CRM.CONTACT.READ" },
  { href: "/crm/leads", labelKey: "crm.nav.leads", Icon: LeadsIcon, capability: "CRM.LEAD.READ" },
  { href: "/crm/pipelines", labelKey: "crm.nav.pipelines", Icon: PipelinesIcon, capability: "CRM.OPPORTUNITY.READ" },
  { href: "/crm/opportunities", labelKey: "crm.nav.opportunities", Icon: OpportunitiesIcon, capability: "CRM.OPPORTUNITY.READ" },
  { href: "/crm/activities", labelKey: "crm.nav.activities", Icon: ActivitiesIcon, capability: "CRM.ACTIVITY.READ" },
  { href: "/crm/tags", labelKey: "crm.nav.tags", Icon: TagsIcon, capability: "CRM.TAG.READ" },
  { href: "/crm/search", labelKey: "crm.nav.search", Icon: SearchIcon },
  { href: "/crm/reports", labelKey: "crm.nav.reports", Icon: ReportsIcon, capability: "CRM.REPORTS.READ" },
  { href: "/crm/notes", labelKey: "crm.nav.notes", Icon: NotesIcon, capability: "CRM.NOTE.READ" },
  { href: "/crm/tasks", labelKey: "crm.nav.tasks", Icon: TasksIcon, capability: "CRM.TASK.READ" },
  { href: "/crm/cases", labelKey: "crm.nav.cases", Icon: CasesIcon, capability: "CRM.CASE.READ" },
  { href: "/crm/intelligence", labelKey: "crm.nav.intelligence", Icon: IntelligenceIcon, capability: "CRM.CUSTOMER_INTELLIGENCE.READ" },
];

const ADMIN_NAV: NavItem[] = [
  { href: "/crm/imports", labelKey: "crm.nav.imports", Icon: ImportsIcon, capability: "CRM.IMPORT.READ" },
  { href: "/crm/settings/custom-fields", labelKey: "crm.nav.customFields", Icon: CustomFieldsIcon, capability: "CRM.CUSTOM_FIELD.READ" },
];

const EXECUTION_NAV: NavItem[] = [
  // Execution Board displays the G0-G10 strategic execution plan. It is an
  // operational CRM surface: any authenticated user holding at least one
  // legitimate CRM operational READ capability can discover and open it.
  // CRM.ADMIN is retained in the ANY-OF list so existing tenant CRM
  // administrators continue to see the nav. The route-level guard
  // (apps/web/app/crm/(operational)/execution/page.tsx) enforces the same
  // ANY-OF policy on direct URL access.
  {
    href: "/crm/execution",
    labelKey: "crm.nav.execution",
    Icon: ExecutionIcon,
    capabilities: [
      "CRM.ACCOUNT.READ",
      "CRM.CONTACT.READ",
      "CRM.LEAD.READ",
      "CRM.OPPORTUNITY.READ",
      "CRM.ACTIVITY.READ",
      "CRM.TASK.READ",
      "CRM.NOTE.READ",
      "CRM.TAG.READ",
      "CRM.ADMIN",
    ],
  },
];

interface CrmShellProps {
  /** The page content. */
  children: ReactNode;
}

/**
 * CrmShell — Persistent CRM layout rendered through the SHARED SNAD module
 * shell primitive (components/sds/module). CRM is the visual reference the
 * shared contract was extracted from; it consumes the same primitives as
 * every other module so no third identity can emerge.
 *
 * Auth gating:
 *   - Reads useAuth() and redirects to "/" if the session is gone.
 *   - Shows the shared auth loading surface while
 *     INITIALIZING / REFRESHING / LOGGING_OUT / AUTHENTICATING.
 *
 * Routing:
 *   - usePathname() feeds the shared shell's active-link resolution, so
 *     /crm/accounts/[accountId] keeps highlighting "Accounts".
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

  // Filter nav items based on user capabilities.
  // IMPORTANT: These hooks MUST be called before any conditional early returns
  // to satisfy React's Rules of Hooks (hooks must not be called conditionally).
  const filteredMainNav = useMemo(
    () => MAIN_NAV.filter((item) => navItemVisible(me, item)),
    [me],
  );
  const filteredAdminNav = useMemo(
    () => ADMIN_NAV.filter((item) => navItemVisible(me, item)),
    [me],
  );
  const filteredExecutionNav = useMemo(
    () => EXECUTION_NAV.filter((item) => navItemVisible(me, item)),
    [me],
  );

  // Redirect to the root login flow when the session is definitively gone.
  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/");
    }
  }, [router, state]);

  function toggleLocale() {
    setLocale(locale === "ar" ? "en" : "ar");
  }

  async function handleLogout() {
    await logout();
    router.replace("/");
  }

  const authLoading =
    state === "INITIALIZING" ||
    state === "REFRESHING" ||
    state === "LOGGING_OUT" ||
    state === "AUTHENTICATING" ||
    state !== "AUTHENTICATED";

  const displayName = me?.displayName ?? me?.email ?? t("crm.shell.user");

  const navSections = [
    { label: t("crm.shell.sidebar.main"), items: filteredMainNav.map((item) => ({ href: item.href, label: t(item.labelKey), Icon: item.Icon })) },
    { label: t("crm.shell.sidebar.admin"), items: filteredAdminNav.map((item) => ({ href: item.href, label: t(item.labelKey), Icon: item.Icon })) },
    { label: t("crm.shell.sidebar.execution"), items: filteredExecutionNav.map((item) => ({ href: item.href, label: t(item.labelKey), Icon: item.Icon })) },
  ];

  return (
    <SnadModuleShell
      brandMark={t("crm.shell.brandMark")}
      brandHref="/crm/overview"
      brandAriaLabel={t("crm.shell.title")}
      title={t("crm.shell.title")}
      subtitle={t("crm.shell.subtitle")}
      navLabel={t("crm.shell.sidebar")}
      navSections={navSections}
      activeHref={pathname ?? "/crm/overview"}
      contentId="crm-operational-content"
      dir={direction}
      user={displayName}
      userAriaLabel={t("crm.shell.user")}
      languageLabel={t("crm.shell.languageToggle")}
      onToggleLanguage={toggleLocale}
      backLabel={t("crm.shell.workspace")}
      onBack={() => router.push("/workspace")}
      logoutLabel={t("crm.shell.logout")}
      onLogout={() => void handleLogout()}
      authLoading={authLoading}
      authLoadingSubtitle={t("crm.shell.loading")}
    >
      {children}
    </SnadModuleShell>
  );
}

/**
 * Predicate that resolves a nav item's visibility for the current session.
 *
 * - If `item.capabilities` (ANY-OF) is present: the item is shown when the
 *   user holds AT LEAST ONE of the listed capabilities.
 * - Otherwise, if `item.capability` (single) is present: the item is shown
 *   when the user holds that capability.
 * - Otherwise (no capability requirement): the item is always shown.
 *
 * The backend remains the authoritative RBAC enforcement; this is a UX
 * guard that hides links the user cannot use to reduce 403s.
 */
function navItemVisible(me: MeResponse | null, item: NavItem): boolean {
  if (Array.isArray(item.capabilities) && item.capabilities.length > 0) {
    return hasAnyCapability(me, item.capabilities);
  }
  if (item.capability) {
    return hasCapability(me, item.capability);
  }
  return true;
}
