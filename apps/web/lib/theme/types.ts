/**
 * SNAD Theme — Type definitions
 * ----------------------------------------------------------------------------
 * Theme system supporting light, dark, and system modes.
 * Arabic/English locale is orthogonal — theme works in both.
 */
export type ThemeMode = "light" | "dark" | "system";

export const THEME_MODES: readonly ThemeMode[] = ["light", "dark", "system"] as const;
export const DEFAULT_THEME_MODE: ThemeMode = "system";

/**
 * The resolved theme is what's actually applied to the DOM.
 * "system" resolves to either "light" or "dark" based on prefers-color-scheme.
 */
export type ResolvedTheme = "light" | "dark";

export interface ThemeContextValue {
  /** User's chosen mode (may be "system"). */
  mode: ThemeMode;
  /** Resolved theme actually applied to <html data-theme>. */
  resolved: ResolvedTheme;
  /** Switch to a specific mode. Persists choice in localStorage. */
  setMode: (mode: ThemeMode) => void;
  /** Convenience: cycle through light → dark → system. */
  cycleMode: () => void;
}

/**
 * Storage key for theme preference. Only stores the mode name — no PII.
 */
export const THEME_STORAGE_KEY = "snad.theme";

/**
 * The data attribute applied to <html> to activate the theme.
 * theme.css uses [data-theme="light"] and [data-theme="dark"] selectors.
 */
export const THEME_DATA_ATTRIBUTE = "data-theme";
