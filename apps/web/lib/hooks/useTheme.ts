"use client";

import { useCallback, useEffect, useSyncExternalStore } from "react";

export type Theme = "light" | "dark";
export type ThemePreference = Theme | "system";

const STORAGE_KEY = "snad-theme";
const COOKIE_NAME = "snad-theme";
const subscribers = new Set<() => void>();

function storedPreference(): ThemePreference {
  if (typeof window === "undefined") return "system";
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    if (value === "light" || value === "dark" || value === "system") return value;
  } catch {
    // Storage can be unavailable in privacy modes.
  }
  return "system";
}

function domPreference(): ThemePreference | null {
  if (typeof document === "undefined") return null;
  const value = document.documentElement.dataset.themePreference;
  return value === "light" || value === "dark" || value === "system" ? value : null;
}

function systemTheme(): Theme {
  return typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

function preferenceSnapshot(): ThemePreference {
  return domPreference() ?? storedPreference();
}

function resolvedTheme(preference = preferenceSnapshot()): Theme {
  return preference === "system" ? systemTheme() : preference;
}

function applyTheme(preference: ThemePreference): void {
  if (typeof document === "undefined") return;
  const resolved = resolvedTheme(preference);
  const root = document.documentElement;
  root.dataset.themePreference = preference;
  root.dataset.theme = resolved;
  root.style.colorScheme = resolved;
}

function persistPreference(preference: ThemePreference): void {
  if (typeof window !== "undefined") {
    try {
      window.localStorage.setItem(STORAGE_KEY, preference);
    } catch {
      // The functional cookie remains available when storage is restricted.
    }
  }
  if (typeof document !== "undefined") {
    document.cookie = `${COOKIE_NAME}=${preference}; Path=/; Max-Age=31536000; SameSite=Lax`;
  }
}

function notify(): void {
  subscribers.forEach((callback) => callback());
}

function subscribe(callback: () => void): () => void {
  subscribers.add(callback);
  const media =
    typeof window !== "undefined" && typeof window.matchMedia === "function"
      ? window.matchMedia("(prefers-color-scheme: dark)")
      : null;

  const onMediaChange = () => {
    if (preferenceSnapshot() !== "system") return;
    applyTheme("system");
    callback();
  };
  const onStorage = (event: StorageEvent) => {
    if (event.key !== STORAGE_KEY) return;
    const value = event.newValue;
    const next: ThemePreference =
      value === "light" || value === "dark" || value === "system"
        ? value
        : "system";
    applyTheme(next);
    callback();
  };

  media?.addEventListener("change", onMediaChange);
  window.addEventListener("storage", onStorage);
  return () => {
    subscribers.delete(callback);
    media?.removeEventListener("change", onMediaChange);
    window.removeEventListener("storage", onStorage);
  };
}

export interface UseThemeResult {
  theme: Theme;
  preference: ThemePreference;
  setTheme: (next: ThemePreference) => void;
  toggleTheme: () => void;
}

export function useTheme(): UseThemeResult {
  const preference = useSyncExternalStore(
    subscribe,
    preferenceSnapshot,
    () => "system",
  );
  const theme = resolvedTheme(preference);

  useEffect(() => {
    applyTheme(preference);
  }, [preference, theme]);

  const setTheme = useCallback((next: ThemePreference) => {
    applyTheme(next);
    persistPreference(next);
    notify();
  }, []);

  const toggleTheme = useCallback(() => {
    const next: Theme = resolvedTheme() === "dark" ? "light" : "dark";
    applyTheme(next);
    persistPreference(next);
    notify();
  }, []);

  return { theme, preference, setTheme, toggleTheme };
}
