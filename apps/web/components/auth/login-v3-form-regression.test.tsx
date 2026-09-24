// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { LoginForm } from "./login-form";

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>
      {children}
    </a>
  ),
}));

afterEach(() => cleanup());

function renderForm(overrides: Partial<React.ComponentProps<typeof LoginForm>> = {}) {
  return render(
    <I18nProvider>
      <LoginForm
        onLogin={vi.fn().mockResolvedValue(undefined)}
        authenticating={false}
        error={null}
        {...overrides}
      />
    </I18nProvider>,
  );
}

describe("Login v3 form regression contract", () => {
  it("keeps exactly one approved official wordmark inside the credential surface", () => {
    const { container } = renderForm();
    expect(
      container.querySelectorAll(
        'img[src="/assets/brand/snad-logo-official-wordmark.png"]',
      ),
    ).toHaveLength(1);
  });

  it("preserves the session-expired retry path", async () => {
    const user = userEvent.setup();
    const retry = vi.fn().mockResolvedValue(undefined);

    renderForm({ sessionExpired: true, onRetrySession: retry });

    expect(screen.getByRole("alert")).toHaveTextContent("انتهت الجلسة");
    await user.click(screen.getByRole("button", { name: "إعادة المحاولة" }));
    expect(retry).toHaveBeenCalledTimes(1);
  });
});
