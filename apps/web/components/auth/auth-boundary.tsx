"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { authApi } from "@/lib/api/auth";
import { useAuth } from "@/lib/auth/auth-provider";

function BrandHeader({ title, description }: { title: string; description: string }) {
  return (
    <header className="mb-7 text-center">
      <div className="mx-auto mb-4 grid size-16 place-items-center rounded-2xl bg-brand-primary text-2xl font-black text-brand-gold shadow-lg">س</div>
      <p className="text-xs font-black tracking-[0.22em] text-teal-700" lang="en">SNAD</p>
      <h1 className="mt-2 text-2xl font-black text-slate-900">{title}</h1>
      <p className="mt-2 text-sm leading-7 text-slate-500">{description}</p>
    </header>
  );
}

function AuthShell({ children }: { children: ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-brand-primary px-4 py-10" dir="rtl">
      <section className="w-full max-w-md rounded-3xl bg-white p-7 shadow-2xl sm:p-9">{children}</section>
    </main>
  );
}

export function AuthBoundary({ children }: { children: ReactNode }) {
  const auth = useAuth();
  if (auth.state === "AUTHENTICATED") return <>{children}</>;
  if (auth.state === "INITIALIZING" || auth.state === "REFRESHING" || auth.state === "LOGGING_OUT") {
    return <AuthShell><BrandHeader title="جارٍ تجهيز مساحة العمل" description="يتم التحقق من الجلسة الآمنة وتحميل بيانات الحساب." /><div className="mx-auto size-9 animate-spin rounded-full border-4 border-teal-100 border-t-teal-700" aria-label="جارٍ التحميل" /></AuthShell>;
  }
  if (auth.state === "AMBIGUOUS_TENANT") return <TenantPicker />;
  if (auth.state === "CREDENTIAL_ROTATION_REQUIRED") return <ForcedPasswordChange />;
  return <LoginPanel />;
}

