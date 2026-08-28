"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { authApi } from "@/lib/api/auth";

/**
 * Forgot Password page.
 *
 * Unknown accounts still receive the backend's generic 200 response to prevent
 * account enumeration. Transport/rate-limit/server failures are different: the
 * UI must not falsely claim that a recovery email was sent when the request
 * itself did not complete.
 */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resetUrl, setResetUrl] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;

    const normalizedEmail = email.trim().toLowerCase();
    if (!normalizedEmail) {
      setError("البريد الإلكتروني مطلوب.");
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
      setError("صيغة البريد الإلكتروني غير صالحة.");
      return;
    }

    setBusy(true);
    setSubmitted(false);
    setError(null);
    setResetUrl(null);

    try {
      const response = await authApi.forgotPassword({ email: normalizedEmail });
      setSubmitted(true);
      if (response?.resetUrl) {
        setResetUrl(response.resetUrl);
      }
    } catch {
      // Unknown accounts are already masked by the backend's generic HTTP 200.
      // Reaching this branch therefore means the request itself failed (for
      // example rate limiting, network failure, or a server error), which is
      // safe and necessary to report without disclosing account existence.
      setError("تعذر إرسال طلب الاستعادة. حاول مرة أخرى بعد قليل.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="snad-reset-root" aria-label="استعادة كلمة المرور">
      <div className="snad-reset-card">
        <svg
          className="snad-reset-brand-icon"
          width="32"
          height="32"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="m21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4" />
        </svg>

        <h1 className="snad-reset-title">
          {submitted ? "تحقّق من بريدك الإلكتروني" : "استعادة كلمة المرور"}
        </h1>
        <p className="snad-reset-description">
          {submitted
            ? "إذا كان البريد الإلكتروني مرتبطًا بحساب في منصة سند، فقد أرسلنا رابط استعادة آمنًا صالحًا لمرة واحدة فقط."
            : "أدخل نفس البريد الإلكتروني الذي تستخدمه لتسجيل الدخول وسنرسل لك رابطًا آمنًا لإعادة تعيين كلمة المرور."}
        </p>

        {submitted ? (
          <div className="snad-reset-form">
            {resetUrl && (
              <div className="snad-reset-info" role="status">
                <p className="snad-reset-info-title">رابط الاستعادة (وضع التشغيل التجريبي):</p>
                <Link href={resetUrl} className="snad-reset-submit">
                  متابعة إعادة تعيين كلمة المرور
                </Link>
              </div>
            )}
            <Link href="/" className="snad-reset-secondary">
              العودة إلى تسجيل الدخول
            </Link>
          </div>
        ) : (
          <form onSubmit={submit} className="snad-reset-form" noValidate>
            {error && (
              <div className="snad-reset-alert" role="alert">
                {error}
              </div>
            )}
            <label htmlFor="forgot-email" className="snad-reset-label">
              البريد الإلكتروني
            </label>
            <input
              id="forgot-email"
              name="username"
              type="email"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              inputMode="email"
              dir="ltr"
              placeholder="you@example.com"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="snad-reset-input"
              disabled={busy}
            />
            <button
              type="submit"
              disabled={busy}
              className="snad-reset-submit"
              aria-busy={busy}
            >
              {busy ? "جارٍ الإرسال…" : "إرسال رابط الاستعادة"}
            </button>
            <Link href="/" className="snad-reset-secondary">
              العودة إلى تسجيل الدخول
            </Link>
          </form>
        )}
      </div>
    </main>
  );
}
