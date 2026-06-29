"use client";

import { useEffect, useState, type FormEvent } from "react";
import panelStyles from "./auth-panel.module.css";
import { AuthShell, BrandHeader, IconBuilding, IconEye, IconGlobe, IconLock, IconMail, IconPerson } from "./auth-shell";
import { authApi } from "@/lib/api/auth";
import { ApiHttpError } from "@/lib/api/errors";
import { registrationApi } from "@/lib/api/registration";
import { useAuth } from "@/lib/auth/auth-provider";

type AuthMode = "login" | "register" | "forgot";

const REGIONS = [
  { code: "SA", label: "السعودية", dialCode: "+966", placeholder: "5xxxxxxxx" },
  { code: "AE", label: "الإمارات", dialCode: "+971", placeholder: "5xxxxxxxx" },
  { code: "KW", label: "الكويت", dialCode: "+965", placeholder: "xxxxxxxx" },
  { code: "BH", label: "البحرين", dialCode: "+973", placeholder: "xxxxxxxx" },
  { code: "QA", label: "قطر", dialCode: "+974", placeholder: "xxxxxxxx" },
  { code: "OM", label: "عُمان", dialCode: "+968", placeholder: "xxxxxxxx" },
] as const;

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiHttpError && error.backendMessage ? error.backendMessage : fallback;
}

