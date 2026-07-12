import type { ReactNode } from "react";
import { CrmShell } from "../components/crm-shell";

/**
 * CRM operational layout.
 *
 * Wraps every /crm/* route (except /crm/command-center which keeps its own
 * independent shell) in the CrmShell which provides:
 *   - Auth gating via useAuth()
 *   - Sidebar navigation with URL-aware active state (usePathname)
 *   - Header with brand, language toggle, workspace link, logout
 *   - Children rendered in <main className={styles.content}>
 *
 * The shell is a client component because it relies on useAuth/usePathname,
 * but this layout file itself is a server component — Next.js handles the
 * boundary automatically.
 */
export default function CrmLayout({ children }: { children: ReactNode }) {
  return <CrmShell>{children}</CrmShell>;
}
