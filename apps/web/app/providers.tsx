"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AuthProvider, useAuth } from "@/lib/auth/auth-provider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { ThemeProvider } from "@/lib/theme/ThemeProvider";
import { CRM_ROOT_ENTRY_COOKIE } from "../proxy";

const PROTECTED_ROOTS = ["/workspace", "/crm", "/control-plane"];

function hasCrmRootEntryMarker(): boolean {
  return document.cookie
    .split(";")
    .some((cookie) => cookie.trim() === `${CRM_ROOT_ENTRY_COOKIE}=1`);
}

function setCrmRootEntryMarker(): void {
  document.cookie = `${CRM_ROOT_ENTRY_COOKIE}=1; Path=/; Max-Age=60; SameSite=Lax`;
}

function clearCrmRootEntryMarker(): void {
  document.cookie = `${CRM_ROOT_ENTRY_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}

function AuthRouteRecovery({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    const protectedRoute = PROTECTED_ROOTS.some(
      (root) => pathname === root || pathname.startsWith(`${root}/`),
    );
    if (!protectedRoute) return;

    // Set the CRM root entry marker when navigating to /crm/overview
    // (replaces the cookie that was previously set by middleware.ts)
    if (pathname === "/crm/overview" && !hasCrmRootEntryMarker()) {
      setCrmRootEntryMarker();
    }

    const crmRootEntry = pathname === "/crm/overview" && hasCrmRootEntryMarker();
    const sessionGone = ["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state);

    if (!sessionGone) {
      if (state === "AUTHENTICATED" && crmRootEntry) clearCrmRootEntryMarker();
      return;
    }

    if (crmRootEntry) clearCrmRootEntryMarker();
    const returnUrl = crmRootEntry
      ? "/crm"
      : `${pathname}${window.location.search}${window.location.hash}`;
    router.replace(`/?returnUrl=${encodeURIComponent(returnUrl)}`);
  }, [pathname, router, state]);

  return children;
}

export function Providers({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <I18nProvider>
        <AuthProvider>
          <AuthRouteRecovery>
            <TenantContextProvider>{children}</TenantContextProvider>
          </AuthRouteRecovery>
        </AuthProvider>
      </I18nProvider>
    </ThemeProvider>
  );
}
