// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ForgotPasswordPage from "./page";

const { forgotPasswordMock } = vi.hoisted(() => ({
  forgotPasswordMock: vi.fn(),
}));

vi.mock("@/lib/api/auth", () => ({
  authApi: {
    forgotPassword: forgotPasswordMock,
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

vi.mock("lucide-react", () => ({
  KeyRound: (props: React.SVGProps<SVGSVGElement>) => (
    <svg data-testid="key-round-icon" {...props} />
  ),
}));

describe("ForgotPasswordPage", () => {
  beforeEach(() => {
    forgotPasswordMock.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders the request form with email input and submit button", () => {
    render(<ForgotPasswordPage />);

    expect(
      screen.getByRole("heading", {
        name: "استعادة كلمة المرور",
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByLabelText("البريد الإلكتروني"),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", {
        name: "إرسال رابط الاستعادة",
      }),
    ).toBeEnabled();
  });

  it("renders the brand icon for visual identity", () => {
    render(<ForgotPasswordPage />);
    expect(screen.getByTestId("key-round-icon")).toBeInTheDocument();
  });

  it("renders a back-to-login link", () => {
    render(<ForgotPasswordPage />);

    const loginLink = screen.getByRole("link", {
      name: "العودة إلى تسجيل الدخول",
    });
    expect(loginLink).toHaveAttribute("href", "/");
  });

  it("rejects submission when email is empty", async () => {
    const user = userEvent.setup();
    render(<ForgotPasswordPage />);

    await user.click(
      screen.getByRole("button", {
        name: "إرسال رابط الاستعادة",
      }),
    );

    expect(
      screen.getByRole("alert"),
    ).toHaveTextContent("البريد الإلكتروني مطلوب.");

    expect(forgotPasswordMock).not.toHaveBeenCalled();
  });

  it("rejects submission when email is malformed", async () => {
    const user = userEvent.setup();
    render(<ForgotPasswordPage />);

    await user.type(
      screen.getByLabelText("البريد الإلكتروني"),
      "not-an-email",
    );

    await user.click(
      screen.getByRole("button", {
        name: "إرسال رابط الاستعادة",
      }),
    );

    expect(
      screen.getByRole("alert"),
    ).toHaveTextContent("صيغة البريد الإلكتروني غير صالحة.");

    expect(forgotPasswordMock).not.toHaveBeenCalled();
  });

  it("submits the normalized email to authApi.forgotPassword", async () => {
    const user = userEvent.setup();
    forgotPasswordMock.mockResolvedValue({
      message: "If the email exists, a reset link has been sent.",
    });

    render(<ForgotPasswordPage />);

    await user.type(
      screen.getByLabelText("البريد الإلكتروني"),
      "  Admin@Example.COM  ",
    );

    await user.click(
      screen.getByRole("button", {
        name: "إرسال رابط الاستعادة",
      }),
    );

    await waitFor(() => {
      expect(forgotPasswordMock).toHaveBeenCalledTimes(1);
    });

    expect(forgotPasswordMock).toHaveBeenCalledWith({
      email: "admin@example.com",
    });
  });

  it("shows the success message after submission", async () => {
    const user = userEvent.setup();
    forgotPasswordMock.mockResolvedValue({
      message: "If the email exists, a reset link has been sent.",
    });

    render(<ForgotPasswordPage />);

    await user.type(
      screen.getByLabelText("البريد الإلكتروني"),
      "user@example.com",
    );

    await user.click(
      screen.getByRole("button", {
        name: "إرسال رابط الاستعادة",
      }),
    );

    expect(
      await screen.findByRole("heading", {
        name: "تحقّق من بريدك الإلكتروني",
      }),
    ).toBeInTheDocument();
  });

  it("still shows the success message when the API rejects (anti-enumeration)", async () => {
    const user = userEvent.setup();
    forgotPasswordMock.mockRejectedValue(new Error("network error"));

    render(<ForgotPasswordPage />);

    await user.type(
      screen.getByLabelText("البريد الإلكتروني"),
      "user@example.com",
    );

    await user.click(
      screen.getByRole("button", {
        name: "إرسال رابط الاستعادة",
      }),
    );

    expect(
      await screen.findByRole("heading", {
        name: "تحقّق من بريدك الإلكتروني",
      }),
    ).toBeInTheDocument();

    // The error must NOT be surfaced to the user (anti-enumeration).
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("renders the inline reset link when the backend returns resetUrl (pilot mode)", async () => {
    const user = userEvent.setup();
    forgotPasswordMock.mockResolvedValue({
      message: "pilot mode",
      resetUrl: "/reset-password?token=pilot-token",
    });

    render(<ForgotPasswordPage />);

    await user.type(
      screen.getByLabelText("البريد الإلكتروني"),
      "user@example.com",
    );

    await user.click(
      screen.getByRole("button", {
        name: "إرسال رابط الاستعادة",
      }),
    );

    const continueLink = await screen.findByRole("link", {
      name: "متابعة إعادة تعيين كلمة المرور",
    });
    expect(continueLink).toHaveAttribute(
      "href",
      "/reset-password?token=pilot-token",
    );
  });
});
