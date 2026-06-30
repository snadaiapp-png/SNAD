"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { authApi } from "@/lib/api/auth";

export default function ResetPasswordPage() {
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const value = new URLSearchParams(window.location.search).get("token") ?? "";
    queueMicrotask(() => setToken(value));
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return setError("رابط الاسترداد غير صالح.");
    if (password.length < 8) return setError("يجب أن تكون كلمة المرور 8 أحرف على الأقل.");
    if (password !== confirmation) return setError("كلمتا المرور غير متطابقتين.");
    setBusy(true);
    setError(null);
    try {
      await authApi.resetPassword({ token, newPassword: password });
      setSuccess(true);
    } catch {
      setError("تعذر استخدام الرابط. قد يكون منتهي الصلاحية أو مستخدمًا مسبقًا.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="snad-reset-root" aria-label="إعادة ضبط كلمة المرور">
      <div className="snad-reset-card">
        <h1 className="snad-reset-title">
          {success ? "تم تحديث كلمة المرور" : "إعداد كلمة مرور جديدة"}
        </h1>
        <p className="snad-reset-description">
          {success
            ? "يمكنك الآن تسجيل الدخول باستخدام كلمة المرور الجديدة."
            : "استخدم الرابط أحادي الاستخدام لإكمال إعداد الحساب."}
        </p>
        {success ? (
          <Link href="/" className="snad-reset-submit">
            العودة إلى الصفحة الرئيسية
          </Link>
        ) : (
          <form onSubmit={submit} className="snad-reset-form">
            {error && (
              <div className="snad-reset-alert" role="alert">
                {error}
              </div>
            )}
            <input
              dir="ltr"
              type="password"
              autoComplete="new-password"
              placeholder="كلمة المرور الجديدة"
              required
              minLength={8}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="snad-reset-input"
            />
            <input
              dir="ltr"
              type="password"
              autoComplete="new-password"
              placeholder="تأكيد كلمة المرور"
              required
              minLength={8}
              value={confirmation}
              onChange={(event) => setConfirmation(event.target.value)}
              className="snad-reset-input"
            />
            <button disabled={busy || !token} className="snad-reset-submit">
              {busy ? "جارٍ الحفظ…" : "تحديث كلمة المرور"}
            </button>
          </form>
        )}
      </div>
    </main>
  );
}
