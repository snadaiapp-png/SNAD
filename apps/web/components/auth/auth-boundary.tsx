"use client";

import { type ReactNode, FormEvent, useState, useEffect } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-provider";
import { authApi } from "@/lib/api/auth";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";

/**
 * Auth Boundary — shows login when anonymous, shows children when authenticated.
 * Also handles:
 * - INITIALIZING (session verification)
 * - AMBIGUOUS_TENANT (tenant picker)
 * - CREDENTIAL_ROTATION_REQUIRED (forced password change)
 * - Forgot password flow
 * - Reset password flow
 *
 * The login form is rendered inline (not imported from /app/login) to avoid
 * SSR issues with useAuth.
 *
 * Login is email+password only — no Tenant UUID required.
 * Tenant context is derived automatically from the authenticated session.
 * If the same email exists in multiple tenants, a 409 is returned and
 * the user is prompted to select the tenant they want to log into.
 */
export function AuthBoundary({ children }: { children: ReactNode }) {
  const { state, error, login, loginWithTenant, dismissAmbiguousTenant, ambiguousTenantIds } = useAuth();

  if (state === "AUTHENTICATED") {
    return <>{children}</>;
  }

  if (state === "INITIALIZING" || state === "REFRESHING") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a]" dir="rtl">
        <p className="text-sm font-bold text-teal-200">جارٍ التحقق من الجلسة…</p>
      </div>
    );
  }

  if (state === "AMBIGUOUS_TENANT") {
    return (
      <TenantPicker
        tenantIds={ambiguousTenantIds}
        loginWithTenant={loginWithTenant}
        onBack={dismissAmbiguousTenant}
      />
    );
  }

  if (state === "CREDENTIAL_ROTATION_REQUIRED") {
    return <ForcedPasswordChange />;
  }

  // ANONYMOUS, AUTHENTICATING, ERROR, EXPIRED, LOGGING_OUT → show login form
  return <LoginForm login={login} state={state} error={error} />;
}

/**
 * Tenant picker — shown when the same email exists in multiple tenants (409).
 * The user selects which tenant to log into, then login is retried with tenantId.
 */
function TenantPicker({
  tenantIds,
  loginWithTenant,
  onBack,
}: {
  tenantIds: string[];
  loginWithTenant: (tenantId: string) => Promise<void>;
  onBack: () => void;
}) {
  const [selectedTenant, setSelectedTenant] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!selectedTenant) return;
    setBusy(true);
    await loginWithTenant(selectedTenant);
    setBusy(false);
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a] px-4" dir="rtl">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <p className="text-xs font-black tracking-[0.18em] text-teal-700">SANAD BUSINESS OPERATING SYSTEM</p>
          <h1 className="mt-2 text-2xl font-black text-slate-900">اختيار المستأجر</h1>
          <p className="mt-1 text-sm text-slate-500">
            بريدك الإلكتروني مرتبط بأكثر من مستأجر. اختر المستأجر الذي تريد تسجيل الدخول إليه.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            {tenantIds.map((tid) => (
              <label
                key={tid}
                className={`flex cursor-pointer items-center gap-3 rounded-xl border p-3 transition-colors ${
                  selectedTenant === tid
                    ? "border-teal-600 bg-teal-50 ring-1 ring-teal-600"
                    : "border-slate-200 hover:border-teal-400"
                }`}
              >
                <input
                  type="radio"
                  name="tenantId"
                  value={tid}
                  checked={selectedTenant === tid}
                  onChange={() => setSelectedTenant(tid)}
                  className="accent-teal-700"
                />
                <span dir="ltr" className="font-mono text-sm text-slate-700">
                  {tid}
                </span>
              </label>
            ))}
          </div>

          <button
            type="submit"
            disabled={!selectedTenant || busy}
            className="w-full rounded-xl bg-teal-800 px-4 py-3 font-black text-white disabled:opacity-50"
          >
            {busy ? "جارٍ تسجيل الدخول…" : "تسجيل الدخول إلى المستأجر المحدد"}
          </button>

          <button
            type="button"
            onClick={onBack}
            className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm font-bold text-slate-600 hover:bg-slate-50"
          >
            العودة لتسجيل الدخول
          </button>
        </form>
      </div>
    </div>
  );
}

/**
 * Forced password change — shown when credentialRotationRequired=true.
 * The user must change their password before accessing the platform.
 * The JWT filter blocks all API calls except /auth/me, /auth/change-credential, /auth/logout.
 */
