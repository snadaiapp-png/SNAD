import { getLocales } from "expo-localization";
import i18n from "i18next";
import { initReactI18next } from "react-i18next";

const resources = {
  ar: {
    translation: {
      title: "SNAD CRM",
      subtitle: "بيئة تطبيقات Android وiPhone جاهزة للاتصال التلقائي.",
      environment: "البيئة",
      api: "واجهة API",
      eas: "EAS",
      configured: "مهيأ",
      pending: "بانتظار الإعداد",
      online: "متصل",
      offline: "غير متصل",
      database: "قاعدة البيانات المحلية",
      ready: "جاهزة",
    },
  },
  en: {
    translation: {
      title: "SNAD CRM",
      subtitle: "Android and iPhone environment ready for automatic activation.",
      environment: "Environment",
      api: "API",
      eas: "EAS",
      configured: "Configured",
      pending: "Pending configuration",
      online: "Online",
      offline: "Offline",
      database: "Local database",
      ready: "Ready",
    },
  },
} as const;

const language = getLocales()[0]?.languageCode === "ar" ? "ar" : "en";

void i18n.use(initReactI18next).init({
  compatibilityJSON: "v4",
  resources,
  lng: language,
  fallbackLng: "en",
  interpolation: { escapeValue: false },
});

export { i18n };
