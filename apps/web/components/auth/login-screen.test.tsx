// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { LoginScreen } from "./login-screen";

afterEach(() => cleanup());

describe("LoginScreen v3 composition", () => {
  it("uses the v3 shell and keeps the official login wordmark", () => {
    const { container } = render(
      <I18nProvider>
        <LoginScreen
          onLogin={vi.fn().mockResolvedValue(undefined)}
          authenticating={false}
          error={null}
        />
      </I18nProvider>,
    );

    expect(container.querySelector('[data-auth-version="v3"]')).not.toBeNull();
    expect(
      container.querySelector('img[src="/assets/brand/snad-logo-official-wordmark.png"]'),
    ).not.toBeNull();
    expect(container.textContent).not.toContain("SNAD • سند");
    expect(container.textContent).not.toContain("نظام تشغيل أعمال ذكي");
  });
});
