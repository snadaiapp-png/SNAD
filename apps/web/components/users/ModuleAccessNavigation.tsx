"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { usersApi, type ModuleProvisioningContext } from "@/lib/api/users";
import { useAuth } from "@/lib/auth/auth-provider";
import { GlobalUserProvisioningLauncher } from "./GlobalUserProvisioningLauncher";
import styles from "./ModuleAccessNavigation.module.css";

export interface ModuleAccessNavigationProps {
  routePath: string;
  presentation: "sidebar" | "floating";
  sidebarItemClassName?: string;
  sidebarIconClassName?: string;
  sidebarLabelClassName?: string;
  sidebarDividerClassName?: string;
  sidebarSectionLabelClassName?: string;
}

function routeRoot(pathname: string): string {
  return pathname.trim().toLowerCase().replace(/^\/+/, "").split("/")[0] ?? "";
}

function moduleHref(kind: "users" | "access", root: string, returnTo: string): string {
  const params = new URLSearchParams({ module: root, returnTo });
  return `/management/${kind}?${params.toString()}`;
}

export function ModuleAccessNavigation({
  routePath,
  presentation,
  sidebarItemClassName,
  sidebarIconClassName,
  sidebarLabelClassName,
  sidebarDividerClassName,
  sidebarSectionLabelClassName,
}: ModuleAccessNavigationProps) {
  const { state } = useAuth();
  const root = useMemo(() => routeRoot(routePath), [routePath]);
  const [resolved, setResolved] = useState<{ root: string; context: ModuleProvisioningContext | null } | null>(null);

  useEffect(() => {
    let cancelled = false;

    // Protected module IAM discovery must never race session bootstrap/refresh.
    // Access tokens are intentionally memory-only; after a hard navigation the
    // AuthProvider must restore the session before any governed IAM call leaves
    // the browser. This prevents transient 401s and unnecessary refresh churn.
    if (state !== "AUTHENTICATED" || !root) {
      return () => { cancelled = true; };
    }

    // Shared-shell modules already render this navigation in their sidebar.
    // The global floating fallback remains mounted for legacy/future surfaces,
    // but must not trigger a duplicate authenticated IAM discovery request.
    if (
      presentation === "floating" &&
      document.querySelector('[data-snad-module-shell="true"]')
    ) {
      return () => { cancelled = true; };
    }

    usersApi.moduleContext(root)
      .then((value) => {
        if (!cancelled) setResolved({ root, context: value });
      })
      .catch(() => {
        // Fail closed: unregistered routes and unauthorized sessions get no
        // module IAM navigation. Backend authorization remains authoritative.
        if (!cancelled) setResolved({ root, context: null });
      });

    return () => {
      cancelled = true;
    };
  }, [presentation, root, state]);

  const context = resolved?.root === root ? resolved.context : null;
  // Never render previously resolved IAM navigation after the session leaves
  // AUTHENTICATED; a later authenticated transition will re-resolve context.
  if (state !== "AUTHENTICATED" || !context) return null;

  const moduleName = context.localizedName || context.name || context.applicationCode;
  const usersHref = moduleHref("users", root, routePath);
  const accessHref = moduleHref("access", root, routePath);

  if (presentation === "floating") {
    return (
      <aside className={styles.floating} data-testid="global-module-access-navigation" aria-label={`إدارة مستخدمي وصلاحيات ${moduleName}`}>
        <strong className={styles.floatingTitle}>{moduleName}</strong>
        <div className={styles.floatingLinks}>
          <Link href={usersHref}>المستخدمون</Link>
          <Link href={accessHref}>الصلاحيات</Link>
        </div>
      </aside>
    );
  }

  return (
    <div data-testid="module-access-navigation" data-application-code={context.applicationCode}>
      {sidebarDividerClassName ? <div className={sidebarDividerClassName} /> : null}
      <span className={sidebarSectionLabelClassName}>المستخدمون والصلاحيات</span>
      <GlobalUserProvisioningLauncher
        presentation="menu"
        menuItemClassName={sidebarItemClassName}
        menuIconClassName={sidebarIconClassName}
        menuLabelClassName={sidebarLabelClassName}
      />
      <Link href={usersHref} className={sidebarItemClassName}>
        <span className={sidebarIconClassName}><UsersGlyph /></span>
        <span className={sidebarLabelClassName}>المستخدمون</span>
      </Link>
      <Link href={accessHref} className={sidebarItemClassName}>
        <span className={sidebarIconClassName}><ShieldGlyph /></span>
        <span className={sidebarLabelClassName}>الصلاحيات</span>
      </Link>
    </div>
  );
}

export function routeRootForModuleAccess(pathname: string): string {
  return routeRoot(pathname);
}

function UsersGlyph() {
  return (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="7" cy="6.5" r="3" />
      <path d="M2 16c.7-3 2.4-4.5 5-4.5s4.3 1.5 5 4.5" />
      <circle cx="14.5" cy="7.5" r="2.2" />
      <path d="M13 12.3c2.6-.2 4.2 1 5 3.7" />
    </svg>
  );
}

function ShieldGlyph() {
  return (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M10 2.5l6 2v4.8c0 4.1-2.2 6.6-6 8.2-3.8-1.6-6-4.1-6-8.2V4.5l6-2z" />
      <path d="M7.5 10l1.6 1.6 3.5-3.6" />
    </svg>
  );
}

export default ModuleAccessNavigation;