function ForcedPasswordChange() {
  const { changeCredential, logout, error, clearError } = useAuth();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setLocalError(null);
    clearError();

    // Client-side validation: minimum length
    if (newPassword.length < 8) {
      setLocalError("يجب أن تكون كلمة المرور الجديدة 8 أحرف على الأقل.");
      return;
    }

    // Client-side validation: confirm password match
    if (newPassword !== confirmPassword) {
      setLocalError("كلمة المرور الجديدة وتأكيدها غير متطابقين.");
      return;
    }

    // Client-side validation: must differ from current password
    if (newPassword === currentPassword) {
      setLocalError("يجب أن تختلف كلمة المرور الجديدة عن الحالية.");
      return;
    }

    setBusy(true);
    try {
      await changeCredential(currentPassword, newPassword);
      setSuccess(true);
    } catch {
      // Error is handled by auth provider — displayed via error prop
    } finally {
      setBusy(false);
    }
  }

  // Password strength indicator
  const passwordStrength = (() => {
    if (!newPassword) return { level: 0, label: "", color: "" };
    let score = 0;
    if (newPassword.length >= 8) score++;
    if (newPassword.length >= 12) score++;
    if (/[A-Z]/.test(newPassword)) score++;
    if (/[0-9]/.test(newPassword)) score++;
    if (/[^A-Za-z0-9]/.test(newPassword)) score++;
    if (score <= 2) return { level: 1, label: "ضعيفة", color: "bg-rose-400" };
    if (score <= 3) return { level: 2, label: "متوسطة", color: "bg-amber-400" };
    return { level: 3, label: "قوية", color: "bg-emerald-400" };
  })();

  if (success) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a] px-4" dir="rtl">
        <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
          <div className="text-center">
            <div className="mx-auto mb-4 grid size-16 place-items-center rounded-full bg-emerald-50 ring-4 ring-emerald-100">
              <span className="text-2xl text-emerald-600">✓</span>
            </div>
            <p className="text-xs font-black tracking-[0.18em] text-teal-700">SANAD BUSINESS OPERATING SYSTEM</p>
            <h1 className="mt-2 text-2xl font-black text-slate-900">تم تغيير كلمة المرور بنجاح</h1>
            <p className="mt-2 text-sm text-slate-500">
              تم تحديث كلمة المرور الخاصة بك. يمكنك الآن الوصول إلى المنصة.
            </p>
          </div>
          <div className="mt-6">
            <Link
              href="/"
              className="block w-full rounded-xl bg-teal-800 px-4 py-3 text-center font-black text-white hover:bg-teal-900"
            >
              المتابعة إلى المنصة
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a] px-4" dir="rtl">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <p className="text-xs font-black tracking-[0.18em] text-teal-700">SANAD BUSINESS OPERATING SYSTEM</p>
          <h1 className="mt-2 text-2xl font-black text-slate-900">تغيير كلمة المرور مطلوب</h1>
          <p className="mt-1 text-sm text-slate-500">
            يجب تغيير كلمة المرور الخاصة بك قبل الاستمرار في استخدام المنصة.
          </p>
        </div>

        {(localError || error) && (
          <div className="mb-4 rounded-xl bg-rose-50 p-3 ring-1 ring-rose-600/20" role="alert">
            <p className="text-sm font-black text-rose-700">
              {localError || error?.title || "حدث خطأ"}
            </p>
            {(error?.message && !localError) && (
              <p className="mt-1 text-sm text-rose-600">{error.message}</p>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            كلمة المرور الحالية
            <input
              dir="ltr"
              type="password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
            />
          </label>
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            كلمة المرور الجديدة
            <input
              dir="ltr"
              type="password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              minLength={8}
              className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
            />
          </label>
          {/* Password strength indicator */}
          {newPassword.length > 0 && (
            <div className="flex items-center gap-2 px-1">
              <div className="flex flex-1 gap-1">
                {[1, 2, 3].map((level) => (
                  <div
                    key={level}
                    className={`h-1.5 flex-1 rounded-full transition-colors ${
                      passwordStrength.level >= level ? passwordStrength.color : "bg-slate-200"
                    }`}
                  />
                ))}
              </div>
              <span className="text-xs font-bold text-slate-500">{passwordStrength.label}</span>
            </div>
          )}
          {/* Password policy hint */}
          <div className="rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-500" dir="rtl">
            <p className="font-bold text-slate-600">متطلبات كلمة المرور:</p>
            <ul className="mt-1 space-y-0.5 pr-3">
              <li className={newPassword.length >= 8 ? "text-emerald-600" : ""}>
                {newPassword.length >= 8 ? "✓" : "○"} 8 أحرف على الأقل
              </li>
              <li className={/[A-Z]/.test(newPassword) ? "text-emerald-600" : ""}>
                {/[A-Z]/.test(newPassword) ? "✓" : "○"} حرف كبير واحد على الأقل
              </li>
              <li className={/[0-9]/.test(newPassword) ? "text-emerald-600" : ""}>
                {/[0-9]/.test(newPassword) ? "✓" : "○"} رقم واحد على الأقل
              </li>
            </ul>
          </div>
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            تأكيد كلمة المرور الجديدة
            <input
              dir="ltr"
              type="password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              minLength={8}
              className={`rounded-xl border px-3 py-2.5 text-sm outline-none focus:border-teal-600 ${
                confirmPassword && newPassword !== confirmPassword
                  ? "border-rose-300 ring-1 ring-rose-200"
                  : "border-slate-200"
              }`}
            />
            {confirmPassword && newPassword !== confirmPassword && (
              <p className="text-xs font-bold text-rose-500">كلمة المرور غير متطابقة</p>
            )}
          </label>
          <button
            type="submit"
            disabled={busy || (confirmPassword.length > 0 && newPassword !== confirmPassword)}
            className="w-full rounded-xl bg-teal-800 px-4 py-3 font-black text-white disabled:opacity-50"
          >
            {busy ? "جارٍ تغيير كلمة المرور…" : "تغيير كلمة المرور"}
          </button>
          <button
            type="button"
            onClick={() => logout()}
            className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm font-bold text-slate-600 hover:bg-slate-50"
          >
            تسجيل الخروج
          </button>
        </form>
      </div>
    </div>
  );
}

function LoginForm({
  login,
  state,
  error,
}: {
  login: (req: { email: string; password: string }) => Promise<void>;
  state: string;
  error: { title: string; message: string } | null;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const busy = state === "AUTHENTICATING";

  if (showForgotPassword) {
    return (
      <ForgotPasswordForm
        onBack={() => setShowForgotPassword(false)}
        initialEmail={email}
      />
    );
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    await login({ email: email.trim(), password });
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a] px-4" dir="rtl">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <p className="text-xs font-black tracking-[0.18em] text-teal-700">SANAD BUSINESS OPERATING SYSTEM</p>
          <h1 className="mt-2 text-2xl font-black text-slate-900">تسجيل الدخول</h1>
          <p className="mt-1 text-sm text-slate-500">أدخل بريدك الإلكتروني وكلمة المرور</p>
        </div>

        {error && (
          <div className="mb-4 rounded-xl bg-rose-50 p-3 ring-1 ring-rose-600/20" role="alert">
            <p className="text-sm font-black text-rose-700">{error.title}</p>
            <p className="mt-1 text-sm text-rose-600">{error.message}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            البريد الإلكتروني
            <input
              dir="ltr"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@example.invalid"
              className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
            />
          </label>
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            كلمة المرور
            <input
              dir="ltr"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
            />
          </label>
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-xl bg-teal-800 px-4 py-3 font-black text-white disabled:opacity-50"
          >
            {busy ? "جارٍ تسجيل الدخول…" : "تسجيل الدخول"}
          </button>
        </form>
        <div className="mt-4 text-center">
          <button
            type="button"
            onClick={() => setShowForgotPassword(true)}
            className="text-sm font-bold text-teal-700 hover:text-teal-900 hover:underline"
          >
            نسيت كلمة المرور؟
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Forgot Password form — sends a reset link to the user's email.
 * Always shows success message (no account enumeration).
 */
function ForgotPasswordForm({
  onBack,
  initialEmail,
}: {
  onBack: () => void;
  initialEmail: string;
}) {
  const [email, setEmail] = useState(initialEmail);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<UserFacingError | null>(null);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.forgotPassword({ email: email.trim() });
      setSent(true);
    } catch (err) {
      setError(toUserFacingError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a] px-4" dir="rtl">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <p className="text-xs font-black tracking-[0.18em] text-teal-700">SANAD BUSINESS OPERATING SYSTEM</p>
          <h1 className="mt-2 text-2xl font-black text-slate-900">نسيت كلمة المرور؟</h1>
          <p className="mt-1 text-sm text-slate-500">
            أدخل بريدك الإلكتروني وسنرسل لك رابط إعادة تعيين كلمة المرور.
          </p>
        </div>

        {sent ? (
          <div className="space-y-4">
            <div className="rounded-xl bg-emerald-50 p-4 ring-1 ring-emerald-600/20" role="alert">
              <p className="text-sm font-black text-emerald-700">تم إرسال رابط إعادة التعيين</p>
              <p className="mt-1 text-sm text-emerald-600">
                إذا كان البريد الإلكتروني مسجلاً لدينا، فستتلقى رابط إعادة تعيين كلمة المرور على بريدك الإلكتروني.
              </p>
            </div>
            <button
              type="button"
              onClick={onBack}
              className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm font-bold text-slate-600 hover:bg-slate-50"
            >
              العودة لتسجيل الدخول
            </button>
          </div>
        ) : (
          <>
            {error && (
              <div className="mb-4 rounded-xl bg-rose-50 p-3 ring-1 ring-rose-600/20" role="alert">
                <p className="text-sm font-black text-rose-700">{error.title}</p>
                <p className="mt-1 text-sm text-rose-600">{error.message}</p>
              </div>
            )}
            <form onSubmit={handleSubmit} className="space-y-4">
              <label className="grid gap-1 text-sm font-bold text-slate-700">
                البريد الإلكتروني
                <input
                  dir="ltr"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@example.invalid"
                  className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
                />
              </label>
              <button
                type="submit"
                disabled={busy}
                className="w-full rounded-xl bg-teal-800 px-4 py-3 font-black text-white disabled:opacity-50"
              >
                {busy ? "جارٍ الإرسال…" : "إرسال رابط إعادة التعيين"}
              </button>
              <button
                type="button"
                onClick={onBack}
                className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm font-bold text-slate-600 hover:bg-slate-50"
              >
                العودة لتسجيل الدخول
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}

/**
 * Reset Password page — handles the token from the email reset link.
 * URL: /reset-password?token=<token>
 * This is a standalone component that can be used in a Next.js page.
 */
export function ResetPasswordPage() {
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<UserFacingError | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    // Extract token from URL query parameters
    if (typeof window !== "undefined") {
      const params = new URLSearchParams(window.location.search);
      const t = params.get("token");
      if (t) {
        queueMicrotask(() => setToken(t));
      }
    }
  }, []);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setLocalError(null);
    setError(null);

    if (!token) {
      setLocalError("رمز إعادة التعيين غير موجود في الرابط.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setLocalError("كلمة المرور الجديدة وتأكيدها غير متطابقين.");
      return;
    }

    if (newPassword.length < 8) {
      setLocalError("يجب أن تكون كلمة المرور الجديدة 8 أحرف على الأقل.");
      return;
    }

    setBusy(true);
    try {
      await authApi.resetPassword({ token, newPassword });
      setSuccess(true);
    } catch (err) {
      setError(toUserFacingError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a] px-4" dir="rtl">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <p className="text-xs font-black tracking-[0.18em] text-teal-700">SANAD BUSINESS OPERATING SYSTEM</p>
          <h1 className="mt-2 text-2xl font-black text-slate-900">إعادة تعيين كلمة المرور</h1>
        </div>

        {success ? (
          <div className="space-y-4">
            <div className="rounded-xl bg-emerald-50 p-4 ring-1 ring-emerald-600/20" role="alert">
              <p className="text-sm font-black text-emerald-700">تم إعادة تعيين كلمة المرور بنجاح</p>
              <p className="mt-1 text-sm text-emerald-600">
                يمكنك الآن تسجيل الدخول باستخدام كلمة المرور الجديدة.
              </p>
            </div>
            <Link
              href="/"
              className="block w-full rounded-xl bg-teal-800 px-4 py-3 text-center font-black text-white hover:bg-teal-900"
            >
              تسجيل الدخول
            </Link>
          </div>
        ) : !token ? (
          <div className="space-y-4">
            <div className="rounded-xl bg-rose-50 p-4 ring-1 ring-rose-600/20" role="alert">
              <p className="text-sm font-black text-rose-700">رمز إعادة التعيين غير صالح</p>
              <p className="mt-1 text-sm text-rose-600">
                الرابط المستخدم غير صالح أو منتهي الصلاحية. يرجى طلب رابط جديد.
              </p>
            </div>
            <Link
              href="/"
              className="block w-full rounded-xl border border-slate-200 px-4 py-3 text-center text-sm font-bold text-slate-600 hover:bg-slate-50"
            >
              العودة لتسجيل الدخول
            </Link>
          </div>
        ) : (
          <>
            {(localError || error) && (
              <div className="mb-4 rounded-xl bg-rose-50 p-3 ring-1 ring-rose-600/20" role="alert">
                <p className="text-sm font-black text-rose-700">
                  {localError || error?.title || "حدث خطأ"}
                </p>
                {(error?.message && !localError) && (
                  <p className="mt-1 text-sm text-rose-600">{error.message}</p>
                )}
              </div>
            )}
            <form onSubmit={handleSubmit} className="space-y-4">
              <label className="grid gap-1 text-sm font-bold text-slate-700">
                كلمة المرور الجديدة
                <input
                  dir="ltr"
                  type="password"
                  required
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  minLength={8}
                  className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
                />
              </label>
              <label className="grid gap-1 text-sm font-bold text-slate-700">
                تأكيد كلمة المرور الجديدة
                <input
                  dir="ltr"
                  type="password"
                  required
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  minLength={8}
                  className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
                />
              </label>
              <button
                type="submit"
                disabled={busy}
                className="w-full rounded-xl bg-teal-800 px-4 py-3 font-black text-white disabled:opacity-50"
              >
                {busy ? "جارٍ إعادة التعيين…" : "إعادة تعيين كلمة المرور"}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
