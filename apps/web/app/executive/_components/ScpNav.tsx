"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { scpApi, type AccessCheckV2 } from "@/lib/api/scp-api";
import { ScpError, ScpNotice } from "./ScpStates";
import styles from "../scp.module.css";

/**
 * Sidebar navigation for the Subscription Control Plane.
 *
 * Sections are declarative (code, route, required capability) so new pages
 * appear by editing this list — and new *applications* never require nav
 * changes at all (the catalog drives that surface).
 *
 * Capability state machine (explicit, never fail-open):
 *   checking     — the access-check request is in flight; links render
 *                  optimistically for this transient window only.
 *   authorized   — the backend answered with an explicit capability map;
 *                  a link is visible only when its capability is exactly
 *                  `true` (fail-closed: missing keys stay hidden).
 *   unauthorized — the backend answered with authenticated=false; no links.
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

type NavAccessState =
  | { phase: "checking" }
  | { phase: "authorized"; access: AccessCheckV2 }
  | { phase: "unauthorized" }
  | { phase: "degraded" };

export function ScpNav() {
  const pathname = usePathname();
  const { t } = useI18n();
  const [state, setState] = useState<NavAccessState>({ phase: "checking" });
  const mountedRef = useRef(true);

  const check = useCallback(async () => {
    setState({ phase: "checking" });
    try {
      const result = await scpApi.accessCheckV2();
      if (!mountedRef.current) return;
      if (!result.authenticated) {
        setState({ phase: "unauthorized" });
        return;
      }
      setState({ phase: "authorized", access: result });
    } catch {
      // An unavailable capability service must not read as "all allowed".
      if (mountedRef.current) setState({ phase: "degraded" });
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    void check();
    return () => {
      mountedRef.current = false;
    };
  }, [check]);

  const visible = (capability: string): boolean => {
    if (state.phase === "checking") return true; // transient optimistic render
    if (state.phase !== "authorized") return false; // fail-closed
    return state.access.capabilities[capability] === true;
  };

  if (state.phase === "degraded") {
    return (
      <nav className={styles.nav} aria-label={t("scp.nav.ariaLabel")}>
        <ScpError message={t("scp.nav.degraded")} onRetry={() => void check()} />
      </nav>
    );
  }

  if (state.phase === "unauthorized") {
    return (
      <nav className={styles.nav} aria-label={t("scp.nav.ariaLabel")}>
        <ScpNotice>{t("scp.nav.unauthorized")}</ScpNotice>
      </nav>
    );
  }

  return (
    <nav
      className={styles.nav}
      aria-label={t("scp.nav.ariaLabel")}
      aria-busy={state.phase === "checking" ? "true" : undefined}
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
