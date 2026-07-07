"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";

export type Locale = "ar" | "en";
export type MessageKey =
  | "logoAlt" | "language" | "appearance" | "arabic" | "english"
  | "light" | "dark" | "system" | "workspaceLink" | "welcome"
  | "loginSubtitle" | "email" | "password" | "emailRequired"
  | "emailInvalid" | "passwordRequired" | "showPassword" | "hidePassword"
  | "forgotPassword" | "forgotPasswordLabel" | "login" | "loggingIn"
  | "needHelp" | "helpText" | "sessionExpired" | "sessionExpiredMessage"
  | "user" | "workspace" | "sessionStatus" | "active" | "loginSuccess"
  | "openControlPlane" | "logout";

const ar: Record<MessageKey, string> = {
  logoAlt: "شعار سند — SNAD Business Operating System",
  language: "اللغة", appearance: "المظهر", arabic: "العربية", english: "English",
  light: "فاتح", dark: "داكن", system: "حسب النظام",
  workspaceLink: "الذهاب إلى مساحة العمل", welcome: "مرحبًا بعودتك",
  loginSubtitle: "سجّل دخولك للوصول إلى منصة تشغيل الأعمال.",
  email: "البريد الإلكتروني", password: "كلمة المرور",
  emailRequired: "البريد الإلكتروني مطلوب.",
  emailInvalid: "صيغة البريد الإلكتروني غير صالحة.",
  passwordRequired: "كلمة المرور مطلوبة.",
  showPassword: "إظهار كلمة المرور", hidePassword: "إخفاء كلمة المرور",
  forgotPassword: "نسيت كلمة المرور؟",
  forgotPasswordLabel: "نسيت كلمة المرور؟ استعادة كلمة المرور",
  login: "تسجيل الدخول", loggingIn: "جارٍ تسجيل الدخول…",
  needHelp: "تحتاج مساعدة في الدخول؟",
  helpText: "تواصل مع مسؤول النظام أو فريق الدعم لاستعادة الوصول إلى حسابك. إذا استلمت رابط استرداد آمنًا، افتح الرابط نفسه لإكمال العملية.",
  sessionExpired: "انتهت الجلسة",
  sessionExpiredMessage: "انتهت صلاحية جلستك. يرجى تسجيل الدخول مرة أخرى.",
  user: "المستخدم", workspace: "مساحة العمل", sessionStatus: "حالة الجلسة",
  active: "نشطة", loginSuccess: "تم تسجيل الدخول بنجاح",
  openControlPlane: "فتح مركز الإدارة العليا", logout: "تسجيل الخروج",
};

const en: Record<MessageKey, string> = {
  logoAlt: "SNAD logo — Business Operating System",
  language: "Language", appearance: "Appearance", arabic: "العربية", english: "English",
  light: "Light", dark: "Dark", system: "System",
  workspaceLink: "Go to workspace", welcome: "Welcome back",
  loginSubtitle: "Sign in to access the business operating platform.",
  email: "Email address", password: "Password",
  emailRequired: "Email address is required.",
  emailInvalid: "Enter a valid email address.",
  passwordRequired: "Password is required.",
  showPassword: "Show password", hidePassword: "Hide password",
  forgotPassword: "Forgot password?",
  forgotPasswordLabel: "Forgot password? Recover your password",
  login: "Sign in", loggingIn: "Signing in…",
  needHelp: "Need help signing in?",
  helpText: "Contact your system administrator or support team to restore access to your account. If you received a secure recovery link, open that same link to complete the process.",
  sessionExpired: "Session expired",
  sessionExpiredMessage: "Your session has expired. Please sign in again.",
  user: "User", workspace: "Workspace", sessionStatus: "Session status",
  active: "Active", loginSuccess: "Signed in successfully",
  openControlPlane: "Open executive control plane", logout: "Sign out",
};

const catalogs = { ar, en };
const LocaleContext = createContext({
  locale: "ar" as Locale,
  direction: "rtl" as "rtl" | "ltr",
  setLocale: (_locale: Locale) => undefined,
  t: (key: MessageKey) => ar[key],
});

export function useI18n() {
  return useContext(LocaleContext);
}

function LocaleProvider({ initialLocale, children }: { initialLocale: Locale; children: ReactNode }) {
  const [locale, setLocaleState] = useState(initialLocale);

  const setLocale = useCallback((next: Locale) => {
    const root = document.documentElement;
    root.lang = next;
    root.dir = next === "ar" ? "rtl" : "ltr";
    root.dataset.locale = next;
    try { window.localStorage.setItem("snad-locale", next); } catch {}
    document.cookie = `snad-locale=${next}; Path=/; Max-Age=31536000; SameSite=Lax`;
    setLocaleState(next);
  }, []);

  useEffect(() => {
    const listener = (event: StorageEvent) => {
      if (event.key === "snad-locale" && (event.newValue === "ar" || event.newValue === "en")) {
        setLocale(event.newValue);
      }
    };
    window.addEventListener("storage", listener);
    return () => window.removeEventListener("storage", listener);
  }, [setLocale]);

  const value = useMemo(() => ({
    locale,
    direction: locale === "ar" ? "rtl" as const : "ltr" as const,
    setLocale,
    t: (key: MessageKey) => catalogs[locale][key],
  }), [locale, setLocale]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function Providers({ children, initialLocale = "ar" }: { children: ReactNode; initialLocale?: Locale }) {
  return (
    <LocaleProvider initialLocale={initialLocale}>
      <AuthProvider>
        <TenantContextProvider>{children}</TenantContextProvider>
      </AuthProvider>
    </LocaleProvider>
  );
}
