"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";

/**
 * Locale-aware formatting helpers for the control plane.
 * Money is minor units (repo standard) — never floating point math.
 */
export function useScpFormat() {
  const { locale } = useI18n();
  const intlLocale = locale === "en" ? "en-US" : "ar-SA";

  function money(minor: number | null | undefined, currency: string | null | undefined): string {
    if (minor === null || minor === undefined || !currency) return "—";
    return new Intl.NumberFormat(intlLocale, {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(minor / 100);
  }

  function day(value: string | null | undefined): string {
    if (!value) return "—";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "—";
    return new Intl.DateTimeFormat(intlLocale, { dateStyle: "medium" }).format(parsed);
  }

  function number(value: number | null | undefined): string {
    if (value === null || value === undefined) return "—";
    return new Intl.NumberFormat(intlLocale).format(value);
  }

  return { money, day, number, intlLocale };
}
