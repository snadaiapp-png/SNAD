"use client";

/**
 * ThemeSwitcher — Light / Dark / System toggle
 * ----------------------------------------------------------------------------
 * - Cycles through light → dark → system on click.
 * - Shows an icon for each mode (sun / moon / auto).
 * - Accessible: aria-label reflects the current mode, focus-visible ring.
 * - 44x44 minimum touch target (WCAG 2.2 AA 2.5.5).
 * - Uses logical properties for layout symmetry.
 * - The official brand colors are NOT affected by theme — only surface tokens
 *   change. The SnadLogo renders identically in both themes.
 */
import { useTheme } from "@/lib/theme/ThemeProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "./ThemeSwitcher.module.css";

function SunIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
    </svg>
  );
}

function MoonIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
    </svg>
  );
}

function SystemIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="2" y="3" width="20" height="14" rx="2" />
      <path d="M8 21h8M12 17v4" />
    </svg>
  );
}

const ICONS = {
  light: SunIcon,
  dark: MoonIcon,
  system: SystemIcon,
} as const;

export function ThemeSwitcher() {
  const { mode, cycleMode } = useTheme();
  const { t } = useI18n();

  const Icon = ICONS[mode];
  const label = t(`theme.${mode}`);

  return (
    <button
      type="button"
      className={styles.button}
      onClick={cycleMode}
      aria-label={`${t("theme.label")}: ${label}`}
      title={label}
    >
      <Icon />
      <span className={styles.label}>{label}</span>
    </button>
  );
}
