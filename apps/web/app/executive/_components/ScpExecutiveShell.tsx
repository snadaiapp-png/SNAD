"use client";

import type { ReactNode } from "react";
import { ExecutiveShell } from "@/components/shell";
import { useI18n } from "@/lib/i18n/I18nProvider";

/**
 * R0C-12 G6-R2 — client boundary for the shared executive shell.
 *
 * The executive layout is a server component and `useI18n` is a client hook,
 * so the logo aria label is translated here through `t()` (keys maintained in
 * BOTH `lib/i18n/locales/ar.ts` and `en.ts`) instead of being hardcoded.
 * ExecutiveShell itself is untouched — this wrapper only supplies the
 * translated label.
 */
export function ScpExecutiveShell({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  return (
    <ExecutiveShell
      logoHref="/executive"
      logoAriaLabel={t("scp.layout.logoAriaLabel")}
    >
      {children}
    </ExecutiveShell>
  );
}
