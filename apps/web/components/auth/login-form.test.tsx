// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { LoginForm } from "./login-form";
import { I18nProvider } from "@/lib/i18n/I18nProvider";

const onLoginMock = vi.fn();

vi.mock("next/image", () => ({
  default: ({ alt = "", ...props }: React.ImgHTMLAttributes<HTMLImageElement>) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img alt={alt} {...props} />
  ),
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

function renderLoginForm(overrides: Partial<React.ComponentProps<typeof LoginForm>> = {}) {
  return render(
    <I18nProvider>
      <LoginForm
        onLogin={onLoginMock}
        authenticating={false}
        error={null}
        {...overrides}
      />
    </I18nProvider>,
  );
}

describe("LoginForm", () => {
  beforeEach(() => {
    onLoginMock.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders the approved official wordmark above the form", () => {
    const { container } = renderLoginForm();
    const logo = container.querySelector(
      'img[src="/assets/brand/snad-logo-official-wordmark.png"]',
    );
    expect(logo).toBeInTheDocument();
  });

  it("renders email and password fields", () => {
    renderLoginForm();
    expect(screen.getByPlaceholderText("name@company.com")).toBeInTheDocument();
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

  it("clears an email validation error on the first correction keystroke", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
    expect(screen.getByText("البريد الإلكتروني مطلوب.")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("name@company.com"), "a");

    expect(screen.queryByText("البريد الإلكتروني مطلوب.")).not.toBeInTheDocument();
  });

  it("validates required password", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.type(screen.getByPlaceholderText("name@company.com"), "test@example.com");
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
    expect(screen.getByText("كلمة المرور مطلوبة.")).toBeInTheDocument();
    expect(onLoginMock).not.toHaveBeenCalled();
  });

  it("clears a password validation error on the first correction keystroke", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.type(screen.getByPlaceholderText("name@company.com"), "test@example.com");
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
    expect(screen.getByText("كلمة المرور مطلوبة.")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("••••••••"), "x");

    expect(screen.queryByText("كلمة المرور مطلوبة.")).not.toBeInTheDocument();
  });

  it("normalizes email to trimmed lowercase before calling login", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.type(screen.getByPlaceholderText("name@company.com"), "  Test@Example.COM  ");
    await user.type(screen.getByPlaceholderText("••••••••"), "Password123!");
    await user.click(screen.getByRole("button", { name: "تسجيل الدخول" }));
    expect(onLoginMock).toHaveBeenCalledWith("test@example.com", "Password123!");
  });

  it("calls onLogin with the correct values", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.type(screen.getByPlaceholderText("name@company.com"), "user@snad.app");
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

  it("shows and clears an advisory Caps Lock warning without blocking the field", () => {
    renderLoginForm();
    const passwordInput = screen.getByPlaceholderText("••••••••");

    const capsOn = new KeyboardEvent("keydown", { key: "A", bubbles: true });
    Object.defineProperty(capsOn, "getModifierState", {
      value: (key: string) => key === "CapsLock",
    });
    fireEvent(passwordInput, capsOn);
    expect(screen.getByRole("status")).toHaveTextContent("مفتاح Caps Lock مفعّل.");
    expect(passwordInput).not.toBeDisabled();

    const capsOff = new KeyboardEvent("keyup", { key: "a", bubbles: true });
    Object.defineProperty(capsOff, "getModifierState", {
      value: () => false,
    });
    fireEvent(passwordInput, capsOff);
    expect(screen.queryByText("مفتاح Caps Lock مفعّل.")).not.toBeInTheDocument();
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

  it("shows help panel and renders a forgot-password link with icon", async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.click(screen.getByRole("button", { name: "تحتاج مساعدة في الدخول؟" }));
    expect(screen.getByText(/تواصل مع مسؤول النظام/)).toBeInTheDocument();
    const forgotLink = screen.getByRole("link", { name: /نسيت كلمة المرور؟/ });
    expect(forgotLink).toHaveAttribute("href", "/auth/forgot-password");
  });

  it("renders the forgot-password link above the submit button (directly below password field)", () => {
    renderLoginForm();
    const forgotLink = screen.getByRole("link", { name: /نسيت كلمة المرور؟/ });
    const submitButton = screen.getByRole("button", { name: /تسجيل الدخول/ });
    expect(forgotLink.compareDocumentPosition(submitButton)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it("provides an accessible aria-label on the forgot-password link", () => {
    renderLoginForm();
    const forgotLink = screen.getByRole("link", { name: /نسيت كلمة المرور؟/ });
    expect(forgotLink).toHaveAttribute(
      "aria-label",
      "نسيت كلمة المرور؟",
    );
  });
});
