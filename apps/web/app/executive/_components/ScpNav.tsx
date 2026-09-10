"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { ScpError, ScpNotice } from "./ScpStates";
import { useScpAccess } from "./ScpAccess";
import styles from "../scp.module.css";

/**
 * Sidebar navigation for the Subscription Control Plane.
 *
 * Sections are declarative (code, route, required capability) so new pages
 * appear by editing this list — and new *applications* never require nav
 * changes at all (the catalog drives that surface).
 *
 * Capability state machine (explicit, never fail-open) — R0C-12 Blocker C:
 * the state now comes from the ONE shared ScpAccessProvider (a single
 * access-check/v2 fetch per console session, shared with every page's
 * mutation gates) instead of a nav-private request:
 *   checking     — the access-check request is in flight; links render
 *                  optimistically for this transient window only.
 *   authorized   — a link is visible only when its capability is exactly
 *                  `true` (fail-closed: missing keys stay hidden).
 *                  When authenticated but NO capability is granted, an
 *                  explicit "signed in, no access" notice renders instead
 *                  of an empty nav (AUTHENTICATED_BUT_NO_CAPABILITIES is
 *                  distinct from AUTHENTICATION_FAILURE).
 *   unauthorized — the backend answered with authenticated=false (an
 *                  authentication/session failure); no links.
 *   degraded     — the access-check failed; links are hidden (fail-closed)
 *                  and an explicit error with a retry control is shown.
 *                  A broken capability service is never silently mapped to
 *                  "full access". Server-side authorization remains
 *                  authoritative regardless of what this nav renders.
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
  const { phase, capabilities, refresh } = useScpAccess();

  const visible = (capability: string): boolean => {
    if (phase === "checking") return true; // transient optimistic render
    if (phase !== "authorized") return false; // fail-closed
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

  // AUTHENTICATED_BUT_NO_CAPABILITIES — distinct from an authentication
  // failure: the backend explicitly said authenticated=true, so the session
  // is valid; zero capabilities means this role simply has no SCP powers.
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
                {t(link.labelKey)}
              </Link>
            ))}
          </div>
        );
      })}
    </nav>
  );
}
