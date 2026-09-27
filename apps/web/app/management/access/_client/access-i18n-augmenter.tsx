"use client";

import { useCallback, useMemo, type ReactNode } from "react";
import {
  I18nContext,
  interpolate,
  useI18n,
  type I18nContextValue,
} from "@/lib/i18n/I18nProvider";
import { accessDictionary } from "@/lib/i18n/access-l10n";

export function AccessI18nAugmenter({ children }: { children: ReactNode }) {
  const parent = useI18n();
  const access = useMemo(() => accessDictionary(parent.locale), [parent.locale]);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      const template = access[key];
      return template === undefined ? parent.t(key, params) : interpolate(template, params);
    },
    [access, parent],
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
