"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { AuthShell, BrandHeader } from "./auth-shell";
import { ModernAuthPanel } from "./modern-auth-panel";
import { authApi } from "@/lib/api/auth";
import { useAuth } from "@/lib/auth/auth-provider";

export function AuthBoundary({ children }: { children: ReactNode }) {
  const auth = useAuth();
  if (auth.state === "AUTHENTICATED") return <>{children}</>;
  if (auth.state === "INITIALIZING" || auth.state === "REFRESHING" || auth.state === "LOGGING_OUT") {
    return <AuthShell><BrandHeader title="جارٍ تجهيز مساحة العمل" description="يتم التحقق من الجلسة الآمنة وتحميل بيانات الحساب."/><div className="mx-auto size-9 animate-spin rounded-full border-4 border-teal-950 border-t-teal-300" aria-label="جارٍ التحميل"/></AuthShell>;
  }
  if (auth.state === "AMBIGUOUS_TENANT") return <TenantPicker/>;
  if (auth.state === "CREDENTIAL_ROTATION_REQUIRED") return <ForcedPasswordChange/>;
  return <ModernAuthPanel/>;
}

function TenantPicker() {
  const { ambiguousTenantIds, loginWithTenant, dismissAmbiguousTenant } = useAuth();
  const [selected, setSelected] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    setBusy(true);
    try { await loginWithTenant(selected); } finally { setBusy(false); }
  }

  return (
    <AuthShell>
      <BrandHeader title="اختيار مساحة العمل" description="البريد مرتبط بأكثر من مساحة عمل. اختر المساحة المطلوبة."/>
      <form onSubmit={submit} className="snad-auth-form">
        {ambiguousTenantIds.map((tenantId) => (
          <label key={tenantId} className={`flex items-center gap-3 rounded-xl border p-3 ${selected === tenantId ? "border-teal-400 bg-teal-950/50" : "border-slate-700 bg-slate-950/30"}`}>
            <input type="radio" name="tenant" value={tenantId} checked={selected === tenantId} onChange={() => setSelected(tenantId)}/>
            <span dir="ltr" className="font-mono text-xs text-slate-200">{tenantId}</span>
          </label>
        ))}
        <button disabled={!selected || busy} className="snad-auth-submit">{busy ? "جارٍ الدخول…" : "المتابعة"}</button>
        <button type="button" onClick={dismissAmbiguousTenant} className="snad-auth-link w-full text-center">رجوع</button>
      </form>
    </AuthShell>
  );
}

function ForcedPasswordChange() {
  const { changeCredential, logout, error, clearError } = useAuth();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    clearError();
    if (newPassword.length < 8) return setLocalError("يجب أن تكون كلمة المرور 8 أحرف على الأقل.");
    if (newPassword !== confirmPassword) return setLocalError("كلمتا المرور غير متطابقتين.");
    if (newPassword === currentPassword) return setLocalError("يجب أن تختلف كلمة المرور الجديدة عن الحالية.");
    setLocalError(null);
    setBusy(true);
    try { await changeCredential(currentPassword, newPassword); } finally { setBusy(false); }
  }

  return (
    <AuthShell>
      <BrandHeader title="تغيير كلمة المرور مطلوب" description="لحماية الحساب، أنشئ كلمة مرور جديدة قبل المتابعة."/>
      {(localError || error) && <div className="snad-auth-alert" role="alert">{localError || error?.message}</div>}
      <form onSubmit={submit} className="snad-auth-form">
        <input dir="ltr" type="password" autoComplete="current-password" placeholder="كلمة المرور الحالية" required value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} className="snad-auth-input text-left"/>
        <input dir="ltr" type="password" autoComplete="new-password" placeholder="كلمة المرور الجديدة" required minLength={8} value={newPassword} onChange={(event) => setNewPassword(event.target.value)} className="snad-auth-input text-left"/>
        <input dir="ltr" type="password" autoComplete="new-password" placeholder="تأكيد كلمة المرور" required minLength={8} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} className="snad-auth-input text-left"/>
        <button disabled={busy} className="snad-auth-submit">{busy ? "جارٍ الحفظ…" : "حفظ كلمة المرور"}</button>
        <button type="button" onClick={logout} className="snad-auth-link w-full text-center">تسجيل الخروج</button>
      </form>
    </AuthShell>
  );
}

export function ResetPasswordPage() {
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
    <AuthShell>
      <BrandHeader title={success ? "تم تحديث كلمة المرور" : "إعداد كلمة مرور جديدة"} description={success ? "يمكنك الآن تسجيل الدخول باستخدام كلمة المرور الجديدة." : "استخدم الرابط أحادي الاستخدام لإكمال إعداد الحساب."}/>
      {success ? (
        <Link href="/" className="snad-auth-submit grid place-items-center">العودة إلى تسجيل الدخول</Link>
      ) : (
        <form onSubmit={submit} className="snad-auth-form">
          {error && <div className="snad-auth-alert" role="alert">{error}</div>}
          <input dir="ltr" type="password" autoComplete="new-password" placeholder="كلمة المرور الجديدة" required minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} className="snad-auth-input text-left"/>
          <input dir="ltr" type="password" autoComplete="new-password" placeholder="تأكيد كلمة المرور" required minLength={8} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} className="snad-auth-input text-left"/>
          <button disabled={busy || !token} className="snad-auth-submit">{busy ? "جارٍ الحفظ…" : "تحديث كلمة المرور"}</button>
        </form>
      )}
    </AuthShell>
  );
}
