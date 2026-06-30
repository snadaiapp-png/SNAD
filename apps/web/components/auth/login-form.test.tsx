// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { LoginForm } from "./login-form";

const onLoginMock = vi.fn();

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

function renderLoginForm(overrides: Partial<React.ComponentProps<typeof LoginForm>> = {}) {
  return render(
    <LoginForm
      onLogin={onLoginMock}
      authenticating={false}
      error={null}
      {...overrides}
    />,
  );
}

describe("LoginForm", () => {
  beforeEach(() => {
    onLoginMock.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders email and password fields", () => {
    renderLoginForm();
    expect(screen.getByPlaceholderText("you@example.com")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("••••••••")).toBeInTheDocument();
  });

  it("does not render a tenant UUID field", () => {
    renderLoginForm();
    expect(screen.queryByText(/tenant/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/مستأجر/i)).not.toBeInTheDocument();
  });

  it("validates required email", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
    expect(screen.getByText("البريد الإلكتروني مطلوب.")).toBeInTheDocument();
    expect(onLoginMock).not.toHaveBeenCalled();
  });

  it("validates required password", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.type(screen.getByPlaceholderText("you@example.com"), "test@example.com");
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
    expect(screen.getByText("كلمة المرور مطلوبة.")).toBeInTheDocument();
    expect(onLoginMock).not.toHaveBeenCalled();
  });

  it("normalizes email to trimmed lowercase before calling login", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.type(screen.getByPlaceholderText("you@example.com"), "  Test@Example.COM  ");
    await user.type(screen.getByPlaceholderText("••••••••"), "Password123!");
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
    expect(onLoginMock).toHaveBeenCalledWith("test@example.com", "Password123!");
  });

  it("calls onLogin with the correct values", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.type(screen.getByPlaceholderText("you@example.com"), "user@snad.app");
    await user.type(screen.getByPlaceholderText("••••••••"), "SecretPass1!");
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
    expect(onLoginMock).toHaveBeenCalledTimes(1);
    expect(onLoginMock).toHaveBeenCalledWith("user@snad.app", "SecretPass1!");
  });

  it("disables the submit button while authenticating", () => {
    renderLoginForm({ authenticating: true });
    const button = screen.getByRole("button", { name: "جارٍ تسجيل الدخول…" });
    expect(button).toBeDisabled();
  });

  it("toggles password visibility", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    const passwordInput = screen.getByPlaceholderText("••••••••");
    expect(passwordInput).toHaveAttribute("type", "password");
    const toggle = screen.getByLabelText("إظهار كلمة المرور");
    await user.click(toggle);
    expect(passwordInput).toHaveAttribute("type", "text");
    const hideToggle = screen.getByLabelText("إخفاء كلمة المرور");
    await user.click(hideToggle);
    expect(passwordInput).toHaveAttribute("type", "password");
  });

  it("displays user-facing error safely", () => {
    renderLoginForm({
      error: { title: "غير مصرح", message: "البريد الإلكتروني أو كلمة المرور غير صحيحة.", kind: "validation" },
    });
    expect(screen.getByRole("alert")).toHaveTextContent("غير مصرح");
    expect(screen.getByRole("alert")).toHaveTextContent("البريد الإلكتروني أو كلمة المرور غير صحيحة.");
  });

  it("does not display raw error or stack trace", () => {
    renderLoginForm({
      error: { title: "خطأ في الخادم", message: "حدث خطأ داخلي في الخادم.", kind: "server" },
    });
    const alert = screen.getByRole("alert");
    expect(alert.textContent).not.toMatch(/stack|trace|at \//i);
    expect(alert.textContent).not.toMatch(/https?:\/\//);
  });

  it("shows help panel without a generic /reset-password link", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.click(screen.getByRole("button", { name: "تحتاج مساعدة في الدخول؟" }));
    // Help panel appears
    expect(screen.getByText(/تواصل مع مسؤول النظام/)).toBeInTheDocument();
    // No generic /reset-password link is rendered
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
