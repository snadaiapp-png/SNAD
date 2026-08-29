"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { scpApi, type AccessCheckV2 } from "@/lib/api/scp-api";
import styles from "../scp.module.css";

/**
 * Sidebar navigation for the Subscription Control Plane.
 *
 * Sections are declarative (code, route, required capability) so new pages
 * appear by editing this list — and new *applications* never require nav
 * changes at all (the catalog drives that surface).
 */
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

export function ScpNav() {
  const pathname = usePathname();
  const { t } = useI18n();
  const [access, setAccess] = useState<AccessCheckV2 | null>(null);

  useEffect(() => {
    let cancelled = false;
    scpApi
      .accessCheckV2()
      .then((result) => {
        if (!cancelled) setAccess(result);
      })
      .catch(() => {
        // nav stays fully visible when capability data is unavailable;
        // every endpoint still enforces authorization server-side
        if (!cancelled) setAccess({ authenticated: false, capabilities: {} });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const allowed = (capability: string): boolean => {
    if (!access) return true; // optimistic render before the check resolves
    const value = access.capabilities[capability];
    return value === undefined ? true : value;
  };

  return (
    <nav className={styles.nav} aria-label={t("scp.nav.ariaLabel")}>
      {SECTIONS.map((section) => {
        const links = section.links.filter((link) => allowed(link.capability));
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
                {t(link.labelKey)}
              </Link>
            ))}
          </div>
        );
      })}
    </nav>
  );
}
