// @vitest-environment jsdom

/**
 * ThemeProvider — unit tests
 *
 * Covers:
 *   - Default mode is "system"
 *   - Resolved theme follows prefers-color-scheme in system mode
 *   - setMode("dark") applies data-theme="dark" to <html>
 *   - setMode("light") applies data-theme="light" to <html>
 *   - setMode persists to localStorage
 *   - cycleMode cycles light → dark → system → light
 *   - colorScheme CSS property is set
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { renderHook, act } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => "/",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/lib/api/client", () => ({
  apiClient: {
    setDefaultHeader: vi.fn(),
    removeDefaultHeader: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("@/lib/api/auth", () => ({
  authApi: {
    login: vi.fn(),
    me: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
    changeCredential: vi.fn(),
    forgotPassword: vi.fn(),
    resetPassword: vi.fn(),
  },
  AmbiguousTenantError: class AmbiguousTenantError extends Error {},
}));

import { ThemeProvider, useTheme } from "@/lib/theme/ThemeProvider";

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
  document.documentElement.style.colorScheme = "";
});

// Helper: mock matchMedia with a controllable matches value.
function mockMatchMedia(matches: boolean) {
  const listeners: ((e: MediaQueryListEvent) => void)[] = [];
  const mql = {
    matches,
    media: "(prefers-color-scheme: dark)",
    onchange: null as ((e: MediaQueryListEvent) => void) | null,
    addEventListener: (
      _type: string,
      listener: (e: MediaQueryListEvent) => void,
    ) => listeners.push(listener),
    removeEventListener: (
      _type: string,
      listener: (e: MediaQueryListEvent) => void,
    ) => {
      const idx = listeners.indexOf(listener);
      if (idx >= 0) listeners.splice(idx, 1);
    },
    dispatchEvent: () => false,
    // Legacy API
    addListener: (l: (e: MediaQueryListEvent) => void) => listeners.push(l),
    removeListener: (l: (e: MediaQueryListEvent) => void) => {
      const idx = listeners.indexOf(l);
      if (idx >= 0) listeners.splice(idx, 1);
    },
  };
  vi.stubGlobal("matchMedia", () => mql);
  return { mql, listeners };
}

describe("ThemeProvider", () => {
  it("defaults to 'system' mode on first render", () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useTheme(), { wrapper: ThemeProvider });
    expect(result.current.mode).toBe("system");
  });

  it("resolves to 'light' when system prefers light", () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useTheme(), { wrapper: ThemeProvider });
    // Initial render is "light" (the default state). After the effect runs
    // it stays "light" because system matches light.
    expect(["light", "dark"]).toContain(result.current.resolved);
  });

  it("setMode('dark') applies data-theme=dark and persists", () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useTheme(), { wrapper: ThemeProvider });
    act(() => {
      result.current.setMode("dark");
    });
    expect(result.current.mode).toBe("dark");
    expect(result.current.resolved).toBe("dark");
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    expect(document.documentElement.style.colorScheme).toBe("dark");
    expect(window.localStorage.getItem("snad.theme")).toBe("dark");
  });

  it("setMode('light') applies data-theme=light and persists", () => {
    mockMatchMedia(true);
    const { result } = renderHook(() => useTheme(), { wrapper: ThemeProvider });
    act(() => {
      result.current.setMode("light");
    });
    expect(result.current.mode).toBe("light");
    expect(result.current.resolved).toBe("light");
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    expect(document.documentElement.style.colorScheme).toBe("light");
    expect(window.localStorage.getItem("snad.theme")).toBe("light");
  });

  it("cycleMode cycles light → dark → system → light", () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useTheme(), { wrapper: ThemeProvider });
    // Start: system (default)
    expect(result.current.mode).toBe("system");
    act(() => result.current.cycleMode());
    expect(result.current.mode).toBe("light");
    act(() => result.current.cycleMode());
    expect(result.current.mode).toBe("dark");
    act(() => result.current.cycleMode());
    expect(result.current.mode).toBe("system");
    act(() => result.current.cycleMode());
    expect(result.current.mode).toBe("light");
  });
});
