"use client";

import type { ComponentType, ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import {
  SnadModuleShell,
  AccountsIcon,
  AdminIcon,
  AssignmentsIcon,
  ComplianceIcon,
  CustomFieldsIcon,
  ExecutionIcon,
  NotesIcon,
  OrgIcon,
  OverviewIcon,
  PeopleIcon,
  PolicyIcon,
  ReportsIcon,
  TasksIcon,
  type SnadModuleNavSection,
} from "@/components/sds/module";
import { ScpAccessProvider, useScpAccess } from "./ScpAccess";
import { ScpAuthGate } from "./ScpStates";

type CapabilityRequirement = string | readonly string[];

interface ExecutiveNavItem {
  href: string;
  labelKey: string;
  capability: CapabilityRequirement;
  Icon: ComponentType;
}

interface ExecutiveNavSection {
  headingKey: string;
  links: ExecutiveNavItem[];
}

const EXECUTIVE_NAV: ExecutiveNavSection[] = [
  {
    headingKey: "scp.nav.section.controlPlane",
    links: [
      { href: "/executive", labelKey: "scp.nav.overview", capability: "subscription.read", Icon: OverviewIcon },
      { href: "/executive/applications", labelKey: "scp.nav.applications", capability: "catalog.read", Icon: CustomFieldsIcon },
      { href: "/executive/tenants", labelKey: "scp.nav.tenants", capability: "subscription.read", Icon: OrgIcon },
      { href: "/executive/subscriptions", labelKey: "scp.nav.subscriptions", capability: "subscription.read", Icon: PolicyIcon },
      { href: "/executive/plans", labelKey: "scp.nav.plans", capability: "plan.read", Icon: NotesIcon },
      { href: "/executive/users", labelKey: "controlPlane.users", capability: "PLATFORM.USER.READ", Icon: PeopleIcon },
      { href: "/executive/access", labelKey: "controlPlane.roles", capability: "PLATFORM.ROLE.READ", Icon: AdminIcon },
      {
        href: "/executive/authorization",
        labelKey: "scp.nav.authorization",
        capability: ["ROLE.READ", "AUTHORIZATION.OVERRIDE.MANAGE", "AUTHORIZATION.RELATIONSHIP.MANAGE"],
        Icon: ComplianceIcon,
      },
    ],
  },
  {
    headingKey: "scp.nav.section.operations",
    links: [
      { href: "/executive/entitlements", labelKey: "scp.nav.entitlements", capability: "entitlement.read", Icon: AssignmentsIcon },
      { href: "/executive/usage", labelKey: "scp.nav.usage", capability: "usage.read", Icon: ReportsIcon },
      { href: "/executive/billing", labelKey: "scp.nav.billing", capability: "billing.read", Icon: AccountsIcon },
      { href: "/executive/provisioning", labelKey: "scp.nav.provisioning", capability: "provisioning.read", Icon: ExecutionIcon },
      { href: "/executive/audit", labelKey: "scp.nav.audit", capability: "audit.read", Icon: TasksIcon },
    ],
  },
];

function requirementGranted(
  capabilities: Record<string, boolean>,
  requirement: CapabilityRequirement,
): boolean {
  const required = Array.isArray(requirement) ? requirement : [requirement];
  return required.some((capability) => capabilities[capability] === true);
}

function ExecutiveModuleWorkspace({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const access = useScpAccess();
  const { t, locale, setLocale, direction } = useI18n();
  const router = useRouter();
  const pathname = usePathname();

  const visible = (requirement: CapabilityRequirement): boolean => {
    if (access.phase === "checking") return true;
    if (access.phase !== "authorized") return false;
    return requirementGranted(access.capabilities, requirement);
  };

  const navSections: SnadModuleNavSection[] = EXECUTIVE_NAV.map((section) => ({
    label: t(section.headingKey),
    items: section.links
      .filter((item) => visible(item.capability))
      .map((item) => ({
        href: item.href,
        label: t(item.labelKey),
        Icon: item.Icon,
      })),
  }));

  function toggleLocale() {
    setLocale(locale === "ar" ? "en" : "ar");
  }

  async function handleLogout() {
    await auth.logout();
    router.replace("/");
  }

  return (
    <SnadModuleShell
      brandMark={t("scp.layout.brandMark")}
      brandHref="/executive"
      brandAriaLabel={t("scp.layout.logoAriaLabel")}
      title={t("scp.layout.title")}
      subtitle={t("scp.layout.subtitle")}
      navLabel={t("scp.nav.ariaLabel")}
      navSections={navSections}
      activeHref={pathname ?? "/executive"}
      contentId="executive-module-main"
      dir={direction}
      user={auth.me?.displayName ?? auth.me?.email}
      userAriaLabel={t("scp.layout.user")}
      languageLabel={t("scp.layout.language")}
      onToggleLanguage={toggleLocale}
      backLabel={t("scp.layout.workspace")}
      onBack={() => router.push("/workspace")}
      logoutLabel={t("scp.layout.logout")}
      onLogout={() => void handleLogout()}
    >
      {children}
    </SnadModuleShell>
  );
}

/**
 * Executive module frame.
 *
 * The legacy ExecutiveShell + ScpLayout double-shell is retired here.
 * Executive, Users, Access and Authorization now consume the exact shared
 * SNAD module shell used by CRM and HR. Capability filtering stays fail-closed
 * and backend authorization remains authoritative.
 */
export function ScpLayout({ children }: { children: ReactNode }) {
  return (
    <ScpAuthGate>
      <ScpAccessProvider>
        <ExecutiveModuleWorkspace>{children}</ExecutiveModuleWorkspace>
      </ScpAccessProvider>
    </ScpAuthGate>
  );
}
