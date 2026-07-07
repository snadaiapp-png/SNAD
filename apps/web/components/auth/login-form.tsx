"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import styles from "./auth.module.css";
import { AuthErrorAlert } from "./auth-error-alert";
import type { UserFacingError } from "@/lib/api/user-facing-errors";
import { SnadLogo } from "@/components/sds";
import { useTheme, type ThemePreference } from "@/lib/hooks/useTheme";
import { useI18n, type Locale } from "@/app/providers";

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
  const { locale, setLocale, t } = useI18n();
  const { theme, preference, setTheme } = useTheme();
  const logoVariant = theme === "dark" ? "white" : "primary";

  function validate(): boolean {
    let valid = true;
    if (!email.trim()) {
      setEmailError(t("emailRequired"));
      valid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setEmailError(t("emailInvalid"));
      valid = false;
    } else {
      setEmailError(null);
    }

    if (!password) {
      setPasswordError(t("passwordRequired"));
      valid = false;
    } else {
      setPasswordError(null);
    }
    return valid;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (authenticating || !validate()) return;
    await onLogin(email.trim().toLowerCase(), password);
  }

  const displayError = sessionExpired && !error
    ? {
        title: t("sessionExpired"),
        message: t("sessionExpiredMessage"),
        kind: "validation" as const,
      }
    : error;

  return (
    <div className={styles.loginCard}>
      <div className={styles.authForgotLinkRow} role="group" aria-label={`${t("language")} · ${t("appearance")}`}>
        <label className={styles.authLabel}>
          <span>{t("language")}</span>
          <select
            className={styles.authInput}
            value={locale}
            onChange={(event) => setLocale(event.target.value as Locale)}
            aria-label={t("language")}
          >
            <option value="ar">{t("arabic")}</option>
            <option value="en">{t("english")}</option>
          </select>
        </label>
        <label className={styles.authLabel}>
          <span>{t("appearance")}</span>
          <select
            className={styles.authInput}
            value={preference}
            onChange={(event) => setTheme(event.target.value as ThemePreference)}
            aria-label={t("appearance")}
          >
            <option value="light">{t("light")}</option>
            <option value="dark">{t("dark")}</option>
            <option value="system">{t("system")}</option>
          </select>
        </label>
      </div>

      <div className={styles.loginBrandMark}>
        <SnadLogo
          variant={logoVariant}
          size="responsive"
          href="/"
          alt={t("logoAlt")}
          priority
        />
      </div>
      <h1 className={styles.loginWelcomeTitle}>{t("welcome")}</h1>
      <p className={styles.loginWelcomeSubtitle}>{t("loginSubtitle")}</p>

      {displayError && <AuthErrorAlert error={displayError} />}

      <form onSubmit={handleSubmit} noValidate>
        <div className={styles.authField}>
          <label htmlFor="login-email" className={styles.authLabel}>{t("email")}</label>
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
              onChange={(event) => setEmail(event.target.value)}
              aria-invalid={Boolean(emailError)}
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
          <label htmlFor="login-password" className={styles.authLabel}>{t("password")}</label>
          <div className={styles.authInputWrapper}>
            <input
              id="login-password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              dir="ltr"
              className={styles.authInput}
              placeholder="••••••••"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={Boolean(passwordError)}
              aria-describedby={passwordError ? "login-password-error" : undefined}
              disabled={authenticating}
              required
            />
            <button
              type="button"
              className={styles.passwordToggle}
              onClick={() => setShowPassword((visible) => !visible)}
              aria-label={showPassword ? t("hidePassword") : t("showPassword")}
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
          <Link href="/auth/forgot-password" className={styles.authForgotLink} aria-label={t("forgotPasswordLabel")}>
            <svg className={styles.authForgotLinkIcon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4" />
            </svg>
            <span>{t("forgotPassword")}</span>
          </Link>
        </div>

        <button type="submit" className={styles.authSubmit} disabled={authenticating} aria-busy={authenticating}>
          {authenticating ? t("loggingIn") : t("login")}
        </button>
      </form>

      <button type="button" className={styles.authHelpLink} onClick={() => setShowHelp((visible) => !visible)} aria-expanded={showHelp}>
        {t("needHelp")}
      </button>

      {showHelp && <div className={styles.authHelpPanel}>{t("helpText")}</div>}
    </div>
  );
}
