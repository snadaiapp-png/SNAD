import type { Locale } from "./types";

const ROLE_LABELS: Record<string, { ar: string; en: string }> = {
  TENANT_ADMIN: { ar: "مسؤول المستأجر", en: "Tenant Administrator" },
  ORG_ADMIN: { ar: "مسؤول المؤسسة", en: "Organization Administrator" },
  OWNER: { ar: "المالك", en: "Owner" },
  ADMIN: { ar: "مسؤول", en: "Administrator" },
  MANAGER: { ar: "مدير", en: "Manager" },
  USER: { ar: "مستخدم", en: "User" },
  HR_ADMIN: { ar: "مسؤول الموارد البشرية", en: "HR Administrator" },
  HR_MANAGER: { ar: "مدير الموارد البشرية", en: "HR Manager" },
  CRM_ADMIN: { ar: "مسؤول إدارة علاقات العملاء", en: "CRM Administrator" },
  SALES_MANAGER: { ar: "مدير المبيعات", en: "Sales Manager" },
  ACCOUNTANT: { ar: "محاسب", en: "Accountant" },
};

const CAPABILITY_LABELS: Record<string, { ar: string; en: string }> = {
  "USER.READ": { ar: "عرض المستخدمين", en: "Read users" },
  "USER.CREATE": { ar: "إنشاء المستخدمين", en: "Create users" },
  "USER.WRITE": { ar: "تعديل المستخدمين", en: "Edit users" },
  "USER.DELETE": { ar: "أرشفة المستخدمين", en: "Archive users" },
  "USER.GRANT_ROLE": { ar: "إسناد الأدوار للمستخدمين", en: "Assign roles to users" },
  "USER.REVOKE_ROLE": { ar: "سحب الأدوار من المستخدمين", en: "Revoke roles from users" },
  "MEMBERSHIP.READ": { ar: "عرض العضويات", en: "Read memberships" },
  "MEMBERSHIP.MANAGE": { ar: "إدارة العضويات", en: "Manage memberships" },
  "ROLE.READ": { ar: "عرض الأدوار", en: "Read roles" },
  "ROLE.MANAGE": { ar: "إدارة الأدوار", en: "Manage roles" },
  "CAPABILITY.READ": { ar: "عرض الصلاحيات", en: "Read capabilities" },
  "CAPABILITY.MANAGE": { ar: "إدارة الصلاحيات", en: "Manage capabilities" },
};

const AR_TOKENS: Record<string, string> = {
  USER: "المستخدمين",
  USERS: "المستخدمين",
  MEMBERSHIP: "العضويات",
  MEMBERSHIPS: "العضويات",
  ROLE: "الأدوار",
  ROLES: "الأدوار",
  CAPABILITY: "الصلاحيات",
  CAPABILITIES: "الصلاحيات",
  TENANT: "المستأجر",
  ORGANIZATION: "المؤسسة",
  ORG: "المؤسسة",
  HR: "الموارد البشرية",
  CRM: "إدارة علاقات العملاء",
  AGENT: "وكيل",
  ERP: "تخطيط الموارد",
  ACCOUNTING: "المحاسبة",
  FINANCE: "المالية",
  WORKFLOW: "سير العمل",
  POS: "نقاط البيع",
  ECOMMERCE: "التجارة الإلكترونية",
  SUBSCRIPTION: "الاشتراكات",
  BILLING: "الفوترة",
  EXECUTIVE: "الإدارة التنفيذية",
  SYSTEM: "النظام",
  READ: "عرض",
  VIEW: "عرض",
  CREATE: "إنشاء",
  WRITE: "تعديل",
  UPDATE: "تعديل",
  DELETE: "حذف",
  ARCHIVE: "أرشفة",
  MANAGE: "إدارة",
  ADMIN: "إدارة",
  GRANT: "إسناد",
  REVOKE: "سحب",
  APPROVE: "اعتماد",
  EXPORT: "تصدير",
  IMPORT: "استيراد",
};

