"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { KeyRound } from "lucide-react";
import styles from "./auth.module.css";
import { AuthErrorAlert } from "./auth-error-alert";
import type { UserFacingError } from "@/lib/api/user-facing-errors";

interface LoginFormProps {
  onLogin: (email: string, password: string) => Promise<void>;
  authenticating: boolean;
  error: UserFacingError | null;
  sessionExpired?: boolean;
}

export function LoginForm({
  onLogin,
  authenticating,
  error,
  sessionExpired = false,
}: LoginFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  function validate(): boolean {
    let valid = true;
    if (!email.trim()) {
      setEmailError("البريد الإلكتروني مطلوب.");
      valid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setEmailError("صيغة البريد الإلكتروني غير صالحة.");
      valid = false;
    } else {
      setEmailError(null);
    }
    if (!password) {
      setPasswordError("كلمة المرور مطلوبة.");
      valid = false;
    } else {
      setPasswordError(null);
    }
    return valid;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (authenticating) return;
    if (!validate()) return;
    const normalizedEmail = email.trim().toLowerCase();
    await onLogin(normalizedEmail, password);
  }

  const displayError = sessionExpired && !error
    ? { title: "انتهت الجلسة", message: "انتهت صلاحية جلستك. يرجى تسجيل الدخول مرة أخرى.", kind: "validation" as const }
    : error;

  return (
    <div className={styles.loginCard}>
      <div className={styles.loginBrandMark}>SNAD</div>
      <h1 className={styles.loginWelcomeTitle}>مرحبًا بعودتك</h1>
      <p className={styles.loginWelcomeSubtitle}>
        سجّل دخولك للوصول إلى منصة تشغيل الأعمال.
      </p>

      {displayError && <AuthErrorAlert error={displayError} />}

      <form onSubmit={handleSubmit} noValidate>
        <div className={styles.authField}>
          <label htmlFor="login-email" className={styles.authLabel}>
            البريد الإلكتروني
          </label>
          <div className={styles.authInputWrapper}>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              inputMode="email"
              dir="ltr"
              className={styles.authInput}
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={!!emailError}
              aria-describedby={emailError ? "login-email-error" : undefined}
              disabled={authenticating}
              required
            />
          </div>
          {emailError && (
            <span id="login-email-error" className={styles.authErrorMessage} role="alert">
              {emailError}
            </span>
          )}
        </div>

        <div className={styles.authField}>
          <label htmlFor="login-password" className={styles.authLabel}>
            كلمة المرور
          </label>
          <div className={styles.authInputWrapper}>
            <input
              id="login-password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              dir="ltr"
              className={styles.authInput}
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-invalid={!!passwordError}
              aria-describedby={passwordError ? "login-password-error" : undefined}
              disabled={authenticating}
              required
            />
            <button
              type="button"
              className={styles.passwordToggle}
              onClick={() => setShowPassword(!showPassword)}
              aria-label={showPassword ? "إخفاء كلمة المرور" : "إظهار كلمة المرور"}
              tabIndex={0}
            >
              {showPassword ? (
                <svg width="18" height="18" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path d="M3.28 2.22a.75.75 0 00-1.06 1.06l14.5 14.5a.75.75 0 101.06-1.06l-1.745-1.745A10.029 10.029 0 0020 10c0-1.2-.21-2.35-.6-3.4a.75.75 0 00-1.42.5c.32.9.52 1.88.52 2.9 0 1.17-.26 2.29-.72 3.3l-1.76-1.76A4.5 4.5 0 007.5 5.5c0-.23.02-.46.05-.69L3.28 2.22zM7.5 10a2.5 2.5 0 003.54 2.54l-3.04-3.04A2.5 2.5 0 007.5 10z" />
                  <path d="M10 3.5c-.73 0-1.44.08-2.13.23l1.6 1.6A4.5 4.5 0 0115.5 10c0 .23-.02.46-.05.69l1.76 1.76A8.5 8.5 0 0010 3.5z" />
                </svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path d="M10 12.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z" />
                  <path fillRule="evenodd" d="M.664 10.59a.75.75 0 010-1.18l.165-.12A8.5 8.5 0 0110 1.5c1.84 0 3.56.58 4.97 1.58l.16-.12a.75.75 0 01.93 1.18l-.16.12A8.5 8.5 0 0110 18.5a8.5 8.5 0 01-5.17-1.76l-.16.12a.75.75 0 01-.93-1.18l.16-.12A8.46 8.46 0 01.664 10.59zM3.5 10a6.5 6.5 0 1113 0 6.5 6.5 0 01-13 0z" clipRule="evenodd" />
                </svg>
              )}
            </button>
          </div>
          {passwordError && (
            <span id="login-password-error" className={styles.authErrorMessage} role="alert">
              {passwordError}
            </span>
          )}
        </div>

        <div className={styles.authForgotLinkRow}>
          <Link
            href="/auth/forgot-password"
            className={styles.authForgotLink}
            aria-label="نسيت كلمة المرور؟ استعادة كلمة المرور"
          >
            <KeyRound
              aria-hidden="true"
              className={styles.authForgotLinkIcon}
              size={16}
            />
            <span>نسيت كلمة المرور؟</span>
          </Link>
        </div>

        <button
          type="submit"
          className={styles.authSubmit}
          disabled={authenticating}
          aria-busy={authenticating}
        >
          {authenticating ? "جارٍ تسجيل الدخول…" : "تسجيل الدخول"}
        </button>
      </form>

      <button
        type="button"
        className={styles.authHelpLink}
        onClick={() => setShowHelp(!showHelp)}
        aria-expanded={showHelp}
      >
        تحتاج مساعدة في الدخول؟
      </button>

      {showHelp && (
        <div className={styles.authHelpPanel}>
          تواصل مع مسؤول النظام أو فريق الدعم لاستعادة الوصول إلى حسابك. إذا استلمت رابط استرداد آمنًا، افتح الرابط نفسه لإكمال العملية.
        </div>
      )}
    </div>
  );
}
