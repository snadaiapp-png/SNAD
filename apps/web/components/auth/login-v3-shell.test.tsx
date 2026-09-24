// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { LoginV3Shell } from "./login-v3-shell";

afterEach(() => cleanup());

function renderShell() {
  return render(
    <I18nProvider>
      <LoginV3Shell>
        <form aria-label="login-form-probe">
          <button type="submit">submit</button>
        </form>
      </LoginV3Shell>
    </I18nProvider>,
  );
}

describe("LoginV3Shell", () => {
  it("marks the canonical login surface as v3 and renders form content once", () => {
    const { container } = renderShell();
    expect(container.querySelector('[data-auth-version="v3"]')).not.toBeNull();
    expect(screen.getAllByRole("form", { name: "login-form-probe" })).toHaveLength(1);
  });

  it("renders the restrained brand narrative without duplicating the official logo", () => {
    const { container } = renderShell();
    expect(screen.getByText("ابدأ من مساحة عمل واحدة")).toBeInTheDocument();
    expect(
      container.querySelector('img[src="/assets/brand/snad-logo-official-wordmark.png"]'),
    ).toBeNull();
  });

  it("does not render the legacy intelligence-panel product lockup", () => {
    renderShell();
    expect(screen.queryByText("SNAD • سند")).not.toBeInTheDocument();
    expect(screen.queryByText("نظام تشغيل أعمال ذكي")).not.toBeInTheDocument();
  });
});
