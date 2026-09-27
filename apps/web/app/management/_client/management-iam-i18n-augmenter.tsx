"use client";

import { useCallback, useMemo, type ReactNode } from "react";
import {
  I18nContext,
  interpolate,
  useI18n,
  type I18nContextValue,
} from "@/lib/i18n/I18nProvider";
import { managementIamDictionary } from "@/lib/i18n/management-iam-l10n";

export function ManagementIamI18nAugmenter({ children }: { children: ReactNode }) {
  const parent = useI18n();
  const iam = useMemo(() => managementIamDictionary(parent.locale), [parent.locale]);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      const template = iam[key];
      return template === undefined ? parent.t(key, params) : interpolate(template, params);
    },
    [iam, parent],
  );

  const value = useMemo<I18nContextValue>(
    () => ({
      locale: parent.locale,
      direction: parent.direction,
      setLocale: parent.setLocale,
      t,
    }),
    [parent.direction, parent.locale, parent.setLocale, t],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}
