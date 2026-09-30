/**
 * Pure validation logic shared between the executive tenants page (static,
 * used before submit) and the dynamically loaded tenant dialogs (rendering).
 * Kept free of React/JSX so the statically imported surface stays minimal.
 */

const COUNTRY_PATTERN = /^[A-Z]{2}$/;
const CURRENCY_PATTERN = /^[A-Z]{3}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export type TenantDialog =
  | { kind: "create" }
  | { kind: "edit"; tenantId: string }
  | { kind: "status"; tenantId: string; targetStatus: "SUSPENDED" | "ACTIVE" | "ARCHIVED"; sourceStatus: string }
  | null;

type Translate = (key: string) => string;

export function validateCreateTenant(
  form: {
    name: string; subdomain: string; adminEmail: string; adminDisplayName: string;
    countryCode: string; currencyCode: string;
  },
  t: Translate,
): string {
  if (!form.name.trim()) return t("scp.tenants.validation.nameRequired");
  if (!/^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$/.test(form.subdomain.trim().toLowerCase())) {
    return t("scp.tenants.validation.subdomainInvalid");
  }
  if (!EMAIL_PATTERN.test(form.adminEmail.trim())) {
    return t("scp.tenants.validation.adminEmailInvalid");
  }
  if (!form.adminDisplayName.trim()) return t("scp.tenants.validation.adminDisplayNameRequired");
  if (form.countryCode.trim() && !COUNTRY_PATTERN.test(form.countryCode.trim().toUpperCase())) {
    return t("scp.tenants.validation.countryInvalid");
  }
  if (form.currencyCode.trim() && !CURRENCY_PATTERN.test(form.currencyCode.trim().toUpperCase())) {
    return t("scp.tenants.validation.currencyInvalid");
  }
  return "";
}

export function validateEditTenant(
  form: { name: string; billingEmail: string; countryCode: string; currencyCode: string },
  t: Translate,
): string {
  if (!form.name.trim()) return t("scp.tenants.validation.nameRequired");
  if (form.billingEmail.trim() && !EMAIL_PATTERN.test(form.billingEmail.trim())) {
    return t("scp.tenants.validation.billingEmailInvalid");
  }
  if (form.countryCode.trim() && !COUNTRY_PATTERN.test(form.countryCode.trim().toUpperCase())) {
    return t("scp.tenants.validation.countryInvalid");
  }
  if (form.currencyCode.trim() && !CURRENCY_PATTERN.test(form.currencyCode.trim().toUpperCase())) {
    return t("scp.tenants.validation.currencyInvalid");
  }
  return "";
}
