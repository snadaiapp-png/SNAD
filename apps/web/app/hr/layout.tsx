import type { ReactNode } from "react";

/**
 * HR route layout — G1-T12 fix.
 * ============================================================================
 * Wraps all HR routes with the HrI18nAugmenter so HRM G1 translation keys
 * are available within /hr/* routes without being included in the global
 * critical bundle (login, executive, control-plane routes).
 *
 * This is a Server Component that renders a Client Component child — the
 * augmenter must be a Client Component because it uses React Context.
 *
 * Architecture:
 *   - The root layout (app/layout.tsx) wraps the entire app with <I18nProvider>
 *     which provides the global ar/en dictionaries.
 *   - This HR layout wraps all /hr/* routes with <HrI18nAugmenter> which
 *     statically imports HRM_G1_I18N_AR/EN (bundled with the HR route chunk)
 *     and overrides the I18nContext with an augmented `t` function.
 *   - Result: HRM G1 dictionary (105+ keys × 2 locales) is bundled only with
 *     /hr/* route chunks, not with login/executive/control-plane routes.
 *
 * Preserved invariants:
 *   - Arabic + English (both locales covered via the augmenter)
 *   - i18n parity (HRM namespace has its own parity)
 *   - SSR correctness (the augmenter renders synchronously)
 *   - RTL (direction is inherited from the parent context)
 *   - Type safety (TranslationDictionary type preserved)
 */

import { HrI18nAugmenter } from "./_client/hr-i18n-augmenter";

export default function HrLayout({ children }: { children: ReactNode }) {
  return <HrI18nAugmenter>{children}</HrI18nAugmenter>;
}
