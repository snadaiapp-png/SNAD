"use client";

/**
 * SNAD I18nProvider — Internationalization context
 * ----------------------------------------------------------------------------
 * Provides:
 *   - locale state (ar | en) with ar as the default
 *   - direction (rtl | ltr) derived from locale
 *   - translation function t(key, params?) with {param} interpolation
 *   - setLocale() that persists to localStorage and updates <html lang dir>
 *
 * Persistence:
 *   - Locale preference stored in localStorage under "snad.locale".
 *   - Only the 2-letter locale code is stored — no PII, no session data.
 *   - On first paint, the provider reads localStorage and applies the locale.
 *   - The inline script in layout.tsx applies the stored locale BEFORE React
 *     hydration to prevent Flash of Incorrect Locale (FOIL) and to keep
 *     <html lang dir> in sync with the server-rendered markup.
 *
 * Hydration safety:
 *   - The initial render uses DEFAULT_LOCALE (ar) to match the server.
 *   - A useEffect reads localStorage and updates the locale AFTER hydration.
 *   - suppressHydrationWarning on <html> covers the brief mismatch window.
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  DEFAULT_LOCALE,
  LOCALE_DIRECTION,
  LOCALE_STORAGE_KEY,
  LOCALES,
  type Locale,
  type TranslationDictionary,
} from "./types";
import { translations } from "./index";

interface I18nContextValue {
  locale: Locale;
  direction: "rtl" | "ltr";
  setLocale: (locale: Locale) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && (LOCALES as readonly string[]).includes(value);
}

function readStoredLocale(): Locale {
  if (typeof window === "undefined") return DEFAULT_LOCALE;
  try {
    const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    if (isLocale(stored)) return stored;
  } catch {
    // localStorage may be blocked (private mode, cookies disabled). Fall back
    // silently to the default locale — i18n still works in-memory.
  }
  return DEFAULT_LOCALE;
}

function persistLocale(locale: Locale): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Silently ignore — preference is not critical for functionality.
  }
}

function applyHtmlAttributes(locale: Locale): void {
  if (typeof document === "undefined") return;
  const direction = LOCALE_DIRECTION[locale];
  document.documentElement.lang = locale;
  document.documentElement.dir = direction;
}

function interpolate(
  template: string,
  params?: Record<string, string | number>,
): string {
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (_, key: string) => {
    const value = params[key];
    return value === undefined || value === null ? `{${key}}` : String(value);
  });
}

export function I18nProvider({ children }: { children: ReactNode }) {
  // Initial state MUST match server render (DEFAULT_LOCALE) to avoid hydration
  // mismatch. The actual stored locale is applied in a useEffect below.
  const [locale, setLocaleState] = useState<Locale>(DEFAULT_LOCALE);

  // Apply stored locale after hydration. This runs once on mount.
  // The setState-in-effect pattern is intentional and necessary here:
  // we cannot read localStorage in useState's initializer because it
  // would run during SSR (where localStorage does not exist). The
  // cascading-render cost is paid exactly once on mount.
  useEffect(() => {
    const stored = readStoredLocale();
    if (stored !== locale) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLocaleState(stored);
    }
    // Always sync <html lang dir> even if locale unchanged (covers SSR case).
    applyHtmlAttributes(stored);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    persistLocale(next);
    applyHtmlAttributes(next);
  }, []);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      const dict: TranslationDictionary = translations[locale];
      const template = dict[key];
      if (template === undefined) {
        // Return the key itself so missing translations are visible during
        // development. The CI check-i18n-keys.py script catches these before
        // they reach production.
        return key;
      }
      return interpolate(template, params);
    },
    [locale],
  );

  const value = useMemo<I18nContextValue>(
    () => ({
      locale,
      direction: LOCALE_DIRECTION[locale],
      setLocale,
      t,
    }),
    [locale, setLocale, t],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (ctx === null) {
    throw new Error("useI18n must be used within an <I18nProvider>");
  }
  return ctx;
}
