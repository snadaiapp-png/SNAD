import type { ReactNode } from "react";
import { CrmI18nProvider } from "../crm-i18n";
import { CrmShell } from "../components/crm-shell";

/**
 * CRM operational layout.
 *
 * Wraps every /crm/* route (except /crm/command-center which keeps its own
 * independent shell) in the CrmI18nProvider and CrmShell which provides:
 *   - CRM-specific i18n context (useCrmI18n) for ar/en translations
 *   - Auth gating via useAuth()
 *   - Sidebar navigation with URL-aware active state (usePathname)
 *   - Header with brand, language toggle, workspace link, logout
 *   - Children rendered in <main className={styles.content}>
 *
 * The shell and i18n provider are client components because they rely on
 * useAuth/usePathname/useState, but this layout file itself is a server
 * component — Next.js handles the boundary automatically.
 */
export default function CrmLayout({ children }: { children: ReactNode }) {
  return (
    <CrmI18nProvider>
      <CrmShell>{children}</CrmShell>
    </CrmI18nProvider>
  );
}
