/**
 * SNAD i18n — Locale index
 * ----------------------------------------------------------------------------
 * Single export point for both locale dictionaries and the registry.
 */
import type { Locale, TranslationDictionary } from "./types";
import { ar } from "./locales/ar";
import { en } from "./locales/en";

export const translations: Record<Locale, TranslationDictionary> = {
  ar,
  en,
};

export { ar, en };
export type { Locale, TranslationDictionary };
