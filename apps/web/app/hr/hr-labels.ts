/**
 * Arabic-first label maps for canonical HRM v2 status/code vocabularies —
 * WS5 Task 9. These render backend enum values in Arabic; unknown values
 * fall back to the raw code (displayed, never invented).
 */

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

/** Gregorian calendar with Arabic month names and Latin digits. */
export function formatArabicDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(`${iso}T00:00:00Z`);
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat("ar", {
    calendar: "gregory",
    numberingSystem: "latn",
    timeZone: "UTC",
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(d);
}

export function employmentStatusAr(status: string): string {
  return EMPLOYMENT_STATUS_AR[status] ?? status;
}

export function workerClassificationAr(code: string): string {
  return WORKER_CLASSIFICATION_AR[code] ?? code;
}
