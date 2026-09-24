/**
 * Normalise a due-date value to a full ISO-8601 date-time string suitable for
 * Jackson's default `OffsetDateTime` deserialization.
 *
 * A bare `YYYY-MM-DD` (from an `<input type="date">`) is converted to
 * `<date>T00:00:00.000Z` (UTC midnight, calendar-day semantics).
 *
 * A value that already contains a time component (e.g. `2026-08-10T14:30:00Z`
 * or `2026-08-10T14:30:00+03:00`) is returned unchanged.
 */
export function toIsoDateTime(
  value: string | undefined | null,
): string | undefined {
  if (!value?.trim()) return undefined;
  // Already contains a time component — pass through as-is.
  if (/T/.test(value)) return value;
  return `${value}T00:00:00.000Z`;
}

export function formValue(form: FormData, key: string): string {
  return String(form.get(key) ?? "").trim();
}

export function optionalValue(form: FormData, key: string): string | undefined {
  return formValue(form, key) || undefined;
}

export function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat("ar-SA", { maximumFractionDigits: 2 }).format(value ?? 0);
}

export function formatDate(value: string | null | undefined): string {
  return value
    ? new Intl.DateTimeFormat("ar-SA", { dateStyle: "medium" }).format(new Date(value))
    : "—";
}

export function formatDateTime(value: string | null | undefined): string {
  return value
    ? new Intl.DateTimeFormat("ar-SA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "—";
}
