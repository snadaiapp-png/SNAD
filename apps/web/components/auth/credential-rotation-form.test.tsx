// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { CredentialRotationForm } from "./credential-rotation-form";

const onChangeCredentialMock = vi.fn();

function renderForm(overrides: Partial<React.ComponentProps<typeof CredentialRotationForm>> = {}) {
  return render(
    <CredentialRotationForm
      onChangeCredential={onChangeCredentialMock}
      processing={false}
      error={null}
      {...overrides}
    />,
  );
}

function getField(label: string) {
  return screen.getByLabelText(label);
}

describe("CredentialRotationForm", () => {
  beforeEach(() => {
    onChangeCredentialMock.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("validates that new passwords match", async () => {
    const user = userEvent.setup();
    renderForm();
    await user.type(getField("كلمة المرور الحالية"), "OldPass123!");
    await user.type(getField("كلمة المرور الجديدة"), "NewPass123!");
    await user.type(getField("تأكيد كلمة المرور الجديدة"), "Different123!");
    await user.click(screen.getByRole("button", { name: "تحديث كلمة المرور" }));
    expect(screen.getByRole("alert")).toHaveTextContent("كلمتا المرور الجديدتان غير متطابقتين.");
    expect(onChangeCredentialMock).not.toHaveBeenCalled();
  });

  it("prevents using the same password as current", async () => {
    const user = userEvent.setup();
    renderForm();
    await user.type(getField("كلمة المرور الحالية"), "SamePass1!");
    await user.type(getField("كلمة المرور الجديدة"), "SamePass1!");
    await user.type(getField("تأكيد كلمة المرور الجديدة"), "SamePass1!");
    await user.click(screen.getByRole("button", { name: "تحديث كلمة المرور" }));
    expect(screen.getByRole("alert")).toHaveTextContent("كلمة المرور الجديدة يجب أن تكون مختلفة عن الحالية.");
    expect(onChangeCredentialMock).not.toHaveBeenCalled();
  });

  it("calls changeCredential with the correct values", async () => {
    const user = userEvent.setup();
    onChangeCredentialMock.mockResolvedValue(undefined);
    renderForm();
    await user.type(getField("كلمة المرور الحالية"), "OldPass123!");
    await user.type(getField("كلمة المرور الجديدة"), "NewPass456!");
    await user.type(getField("تأكيد كلمة المرور الجديدة"), "NewPass456!");
    await user.click(screen.getByRole("button", { name: "تحديث كلمة المرور" }));
    await waitFor(() => {
      expect(onChangeCredentialMock).toHaveBeenCalledWith("OldPass123!", "NewPass456!");
    });
  });

  it("disables the form while processing", () => {
    renderForm({ processing: true });
    expect(screen.getByRole("button", { name: "جارٍ التحديث…" })).toBeDisabled();
    expect(getField("كلمة المرور الحالية")).toBeDisabled();
    expect(getField("كلمة المرور الجديدة")).toBeDisabled();
    expect(getField("تأكيد كلمة المرور الجديدة")).toBeDisabled();
  });
});