function titleCase(value: string): string {
  return value
    .toLowerCase()
    .replace(/(^|[\s_-])([a-z])/g, (_, prefix: string, letter: string) => `${prefix}${letter.toUpperCase()}`)
    .replace(/[_-]+/g, " ");
}

function fallbackLabel(code: string | null | undefined, locale: Locale): string {
  if (!code?.trim()) return locale === "ar" ? "غير محدد" : "Not specified";
  const tokens = code.split(/[._-]+/).filter(Boolean);
  if (locale === "en") return titleCase(tokens.join(" "));
  return tokens.map((token) => AR_TOKENS[token.toUpperCase()] ?? token).join(" ");
}

export function roleDisplayName(
  code: string,
  backendName: string | null | undefined,
  locale: Locale,
): string {
  const known = ROLE_LABELS[code.toUpperCase()];
  if (known) return known[locale];
  if (locale === "en" && backendName?.trim()) return backendName.trim();
  return fallbackLabel(code, locale);
}

export function capabilityDisplayName(
  code: string,
  backendName: string | null | undefined,
  locale: Locale,
): string {
  const known = CAPABILITY_LABELS[code.toUpperCase()];
  if (known) return known[locale];
  if (locale === "en" && backendName?.trim()) return backendName.trim();
  if (locale === "ar" && /^[0-9a-f-]{8,}$/i.test(code)) return `صلاحية ${code}`;
  return fallbackLabel(code, locale);
}

export function statusDisplayName(status: string | null | undefined, locale: Locale): string {
  const normalized = status?.toUpperCase() ?? "";
  const labels: Record<string, { ar: string; en: string }> = {
    ACTIVE: { ar: "نشط", en: "Active" },
    INACTIVE: { ar: "غير نشط", en: "Inactive" },
    INVITED: { ar: "مدعو", en: "Invited" },
    SUSPENDED: { ar: "موقوف", en: "Suspended" },
    ARCHIVED: { ar: "مؤرشف", en: "Archived" },
    REVOKED: { ar: "مسحوب", en: "Revoked" },
  };
  return labels[normalized]?.[locale] ?? fallbackLabel(status, locale);
}

export function scopeDisplayName(scope: string | null | undefined, locale: Locale): string {
  const normalized = scope?.toUpperCase() ?? "";
  const labels: Record<string, { ar: string; en: string }> = {
    TENANT: { ar: "المستأجر", en: "Tenant" },
    ORGANIZATION: { ar: "المؤسسة", en: "Organization" },
    GLOBAL: { ar: "عام", en: "Global" },
    USER: { ar: "المستخدم", en: "User" },
    RESOURCE: { ar: "المورد", en: "Resource" },
  };
  return labels[normalized]?.[locale] ?? fallbackLabel(scope, locale);
}

export function effectDisplayName(effect: string | null | undefined, locale: Locale): string {
  const normalized = effect?.toUpperCase() ?? "";
  if (normalized === "ALLOW") return locale === "ar" ? "سماح" : "Allow";
  if (normalized === "DENY") return locale === "ar" ? "منع" : "Deny";
  return fallbackLabel(effect, locale);
}

export function sourceDisplayName(source: string | null | undefined, locale: Locale): string {
  const normalized = source?.toUpperCase() ?? "";
  const labels: Record<string, { ar: string; en: string }> = {
    ROLE: { ar: "دور", en: "Role" },
    OVERRIDE: { ar: "استثناء مباشر", en: "Override" },
    BREAK_GLASS: { ar: "وصول طارئ", en: "Break-glass access" },
    DIRECT: { ar: "مباشر", en: "Direct" },
    POLICY: { ar: "سياسة", en: "Policy" },
    SYSTEM: { ar: "النظام", en: "System" },
  };
  return labels[normalized]?.[locale] ?? fallbackLabel(source, locale);
}