function LoginPanel() {
  const { state, error, login, clearError } = useAuth();
  const [mode, setMode] = useState<"login" | "forgot">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function submitLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    clearError();
    await login({ email: email.trim(), password });
  }

  async function submitRecovery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      const response = await authApi.forgotPassword({ email: email.trim() });
      setNotice(response.message);
    } catch {
      setNotice("إذا كان البريد الإلكتروني مسجلاً لدينا، فستصلك رسالة الاسترداد.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell>
      <BrandHeader title={mode === "login" ? "تسجيل الدخول" : "استعادة كلمة المرور"} description={mode === "login" ? "ادخل إلى مساحة عمل سند المؤسسية." : "سنرسل رابطًا آمنًا وأحادي الاستخدام إلى بريدك."} />
      {error && mode === "login" && <div className="mb-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700" role="alert"><strong>{error.title}</strong><p className="mt-1">{error.message}</p></div>}
      {notice && <div className="mb-4 rounded-xl bg-emerald-50 p-3 text-sm font-bold text-emerald-700" role="status">{notice}</div>}
      <form onSubmit={mode === "login" ? submitLogin : submitRecovery} className="space-y-4">
        <label className="grid gap-1 text-sm font-bold text-slate-700">البريد الإلكتروني<input dir="ltr" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-left outline-none focus:border-teal-600" /></label>
        {mode === "login" && <label className="grid gap-1 text-sm font-bold text-slate-700">كلمة المرور<input dir="ltr" type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-left outline-none focus:border-teal-600" /></label>}
        <button disabled={busy || state === "AUTHENTICATING"} className="w-full rounded-xl bg-brand-primary px-4 py-3 font-black text-white hover:bg-brand-primary-hover disabled:opacity-50">{mode === "login" ? (state === "AUTHENTICATING" ? "جارٍ تسجيل الدخول…" : "تسجيل الدخول") : (busy ? "جارٍ الإرسال…" : "إرسال رابط الاسترداد")}</button>
      </form>
      <button type="button" onClick={() => { setMode(mode === "login" ? "forgot" : "login"); setNotice(null); clearError(); }} className="mt-4 w-full text-sm font-bold text-teal-700 hover:underline">{mode === "login" ? "نسيت كلمة المرور؟" : "العودة إلى تسجيل الدخول"}</button>
      <p className="mt-6 text-center text-xs text-slate-500">لن تُرسل كلمة المرور نفسها عبر البريد؛ يُرسل رابط آمن صالح لمرة واحدة.</p>
    </AuthShell>
  );
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
  return <AuthShell><BrandHeader title="اختيار مساحة العمل" description="البريد مرتبط بأكثر من مستأجر. اختر المساحة المطلوبة." /><form onSubmit={submit} className="space-y-3">{ambiguousTenantIds.map((tenantId) => <label key={tenantId} className={`flex items-center gap-3 rounded-xl border p-3 ${selected === tenantId ? "border-teal-600 bg-teal-50" : "border-slate-200"}`}><input type="radio" name="tenant" value={tenantId} checked={selected === tenantId} onChange={() => setSelected(tenantId)} /><span dir="ltr" className="font-mono text-xs">{tenantId}</span></label>)}<button disabled={!selected || busy} className="w-full rounded-xl bg-brand-primary px-4 py-3 font-black text-white disabled:opacity-50">{busy ? "جارٍ الدخول…" : "المتابعة"}</button><button type="button" onClick={dismissAmbiguousTenant} className="w-full rounded-xl border border-slate-200 px-4 py-3 font-bold text-slate-600">رجوع</button></form></AuthShell>;
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
  return <AuthShell><BrandHeader title="تغيير كلمة المرور مطلوب" description="لحماية الحساب، أنشئ كلمة مرور جديدة قبل المتابعة." />{(localError || error) && <div className="mb-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700" role="alert">{localError || error?.message}</div>}<form onSubmit={submit} className="space-y-4"><input dir="ltr" type="password" autoComplete="current-password" placeholder="كلمة المرور الحالية" required value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-left" /><input dir="ltr" type="password" autoComplete="new-password" placeholder="كلمة المرور الجديدة" required minLength={8} value={newPassword} onChange={(event) => setNewPassword(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-left" /><input dir="ltr" type="password" autoComplete="new-password" placeholder="تأكيد كلمة المرور" required minLength={8} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-left" /><button disabled={busy} className="w-full rounded-xl bg-brand-primary px-4 py-3 font-black text-white disabled:opacity-50">{busy ? "جارٍ الحفظ…" : "حفظ كلمة المرور"}</button><button type="button" onClick={logout} className="w-full text-sm font-bold text-slate-500">تسجيل الخروج</button></form></AuthShell>;
}

export function ResetPasswordPage() {
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { setToken(new URLSearchParams(window.location.search).get("token") ?? ""); }, []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return setError("رابط الاسترداد غير صالح.");
    if (password.length < 8) return setError("يجب أن تكون كلمة المرور 8 أحرف على الأقل.");
    if (password !== confirmation) return setError("كلمتا المرور غير متطابقتين.");
    setBusy(true); setError(null);
    try { await authApi.resetPassword({ token, newPassword: password }); setSuccess(true); }
    catch { setError("تعذر استخدام الرابط. قد يكون منتهي الصلاحية أو مستخدمًا مسبقًا."); }
    finally { setBusy(false); }
  }
  return <AuthShell><BrandHeader title={success ? "تم تحديث كلمة المرور" : "إعداد كلمة مرور جديدة"} description={success ? "يمكنك الآن تسجيل الدخول باستخدام كلمة المرور الجديدة." : "استخدم الرابط أحادي الاستخدام لإكمال استرداد الحساب."} />{success ? <Link href="/" className="block w-full rounded-xl bg-brand-primary px-4 py-3 text-center font-black text-white">العودة إلى تسجيل الدخول</Link> : <form onSubmit={submit} className="space-y-4">{error && <div className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700" role="alert">{error}</div>}<input dir="ltr" type="password" autoComplete="new-password" placeholder="كلمة المرور الجديدة" required minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-left" /><input dir="ltr" type="password" autoComplete="new-password" placeholder="تأكيد كلمة المرور" required minLength={8} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-left" /><button disabled={busy || !token} className="w-full rounded-xl bg-brand-primary px-4 py-3 font-black text-white disabled:opacity-50">{busy ? "جارٍ الحفظ…" : "تحديث كلمة المرور"}</button></form>}</AuthShell>;
}
