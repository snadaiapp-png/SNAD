'use client';

/*
 * ============================================================================
 *  useTheme — SSR-safe theme detection hook
 * ----------------------------------------------------------------------------
 *  PURPOSE
 *  -------
 *  Returns the current active color theme (`'light' | 'dark'`) and helpers to
 *  change it. The hook is consumed by SnadLogo and any other component that
 *  needs to switch artwork based on the active theme — without waiting for
 *  the CSS-only `prefers-color-scheme` media query to react.
 *
 *  IMPLEMENTATION
 *  --------------
 *  Uses `useSyncExternalStore` (the React-recommended primitive for
 *  subscribing to external state). This avoids the `setState-in-effect`
 *  anti-pattern and the cascading renders it triggers.
 *
 *    • subscribe(callback)  — registers a listener; called by setTheme and
 *      by the OS-level matchMedia + storage event listeners.
 *    • getSnapshot()        — reads the current theme from
 *      `<html data-theme>`, then localStorage, then matchMedia, then 'light'.
 *    • getServerSnapshot()  — returns 'light' on the server to keep SSR
 *      output stable and avoid hydration mismatch.
 *
 *  DETECTION ORDER (at snapshot time)
 *  ----------------------------------
 *    1. `document.documentElement.dataset.theme` — explicit user preference
 *       (set by `setTheme` below, persisted to localStorage).
 *    2. `localStorage['snad-theme']` — restored on next page load.
 *    3. `window.matchMedia('(prefers-color-scheme: dark)')` — OS preference.
 *    4. `'light'` — safe default.
 *
 *  SSR SAFETY
 *  ----------
 *  `getServerSnapshot` returns `'light'` on the server. After hydration,
 *  `getSnapshot` resolves the real theme and React re-renders. To avoid a
 *  flash of the wrong theme, the app layout SHOULD inject an inline script
 *  into `<head>` that sets `<html data-theme="...">` before React hydrates.
 *  Consumers should render the same markup for both themes and switch via
 *  CSS (`[data-theme="dark"] .foo { ... }`) to absorb the post-mount swap
 *  without layout shift.
 *
 *  PERSISTENCE
 *  -----------
 *  When `setTheme` is called, the hook:
 *    • Writes `data-theme="light|dark"` onto `<html>` so SDS token CSS rules
 *      activate immediately.
 *    • Persists the choice to `localStorage` under the key `'snad-theme'`
 *      so it survives reloads.
 *    • Notifies all subscribed components to re-render.
 *
 *  TOKENS
 *  ------
 *  No SDS tokens are used here — this is a behaviour-only hook with no
 *  visual output.
 * ============================================================================
 */

import { useCallback, useEffect, useSyncExternalStore } from 'react';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'snad-theme';

/**
 * Read the persisted theme from localStorage. Returns `null` if no preference
 * has been stored or if localStorage is unavailable (SSR, privacy mode).
 */
function readStoredTheme(): Theme | null {
  if (typeof window === 'undefined') return null;
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    if (value === 'light' || value === 'dark') return value;
  } catch {
    // localStorage may throw in private-browsing modes — fall through.
  }
  return null;
}

/**
 * Read the OS-level color scheme preference via matchMedia. Returns `null`
 * if matchMedia is unavailable (SSR, very old browsers, jsdom without the
 * matchMedia polyfill).
 */
function readSystemTheme(): Theme | null {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return null;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
}

/**
 * Read the explicit theme set on `<html data-theme="...">`. This is the
 * authoritative source of truth at runtime because SDS token CSS scopes by
 * `[data-theme]`.
 */
function readDomTheme(): Theme | null {
  if (typeof document === 'undefined') return null;
  const attr = document.documentElement.dataset.theme;
  if (attr === 'light' || attr === 'dark') return attr;
  return null;
}

/**
 * Resolve the active theme by checking DOM → localStorage → OS → 'light'.
 * This is the snapshot function used by `useSyncExternalStore`.
 */
function resolveTheme(): Theme {
  return readDomTheme() ?? readStoredTheme() ?? readSystemTheme() ?? 'light';
}

/*
 * Module-level subscriber registry. `useSyncExternalStore` requires a
 * `subscribe(callback)` function that registers a listener and returns an
 * unsubscribe function. We keep the set at module scope so `setTheme` can
 * notify every mounted component that consumes the hook.
 */
