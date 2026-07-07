"use client";

/**
 * LanguageSwitcher — Arabic / English toggle
 * ----------------------------------------------------------------------------
 * - Renders a compact segmented control with two buttons (ar | en).
 * - Accessible: each button has aria-label, aria-pressed, focus-visible ring.
 * - 44x44 minimum touch target on each button (WCAG 2.2 AA 2.5.5).
 * - Uses logical properties (margin-inline-start, padding-inline-start) so
 *   layout is identical in RTL and LTR.
 * - Does NOT lose session or current route — only switches locale.
 */
import { useI18n } from "@/lib/i18n/I18nProvider";
import type { Locale } from "@/lib/i18n/types";
import styles from "./LanguageSwitcher.module.css";

const OPTIONS: { value: Locale; label: string; ariaLabel: string }[] = [
  { value: "ar", label: "ع", ariaLabel: "العربية" },
  { value: "en", label: "EN", ariaLabel: "English" },
];

export function LanguageSwitcher() {
  const { locale, setLocale } = useI18n();

  return (
    <div
      role="group"
      aria-label="Language"
      className={styles.container}
    >
      {OPTIONS.map((opt) => {
        const isActive = locale === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            className={styles.button}
            aria-pressed={isActive}
            aria-label={opt.ariaLabel}
            onClick={() => setLocale(opt.value)}
            data-active={isActive ? "true" : undefined}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
