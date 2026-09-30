"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { ScpError, ScpNotice } from "./ScpStates";
import { useScpAccess } from "./ScpAccess";
import styles from "../scp.module.css";

interface NavSection {
  headingKey: string;
  links: Array<[href: string, labelKey: string, capability: string]>;
}

const SECTIONS: NavSection[] = [
  {
    headingKey: "scp.nav.section.controlPlane",
    links: [
      ["/executive", "scp.nav.overview", "subscription.read"],
      ["/executive/applications", "scp.nav.applications", "catalog.read"],
      ["/executive/tenants", "scp.nav.tenants", "subscription.read"],
      ["/executive/subscriptions", "scp.nav.subscriptions", "subscription.read"],
      ["/executive/plans", "scp.nav.plans", "plan.read"],
      ["/executive/authorization", "scp.nav.authorization", "ROLE.READ"],
      ["/executive/users", "controlPlane.users", "PLATFORM.USER.READ"],
      ["/executive/access", "controlPlane.roles", "PLATFORM.ROLE.READ"],
    ],
  },
  {
    headingKey: "scp.nav.section.operations",
    links: [
      ["/executive/entitlements", "scp.nav.entitlements", "entitlement.read"],
      ["/executive/usage", "scp.nav.usage", "usage.read"],
      ["/executive/billing", "scp.nav.billing", "billing.read"],
      ["/executive/provisioning", "scp.nav.provisioning", "provisioning.read"],
      ["/executive/audit", "scp.nav.audit", "audit.read"],
    ],
  },
];

export function ScpNav() {
  const pathname = usePathname();
  const { t } = useI18n();
  const { phase, capabilities, refresh } = useScpAccess();

  const visible = (capability: string): boolean => {
    if (phase === "checking") return true;
    if (phase !== "authorized") return false;
    return capabilities[capability] === true;
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
      section.links.some(([, , capability]) => capabilities[capability] === true),
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
        const links = section.links.filter(([, , capability]) => visible(capability));
        if (links.length === 0) return null;
        return (
          <div key={section.headingKey}>
            <h2 className={styles.navHeading}>{t(section.headingKey)}</h2>
            {links.map(([href, labelKey]) => (
              <Link
                key={href}
                href={href}
                className={styles.navLink}
                data-active={pathname === href}
                aria-current={pathname === href ? "page" : undefined}
              >
                {t(labelKey)}
              </Link>
            ))}
          </div>
        );
      })}
    </nav>
  );
}
