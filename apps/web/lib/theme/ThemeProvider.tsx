"use client";

/**
 * SNAD ThemeProvider — Dynamic light/dark/system theme
 * ----------------------------------------------------------------------------
 * Provides:
 *   - mode state (light | dark | system)
 *   - resolved theme actually applied to the DOM
 *   - setMode() that persists to localStorage and updates <html data-theme>
 *   - cycleMode() convenience for switcher UIs
 *   - automatic re-resolution when OS preference changes (in "system" mode)
 *
 * FOUC prevention:
 *   - An inline script in layout.tsx applies the stored theme BEFORE React
 *     hydration by setting <html data-theme> based on localStorage and
 *     prefers-color-scheme. This prevents Flash of Incorrect Theme.
 *
 * Hydration safety:
 *   - Initial render uses DEFAULT_THEME_MODE ("system") to match the server.
 *   - A useEffect reads localStorage and updates the mode AFTER hydration.
 *   - suppressHydrationWarning on <html> covers the brief mismatch window.
 *
 * System mode:
 *   - Listens to `prefers-color-scheme: dark` media query.
 *   - When the OS preference changes, re-resolves and updates the DOM
 *     (only if the user's mode is "system").
 *
 * Brand colors:
 *   - The official brand colors (petroleum green, royal gold) are defined as
 *     design tokens in theme.css and are identical in both themes — only
 *     surface/background/text tokens change between light/dark.
 *   - The SnadLogo component renders the same in both themes (its colors are
 *     fixed brand tokens, not theme-dependent).
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
  DEFAULT_THEME_MODE,
  THEME_DATA_ATTRIBUTE,
  THEME_MODES,
  THEME_STORAGE_KEY,
  type ResolvedTheme,
  type ThemeMode,
} from "./types";

interface ThemeContextValue {
  mode: ThemeMode;
  resolved: ResolvedTheme;
  setMode: (mode: ThemeMode) => void;
  cycleMode: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function isThemeMode(value: unknown): value is ThemeMode {
  return typeof value === "string" && (THEME_MODES as readonly string[]).includes(value);
}

function readStoredMode(): ThemeMode {
  if (typeof window === "undefined") return DEFAULT_THEME_MODE;
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (isThemeMode(stored)) return stored;
  } catch {
    // localStorage may be blocked. Fall back to default.
  }
  return DEFAULT_THEME_MODE;
}

function persistMode(mode: ThemeMode): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, mode);
  } catch {
    // Silently ignore.
  }
}

function getSystemPreference(): ResolvedTheme {
  if (typeof window === "undefined") return "light";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function resolveTheme(mode: ThemeMode): ResolvedTheme {
  return mode === "system" ? getSystemPreference() : mode;
}

function applyThemeToDom(resolved: ResolvedTheme): void {
  if (typeof document === "undefined") return;
  document.documentElement.setAttribute(THEME_DATA_ATTRIBUTE, resolved);
  // Also set the color-scheme CSS property so native form controls and
  // scrollbars match the theme.
  document.documentElement.style.colorScheme = resolved;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  // Initial state MUST match server render (DEFAULT_THEME_MODE) to avoid
  // hydration mismatch. Actual stored mode applied in useEffect below.
  const [mode, setModeState] = useState<ThemeMode>(DEFAULT_THEME_MODE);
  const [resolved, setResolved] = useState<ResolvedTheme>("light");

  // Apply stored mode after hydration. Runs once on mount.
  // The setState-in-effect pattern is intentional and necessary here:
  // we cannot read localStorage in useState's initializer because it
  // would run during SSR (where localStorage does not exist). The
  // cascading-render cost is paid exactly once on mount.
  useEffect(() => {
    const stored = readStoredMode();
    const resolvedNow = resolveTheme(stored);
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setModeState(stored);
    setResolved(resolvedNow);
    applyThemeToDom(resolvedNow);
  }, []);

  // Listen to OS preference changes when in "system" mode.
  useEffect(() => {
    if (mode !== "system") return;
    if (typeof window === "undefined") return;
    const mql = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = (e: MediaQueryListEvent) => {
      const newResolved: ResolvedTheme = e.matches ? "dark" : "light";
      setResolved(newResolved);
      applyThemeToDom(newResolved);
    };
    // addEventListener is the modern API; addListener is the Safari < 14 fallback.
    if (typeof mql.addEventListener === "function") {
      mql.addEventListener("change", handler);
      return () => mql.removeEventListener("change", handler);
    }
    // Legacy Safari fallback — addListener/removeListener are deprecated.
    // Cast to access the legacy API without tripping tsc.
    const legacy = mql as unknown as {
      addListener: (l: (e: MediaQueryListEvent) => void) => void;
      removeListener: (l: (e: MediaQueryListEvent) => void) => void;
    };
    legacy.addListener(handler);
    return () => {
      legacy.removeListener(handler);
    };
  }, [mode]);

  const setMode = useCallback((next: ThemeMode) => {
    const resolvedNow = resolveTheme(next);
    setModeState(next);
    setResolved(resolvedNow);
    persistMode(next);
    applyThemeToDom(resolvedNow);
  }, []);

  const cycleMode = useCallback(() => {
    const order: ThemeMode[] = ["light", "dark", "system"];
    const currentIdx = order.indexOf(mode);
    const next = order[(currentIdx + 1) % order.length] ?? "system";
    setMode(next);
  }, [mode, setMode]);

  const value = useMemo<ThemeContextValue>(
    () => ({ mode, resolved, setMode, cycleMode }),
    [mode, resolved, setMode, cycleMode],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (ctx === null) {
    throw new Error("useTheme must be used within a <ThemeProvider>");
  }
  return ctx;
}
