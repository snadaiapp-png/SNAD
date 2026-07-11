"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { authApi } from "@/lib/api/auth";
import { KeyRound } from "lucide-react";

/**
 * Forgot Password page.
 *
 * Flow:
 *  1. User enters their email.
 *  2. We POST /api/v1/auth/forgot-password — backend always returns 200
 *     (anti-enumeration) and emails a single-use reset link if the account exists.
 *  3. UI shows a generic confirmation message regardless of whether the email
 *     is registered, to mirror the backend's anti-enumeration behavior.
 *  4. If the backend returns a resetUrl (pilot/dev mode only), we display it
 *     inline so the user can click through without an email roundtrip.
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
    setError(null);
    setResetUrl(null);

    try {
      const response = await authApi.forgotPassword({ email: normalizedEmail });
      setSubmitted(true);
      // Some pilot/dev backends return the reset URL directly for convenience.
      if (response?.resetUrl) {
        setResetUrl(response.resetUrl);
      }
    } catch {
      // Per anti-enumeration design, still show the success message.
      // The backend itself returns 200 even for unknown emails.
      setSubmitted(true);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="snad-reset-root" aria-label="استعادة كلمة المرور">
      <div className="snad-reset-card">
        <div className="snad-reset-brand">
          <KeyRound aria-hidden="true" className="snad-reset-brand-icon" />
          <span className="snad-reset-brand-mark">SNAD</span>
        </div>
        <h1 className="snad-reset-title">
          {submitted ? "تحقّق من بريدك الإلكتروني" : "استعادة كلمة المرور"}
        </h1>
        <p className="snad-reset-description">
          {submitted
            ? "إذا كان البريد الإلكتروني مرتبطًا بحساب في منصة سند، فقد أرسلنا رابط استعادة آمنًا صالحًا لمرة واحدة فقط."
            : "أدخل بريدك الإلكتروني وسنرسل لك رابطًا آمنًا لإعادة تعيين كلمة المرور."}
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
              type="email"
              autoComplete="email"
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
