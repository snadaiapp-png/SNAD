/**
 * SNAD i18n — Type definitions
 * ----------------------------------------------------------------------------
 * Central type contract for the SNAD internationalization layer.
 *
 * Design goals:
 *   - Arabic (ar) is the default locale.
 *   - English (en) is the secondary locale.
 *   - Every user-facing string MUST be referenced via a translation key.
 *   - Translation keys are statically typed so CI can detect missing keys.
 *   - No PII, tokens, or session data are stored in locale preferences.
 */
export type Locale = "ar" | "en";

export const LOCALES: readonly Locale[] = ["ar", "en"] as const;
export const DEFAULT_LOCALE: Locale = "ar";

/**
 * Arabic is RTL, English is LTR. This mapping is the single source of truth.
 */
export const LOCALE_DIRECTION: Record<Locale, "rtl" | "ltr"> = {
  ar: "rtl",
  en: "ltr",
};

/**
 * Translation dictionaries. Both locales MUST expose the SAME set of keys.
 * The `check-i18n-keys.py` CI script enforces this invariant.
 */
export interface TranslationDictionary {
  [key: string]: string;
}

export interface I18nContextValue {
  /** Active locale. */
  locale: Locale;
  /** Direction for the active locale. */
  direction: "rtl" | "ltr";
  /** Switch to a different locale. Persists choice in localStorage. */
  setLocale: (locale: Locale) => void;
  /**
   * Translate a key with optional interpolation parameters.
   * Returns the key itself if missing (so missing keys are visible in UI).
   */
  t: (key: string, params?: Record<string, string | number>) => string;
}

/**
 * Storage key for the user's locale preference. Stored in localStorage only —
 * NOT in cookies, to keep the storage surface minimal. The value is just a
 * 2-letter locale code; no PII, no session data.
 */
export const LOCALE_STORAGE_KEY = "snad.locale";
