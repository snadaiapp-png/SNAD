"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { authApi } from "@/lib/api/auth";
import { useAuth } from "@/lib/auth/auth-provider";

type IconProps = { className?: string };

function IconMail({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6h16v12H4z" stroke="currentColor" strokeWidth="1.7"/><path d="m5 7 7 6 7-6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"/></svg>;
}

function IconLock({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="5" y="10" width="14" height="10" rx="2" stroke="currentColor" strokeWidth="1.7"/><path d="M8 10V7a4 4 0 0 1 8 0v3" stroke="currentColor" strokeWidth="1.7"/><path d="M12 14v2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round"/></svg>;
}

function IconEye({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M2.5 12s3.5-5 9.5-5 9.5 5 9.5 5-3.5 5-9.5 5-9.5-5-9.5-5Z" stroke="currentColor" strokeWidth="1.6"/><circle cx="12" cy="12" r="2.5" stroke="currentColor" strokeWidth="1.6"/></svg>;
}

function IconUsers({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="9" cy="8" r="3" stroke="currentColor" strokeWidth="1.6"/><path d="M3.5 18c.5-3.2 2.4-5 5.5-5s5 1.8 5.5 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/><circle cx="17" cy="9" r="2" stroke="currentColor" strokeWidth="1.5"/><path d="M16 14c2.8 0 4.2 1.2 4.6 3.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/></svg>;
}

function IconPerson({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="7" r="3.5" stroke="currentColor" strokeWidth="1.6"/><path d="M5 20c.6-4 3-6 7-6s6.4 2 7 6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>;
}

function IconGear({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.6"/><path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1a8 8 0 0 0-1.7-1L14.5 3h-4l-.4 3a8 8 0 0 0-1.7 1L6 6.1l-2 3.4L6.1 11a7 7 0 0 0 0 2L4 14.5l2 3.4 2.4-1a8 8 0 0 0 1.7 1l.4 3h4l.4-3a8 8 0 0 0 1.7-1l2.4 1 2-3.4-2.1-1.5c.1-.3.1-.7.1-1Z" stroke="currentColor" strokeWidth="1.25" strokeLinejoin="round"/></svg>;
}

function IconTerminal({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="4" y="3" width="16" height="18" rx="2" stroke="currentColor" strokeWidth="1.6"/><path d="M8 7h8M8 11h8M8 15h3M15 15h1M8 18h8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/></svg>;
}

function IconCard({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3" y="6" width="18" height="12" rx="2" stroke="currentColor" strokeWidth="1.6"/><path d="M3 10h18M7 15h4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>;
}

function IconWindow({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.6"/><path d="M3 9h18M7 6.5h.01M10 6.5h.01" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
}

function IconCart({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M3 4h2l2 10h10l2-7H6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/><circle cx="9" cy="18" r="1.5" stroke="currentColor" strokeWidth="1.5"/><circle cx="17" cy="18" r="1.5" stroke="currentColor" strokeWidth="1.5"/></svg>;
}

function IconMegaphone({ className }: IconProps) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m4 13 11-5v8L4 11v2Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"/><path d="M15 10c2-1 3-2.5 4-4v12c-1-1.5-2-3-4-4M6 13l1.5 5h3L9 14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>;
}

const modules = [
  { label: "CRM", x: "50%", y: "7%", delay: "-0.3s", icon: IconUsers },
  { label: "HR", x: "83%", y: "21%", delay: "-0.8s", icon: IconPerson },
  { label: "POS", x: "95%", y: "51%", delay: "-1.3s", icon: IconTerminal },
  { label: "PAY", x: "82%", y: "80%", delay: "-1.8s", icon: IconCard },
  { label: "SITES", x: "50%", y: "94%", delay: "-2.3s", icon: IconWindow },
  { label: "STORES", x: "18%", y: "80%", delay: "-2.8s", icon: IconCart },
  { label: "ERP", x: "5%", y: "51%", delay: "-3.3s", icon: IconGear },
  { label: "MARKETING", x: "17%", y: "21%", delay: "-3.8s", icon: IconMegaphone },
];

function DataFlow() {
  return (
    <svg className="snad-auth-data-flow" viewBox="0 0 1600 900" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient id="snad-auth-flow-gradient" x1="0" x2="1">
          <stop offset="0%" stopColor="#d4af37" />
          <stop offset="45%" stopColor="#00a7a0" />
          <stop offset="100%" stopColor="#f4d36f" />
        </linearGradient>
        <filter id="snad-auth-soft-glow"><feGaussianBlur stdDeviation="3" result="blur"/><feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
      </defs>
      <path className="snad-auth-flow-track" d="M1585 720 C1370 720 1260 560 1110 510 C1010 478 950 470 850 455" />
      <path className="snad-auth-flow-pulse" d="M1585 720 C1370 720 1260 560 1110 510 C1010 478 950 470 850 455" />
      <path className="snad-auth-flow-pulse" d="M1585 704 C1360 704 1250 550 1090 500 C1000 472 940 465 850 455" />
      <path className="snad-auth-flow-track" d="M760 455 C620 455 520 410 390 430 C240 452 150 520 0 520" />
      <path className="snad-auth-flow-pulse" d="M760 455 C620 455 520 410 390 430 C240 452 150 520 0 520" />
      <circle className="snad-auth-particle" r="4" />
      <circle className="snad-auth-particle" r="3.5" />
      <circle className="snad-auth-particle" r="3" />
    </svg>
  );
}

function ModuleOrbit() {
  return (
    <aside className="snad-auth-orbit" aria-label="أنظمة سند المتكاملة">
      <svg className="snad-auth-orbit-lines" viewBox="0 0 100 100" aria-hidden="true">
        {modules.map((module) => <line key={module.label} x1="50" y1="50" x2={module.x.replace("%", "")} y2={module.y.replace("%", "")} />)}
      </svg>
      <div className="snad-auth-ai-core">AI</div>
      {modules.map(({ label, x, y, delay, icon: Icon }) => (
        <div key={label} className="snad-auth-node" style={{ "--node-x": x, "--node-y": y, "--node-delay": delay } as React.CSSProperties}>
          <Icon />
          <span>{label}</span>
        </div>
      ))}
    </aside>
  );
}

function BrandHeader({ title, description }: { title: string; description: string }) {
  return (
    <header className="snad-auth-brand">
      <div className="snad-auth-logo" aria-label="سند">سند</div>
      <p className="snad-auth-subtitle">منظومة الأعمال الرقمية</p>
      <h1 className="snad-auth-title">{title}</h1>
      <p className="snad-auth-description">{description}</p>
    </header>
  );
}

function AuthShell({ children }: { children: ReactNode }) {
  return (
    <main className="snad-auth-scene" dir="rtl">
      <DataFlow />
      <section className="snad-auth-card">{children}</section>
      <ModuleOrbit />
    </main>
  );
}

export function AuthBoundary({ children }: { children: ReactNode }) {
  const auth = useAuth();
  if (auth.state === "AUTHENTICATED") return <>{children}</>;
  if (auth.state === "INITIALIZING" || auth.state === "REFRESHING" || auth.state === "LOGGING_OUT") {
    return <AuthShell><BrandHeader title="جارٍ تجهيز مساحة العمل" description="يتم التحقق من الجلسة الآمنة وتحميل بيانات الحساب." /><div className="mx-auto size-9 animate-spin rounded-full border-4 border-teal-950 border-t-teal-300" aria-label="جارٍ التحميل" /></AuthShell>;
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
  const [remember, setRemember] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
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
      <BrandHeader title={mode === "login" ? "تسجيل الدخول" : "استعادة كلمة المرور"} description={mode === "login" ? "الدخول الآمن إلى منصة سند وإدارة عمليات الأعمال المتكاملة." : "سنرسل رابطًا آمنًا وأحادي الاستخدام إلى بريدك."} />
      {error && mode === "login" && <div className="snad-auth-alert" role="alert"><strong>{error.title}</strong><p className="mt-1">{error.message}</p></div>}
      {notice && <div className="snad-auth-alert" data-kind="success" role="status">{notice}</div>}
      <form onSubmit={mode === "login" ? submitLogin : submitRecovery} className="snad-auth-form">
        <label className="snad-auth-label">البريد الإلكتروني<div className="snad-auth-input-wrap"><input dir="ltr" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="أدخل بريدك الإلكتروني" className="snad-auth-input text-left" /><span className="snad-auth-input-icon"><IconMail /></span></div></label>
        {mode === "login" && <label className="snad-auth-label">كلمة المرور<div className="snad-auth-input-wrap"><input dir="ltr" type={showPassword ? "text" : "password"} autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} placeholder="أدخل كلمة المرور" className="snad-auth-input text-left" /><span className="snad-auth-input-icon"><IconLock /></span><button type="button" className="snad-auth-input-action" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "إخفاء كلمة المرور" : "إظهار كلمة المرور"}><IconEye /></button></div></label>}
        {mode === "login" && <div className="snad-auth-row"><label className="snad-auth-remember"><input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} /><span>تذكرني</span></label><button type="button" onClick={() => { setMode("forgot"); setNotice(null); clearError(); }} className="snad-auth-link">نسيت كلمة المرور؟</button></div>}
        <button disabled={busy || state === "AUTHENTICATING"} className="snad-auth-submit">{mode === "login" ? (state === "AUTHENTICATING" ? "جارٍ تسجيل الدخول…" : "تسجيل الدخول ←") : (busy ? "جارٍ الإرسال…" : "إرسال رابط الاسترداد")}</button>
      </form>
      {mode === "forgot" && <button type="button" onClick={() => { setMode("login"); setNotice(null); clearError(); }} className="snad-auth-link mt-4 w-full text-center">العودة إلى تسجيل الدخول</button>}
      <p className="snad-auth-modules-line">ERP • CRM • HR • POS • PAY • STORES • SITES • MARKETING • <strong>AI</strong></p>
      <p className="snad-auth-note">لن تُرسل كلمة المرور نفسها عبر البريد؛ يُرسل رابط آمن صالح لمرة واحدة.</p>
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
  return <AuthShell><BrandHeader title="اختيار مساحة العمل" description="البريد مرتبط بأكثر من مستأجر. اختر المساحة المطلوبة." /><form onSubmit={submit} className="snad-auth-form">{ambiguousTenantIds.map((tenantId) => <label key={tenantId} className={`flex items-center gap-3 rounded-xl border p-3 ${selected === tenantId ? "border-teal-400 bg-teal-950/50" : "border-slate-700 bg-slate-950/30"}`}><input type="radio" name="tenant" value={tenantId} checked={selected === tenantId} onChange={() => setSelected(tenantId)} /><span dir="ltr" className="font-mono text-xs text-slate-200">{tenantId}</span></label>)}<button disabled={!selected || busy} className="snad-auth-submit">{busy ? "جارٍ الدخول…" : "المتابعة"}</button><button type="button" onClick={dismissAmbiguousTenant} className="snad-auth-link w-full text-center">رجوع</button></form></AuthShell>;
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
  return <AuthShell><BrandHeader title="تغيير كلمة المرور مطلوب" description="لحماية الحساب، أنشئ كلمة مرور جديدة قبل المتابعة." />{(localError || error) && <div className="snad-auth-alert" role="alert">{localError || error?.message}</div>}<form onSubmit={submit} className="snad-auth-form"><input dir="ltr" type="password" autoComplete="current-password" placeholder="كلمة المرور الحالية" required value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} className="snad-auth-input text-left" /><input dir="ltr" type="password" autoComplete="new-password" placeholder="كلمة المرور الجديدة" required minLength={8} value={newPassword} onChange={(event) => setNewPassword(event.target.value)} className="snad-auth-input text-left" /><input dir="ltr" type="password" autoComplete="new-password" placeholder="تأكيد كلمة المرور" required minLength={8} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} className="snad-auth-input text-left" /><button disabled={busy} className="snad-auth-submit">{busy ? "جارٍ الحفظ…" : "حفظ كلمة المرور"}</button><button type="button" onClick={logout} className="snad-auth-link w-full text-center">تسجيل الخروج</button></form></AuthShell>;
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
    setBusy(true); setError(null);
    try { await authApi.resetPassword({ token, newPassword: password }); setSuccess(true); }
    catch { setError("تعذر استخدام الرابط. قد يكون منتهي الصلاحية أو مستخدمًا مسبقًا."); }
    finally { setBusy(false); }
  }
  return <AuthShell><BrandHeader title={success ? "تم تحديث كلمة المرور" : "إعداد كلمة مرور جديدة"} description={success ? "يمكنك الآن تسجيل الدخول باستخدام كلمة المرور الجديدة." : "استخدم الرابط أحادي الاستخدام لإكمال استرداد الحساب."} />{success ? <Link href="/" className="snad-auth-submit grid place-items-center">العودة إلى تسجيل الدخول</Link> : <form onSubmit={submit} className="snad-auth-form">{error && <div className="snad-auth-alert" role="alert">{error}</div>}<input dir="ltr" type="password" autoComplete="new-password" placeholder="كلمة المرور الجديدة" required minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} className="snad-auth-input text-left" /><input dir="ltr" type="password" autoComplete="new-password" placeholder="تأكيد كلمة المرور" required minLength={8} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} className="snad-auth-input text-left" /><button disabled={busy || !token} className="snad-auth-submit">{busy ? "جارٍ الحفظ…" : "تحديث كلمة المرور"}</button></form>}</AuthShell>;
}
