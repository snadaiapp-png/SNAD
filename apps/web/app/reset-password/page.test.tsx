// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ResetPasswordPage from "./page";

const { resetPasswordMock } = vi.hoisted(() => ({
  resetPasswordMock: vi.fn(),
}));

vi.mock("@/lib/api/auth", () => ({
  authApi: {
    resetPassword: resetPasswordMock,
  },
}));

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

function setLocation(search: string) {
  window.history.replaceState({}, "", `/reset-password${search}`);
}

describe("ResetPasswordPage", () => {
  beforeEach(() => {
    resetPasswordMock.mockReset();
    setLocation("");
  });

  afterEach(() => {
    cleanup();
  });

  it("renders the password-reset form when a token is supplied", async () => {
    setLocation("?token=recovery-token");

    render(<ResetPasswordPage />);

    expect(
      await screen.findByRole("heading", {
        name: "إعداد كلمة مرور جديدة",
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByPlaceholderText("كلمة المرور الجديدة"),
    ).toBeInTheDocument();

    expect(
      screen.getByPlaceholderText("تأكيد كلمة المرور"),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", {
        name: "تحديث كلمة المرور",
      }),
    ).toBeEnabled();
  });

  it("rejects submission when the recovery token is missing", async () => {
    render(<ResetPasswordPage />);

    const submitButton = screen.getByRole("button", {
      name: "تحديث كلمة المرور",
    });

    await waitFor(() => {
      expect(submitButton).toBeDisabled();
    });

    expect(resetPasswordMock).not.toHaveBeenCalled();
  });

  it("rejects mismatched passwords without calling the API", async () => {
    const user = userEvent.setup();
    setLocation("?token=recovery-token");

    render(<ResetPasswordPage />);

    await user.type(
      screen.getByPlaceholderText("كلمة المرور الجديدة"),
      "Password123!",
    );

    await user.type(
      screen.getByPlaceholderText("تأكيد كلمة المرور"),
      "Different123!",
    );

    await user.click(
      screen.getByRole("button", {
        name: "تحديث كلمة المرور",
      }),
    );

    expect(
      screen.getByRole("alert"),
    ).toHaveTextContent("كلمتا المرور غير متطابقتين.");

    expect(resetPasswordMock).not.toHaveBeenCalled();
  });

  it("submits the token and new password to authApi.resetPassword", async () => {
    const user = userEvent.setup();
    resetPasswordMock.mockResolvedValue({
      message: "updated",
    });

    setLocation("?token=recovery-token");

    render(<ResetPasswordPage />);

    await user.type(
      screen.getByPlaceholderText("كلمة المرور الجديدة"),
      "Password123!",
    );

    await user.type(
      screen.getByPlaceholderText("تأكيد كلمة المرور"),
      "Password123!",
    );

    await user.click(
      screen.getByRole("button", {
        name: "تحديث كلمة المرور",
      }),
    );

    await waitFor(() => {
      expect(resetPasswordMock).toHaveBeenCalledTimes(1);
    });

    expect(resetPasswordMock).toHaveBeenCalledWith({
      token: "recovery-token",
      newPassword: "Password123!",
    });
  });

  it("renders the success state after a successful reset", async () => {
    const user = userEvent.setup();
    resetPasswordMock.mockResolvedValue({
      message: "updated",
    });

    setLocation("?token=recovery-token");

    render(<ResetPasswordPage />);

    await user.type(
      screen.getByPlaceholderText("كلمة المرور الجديدة"),
      "Password123!",
    );

    await user.type(
      screen.getByPlaceholderText("تأكيد كلمة المرور"),
      "Password123!",
    );

    await user.click(
      screen.getByRole("button", {
        name: "تحديث كلمة المرور",
      }),
    );

    expect(
      await screen.findByRole("heading", {
        name: "تم تحديث كلمة المرور",
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("link"),
    ).toHaveAttribute("href", "/");
  });

  it("renders an error when the reset API rejects the request", async () => {
    const user = userEvent.setup();
    resetPasswordMock.mockRejectedValue(
      new Error("expired token"),
    );

    setLocation("?token=recovery-token");

    render(<ResetPasswordPage />);

    await user.type(
      screen.getByPlaceholderText("كلمة المرور الجديدة"),
      "Password123!",
    );

    await user.type(
      screen.getByPlaceholderText("تأكيد كلمة المرور"),
      "Password123!",
    );

    await user.click(
      screen.getByRole("button", {
        name: "تحديث كلمة المرور",
      }),
    );

    expect(
      await screen.findByRole("alert"),
    ).toHaveTextContent(
      "تعذر استخدام الرابط. قد يكون منتهي الصلاحية أو مستخدمًا مسبقًا.",
    );
  });
});
