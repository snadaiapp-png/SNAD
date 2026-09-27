"use client";

/**
 * HR I18n Augmenter — route-scoped translation namespace loader (G1-T12 fix).
 * ============================================================================
 * Problem: spreading HRM_G1_I18N_AR/EN into the global `ar.ts`/`en.ts`
 * dictionaries caused the entire HRM G1 dictionary (105+ keys × 2 locales) to
 * be bundled into EVERY route's initial JS — including login, executive,
 * and control-plane surfaces that never reference HRM keys. This pushed
 * `total_initial_js` and `login_route_js` over the performance budget.
 *
 * Solution: route-scoped dictionary augmentation. This component:
 *   1. Statically imports HRM dictionaries (bundled with the HR route chunk only)
 *   2. Reads the parent I18nContext (provided by the root I18nProvider)
 *   3. Computes an augmented `t` function via useMemo (synchronous)
 *   4. Overrides the I18nContext for children with the augmented `t`
 */

import { useMemo, type ReactNode } from "react";
import { I18nContext, interpolate, useI18n, type I18nContextValue } from "@/lib/i18n/I18nProvider";
import { HRM_G1_I18N_AR, HRM_G1_I18N_EN } from "@/lib/i18n/locales/hrm-g1-i18n";
import { HRM_G2_I18N_AR, HRM_G2_I18N_EN } from "@/lib/i18n/locales/hrm-g2-i18n";
import { HRM_G2_LIFECYCLE_I18N_AR, HRM_G2_LIFECYCLE_I18N_EN } from "@/lib/i18n/locales/hrm-g2-lifecycle-i18n";
import { HRM_G2_PRODUCT_I18N_AR, HRM_G2_PRODUCT_I18N_EN } from "@/lib/i18n/locales/hrm-g2-product-i18n";
import { translations } from "@/lib/i18n";

export function HrI18nAugmenter({ children }: { children: ReactNode }) {
  const parent = useI18n();

  const augmentedT = useMemo(() => {
    const extraAr = {
      ...HRM_G1_I18N_AR,
      ...HRM_G2_I18N_AR,
      ...HRM_G2_LIFECYCLE_I18N_AR,
      ...HRM_G2_PRODUCT_I18N_AR,
    };
    const extraEn = {
      ...HRM_G1_I18N_EN,
      ...HRM_G2_I18N_EN,
      ...HRM_G2_LIFECYCLE_I18N_EN,
      ...HRM_G2_PRODUCT_I18N_EN,
    };
    const extra = parent.locale === "ar" ? extraAr : extraEn;
    return (key: string, params?: Record<string, string | number>) => {
      const nsTemplate = extra[key];
      if (nsTemplate !== undefined) {
        return interpolate(nsTemplate, params);
      }
      const baseDict = translations[parent.locale];
      const baseTemplate = baseDict[key];
      if (baseTemplate === undefined) {
        return key;
      }
      return interpolate(baseTemplate, params);
    };
  }, [parent.locale]);

  const value: I18nContextValue = useMemo(
    () => ({
      locale: parent.locale,
      direction: parent.direction,
      setLocale: parent.setLocale,
      t: augmentedT,
    }),
    [parent, augmentedT],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}
