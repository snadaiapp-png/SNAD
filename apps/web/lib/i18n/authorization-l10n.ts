import type { Locale } from "./types";

const en = {
  title: "Authorization",
  subtitle: "Roles, direct permissions, data scopes and effective access.",
  noAccess: "You do not have permission to view authorization administration.",
  users: "Users",
  roles: "Roles",
  capabilities: "Capabilities",
  policies: "Policies & scopes",
  audit: "Access audit",
  userId: "User ID",
  openUser: "Open user access",
  overview: "Overview",
  directPermissions: "Direct permissions",
  dataScopes: "Data scopes",
  effectivePermissions: "Effective permissions",
  resync: "Resync effective access",
  loading: "Loading authorization data…",
  empty: "No authorization records found.",
  source: "Source",
  scope: "Scope",
  capability: "Capability",
  effect: "Effect",
  relationship: "Relationship",
  refresh: "Refresh",
} as const;

const ar: Record<keyof typeof en, string> = {
  title: "التفويض والصلاحيات",
  subtitle: "الأدوار والصلاحيات المباشرة ونطاقات البيانات والوصول الفعلي.",
  noAccess: "لا تملك صلاحية عرض إدارة التفويض.",
  users: "المستخدمون",
  roles: "الأدوار",
  capabilities: "الصلاحيات",
  policies: "السياسات والنطاقات",
  audit: "سجل الوصول",
  userId: "معرّف المستخدم",
  openUser: "فتح صلاحيات المستخدم",
  overview: "نظرة عامة",
  directPermissions: "الصلاحيات المباشرة",
  dataScopes: "نطاقات البيانات",
  effectivePermissions: "الصلاحيات الفعلية",
  resync: "إعادة مزامنة الوصول الفعلي",
  loading: "جارٍ تحميل بيانات التفويض…",
  empty: "لا توجد سجلات تفويض.",
  source: "المصدر",
  scope: "النطاق",
  capability: "الصلاحية",
  effect: "الأثر",
  relationship: "العلاقة",
  refresh: "تحديث",
};

export type AuthorizationTextKey = keyof typeof en;

export function authorizationText(locale: Locale, key: AuthorizationTextKey): string {
  return (locale === "ar" ? ar : en)[key];
}

export const authorizationLocaleParity = {
  en: Object.keys(en).sort(),
  ar: Object.keys(ar).sort(),
};
