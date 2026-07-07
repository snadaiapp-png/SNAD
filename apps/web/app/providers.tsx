"use client";

import type { ReactNode } from "react";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { ThemeProvider } from "@/lib/theme/ThemeProvider";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <I18nProvider>
        <AuthProvider>
          <TenantContextProvider>
            {children}
          </TenantContextProvider>
        </AuthProvider>
      </I18nProvider>
    </ThemeProvider>
  );
}
