"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AuthProvider, useAuth } from "@/lib/auth/auth-provider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { ThemeProvider } from "@/lib/theme/ThemeProvider";

const PROTECTED_ROOTS = ["/workspace", "/crm", "/control-plane"];

function AuthRouteRecovery({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    const protectedRoute = PROTECTED_ROOTS.some(
      (root) => pathname === root || pathname.startsWith(`${root}/`),
    );
    const sessionGone = ["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state);
    if (!protectedRoute || !sessionGone) return;
    const returnUrl = `${pathname}${window.location.search}${window.location.hash}`;
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
