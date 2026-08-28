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
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

describe("ForgotPasswordPage", () => {
  beforeEach(() => {
    forgotPasswordMock.mockReset();
  });

  afterEach(() => cleanup());

  it("normalizes the login email before requesting recovery", async () => {
    const user = userEvent.setup();
    forgotPasswordMock.mockResolvedValue({ message: "ok" });

    render(<ForgotPasswordPage />);
    await user.type(screen.getByLabelText("البريد الإلكتروني"), "  SNAD.AI.APP@GMAIL.COM  ");
    await user.click(screen.getByRole("button", { name: "إرسال رابط الاستعادة" }));

    await waitFor(() => expect(forgotPasswordMock).toHaveBeenCalledTimes(1));
    expect(forgotPasswordMock).toHaveBeenCalledWith({ email: "snad.ai.app@gmail.com" });
    expect(await screen.findByRole("heading", { name: "تحقّق من بريدك الإلكتروني" })).toBeInTheDocument();
  });

  it("does not claim an email was sent when the recovery request itself fails", async () => {
    const user = userEvent.setup();
    forgotPasswordMock.mockRejectedValue(new Error("transport failed"));

    render(<ForgotPasswordPage />);
    await user.type(screen.getByLabelText("البريد الإلكتروني"), "snad.ai.app@gmail.com");
    await user.click(screen.getByRole("button", { name: "إرسال رابط الاستعادة" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "تعذر إرسال طلب الاستعادة. حاول مرة أخرى بعد قليل.",
    );
    expect(screen.queryByRole("heading", { name: "تحقّق من بريدك الإلكتروني" })).not.toBeInTheDocument();
  });
});
