// @vitest-environment jsdom

/**
 * Test helper: wraps children with all providers required by the app shell.
 *
 * Use this in any test that renders a component which transitively uses
 * ExecutiveShell (which renders LanguageSwitcher and ThemeSwitcher that
 * require I18nProvider and ThemeProvider respectively).
 */
import type { ReactNode } from "react";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { ThemeProvider } from "@/lib/theme/ThemeProvider";

export function AllProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <I18nProvider>
        <AuthProvider>
          <TenantContextProvider>{children}</TenantContextProvider>
        </AuthProvider>
      </I18nProvider>
    </ThemeProvider>
  );
}

/**
 * Convenience wrapper for tests that only need AuthProvider but render a
 * component that transitively uses ExecutiveShell (which needs Theme/I18n).
 */
export function AuthWithShellProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <I18nProvider>
        <AuthProvider>
          <TenantContextProvider>{children}</TenantContextProvider>
        </AuthProvider>
      </I18nProvider>
    </ThemeProvider>
  );
}
