// @vitest-environment jsdom

/**
 * I18nProvider — unit tests
 *
 * Covers:
 *   - Default locale is Arabic
 *   - Direction is rtl for ar, ltr for en
 *   - t() returns translated string
 *   - t() interpolates {param} placeholders
 *   - t() returns the key itself when missing
 *   - setLocale() persists to localStorage
 *   - setLocale() updates <html lang dir>
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, renderHook, act } from "@testing-library/react";
import type { ReactNode } from "react";

// Mock next/navigation useRouter (used by AuthEntry, not directly here, but
// Providers transitively imports it).
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => "/",
  useSearchParams: () => new URLSearchParams(),
}));

// Mock the API client to avoid network calls during provider tests.
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

// Mock auth API to avoid network calls.
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

import { I18nProvider, useI18n } from "@/lib/i18n/I18nProvider";

function wrapper({ children }: { children: ReactNode }) {
  return <I18nProvider>{children}</I18nProvider>;
}

beforeEach(() => {
  // Reset localStorage and document attributes before each test.
  window.localStorage.clear();
  document.documentElement.lang = "ar";
  document.documentElement.dir = "rtl";
});

describe("I18nProvider", () => {
  it("defaults to Arabic (ar) on first render", () => {
    const { result } = renderHook(() => useI18n(), { wrapper });
    expect(result.current.locale).toBe("ar");
    expect(result.current.direction).toBe("rtl");
  });

  it("t() returns translated string for known key", () => {
    const { result } = renderHook(() => useI18n(), { wrapper });
    expect(result.current.t("auth.login.title")).toBe("تسجيل الدخول");
  });

  it("t() returns the key itself when key is missing", () => {
    const { result } = renderHook(() => useI18n(), { wrapper });
    expect(result.current.t("nonexistent.key.xyz")).toBe("nonexistent.key.xyz");
  });

  it("t() interpolates {param} placeholders", () => {
    const { result } = renderHook(() => useI18n(), { wrapper });
    expect(result.current.t("workspace.welcome", { name: "أحمد" })).toBe(
      "أهلاً، أحمد",
    );
  });

  it("setLocale('en') switches to English and persists", () => {
    const { result } = renderHook(() => useI18n(), { wrapper });
    act(() => {
      result.current.setLocale("en");
    });
    expect(result.current.locale).toBe("en");
    expect(result.current.direction).toBe("ltr");
    expect(window.localStorage.getItem("snad.locale")).toBe("en");
    expect(document.documentElement.lang).toBe("en");
    expect(document.documentElement.dir).toBe("ltr");
  });

  it("setLocale('ar') switches back to Arabic and persists", () => {
    const { result } = renderHook(() => useI18n(), { wrapper });
    act(() => {
      result.current.setLocale("en");
    });
    act(() => {
      result.current.setLocale("ar");
    });
    expect(result.current.locale).toBe("ar");
    expect(result.current.direction).toBe("rtl");
    expect(window.localStorage.getItem("snad.locale")).toBe("ar");
    expect(document.documentElement.lang).toBe("ar");
    expect(document.documentElement.dir).toBe("rtl");
  });

  it("renders children without crashing", () => {
    const { getByText } = render(
      <I18nProvider>
        <div>child content</div>
      </I18nProvider>,
    );
    expect(getByText("child content")).toBeTruthy();
  });
});