const subscribers = new Set<() => void>();

function notifySubscribers(): void {
  for (const cb of subscribers) {
    cb();
  }
}

/**
 * Subscribe to theme changes. The callback is invoked when:
 *   • `setTheme` / `toggleTheme` is called (via `notifySubscribers`).
 *   • The OS color scheme changes (via `matchMedia` change event).
 *   • Another tab updates `localStorage['snad-theme']` (via storage event).
 *
 * Returns an unsubscribe function that removes the callback and the event
 * listeners.
 */
function subscribe(callback: () => void): () => void {
  subscribers.add(callback);

  let cleanupMql: (() => void) | undefined;
  let cleanupStorage: (() => void) | undefined;

  if (typeof window !== 'undefined') {
    // matchMedia change: only act when the user has NOT explicitly chosen a
    // theme (so OS preference changes don't override explicit user choice).
    if (typeof window.matchMedia === 'function') {
      const mql = window.matchMedia('(prefers-color-scheme: dark)');
      const onMqlChange = () => {
        if (readStoredTheme() !== null) return;
        // Clear the DOM attribute so resolveTheme() picks up the new OS pref.
        document.documentElement.removeAttribute('data-theme');
        callback();
      };
      mql.addEventListener('change', onMqlChange);
      cleanupMql = () => mql.removeEventListener('change', onMqlChange);
    }

    // storage event: another tab updated the theme.
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY) {
        // Sync the DOM attribute with the new stored value.
        const next = readStoredTheme();
        if (next !== null) {
          document.documentElement.dataset.theme = next;
        }
        callback();
      }
    };
    window.addEventListener('storage', onStorage);
    cleanupStorage = () => window.removeEventListener('storage', onStorage);
  }

  return () => {
    subscribers.delete(callback);
    cleanupMql?.();
    cleanupStorage?.();
  };
}

/**
 * Client snapshot — reads the live theme from DOM/localStorage/matchMedia.
 */
function getSnapshot(): Theme {
  return resolveTheme();
}

/**
 * Server snapshot — always returns 'light' to keep SSR output stable.
 */
function getServerSnapshot(): Theme {
  return 'light';
}

export interface UseThemeResult {
  /** The currently active theme. SSR always returns `'light'`. */
  theme: Theme;
  /** Set the theme explicitly. Persists to localStorage and writes to `<html>`. */
  setTheme: (next: Theme) => void;
  /** Toggle between light and dark. */
  toggleTheme: () => void;
}

/**
 * useTheme — SSR-safe theme detection + control.
 *
 * @example
 * ```tsx
 * const { theme, toggleTheme } = useTheme();
 * return (
 *   <SnadLogo
 *     variant={theme === 'dark' ? 'white' : 'primary'}
 *     href="/"
 *   />
 * );
 * ```
 */
export function useTheme(): UseThemeResult {
  const theme = useSyncExternalStore(
    subscribe,
    getSnapshot,
    getServerSnapshot,
  );

  /*
   * Sync the DOM attribute with the resolved theme after mount. This is a
   * DOM mutation (not a React state update), so it does not trigger the
   * `react-hooks/set-state-in-effect` rule. The SDS token CSS scopes by
   * `[data-theme]`, so setting the attribute activates the correct theme
   * tokens on the very first paint after hydration.
   */
  useEffect(() => {
    if (typeof document === 'undefined') return;
    if (document.documentElement.dataset.theme !== theme) {
      document.documentElement.dataset.theme = theme;
    }
  }, [theme]);

  const setTheme = useCallback((next: Theme) => {
    if (typeof document !== 'undefined') {
      document.documentElement.dataset.theme = next;
    }
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // Ignore storage failures (private mode, quota exceeded, etc.).
      }
    }
    notifySubscribers();
  }, []);

  const toggleTheme = useCallback(() => {
    const next: Theme = resolveTheme() === 'dark' ? 'light' : 'dark';
    if (typeof document !== 'undefined') {
      document.documentElement.dataset.theme = next;
    }
    if (typeof window !== 'undefined') {
      try {
        window.localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // Ignore storage failures.
      }
    }
    notifySubscribers();
  }, []);

  return { theme, setTheme, toggleTheme };
}