function IconPhone() {
  return <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M7.2 3.5 10 7.8 8.3 9.5c1.2 2.5 3.2 4.5 5.7 5.7l1.7-1.7 4.3 2.8-.7 3.2c-.2.8-.9 1.4-1.8 1.4C9.6 20.9 3.1 14.4 3.1 6.5c0-.9.6-1.6 1.4-1.8l2.7-.6Z" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/></svg>;
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
  const [regionCode, setRegionCode] = useState("SA");
  const [mobileNumber, setMobileNumber] = useState("");
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [registrationComplete, setRegistrationComplete] = useState(false);

  const selectedRegion = REGIONS.find((region) => region.code === regionCode) ?? REGIONS[0];

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
        regionCode: selectedRegion.code,
        countryCode: selectedRegion.dialCode,
        mobileNumber: mobileNumber.trim(),
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

  const title = mode === "forgot" ? "استعادة كلمة المرور" : "";
  const description = mode === "register"
    ? "أدخل بياناتك الأساسية، ثم فعّل كلمة المرور من الرابط الآمن المرسل إلى بريدك."
    : mode === "forgot"
      ? "سنرسل رابطًا آمنًا وأحادي الاستخدام إلى بريدك."
      : "الدخول الآمن إلى منصة سند وإدارة عمليات الأعمال المتكاملة.";

  return (
    <AuthShell wide={mode === "register"}>
      <BrandHeader title={title} description={description}/>

      {mode !== "forgot" && (
        <div className={panelStyles.tabs} role="tablist" aria-label="الدخول أو إنشاء حساب">
          <button type="button" role="tab" aria-selected={mode === "login"} onClick={() => changeMode("login")} className={`${panelStyles.tab} ${mode === "login" ? panelStyles.tabActive : ""}`}>دخول</button>
          <button type="button" role="tab" aria-selected={mode === "register"} onClick={() => changeMode("register")} className={`${panelStyles.tab} ${mode === "register" ? panelStyles.tabActive : ""}`}>حساب جديد</button>
        </div>
      )}

      <p className={panelStyles.intro}>
        {mode === "login" && "مرحبًا بعودتك. أدخل بياناتك للوصول إلى مساحة العمل."}
        {mode === "register" && "أدخل بيانات مسؤول الحساب والمنشأة، ثم فعّل كلمة المرور من بريدك."}
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
          <button disabled={state === "AUTHENTICATING"} className="snad-auth-submit">{state === "AUTHENTICATING" ? "جارٍ الدخول…" : "الدخول إلى سند"}</button>
          <div className={panelStyles.secureRow}><span className={panelStyles.secureBadge}><span className={panelStyles.secureDot}/>اتصال آمن ومحمي</span><span>Multi-Tenant Security</span></div>
        </form>
      )}

      {mode === "register" && !registrationComplete && (
        <form onSubmit={submitRegistration} className={`snad-auth-form ${panelStyles.form}`}>
          <label className="snad-auth-label">الاسم الكامل<div className="snad-auth-input-wrap"><input type="text" autoComplete="name" required maxLength={200} value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="اسم مسؤول الحساب" className="snad-auth-input"/><span className="snad-auth-input-icon"><IconPerson/></span></div></label>
          <label className="snad-auth-label">البريد الإلكتروني<div className="snad-auth-input-wrap"><input dir="ltr" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@company.com" className="snad-auth-input text-left"/><span className="snad-auth-input-icon"><IconMail/></span></div></label>
          <label className="snad-auth-label">اسم المنشأة التجاري<div className="snad-auth-input-wrap"><input type="text" autoComplete="organization" required maxLength={200} value={organizationName} onChange={(event) => setOrganizationName(event.target.value)} placeholder="مثال: شركة سند للتقنية" className="snad-auth-input"/><span className="snad-auth-input-icon"><IconBuilding/></span></div></label>
          <div className={panelStyles.mobileGrid}>
            <label className="snad-auth-label">الدولة / المنطقة<div className="snad-auth-input-wrap"><select value={regionCode} onChange={(event) => setRegionCode(event.target.value)} className={`snad-auth-input ${panelStyles.regionSelect}`} aria-label="الدولة أو المنطقة">{REGIONS.map((region) => <option key={region.code} value={region.code}>{region.label} ({region.dialCode})</option>)}</select><span className="snad-auth-input-icon"><IconGlobe/></span></div></label>
            <label className="snad-auth-label">رقم الجوال<div className="snad-auth-input-wrap"><input dir="ltr" type="tel" inputMode="numeric" autoComplete="tel-national" required minLength={7} maxLength={15} pattern="[0-9]{7,15}" value={mobileNumber} onChange={(event) => setMobileNumber(event.target.value.replace(/\D/g, ""))} placeholder={selectedRegion.placeholder} className={`snad-auth-input text-left ${panelStyles.mobileInput}`}/><span className={panelStyles.dialCode} dir="ltr">{selectedRegion.dialCode}</span><span className="snad-auth-input-icon"><IconPhone/></span></div></label>
          </div>
          <label className={panelStyles.terms}><input type="checkbox" checked={acceptTerms} onChange={(event) => setAcceptTerms(event.target.checked)} required/><span>أوافق على شروط الاستخدام وسياسة الخصوصية، وأقر بصحة بيانات المنشأة.</span></label>
          <button disabled={busy || !acceptTerms} className="snad-auth-submit">{busy ? "جارٍ إنشاء الحساب…" : "إنشاء الحساب وإرسال رابط التفعيل"}</button>
          <p className={panelStyles.footer}>يتم تعيين كلمة المرور من رابط أحادي الاستخدام، ولا تُرسل كلمة المرور نفسها عبر البريد.</p>
        </form>
      )}

      {mode === "register" && registrationComplete && <button type="button" onClick={() => changeMode("login")} className="snad-auth-submit">العودة للدخول</button>}

      {mode === "forgot" && (
        <form onSubmit={submitRecovery} className={`snad-auth-form ${panelStyles.form}`}>
          <label className="snad-auth-label">البريد الإلكتروني<div className="snad-auth-input-wrap"><input dir="ltr" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@company.com" className="snad-auth-input text-left"/><span className="snad-auth-input-icon"><IconMail/></span></div></label>
          <button disabled={busy} className="snad-auth-submit">{busy ? "جارٍ الإرسال…" : "إرسال رابط الاسترداد"}</button>
          <button type="button" onClick={() => changeMode("login")} className="snad-auth-link w-full text-center">العودة للدخول</button>
        </form>
      )}
    </AuthShell>
  );
}
