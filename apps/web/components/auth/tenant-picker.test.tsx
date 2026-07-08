// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { TenantPicker } from "./tenant-picker";
import { I18nProvider } from "@/lib/i18n/I18nProvider";

const onSelectMock = vi.fn();
const onDismissMock = vi.fn();

function renderPicker(overrides: Partial<React.ComponentProps<typeof TenantPicker>> = {}) {
  return render(
    <I18nProvider>
      <TenantPicker
        tenantIds={["tenant-aaaa-bbbb-8F21", "tenant-cccc-dddd-C742"]}
        onSelect={onSelectMock}
        onDismiss={onDismissMock}
        authenticating={false}
        {...overrides}
      />
    </I18nProvider>,
  );
}

describe("TenantPicker", () => {
  beforeEach(() => {
    onSelectMock.mockReset();
    onDismissMock.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("displays shortened tenant identifiers, not full UUIDs", () => {
    renderPicker();
    expect(screen.getByText("مساحة عمل •••• 8F21")).toBeInTheDocument();
    expect(screen.getByText("مساحة عمل •••• C742")).toBeInTheDocument();
  });

  it("does not display the password anywhere in the DOM", () => {
    const { container } = renderPicker();
    expect(container.textContent).not.toMatch(/password|secret|credential/i);
  });

  it("calls onSelect with the full tenantId when a tenant is chosen", async () => {
    const user = userEvent.setup();
    renderPicker();
    await user.click(screen.getByText("مساحة عمل •••• 8F21"));
    await user.click(screen.getByRole("button", { name: "متابعة" }));
    expect(onSelectMock).toHaveBeenCalledWith("tenant-aaaa-bbbb-8F21");
  });

  it("allows going back via dismissAmbiguousTenant", async () => {
    const user = userEvent.setup();
    renderPicker();
    await user.click(screen.getByRole("button", { name: "العودة إلى تسجيل الدخول" }));
    expect(onDismissMock).toHaveBeenCalledTimes(1);
  });
});
