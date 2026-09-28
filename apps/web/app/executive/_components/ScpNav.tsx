"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { ScpError, ScpNotice } from "./ScpStates";
import { useScpAccess } from "./ScpAccess";
import styles from "../scp.module.css";

interface NavSection {
  headingKey: string;
  links: Array<{ href: string; labelKey: string; capability: string }>;
}

const SECTIONS: NavSection[] = [
  {
    headingKey: "scp.nav.section.controlPlane",
    links: [
      { href: "/executive", labelKey: "scp.nav.overview", capability: "subscription.read" },
      { href: "/executive/applications", labelKey: "scp.nav.applications", capability: "catalog.read" },
      { href: "/executive/tenants", labelKey: "scp.nav.tenants", capability: "subscription.read" },
      { href: "/executive/subscriptions", labelKey: "scp.nav.subscriptions", capability: "subscription.read" },
      { href: "/executive/plans", labelKey: "scp.nav.plans", capability: "plan.read" },
      { href: "/executive/users", labelKey: "scp.nav.users", capability: "PLATFORM.USER.READ" },
      { href: "/executive/access", labelKey: "scp.nav.access", capability: "PLATFORM.ROLE.READ" },
    ],
  },
  {
    headingKey: "scp.nav.section.operations",
    links: [
      { href: "/executive/entitlements", labelKey: "scp.nav.entitlements", capability: "entitlement.read" },
      { href: "/executive/usage", labelKey: "scp.nav.usage", capability: "usage.read" },
      { href: "/executive/billing", labelKey: "scp.nav.billing", capability: "billing.read" },
      { href: "/executive/provisioning", labelKey: "scp.nav.provisioning", capability: "provisioning.read" },
      { href: "/executive/audit", labelKey: "scp.nav.audit", capability: "audit.read" },
    ],
  },
];

const PLATFORM_NAV_LABELS = {
  ar: { "scp.nav.users": "المستخدمون", "scp.nav.access": "الوصول والصلاحيات" },
  en: { "scp.nav.users": "Users", "scp.nav.access": "Access" },
} as const;

export function ScpNav() {
  const pathname = usePathname();
  const { t, locale } = useI18n();
  const { phase, capabilities, refresh } = useScpAccess();

  const visible = (capability: string): boolean => {
    if (phase === "checking") return true;
    if (phase !== "authorized") return false;
    return capabilities[capability] === true;
  };

  const label = (key: string): string => {
    const labels = PLATFORM_NAV_LABELS[locale === "en" ? "en" : "ar"];
    const platformLabel = labels[key as keyof typeof labels];
    return platformLabel ?? t(key);
  };

  if (phase === "degraded") {
    return (
      <nav className={styles.nav} aria-label={t("scp.nav.ariaLabel")}>
        <ScpError message={t("scp.nav.degraded")} onRetry={() => refresh()} />
      </nav>
    );
  }

  if (phase === "unauthorized") {
    return (
      <nav className={styles.nav} aria-label={t("scp.nav.ariaLabel")}>
        <ScpNotice>{t("scp.nav.unauthorized")}</ScpNotice>
      </nav>
    );
  }

  if (phase === "authorized") {
    const anyVisible = SECTIONS.some((section) =>
      section.links.some((link) => capabilities[link.capability] === true),
    );
    if (!anyVisible) {
      return (
        <nav className={styles.nav} aria-label={t("scp.nav.ariaLabel")}>
          <ScpNotice>{t("scp.nav.noAccess")}</ScpNotice>
        </nav>
      );
    }
  }

  return (
    <nav
      className={styles.nav}
      aria-label={t("scp.nav.ariaLabel")}
      aria-busy={phase === "checking" ? "true" : undefined}
    >
      {SECTIONS.map((section) => {
        const links = section.links.filter((link) => visible(link.capability));
        if (links.length === 0) return null;
        return (
          <div key={section.headingKey}>
            <h2 className={styles.navHeading}>{t(section.headingKey)}</h2>
            {links.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={styles.navLink}
                data-active={pathname === link.href}
                aria-current={pathname === link.href ? "page" : undefined}
              >
                {label(link.labelKey)}
              </Link>
            ))}
          </div>
        );
      })}
    </nav>
  );
}
