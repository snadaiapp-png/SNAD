"use client";

import { useEffect, useState, type FormEvent } from "react";
import panelStyles from "./auth-panel.module.css";
import { AuthShell, BrandHeader, IconBuilding, IconEye, IconGlobe, IconLock, IconMail, IconPerson } from "./auth-shell";
import { authApi } from "@/lib/api/auth";
import { ApiHttpError } from "@/lib/api/errors";
import { registrationApi } from "@/lib/api/registration";
import { useAuth } from "@/lib/auth/auth-provider";

type AuthMode = "login" | "register" | "forgot";

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiHttpError && error.backendMessage ? error.backendMessage : fallback;
}

export function ModernAuthPanel() {
  const { state, error, login, clearError } = useAuth();
  const [mode, setMode] = useState<AuthMode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [displayName, setDisplayName] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [subdomain, setSubdomain] = useState("");
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [registrationComplete, setRegistrationComplete] = useState(false);

  useEffect(() => {
    const rememberedEmail = window.localStorage.getItem("snad.rememberedEmail");
    if (rememberedEmail) {
      queueMicrotask(() => {
        setEmail(rememberedEmail);
        setRemember(true);
      });
    }
  }, []);

  function changeMode(nextMode: AuthMode) {
    setMode(nextMode);
    setNotice(null);
    setLocalError(null);
    setRegistrationComplete(false);
    clearError();
  }

  async function submitLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLocalError(null);
    clearError();
    if (remember) window.localStorage.setItem("snad.rememberedEmail", email.trim());
    else window.localStorage.removeItem("snad.rememberedEmail");
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

  async function submitRegistration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    setLocalError(null);
    try {
      const response = await registrationApi.register({
        displayName: displayName.trim(),
        email: email.trim(),
        organizationName: organizationName.trim(),
        subdomain: subdomain.trim().toLowerCase(),
        acceptTerms,
      });
      setNotice(response.message);
      setRegistrationComplete(true);
    } catch (registrationError) {
      setLocalError(errorMessage(registrationError, "تعذر إنشاء الحساب حاليًا. تحقق من البيانات وحاول مرة أخرى."));
    } finally {
      setBusy(false);
    }
  }

  const title = mode === "login" ? "تسجيل الدخول" : mode === "register" ? "إنشاء حساب جديد" : "استعادة كلمة المرور";
  const description = mode === "register"
    ? "أنشئ مساحة عملك ثم فعّل كلمة المرور من الرابط الآمن المرسل إلى بريدك."
    : mode === "forgot"
      ? "سنرسل رابطًا آمنًا وأحادي الاستخدام إلى بريدك."
      : "الدخول الآمن إلى منصة سند وإدارة عمليات الأعمال المتكاملة.";

  return (
    <AuthShell wide={mode === "register"}>
      <BrandHeader title={title} description={description}/>

      {mode !== "forgot" && (
        <div className={panelStyles.tabs} role="tablist" aria-label="الدخول أو إنشاء حساب">
          <button type="button" role="tab" aria-selected={mode === "login"} onClick={() => changeMode("login")} className={`${panelStyles.tab} ${mode === "login" ? panelStyles.tabActive : ""}`}>تسجيل الدخول</button>
          <button type="button" role="tab" aria-selected={mode === "register"} onClick={() => changeMode("register")} className={`${panelStyles.tab} ${mode === "register" ? panelStyles.tabActive : ""}`}>إنشاء حساب</button>
        </div>
      )}

      <p className={panelStyles.intro}>
        {mode === "login" && "مرحبًا بعودتك. أدخل بياناتك للوصول إلى مساحة العمل."}
        {mode === "register" && "أدخل بيانات مسؤول الحساب ومساحة العمل، ثم فعّل كلمة المرور من بريدك."}
        {mode === "forgot" && "أدخل بريدك المسجل وسنرسل تعليمات الاستعادة عند مطابقة الحساب."}
      </p>

      {error && mode === "login" && <div className="snad-auth-alert" role="alert"><strong>{error.title}</strong><p className="mt-1">{error.message}</p></div>}
      {localError && <div className="snad-auth-alert" role="alert">{localError}</div>}
      {notice && <div className={panelStyles.successBox} role="status">{notice}</div>}

      {mode === "login" && (
        <form onSubmit={submitLogin} className={`snad-auth-form ${panelStyles.form}`}>
          <label className="snad-auth-label">البريد الإلكتروني<div className="snad-auth-input-wrap"><input dir="ltr" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@company.com" className="snad-auth-input text-left"/><span className="snad-auth-input-icon"><IconMail/></span></div></label>
          <label className="snad-auth-label">كلمة المرور<div className="snad-auth-input-wrap"><input dir="ltr" type={showPassword ? "text" : "password"} autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} placeholder="أدخل كلمة المرور" className="snad-auth-input text-left"/><span className="snad-auth-input-icon"><IconLock/></span><button type="button" className="snad-auth-input-action" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "إخفاء كلمة المرور" : "إظهار كلمة المرور"}><IconEye/></button></div></label>
          <div className="snad-auth-row"><label className="snad-auth-remember"><input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)}/><span>تذكر البريد</span></label><button type="button" onClick={() => changeMode("forgot")} className="snad-auth-link">نسيت كلمة المرور؟</button></div>
          <button disabled={state === "AUTHENTICATING"} className="snad-auth-submit">{state === "AUTHENTICATING" ? "جارٍ تسجيل الدخول…" : "الدخول إلى سند"}</button>
          <div className={panelStyles.secureRow}><span className={panelStyles.secureBadge}><span className={panelStyles.secureDot}/>اتصال آمن ومحمي</span><span>Multi-Tenant Security</span></div>
        </form>
      )}

      {mode === "register" && !registrationComplete && (
        <form onSubmit={submitRegistration} className={`snad-auth-form ${panelStyles.form}`}>
          <label className="snad-auth-label">الاسم الكامل<div className="snad-auth-input-wrap"><input type="text" autoComplete="name" required maxLength={200} value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="اسم مسؤول الحساب" className="snad-auth-input"/><span className="snad-auth-input-icon"><IconPerson/></span></div></label>
          <label className="snad-auth-label">البريد الإلكتروني للعمل<div className="snad-auth-input-wrap"><input dir="ltr" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@company.com" className="snad-auth-input text-left"/><span className="snad-auth-input-icon"><IconMail/></span></div></label>
          <label className="snad-auth-label">اسم المنشأة أو مساحة العمل<div className="snad-auth-input-wrap"><input type="text" autoComplete="organization" required maxLength={200} value={organizationName} onChange={(event) => setOrganizationName(event.target.value)} placeholder="مثال: شركة سند للتقنية" className="snad-auth-input"/><span className="snad-auth-input-icon"><IconBuilding/></span></div></label>
          <label className="snad-auth-label">رمز مساحة العمل<div className="snad-auth-input-wrap"><input dir="ltr" type="text" autoComplete="off" required minLength={3} maxLength={63} pattern="[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])" value={subdomain} onChange={(event) => setSubdomain(event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ""))} placeholder="company-name" className="snad-auth-input text-left"/><span className="snad-auth-input-icon"><IconGlobe/></span></div><span className={panelStyles.fieldHint} dir="ltr">{subdomain ? `${subdomain}.snad` : "company-name.snad"}</span></label>
          <label className={panelStyles.terms}><input type="checkbox" checked={acceptTerms} onChange={(event) => setAcceptTerms(event.target.checked)} required/><span>أوافق على شروط الاستخدام وسياسة الخصوصية، وأقر بصحة بيانات مساحة العمل.</span></label>
          <button disabled={busy || !acceptTerms} className="snad-auth-submit">{busy ? "جارٍ إنشاء مساحة العمل…" : "إنشاء الحساب وإرسال رابط التفعيل"}</button>
          <p className={panelStyles.footer}>يتم تعيين كلمة المرور من رابط أحادي الاستخدام، ولا تُرسل كلمة المرور نفسها عبر البريد.</p>
        </form>
      )}

      {mode === "register" && registrationComplete && <button type="button" onClick={() => changeMode("login")} className="snad-auth-submit">العودة إلى تسجيل الدخول</button>}

      {mode === "forgot" && (
        <form onSubmit={submitRecovery} className={`snad-auth-form ${panelStyles.form}`}>
          <label className="snad-auth-label">البريد الإلكتروني<div className="snad-auth-input-wrap"><input dir="ltr" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@company.com" className="snad-auth-input text-left"/><span className="snad-auth-input-icon"><IconMail/></span></div></label>
          <button disabled={busy} className="snad-auth-submit">{busy ? "جارٍ الإرسال…" : "إرسال رابط الاسترداد"}</button>
          <button type="button" onClick={() => changeMode("login")} className="snad-auth-link w-full text-center">العودة إلى تسجيل الدخول</button>
        </form>
      )}
    </AuthShell>
  );
}
