/**
 * Arabic-first label maps for canonical HRM v2 status/code vocabularies —
 * WS5 Task 9. These render backend enum values in Arabic; unknown values
 * fall back to the raw code (displayed, never invented).
 *
 * G1-T11 additions: i18n-keyed fallback helpers for recruitment/onboarding
 * status vocabularies. These helpers accept a translation function and
 * resolve a backend code to a localized label. If the key is missing in
 * the dictionary, the raw code is returned (never invented).
 */

import type { TranslationDictionary } from "@/lib/i18n/types";

export const EMPLOYMENT_STATUS_AR: Record<string, string> = {
  DRAFT: "مسودة",
  ONBOARDING: "تأهيل",
  ACTIVE: "نشِط",
  ON_LEAVE: "في إجازة",
  SUSPENDED: "موقوف",
  TERMINATED: "منتهي الخدمة",
  VOID: "مُلغى",
};

export const ASSIGNMENT_STATUS_AR: Record<string, string> = {
  ACTIVE: "ساري",
  ENDED: "منتهٍ",
  PLANNED: "مخطط",
};

export const CONTRACT_STATUS_AR: Record<string, string> = {
  DRAFT: "مسودة",
  ACTIVE: "ساري",
  TERMINATED: "منتهٍ",
  SUPERSEDED: "مُستبدل",
};

export const WORKER_CLASSIFICATION_AR: Record<string, string> = {
  FULL_TIME: "دوام كامل",
  PART_TIME: "دوام جزئي",
  CONTRACTOR: "متعاون",
  TEMPORARY: "مؤقت",
  INTERN: "متدرب",
};

/** Gregorian date rendered in the explicitly selected application locale. */
export function formatLocalizedDate(
  iso: string | null | undefined,
  locale: "ar" | "en",
): string {
  if (!iso) return "—";
  const d = new Date(`${iso}T00:00:00Z`);
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat(locale === "ar" ? "ar" : "en-US", {
    calendar: "gregory",
    numberingSystem: "latn",
    timeZone: "UTC",
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(d);
}

/** Gregorian calendar with Arabic month names and Latin digits. */
export function formatArabicDate(iso: string | null | undefined): string {
  return formatLocalizedDate(iso, "ar");
}

export function employmentStatusAr(status: string): string {
  return EMPLOYMENT_STATUS_AR[status] ?? status;
}

export function workerClassificationAr(code: string): string {
  return WORKER_CLASSIFICATION_AR[code] ?? code;
}

// ---------------------------------------------------------------------------
// G1-T11 i18n-keyed helpers — resolve a backend code via a translation
// dictionary. Falls back to the raw code if the key is missing (never
// invented). Callers pass the dictionary so this works for both ar and en.
// ---------------------------------------------------------------------------

/**
 * Resolve a backend status code to a localized label using i18n keys.
 * Caller passes the dictionary (so this works for both ar and en).
 * If the key is missing, the raw code is returned (never invented).
 *
 * @param dict — the translation dictionary (ar or en)
 * @param prefix — the i18n key prefix (e.g. "hrm.recruitment.opening.status")
 * @param code — the backend status code (e.g. "OPEN")
 * @returns the localized label, or the raw code if the key is missing
 */
export function resolveStatusLabel(
  dict: TranslationDictionary,
  prefix: string,
  code: string,
): string {
  const key = `${prefix}.${code}`;
  const value = dict[key];
  return value === undefined ? code : value;
}

/** Convenience: opening status → localized label. */
export function openingStatusLabel(dict: TranslationDictionary, code: string): string {
  return resolveStatusLabel(dict, "hrm.recruitment.opening.status", code);
}

/** Convenience: candidate pool state → localized label. */
export function candidatePoolStateLabel(dict: TranslationDictionary, code: string): string {
  return resolveStatusLabel(dict, "hrm.recruitment.candidates.poolState", code);
}

/** Convenience: onboarding plan state → localized label. */
export function onboardingPlanStateLabel(dict: TranslationDictionary, code: string): string {
  return resolveStatusLabel(dict, "hrm.onboarding.plan.state", code);
}

/** Convenience: onboarding task state → localized label. */
export function onboardingTaskStateLabel(dict: TranslationDictionary, code: string): string {
  return resolveStatusLabel(dict, "hrm.onboarding.planDetail.task", code);
}

/** Convenience: compliance decision → localized label. */
export function complianceDecisionLabel(dict: TranslationDictionary, code: string | null): string {
  if (!code) return resolveStatusLabel(dict, "hrm.recruitment.openingDetail.compliance", "NONE");
  return resolveStatusLabel(dict, "hrm.recruitment.openingDetail.compliance", code);
}
